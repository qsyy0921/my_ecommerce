package cn.bugstack.infrastructure.adapter.repository;

import cn.bugstack.domain.seckill.adapter.port.ISeckillMaintenancePort;
import cn.bugstack.domain.seckill.adapter.repository.ISeckillRepository;
import cn.bugstack.domain.seckill.adapter.port.ISeckillMetricsPort;
import cn.bugstack.domain.seckill.adapter.port.ISeckillResultCachePort;
import cn.bugstack.domain.seckill.adapter.port.ISeckillStockFlowPort;
import cn.bugstack.domain.seckill.adapter.port.ISeckillStockReservationPort;
import cn.bugstack.domain.seckill.model.entity.SeckillActivityEntity;
import cn.bugstack.domain.seckill.model.entity.SeckillOrderEntity;
import cn.bugstack.domain.seckill.model.entity.SeckillStockReservationEntity;
import cn.bugstack.domain.seckill.model.entity.SeckillStockFlowEntity;
import cn.bugstack.domain.seckill.model.valobj.SeckillOrderStatusEnumVO;
import cn.bugstack.domain.shared.adapter.port.IOrderStateFlowPort;
import cn.bugstack.domain.shared.model.entity.OrderStateTransitionEntity;
import cn.bugstack.infrastructure.dao.ISeckillActivityDao;
import cn.bugstack.infrastructure.dao.ISeckillOrderDao;
import cn.bugstack.infrastructure.dao.ISkuDao;
import cn.bugstack.infrastructure.adapter.support.SeckillOrderShardRouter;
import cn.bugstack.infrastructure.dao.po.SeckillActivity;
import cn.bugstack.infrastructure.dao.po.SeckillOrder;
import cn.bugstack.infrastructure.dao.po.Sku;
import cn.bugstack.infrastructure.event.EventPublisher;
import cn.bugstack.infrastructure.event.SeckillFaultInjector;
import cn.bugstack.infrastructure.event.SeckillOrderCreateBuffer;
import cn.bugstack.infrastructure.event.SeckillStreamMetrics;
import cn.bugstack.types.enums.ResponseCode;
import cn.bugstack.types.exception.AppException;
import com.alibaba.fastjson.JSON;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Seckill repository adapter.
 */
@Slf4j
@Repository
public class SeckillRepository implements ISeckillRepository, ISeckillMaintenancePort {

    @Value("${app.seckill.activity-cache-ttl-millis:3000}")
    private Long activityCacheTtlMillis;
    @Value("${app.seckill.sold-out-cache-ttl-millis:5000}")
    private Long soldOutCacheTtlMillis;
    @Value("${app.seckill.stock-init-lock-wait-millis:200}")
    private Long stockInitLockWaitMillis;
    @Value("${app.seckill.stock-bucket-try-count:64}")
    private Integer stockBucketTryCount;
    @Value("${spring.rabbitmq.config.producer.topic_seckill_order_create.routing_key}")
    private String topicSeckillOrderCreate;

    @Resource
    private ISeckillActivityDao seckillActivityDao;
    @Resource
    private ISeckillOrderDao seckillOrderDao;
    @Resource
    private IOrderStateFlowPort orderStateFlowPort;
    @Resource
    private ISeckillStockFlowPort seckillStockFlowPort;
    @Resource
    private ISeckillResultCachePort seckillResultCachePort;
    @Resource
    private ISeckillStockReservationPort seckillStockReservationPort;
    @Resource
    private SeckillOrderShardRouter seckillOrderShardRouter;
    @Resource
    private ISkuDao skuDao;
    @Resource
    private EventPublisher eventPublisher;
    @Resource
    private SeckillOrderCreateBuffer seckillOrderCreateBuffer;
    @Resource
    private SeckillStreamMetrics seckillStreamMetrics;
    @Resource
    private SeckillFaultInjector seckillFaultInjector;
    @Resource
    private ISeckillMetricsPort seckillMetricsPort;

    private final ConcurrentHashMap<String, ActivityCacheEntry> activityCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, Long> soldOutCache = new ConcurrentHashMap<>();

