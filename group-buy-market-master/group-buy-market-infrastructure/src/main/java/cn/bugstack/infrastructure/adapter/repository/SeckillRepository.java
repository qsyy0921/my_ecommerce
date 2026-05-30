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
import cn.bugstack.domain.shared.adapter.port.IOrderStateFlowPort;
import cn.bugstack.domain.shared.model.entity.OrderStateTransitionEntity;
import cn.bugstack.infrastructure.dao.ISeckillActivityDao;
import cn.bugstack.infrastructure.dao.ISeckillOrderDao;
import cn.bugstack.infrastructure.dao.ISkuDao;
import cn.bugstack.infrastructure.adapter.support.SeckillOrderShardRouter;
import cn.bugstack.infrastructure.adapter.support.SeckillSoldOutCache;
import cn.bugstack.infrastructure.dao.po.SeckillActivity;
import cn.bugstack.infrastructure.dao.po.SeckillOrder;
import cn.bugstack.infrastructure.dao.po.Sku;
import cn.bugstack.infrastructure.event.EventPublisher;
import cn.bugstack.infrastructure.event.SeckillOrderCreateBuffer;
import cn.bugstack.types.enums.ResponseCode;
import cn.bugstack.types.exception.AppException;
import com.alibaba.fastjson.JSON;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import javax.annotation.Resource;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Seckill repository adapter.
 */
@Slf4j
@Repository
public class SeckillRepository implements ISeckillRepository, ISeckillMaintenancePort {

    @Value("${app.seckill.activity-cache-ttl-millis:3000}")
    private Long activityCacheTtlMillis;
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
    private SeckillSoldOutCache seckillSoldOutCache;
    @Resource
    private ISkuDao skuDao;
    @Resource
    private EventPublisher eventPublisher;
    @Resource
    private SeckillOrderCreateBuffer seckillOrderCreateBuffer;
    @Resource
    private ISeckillMetricsPort seckillMetricsPort;

    private final ConcurrentHashMap<String, ActivityCacheEntry> activityCache = new ConcurrentHashMap<>();

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
        if (seckillSoldOutCache.isSoldOut(activityId)) {
            return 0;
        }
        if (seckillStockReservationPort.isStockInitialized(activityId)) {
            int stock = seckillStockReservationPort.queryStock(activityId);
            if (stock <= 0) {
                seckillSoldOutCache.markSoldOut(activityId);
            } else {
                seckillSoldOutCache.clear(activityId);
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
                seckillSoldOutCache.markSoldOut(activityId);
            } else {
                seckillSoldOutCache.clear(activityId);
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
        if (seckillSoldOutCache.isSoldOut(activityId)) {
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
            seckillSoldOutCache.markSoldOut(activityId);
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
            seckillSoldOutCache.clear(order.getActivityId());
            count++;
        }
        return count;
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

    private String activityCacheKey(Long activityId, String source, String channel, String goodsId) {
        return String.valueOf(activityId) + ":" + source + ":" + channel + ":" + goodsId;
    }

    private void rollbackReservation(SeckillOrderEntity seckillOrderEntity, boolean removeResult, String reason) {
        seckillStockReservationPort.rollback(seckillOrderEntity, removeResult);
        seckillSoldOutCache.clear(seckillOrderEntity.getActivityId());
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