    @Override
    public SeckillActivityEntity querySeckillActivity(Long activityId, String source, String channel, String goodsId) {
        String cacheKey = activityCacheKey(activityId, source, channel, goodsId);
        ActivityCacheEntry cacheEntry = activityCache.get(cacheKey);
        long now = System.currentTimeMillis();
        if (null != cacheEntry && cacheEntry.getExpireTime() > now) {
            return cacheEntry.getSeckillActivityEntity();
        }

        SeckillActivity seckillActivityReq = SeckillActivity.builder()
                .activityId(activityId)
                .source(source)
                .channel(channel)
                .goodsId(goodsId)
                .build();
        SeckillActivity seckillActivity = seckillActivityDao.querySeckillActivity(seckillActivityReq);
        if (null == seckillActivity) return null;

        Sku sku = skuDao.querySkuByGoodsId(seckillActivity.getGoodsId());
        if (null == sku) return null;

        SeckillActivityEntity seckillActivityEntity = SeckillActivityEntity.builder()
                .activityId(seckillActivity.getActivityId())
                .activityName(seckillActivity.getActivityName())
                .source(seckillActivity.getSource())
                .channel(seckillActivity.getChannel())
                .goodsId(seckillActivity.getGoodsId())
                .goodsName(sku.getGoodsName())
                .originalPrice(sku.getOriginalPrice())
                .seckillPrice(seckillActivity.getSeckillPrice())
                .totalCount(seckillActivity.getTotalCount())
                .availableCount(seckillActivity.getAvailableCount())
                .lockCount(seckillActivity.getLockCount())
                .takeLimitCount(seckillActivity.getTakeLimitCount())
                .status(seckillActivity.getStatus())
                .startTime(seckillActivity.getStartTime())
                .endTime(seckillActivity.getEndTime())
                .build();
        if (activityCacheTtlMillis > 0) {
            activityCache.put(cacheKey, new ActivityCacheEntry(seckillActivityEntity, now + activityCacheTtlMillis));
        }
        return seckillActivityEntity;
    }

    @Override
    public Integer queryAvailableStock(Long activityId) {
        if (isLocalSoldOut(activityId)) {
            return 0;
        }
        if (seckillStockReservationPort.isStockInitialized(activityId)) {
            int stock = seckillStockReservationPort.queryStock(activityId);
            if (stock <= 0) {
                markLocalSoldOut(activityId);
            } else {
                clearLocalSoldOut(activityId);
            }
            return stock;
        }

        boolean locked = false;
        try {
            locked = seckillStockReservationPort.tryAcquireInitializationLock(activityId, stockInitLockWaitMillis, 10_000);
            if (!locked) {
                if (seckillStockReservationPort.isStockInitialized(activityId)) {
                    return seckillStockReservationPort.queryStock(activityId);
                }
                throw new AppException(ResponseCode.E0205);
            }
            if (seckillStockReservationPort.isStockInitialized(activityId)) {
                return seckillStockReservationPort.queryStock(activityId);
            }

            SeckillActivity seckillActivity = seckillActivityDao.querySeckillActivity(SeckillActivity.builder().activityId(activityId).build());
            if (null == seckillActivity) {
                throw new AppException(ResponseCode.E0201);
            }
            seckillStockReservationPort.initializeStock(activityId, seckillActivity.getAvailableCount());
            if (seckillActivity.getAvailableCount() <= 0) {
                markLocalSoldOut(activityId);
            } else {
                clearLocalSoldOut(activityId);
            }
            return seckillActivity.getAvailableCount();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AppException(ResponseCode.E0205);
        } finally {
            if (locked) {
                seckillStockReservationPort.releaseInitializationLock(activityId);
            }
        }
    }

    @Override
    public SeckillOrderEntity querySeckillOrderByOutTradeNo(String userId, String outTradeNo) {
        SeckillOrder seckillOrderReq = SeckillOrder.builder()
                .userId(userId)
                .outTradeNo(outTradeNo)
                .build();
        SeckillOrder seckillOrder;
        if (seckillOrderShardRouter.useSharding()) {
            seckillOrder = seckillOrderDao.querySeckillOrderByOutTradeNoFromTable(seckillOrderShardRouter.tableName(userId, outTradeNo), seckillOrderReq);
        } else {
            seckillOrder = seckillOrderDao.querySeckillOrderByOutTradeNo(seckillOrderReq);
        }
        return buildSeckillOrderEntity(seckillOrder);
    }

    @Override
    public SeckillOrderEntity querySeckillResult(String userId, Long activityId, String outTradeNo) {
        SeckillOrderEntity result = seckillResultCachePort.query(activityId, userId, outTradeNo);
        if (null != result) {
            return result;
        }

        SeckillOrderEntity existsOrder = querySeckillOrderByOutTradeNo(userId, outTradeNo);
        if (null != existsOrder) {
            seckillResultCachePort.cache(existsOrder, SeckillOrderEntity.RESULT_SUCCESS, "order created");
            return existsOrder;
        }

        return SeckillOrderEntity.builder()
                .userId(userId)
                .activityId(activityId)
                .outTradeNo(outTradeNo)
                .resultStatus(SeckillOrderEntity.RESULT_NOT_FOUND)
                .message("seckill order not found")
                .build();
    }

    @Override
    public SeckillOrderEntity lockSeckillOrder(SeckillOrderEntity seckillOrderEntity) {
        Long activityId = seckillOrderEntity.getActivityId();
        seckillOrderEntity.setTraceId(MDC.get("trace-id"));
        if (isLocalSoldOut(activityId)) {
            seckillMetricsPort.recordStockNotEnough();
            throw new AppException(ResponseCode.E0203);
        }
        ensureStockInitialized(activityId);

        SeckillStockReservationEntity reservation = seckillStockReservationPort.reserve(seckillOrderEntity, stockBucketTryCount);
        if (reservation.isDuplicate()) {
            seckillMetricsPort.recordDuplicate();
            throw new AppException(ResponseCode.E0204);
        }
        if (reservation.isSuccess()) {
            try {
                enqueueOrderCreate(seckillOrderEntity);
                return seckillOrderEntity;
            } catch (RuntimeException e) {
                rollbackReservation(seckillOrderEntity, true, "enqueue order create failed");
                throw e;
            }
        }

        if (queryAvailableStock(activityId) <= 0) {
            markLocalSoldOut(activityId);
            seckillMetricsPort.recordStockNotEnough();
        }
        throw new AppException(ResponseCode.E0203);
    }

    private void enqueueOrderCreate(SeckillOrderEntity seckillOrderEntity) {
        String message = JSON.toJSONString(seckillOrderEntity);
        if (seckillOrderCreateBuffer.useMq()) {
            eventPublisher.publishWithoutConfirm(topicSeckillOrderCreate, message);
            return;
        }
        String routeKey = seckillOrderEntity.getActivityId() + ":" + seckillOrderEntity.getUserId() + ":" + seckillOrderEntity.getOutTradeNo();
        boolean offered = seckillOrderCreateBuffer.offer(message, routeKey);
        if (!offered) {
            throw new AppException(ResponseCode.RATE_LIMITER);
        }
    }

    private void ensureStockInitialized(Long activityId) {
        if (seckillStockReservationPort.isStockInitialized(activityId)) {
            return;
        }
        queryAvailableStock(activityId);
    }

    @Transactional(timeout = 5)
    @Override
    public void createSeckillOrder(SeckillOrderEntity seckillOrderEntity) {
        SeckillOrderEntity existsOrder = querySeckillOrderByOutTradeNo(seckillOrderEntity.getUserId(), seckillOrderEntity.getOutTradeNo());
        if (null != existsOrder) {
            seckillResultCachePort.cache(existsOrder, SeckillOrderEntity.RESULT_SUCCESS, "order already created");
            return;
        }

        try {
            SeckillOrder seckillOrder = SeckillOrder.builder()
                    .userId(seckillOrderEntity.getUserId())
                    .activityId(seckillOrderEntity.getActivityId())
                    .activityName(seckillOrderEntity.getActivityName())
                    .goodsId(seckillOrderEntity.getGoodsId())
                    .goodsName(seckillOrderEntity.getGoodsName())
                    .source(seckillOrderEntity.getSource())
                    .channel(seckillOrderEntity.getChannel())
                    .orderId(seckillOrderEntity.getOrderId())
                    .outTradeNo(seckillOrderEntity.getOutTradeNo())
                    .originalPrice(seckillOrderEntity.getOriginalPrice())
                    .seckillPrice(seckillOrderEntity.getSeckillPrice())
                    .status(seckillOrderEntity.getStatus())
                    .build();
            insertSeckillOrder(seckillOrder);
            seckillStockFlowPort.record(SeckillStockFlowEntity.reserved(seckillOrderEntity, "order created"));
            orderStateFlowPort.record(OrderStateTransitionEntity.seckillOrderCreated(
                    seckillOrderEntity.getOutTradeNo(),
                    seckillOrderEntity.getOrderId(),
                    seckillOrderEntity.getUserId(),
                    seckillOrderEntity.getTraceId(),
                    seckillOrderEntity.getSourceMessageId()));
            seckillResultCachePort.cache(seckillOrderEntity, SeckillOrderEntity.RESULT_SUCCESS, "order created");
        } catch (DuplicateKeyException e) {
            SeckillOrderEntity duplicateOrder = querySeckillOrderByOutTradeNo(seckillOrderEntity.getUserId(), seckillOrderEntity.getOutTradeNo());
            if (null != duplicateOrder) {
                seckillResultCachePort.cache(duplicateOrder, SeckillOrderEntity.RESULT_SUCCESS, "order already created");
                return;
            }
            seckillResultCachePort.cache(seckillOrderEntity, SeckillOrderEntity.RESULT_DUPLICATE, "duplicate seckill order");
            rollbackReservation(seckillOrderEntity, false, "duplicate seckill order");
        } catch (AppException e) {
            throw e;
        } catch (RuntimeException e) {
            seckillResultCachePort.cache(seckillOrderEntity, SeckillOrderEntity.RESULT_FAIL, e.getMessage());
            rollbackReservation(seckillOrderEntity, false, e.getMessage());
            throw e;
        }
    }

    @Transactional(timeout = 10)
    @Override
    public void createSeckillOrders(List<SeckillOrderEntity> seckillOrderEntities) {
        if (null == seckillOrderEntities || seckillOrderEntities.isEmpty()) {
            return;
        }

        List<SeckillOrder> seckillOrders = new ArrayList<>(seckillOrderEntities.size());
        for (SeckillOrderEntity seckillOrderEntity : seckillOrderEntities) {
            seckillOrders.add(buildSeckillOrder(seckillOrderEntity));
        }

        seckillFaultInjector.beforeBatchInsert();
        long startNanos = System.nanoTime();
        try {
            insertSeckillOrders(seckillOrders);
        } finally {
            seckillStreamMetrics.recordBatchInsert(System.nanoTime() - startNanos, seckillOrderEntities.size());
        }

        List<SeckillStockFlowEntity> stockFlows = new ArrayList<>(seckillOrderEntities.size());
        for (SeckillOrderEntity seckillOrderEntity : seckillOrderEntities) {
            SeckillOrderEntity existsOrder = querySeckillOrderByOutTradeNo(seckillOrderEntity.getUserId(), seckillOrderEntity.getOutTradeNo());
            if (null == existsOrder) {
                seckillResultCachePort.cache(seckillOrderEntity, SeckillOrderEntity.RESULT_DUPLICATE, "order create ignored by unique constraint");
                rollbackReservation(
                        seckillOrderEntity,
                        false,
                        "batch order create ignored by unique constraint");
                continue;
            }

            stockFlows.add(SeckillStockFlowEntity.reserved(seckillOrderEntity, "order created"));
            orderStateFlowPort.record(OrderStateTransitionEntity.seckillOrderCreated(
                    seckillOrderEntity.getOutTradeNo(),
                    seckillOrderEntity.getOrderId(),
                    seckillOrderEntity.getUserId(),
                    seckillOrderEntity.getTraceId(),
                    seckillOrderEntity.getSourceMessageId()));
            seckillResultCachePort.cache(existsOrder, SeckillOrderEntity.RESULT_SUCCESS, "order created");
        }
        if (!stockFlows.isEmpty()) {
            seckillStockFlowPort.recordBatch(stockFlows);
        }
    }

    @Transactional(timeout = 5)
    @Override
    public SeckillOrderEntity settlementSeckillOrder(String userId, String outTradeNo) {
        SeckillOrder seckillOrder = querySeckillOrderPo(userId, outTradeNo);
        if (null == seckillOrder) {
            throw new AppException(ResponseCode.E0206);
        }

        SeckillOrderStatusEnumVO status = SeckillOrderStatusEnumVO.valueOf(seckillOrder.getStatus());
        if (SeckillOrderStatusEnumVO.COMPLETE.equals(status)) {
            SeckillOrderEntity entity = buildSeckillOrderEntity(seckillOrder);
            seckillResultCachePort.cache(entity, SeckillOrderEntity.RESULT_SUCCESS, "seckill order already paid");
            return entity;
        }
        if (!status.canPay()) {
            throw new AppException(ResponseCode.E0207);
        }

        int updated = seckillOrderShardRouter.useSharding()
                ? seckillOrderDao.paySuccessOrderFromTable(seckillOrderShardRouter.tableName(userId, outTradeNo), seckillOrder.getOrderId())
                : seckillOrderDao.paySuccessOrder(seckillOrder.getOrderId());
        if (updated <= 0) {
            SeckillOrder latest = querySeckillOrderPo(userId, outTradeNo);
            if (null != latest && SeckillOrderStatusEnumVO.COMPLETE.equals(SeckillOrderStatusEnumVO.valueOf(latest.getStatus()))) {
                SeckillOrderEntity entity = buildSeckillOrderEntity(latest);
                seckillResultCachePort.cache(entity, SeckillOrderEntity.RESULT_SUCCESS, "seckill order already paid");
                return entity;
            }
            throw new AppException(ResponseCode.UPDATE_ZERO);
        }

        orderStateFlowPort.record(OrderStateTransitionEntity.seckillOrderPaid(
                seckillOrder.getOutTradeNo(),
                seckillOrder.getOrderId(),
                seckillOrder.getUserId(),
                MDC.get("trace-id")));
        SeckillOrder updatedOrder = querySeckillOrderPo(userId, outTradeNo);
        SeckillOrderEntity entity = buildSeckillOrderEntity(updatedOrder);
        seckillResultCachePort.cache(entity, SeckillOrderEntity.RESULT_SUCCESS, "seckill order paid");
        return entity;
    }

    @Transactional(timeout = 5)
    @Override
    public SeckillOrderEntity refundSeckillOrder(String userId, String outTradeNo, String refundReason) {
        SeckillOrder seckillOrder = querySeckillOrderPo(userId, outTradeNo);
        if (null == seckillOrder) {
            throw new AppException(ResponseCode.E0206);
        }

        SeckillOrderStatusEnumVO status = SeckillOrderStatusEnumVO.valueOf(seckillOrder.getStatus());
        if (SeckillOrderStatusEnumVO.REFUND.equals(status) || SeckillOrderStatusEnumVO.CLOSE.equals(status)) {
            SeckillOrderEntity entity = buildSeckillOrderEntity(seckillOrder);
            seckillResultCachePort.cache(entity, SeckillOrderEntity.RESULT_SUCCESS, "seckill refund already handled");
            return entity;
        }

        if (SeckillOrderStatusEnumVO.CREATE.equals(status)) {
            int updated = seckillOrderShardRouter.useSharding()
                    ? seckillOrderDao.closeUnpaidOrderFromTable(seckillOrderShardRouter.tableName(userId, outTradeNo), seckillOrder.getOrderId())
                    : seckillOrderDao.closeUnpaidOrder(seckillOrder.getOrderId());
            if (updated <= 0) {
                throw new AppException(ResponseCode.UPDATE_ZERO);
            }
            String message = null == refundReason ? "unpaid seckill order canceled" : refundReason;
            releaseSeckillStock(seckillOrder, SeckillStockFlowEntity.ROLLBACK_CANCEL, 1, message);
            orderStateFlowPort.record(OrderStateTransitionEntity.seckillUnpaidCanceled(
                    seckillOrder.getOutTradeNo(),
                    seckillOrder.getOrderId(),
                    seckillOrder.getUserId(),
                    MDC.get("trace-id"),
                    message));
        } else if (SeckillOrderStatusEnumVO.COMPLETE.equals(status)) {
            int updated = seckillOrderShardRouter.useSharding()
                    ? seckillOrderDao.refundPaidOrderFromTable(seckillOrderShardRouter.tableName(userId, outTradeNo), seckillOrder.getOrderId())
                    : seckillOrderDao.refundPaidOrder(seckillOrder.getOrderId());
            if (updated <= 0) {
                throw new AppException(ResponseCode.UPDATE_ZERO);
            }
            String message = null == refundReason ? "paid seckill order refunded" : refundReason;
            releaseSeckillStock(seckillOrder, SeckillStockFlowEntity.ROLLBACK_REFUND, 1, message);
            orderStateFlowPort.record(OrderStateTransitionEntity.seckillOrderRefunded(
                    seckillOrder.getOutTradeNo(),
                    seckillOrder.getOrderId(),
                    seckillOrder.getUserId(),
                    MDC.get("trace-id"),
                    message));
        } else {
            throw new AppException(ResponseCode.E0207);
        }

        SeckillOrder updatedOrder = querySeckillOrderPo(userId, outTradeNo);
        SeckillOrderEntity entity = buildSeckillOrderEntity(updatedOrder);
        seckillResultCachePort.cache(entity, SeckillOrderEntity.RESULT_SUCCESS, "seckill refund handled");
        return entity;
    }

    @Override
    public void syncSeckillActivityStock() {
        List<Long> activityIds = seckillActivityDao.queryStockSyncActivityIds();
        if (null == activityIds || activityIds.isEmpty()) {
            return;
        }
        for (Long activityId : activityIds) {
            try {
                if (!seckillOrderShardRouter.useSharding()) {
                    seckillActivityDao.syncStockByOrderCount(activityId);
                    continue;
                }
                int activeCount = 0;
                for (int shardIndex = 0; shardIndex < seckillOrderShardRouter.shardCount(); shardIndex++) {
                    activeCount += seckillOrderDao.countActiveOrdersFromTable(seckillOrderShardRouter.tableName(shardIndex), activityId);
                }
                seckillActivityDao.syncStockByActiveCount(activityId, activeCount);
            } catch (Exception e) {
                log.error("sync seckill activity stock failed activityId:{}", activityId, e);
            }
        }
    }

    @Override
    public int releaseTimeoutUnpaidOrders() {
        if (!seckillOrderShardRouter.useSharding()) {
            return releaseTimeoutUnpaidOrders(seckillOrderDao.queryTimeoutUnpaidOrders(), null);
        }

        int count = 0;
        for (int shardIndex = 0; shardIndex < seckillOrderShardRouter.shardCount(); shardIndex++) {
            String tableName = seckillOrderShardRouter.tableName(shardIndex);
            count += releaseTimeoutUnpaidOrders(seckillOrderDao.queryTimeoutUnpaidOrdersFromTable(tableName), tableName);
        }
        return count;
    }

    @Override
    public int prewarmUpcomingActivities(Integer beforeMinutes, Integer limit) {
        int safeBeforeMinutes = Math.max(0, null == beforeMinutes ? 10 : beforeMinutes);
        int safeLimit = Math.max(1, Math.min(null == limit ? 50 : limit, 500));
        List<SeckillActivity> activities = seckillActivityDao.queryPrewarmActivities(safeBeforeMinutes, safeLimit);
        if (null == activities || activities.isEmpty()) {
            return 0;
        }

        int count = 0;
        for (SeckillActivity activity : activities) {
            try {
                SeckillActivityEntity activityEntity = querySeckillActivity(
                        activity.getActivityId(),
                        activity.getSource(),
                        activity.getChannel(),
                        activity.getGoodsId());
                if (null == activityEntity) {
                    continue;
                }
                queryAvailableStock(activity.getActivityId());
                count++;
            } catch (Exception e) {
                log.error("prewarm seckill activity failed activityId:{}", activity.getActivityId(), e);
            }
        }
        return count;
    }

    private int releaseTimeoutUnpaidOrders(List<SeckillOrder> timeoutOrders, String tableName) {
        if (null == timeoutOrders || timeoutOrders.isEmpty()) {
            return 0;
        }
        int count = 0;
        for (SeckillOrder order : timeoutOrders) {
            int updated = null == tableName
                    ? seckillOrderDao.closeTimeoutUnpaidOrder(order.getOrderId())
                    : seckillOrderDao.closeTimeoutUnpaidOrderFromTable(tableName, order.getOrderId());
            if (updated <= 0) {
                continue;
            }
            seckillActivityDao.updateReleaseStock(order.getActivityId());
            SeckillOrderEntity entity = buildSeckillOrderEntity(order);
            seckillStockReservationPort.release(entity, 1);
            seckillStockFlowPort.record(SeckillStockFlowEntity.rollback(entity, SeckillStockFlowEntity.ROLLBACK_TIMEOUT, 1, "timeout unpaid released"));
            orderStateFlowPort.record(OrderStateTransitionEntity.seckillTimeoutClosed(
                    order.getOutTradeNo(),
                    order.getOrderId(),
                    order.getUserId(),
                    MDC.get("trace-id")));
            clearLocalSoldOut(order.getActivityId());
            count++;
        }
        return count;
    }

    private SeckillOrder querySeckillOrderPo(String userId, String outTradeNo) {
        SeckillOrder seckillOrderReq = SeckillOrder.builder()
                .userId(userId)
                .outTradeNo(outTradeNo)
                .build();
        if (seckillOrderShardRouter.useSharding()) {
            return seckillOrderDao.querySeckillOrderByOutTradeNoFromTable(seckillOrderShardRouter.tableName(userId, outTradeNo), seckillOrderReq);
        }
        return seckillOrderDao.querySeckillOrderByOutTradeNo(seckillOrderReq);
    }

    private void releaseSeckillStock(SeckillOrder order, String changeType, int changeCount, String message) {
        seckillActivityDao.updateReleaseStock(order.getActivityId());
        SeckillOrderEntity entity = buildSeckillOrderEntity(order);
        entity.setTraceId(MDC.get("trace-id"));
        seckillStockReservationPort.release(entity, changeCount);
        seckillStockFlowPort.record(SeckillStockFlowEntity.rollback(entity, changeType, changeCount, message));
        clearLocalSoldOut(order.getActivityId());
    }

    private SeckillOrderEntity buildSeckillOrderEntity(SeckillOrder seckillOrder) {
        if (null == seckillOrder) return null;
        return SeckillOrderEntity.builder()
                .userId(seckillOrder.getUserId())
                .activityId(seckillOrder.getActivityId())
                .activityName(seckillOrder.getActivityName())
                .goodsId(seckillOrder.getGoodsId())
                .goodsName(seckillOrder.getGoodsName())
                .source(seckillOrder.getSource())
                .channel(seckillOrder.getChannel())
                .orderId(seckillOrder.getOrderId())
                .outTradeNo(seckillOrder.getOutTradeNo())
                .originalPrice(seckillOrder.getOriginalPrice())
                .seckillPrice(seckillOrder.getSeckillPrice())
                .status(seckillOrder.getStatus())
                .resultStatus(SeckillOrderEntity.RESULT_SUCCESS)
                .message("order created")
                .createTime(seckillOrder.getCreateTime())
                .build();
    }

    private SeckillOrder buildSeckillOrder(SeckillOrderEntity seckillOrderEntity) {
        return SeckillOrder.builder()
                .userId(seckillOrderEntity.getUserId())
                .activityId(seckillOrderEntity.getActivityId())
                .activityName(seckillOrderEntity.getActivityName())
                .goodsId(seckillOrderEntity.getGoodsId())
                .goodsName(seckillOrderEntity.getGoodsName())
                .source(seckillOrderEntity.getSource())
                .channel(seckillOrderEntity.getChannel())
                .orderId(seckillOrderEntity.getOrderId())
                .outTradeNo(seckillOrderEntity.getOutTradeNo())
                .originalPrice(seckillOrderEntity.getOriginalPrice())
                .seckillPrice(seckillOrderEntity.getSeckillPrice())
                .status(seckillOrderEntity.getStatus())
                .build();
    }

    private void insertSeckillOrder(SeckillOrder seckillOrder) {
        if (seckillOrderShardRouter.useSharding()) {
            seckillOrderDao.insertToTable(seckillOrderShardRouter.tableName(seckillOrder.getUserId(), seckillOrder.getOutTradeNo()), seckillOrder);
            return;
        }
        seckillOrderDao.insert(seckillOrder);
    }

    private void insertSeckillOrders(List<SeckillOrder> seckillOrders) {
        if (!seckillOrderShardRouter.useSharding()) {
            seckillOrderDao.insertIgnoreBatch(seckillOrders);
            return;
        }

        Map<String, List<SeckillOrder>> orderMap = seckillOrderShardRouter.groupByTable(seckillOrders);
        for (Map.Entry<String, List<SeckillOrder>> entry : orderMap.entrySet()) {
            seckillOrderDao.insertIgnoreShardBatch(entry.getKey(), entry.getValue());
        }
    }

    private String activityCacheKey(Long activityId, String source, String channel, String goodsId) {
        return String.valueOf(activityId) + ":" + source + ":" + channel + ":" + goodsId;
    }

    private boolean isLocalSoldOut(Long activityId) {
        Long expireTime = soldOutCache.get(activityId);
        if (null == expireTime) {
            return false;
        }
        if (expireTime <= System.currentTimeMillis()) {
            soldOutCache.remove(activityId);
            return false;
        }
        return true;
    }

    private void markLocalSoldOut(Long activityId) {
        if (soldOutCacheTtlMillis > 0) {
            soldOutCache.put(activityId, System.currentTimeMillis() + soldOutCacheTtlMillis);
        }
    }

    private void clearLocalSoldOut(Long activityId) {
        soldOutCache.remove(activityId);
    }

    private void rollbackReservation(SeckillOrderEntity seckillOrderEntity, boolean removeResult, String reason) {
        seckillStockReservationPort.rollback(seckillOrderEntity, removeResult);
        clearLocalSoldOut(seckillOrderEntity.getActivityId());
        try {
            seckillStockFlowPort.record(SeckillStockFlowEntity.rollback(seckillOrderEntity, SeckillStockFlowEntity.ROLLBACK, 1, reason));
        } catch (Exception e) {
            log.error("record seckill stock rollback flow failed activityId:{} userId:{} outTradeNo:{}",
                    seckillOrderEntity.getActivityId(), seckillOrderEntity.getUserId(), seckillOrderEntity.getOutTradeNo(), e);
        }
    }

    private static class ActivityCacheEntry {

        private final SeckillActivityEntity seckillActivityEntity;
        private final long expireTime;

        private ActivityCacheEntry(SeckillActivityEntity seckillActivityEntity, long expireTime) {
            this.seckillActivityEntity = seckillActivityEntity;
            this.expireTime = expireTime;
        }

        public SeckillActivityEntity getSeckillActivityEntity() {
            return seckillActivityEntity;
        }

        public long getExpireTime() {
            return expireTime;
        }
    }

}
