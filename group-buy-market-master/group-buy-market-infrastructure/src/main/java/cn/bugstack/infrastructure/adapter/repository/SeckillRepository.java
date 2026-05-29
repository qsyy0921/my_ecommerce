package cn.bugstack.infrastructure.adapter.repository;

import cn.bugstack.domain.seckill.adapter.repository.ISeckillRepository;
import cn.bugstack.domain.seckill.adapter.port.ISeckillMetricsPort;
import cn.bugstack.domain.seckill.model.entity.SeckillActivityEntity;
import cn.bugstack.domain.seckill.model.entity.SeckillOrderEntity;
import cn.bugstack.domain.shared.adapter.port.IOrderStateFlowPort;
import cn.bugstack.domain.shared.model.entity.OrderStateTransitionEntity;
import cn.bugstack.infrastructure.dao.ISeckillActivityDao;
import cn.bugstack.infrastructure.dao.ISeckillOrderDao;
import cn.bugstack.infrastructure.dao.ISeckillStockFlowDao;
import cn.bugstack.infrastructure.dao.ISkuDao;
import cn.bugstack.infrastructure.dao.po.SeckillActivity;
import cn.bugstack.infrastructure.dao.po.SeckillOrder;
import cn.bugstack.infrastructure.dao.po.SeckillStockFlow;
import cn.bugstack.infrastructure.dao.po.Sku;
import cn.bugstack.infrastructure.event.EventPublisher;
import cn.bugstack.infrastructure.event.SeckillFaultInjector;
import cn.bugstack.infrastructure.event.SeckillOrderCreateBuffer;
import cn.bugstack.infrastructure.event.SeckillStreamMetrics;
import cn.bugstack.infrastructure.redis.IRedisService;
import cn.bugstack.types.enums.ResponseCode;
import cn.bugstack.types.exception.AppException;
import com.alibaba.fastjson.JSON;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.zip.CRC32;

/**
 * Seckill repository adapter.
 */
@Slf4j
@Repository
public class SeckillRepository implements ISeckillRepository {

    private static final String SECKILL_STOCK_KEY = "seckill:stock:";
    private static final String SECKILL_STOCK_INIT_LOCK_KEY = "seckill:stock:init:";
    private static final String SECKILL_USER_LOCK_KEY = "seckill:user:lock:";
    private static final String SECKILL_RESULT_KEY = "seckill:result:";
    private static final String STOCK_FLOW_RESERVE = "RESERVE";
    private static final String STOCK_FLOW_ROLLBACK = "ROLLBACK";
    private static final String STOCK_FLOW_ROLLBACK_TIMEOUT = "ROLLBACK_TIMEOUT";
    private static final long SECKILL_USER_LOCK_TTL_HOURS = 24;
    private static final long SECKILL_RESULT_TTL_HOURS = 24;

    @Value("${app.seckill.activity-cache-ttl-millis:3000}")
    private Long activityCacheTtlMillis;
    @Value("${app.seckill.sold-out-cache-ttl-millis:5000}")
    private Long soldOutCacheTtlMillis;
    @Value("${app.seckill.stock-init-lock-wait-millis:200}")
    private Long stockInitLockWaitMillis;
    @Value("${app.seckill.stock-init-cache-ttl-millis:60000}")
    private Long stockInitCacheTtlMillis;
    @Value("${app.seckill.stock-bucket-count:64}")
    private Integer stockBucketCount;
    @Value("${app.seckill.stock-bucket-try-count:64}")
    private Integer stockBucketTryCount;
    @Value("${app.seckill.order-shard-count:1}")
    private Integer orderShardCount;
    @Value("${app.seckill.order-table-prefix:seckill_order}")
    private String orderTablePrefix;
    @Value("${spring.rabbitmq.config.producer.topic_seckill_order_create.routing_key}")
    private String topicSeckillOrderCreate;

    @Resource
    private ISeckillActivityDao seckillActivityDao;
    @Resource
    private ISeckillOrderDao seckillOrderDao;
    @Resource
    private ISeckillStockFlowDao seckillStockFlowDao;
    @Resource
    private IOrderStateFlowPort orderStateFlowPort;
    @Resource
    private ISkuDao skuDao;
    @Resource
    private IRedisService redisService;
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
    private final ConcurrentHashMap<Long, Long> stockInitializedCache = new ConcurrentHashMap<>();

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
        if (redisService.isExists(stockBucketKey(activityId, 0))) {
            markStockInitialized(activityId);
            int stock = sumStockBuckets(activityId);
            if (stock <= 0) {
                markLocalSoldOut(activityId);
            } else {
                clearLocalSoldOut(activityId);
            }
            return stock;
        }

        RLock lock = redisService.getLock(SECKILL_STOCK_INIT_LOCK_KEY + activityId);
        boolean locked = false;
        try {
            locked = lock.tryLock(stockInitLockWaitMillis, 10_000, TimeUnit.MILLISECONDS);
            if (!locked) {
                if (redisService.isExists(stockBucketKey(activityId, 0))) {
                    markStockInitialized(activityId);
                    return sumStockBuckets(activityId);
                }
                throw new AppException(ResponseCode.E0205);
            }
            if (redisService.isExists(stockBucketKey(activityId, 0))) {
                markStockInitialized(activityId);
                return sumStockBuckets(activityId);
            }

            SeckillActivity seckillActivity = seckillActivityDao.querySeckillActivity(SeckillActivity.builder().activityId(activityId).build());
            if (null == seckillActivity) {
                throw new AppException(ResponseCode.E0201);
            }
            initializeStockBuckets(activityId, seckillActivity.getAvailableCount());
            markStockInitialized(activityId);
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
            if (locked && lock.isHeldByCurrentThread()) {
                lock.unlock();
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
        if (useOrderSharding()) {
            seckillOrder = seckillOrderDao.querySeckillOrderByOutTradeNoFromTable(orderTableName(userId, outTradeNo), seckillOrderReq);
        } else {
            seckillOrder = seckillOrderDao.querySeckillOrderByOutTradeNo(seckillOrderReq);
        }
        return buildSeckillOrderEntity(seckillOrder);
    }

    @Override
    public SeckillOrderEntity querySeckillResult(String userId, Long activityId, String outTradeNo) {
        String resultKey = resultKey(activityId, userId, outTradeNo);
        String result = redisService.getValue(resultKey);
        if (null != result) {
            return JSON.parseObject(result, SeckillOrderEntity.class);
        }

        SeckillOrderEntity existsOrder = querySeckillOrderByOutTradeNo(userId, outTradeNo);
        if (null != existsOrder) {
            setResult(existsOrder, SeckillOrderEntity.RESULT_SUCCESS, "order created");
            redisService.setValue(resultKey, JSON.toJSONString(existsOrder), TimeUnit.HOURS.toMillis(SECKILL_RESULT_TTL_HOURS));
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
        String userLockKey = userLockKey(activityId, seckillOrderEntity.getUserId());
        String resultKey = resultKey(activityId, seckillOrderEntity.getUserId(), seckillOrderEntity.getOutTradeNo());
        seckillOrderEntity.setTraceId(MDC.get("trace-id"));
        if (isLocalSoldOut(activityId)) {
            seckillMetricsPort.recordStockNotEnough();
            throw new AppException(ResponseCode.E0203);
        }
        ensureStockInitialized(activityId);

        int startBucket = bucketOf(seckillOrderEntity.getUserId(), seckillOrderEntity.getOutTradeNo());
        int tryCount = Math.min(bucketCount(), Math.max(1, stockBucketTryCount));
        for (int i = 0; i < tryCount; i++) {
            int bucket = (startBucket + i) % bucketCount();
            String stockKey = stockBucketKey(activityId, bucket);
            setResult(seckillOrderEntity, SeckillOrderEntity.RESULT_PROCESSING, "qualification reserved, waiting for order creation");
            seckillOrderEntity.setStockBucket(bucket);

            seckillFaultInjector.beforeRedisReserve();
            Long reserveResult = redisService.reserveSeckillQualification(
                    stockKey,
                    userLockKey,
                    resultKey,
                    JSON.toJSONString(seckillOrderEntity),
                    SECKILL_RESULT_TTL_HOURS,
                    TimeUnit.HOURS);
            if (-2L == reserveResult) {
                seckillMetricsPort.recordDuplicate();
                throw new AppException(ResponseCode.E0204);
            }
            if (-1L == reserveResult) {
                continue;
            }
            seckillOrderEntity.setStockBefore(reserveResult.intValue() + 1);
            seckillOrderEntity.setStockAfter(reserveResult.intValue());

            try {
                enqueueOrderCreate(seckillOrderEntity);
                return seckillOrderEntity;
            } catch (RuntimeException e) {
                rollbackReservation(seckillOrderEntity, stockKey, userLockKey, resultKey, true, "enqueue order create failed");
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
        if (isStockInitialized(activityId)) {
            return;
        }
        if (redisService.isExists(stockBucketKey(activityId, 0))) {
            markStockInitialized(activityId);
            return;
        }
        queryAvailableStock(activityId);
    }

    @Transactional(timeout = 5)
    @Override
    public void createSeckillOrder(SeckillOrderEntity seckillOrderEntity) {
        Long activityId = seckillOrderEntity.getActivityId();
        String userLockKey = userLockKey(activityId, seckillOrderEntity.getUserId());
        String resultKey = resultKey(activityId, seckillOrderEntity.getUserId(), seckillOrderEntity.getOutTradeNo());
        String stockKey = stockBucketKey(activityId, null == seckillOrderEntity.getStockBucket() ? 0 : seckillOrderEntity.getStockBucket());

        SeckillOrderEntity existsOrder = querySeckillOrderByOutTradeNo(seckillOrderEntity.getUserId(), seckillOrderEntity.getOutTradeNo());
        if (null != existsOrder) {
            setResult(existsOrder, SeckillOrderEntity.RESULT_SUCCESS, "order already created");
            redisService.setValue(resultKey, JSON.toJSONString(existsOrder), TimeUnit.HOURS.toMillis(SECKILL_RESULT_TTL_HOURS));
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
            seckillStockFlowDao.insertIgnore(buildStockFlow(seckillOrderEntity, STOCK_FLOW_RESERVE, -1, "order created"));
            orderStateFlowPort.record(OrderStateTransitionEntity.seckillOrderCreated(
                    seckillOrderEntity.getOutTradeNo(),
                    seckillOrderEntity.getOrderId(),
                    seckillOrderEntity.getUserId(),
                    seckillOrderEntity.getTraceId(),
                    seckillOrderEntity.getSourceMessageId()));
            setResult(seckillOrderEntity, SeckillOrderEntity.RESULT_SUCCESS, "order created");
            redisService.setValue(resultKey, JSON.toJSONString(seckillOrderEntity), TimeUnit.HOURS.toMillis(SECKILL_RESULT_TTL_HOURS));
        } catch (DuplicateKeyException e) {
            SeckillOrderEntity duplicateOrder = querySeckillOrderByOutTradeNo(seckillOrderEntity.getUserId(), seckillOrderEntity.getOutTradeNo());
            if (null != duplicateOrder) {
                setResult(duplicateOrder, SeckillOrderEntity.RESULT_SUCCESS, "order already created");
                redisService.setValue(resultKey, JSON.toJSONString(duplicateOrder), TimeUnit.HOURS.toMillis(SECKILL_RESULT_TTL_HOURS));
                return;
            }
            setResult(seckillOrderEntity, SeckillOrderEntity.RESULT_DUPLICATE, "duplicate seckill order");
            redisService.setValue(resultKey, JSON.toJSONString(seckillOrderEntity), TimeUnit.HOURS.toMillis(SECKILL_RESULT_TTL_HOURS));
            rollbackReservation(seckillOrderEntity, stockKey, userLockKey, null, false, "duplicate seckill order");
        } catch (AppException e) {
            throw e;
        } catch (RuntimeException e) {
            setResult(seckillOrderEntity, SeckillOrderEntity.RESULT_FAIL, e.getMessage());
            redisService.setValue(resultKey, JSON.toJSONString(seckillOrderEntity), TimeUnit.HOURS.toMillis(SECKILL_RESULT_TTL_HOURS));
            rollbackReservation(seckillOrderEntity, stockKey, userLockKey, null, false, e.getMessage());
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

        List<SeckillStockFlow> stockFlows = new ArrayList<>(seckillOrderEntities.size());
        for (SeckillOrderEntity seckillOrderEntity : seckillOrderEntities) {
            SeckillOrderEntity existsOrder = querySeckillOrderByOutTradeNo(seckillOrderEntity.getUserId(), seckillOrderEntity.getOutTradeNo());
            if (null == existsOrder) {
                setResult(seckillOrderEntity, SeckillOrderEntity.RESULT_DUPLICATE, "order create ignored by unique constraint");
                redisService.setValue(
                        resultKey(seckillOrderEntity.getActivityId(), seckillOrderEntity.getUserId(), seckillOrderEntity.getOutTradeNo()),
                        JSON.toJSONString(seckillOrderEntity),
                        TimeUnit.HOURS.toMillis(SECKILL_RESULT_TTL_HOURS));
                rollbackReservation(
                        seckillOrderEntity,
                        stockBucketKey(seckillOrderEntity.getActivityId(), null == seckillOrderEntity.getStockBucket() ? 0 : seckillOrderEntity.getStockBucket()),
                        userLockKey(seckillOrderEntity.getActivityId(), seckillOrderEntity.getUserId()),
                        null,
                        false,
                        "batch order create ignored by unique constraint");
                continue;
            }

            stockFlows.add(buildStockFlow(seckillOrderEntity, STOCK_FLOW_RESERVE, -1, "order created"));
            orderStateFlowPort.record(OrderStateTransitionEntity.seckillOrderCreated(
                    seckillOrderEntity.getOutTradeNo(),
                    seckillOrderEntity.getOrderId(),
                    seckillOrderEntity.getUserId(),
                    seckillOrderEntity.getTraceId(),
                    seckillOrderEntity.getSourceMessageId()));
            setResult(existsOrder, SeckillOrderEntity.RESULT_SUCCESS, "order created");
            redisService.setValue(
                    resultKey(existsOrder.getActivityId(), existsOrder.getUserId(), existsOrder.getOutTradeNo()),
                    JSON.toJSONString(existsOrder),
                    TimeUnit.HOURS.toMillis(SECKILL_RESULT_TTL_HOURS));
        }
        if (!stockFlows.isEmpty()) {
            seckillStockFlowDao.insertIgnoreBatch(stockFlows);
        }
    }

    @Override
    public void syncSeckillActivityStock() {
        List<Long> activityIds = seckillActivityDao.queryStockSyncActivityIds();
        if (null == activityIds || activityIds.isEmpty()) {
            return;
        }
        for (Long activityId : activityIds) {
            try {
                if (!useOrderSharding()) {
                    seckillActivityDao.syncStockByOrderCount(activityId);
                    continue;
                }
                int activeCount = 0;
                for (int shardIndex = 0; shardIndex < orderShardCount(); shardIndex++) {
                    activeCount += seckillOrderDao.countActiveOrdersFromTable(orderTableName(shardIndex), activityId);
                }
                seckillActivityDao.syncStockByActiveCount(activityId, activeCount);
            } catch (Exception e) {
                log.error("sync seckill activity stock failed activityId:{}", activityId, e);
            }
        }
    }

    @Override
    public int releaseTimeoutUnpaidOrders() {
        if (!useOrderSharding()) {
            return releaseTimeoutUnpaidOrders(seckillOrderDao.queryTimeoutUnpaidOrders(), null);
        }

        int count = 0;
        for (int shardIndex = 0; shardIndex < orderShardCount(); shardIndex++) {
            String tableName = orderTableName(shardIndex);
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
            entity.setStockBucket(bucketOf(order.getUserId(), order.getOutTradeNo()));
            long stockAfter = redisService.incr(stockBucketKey(order.getActivityId(), entity.getStockBucket()));
            entity.setStockBefore((int) stockAfter - 1);
            entity.setStockAfter((int) stockAfter);
            redisService.remove(userLockKey(order.getActivityId(), order.getUserId()));
            seckillStockFlowDao.insertIgnore(buildStockFlow(entity, STOCK_FLOW_ROLLBACK_TIMEOUT, 1, "timeout unpaid released"));
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

    private SeckillStockFlow buildStockFlow(SeckillOrderEntity seckillOrderEntity, String changeType, int changeCount, String message) {
        return SeckillStockFlow.builder()
                .flowNo(stockFlowNo(seckillOrderEntity, changeType))
                .userId(seckillOrderEntity.getUserId())
                .activityId(seckillOrderEntity.getActivityId())
                .orderId(seckillOrderEntity.getOrderId())
                .outTradeNo(seckillOrderEntity.getOutTradeNo())
                .stockBucket(seckillOrderEntity.getStockBucket())
                .changeType(changeType)
                .changeCount(changeCount)
                .stockBefore(seckillOrderEntity.getStockBefore())
                .stockAfter(seckillOrderEntity.getStockAfter())
                .bizEvent(changeType)
                .traceId(seckillOrderEntity.getTraceId())
                .sourceMessageId(seckillOrderEntity.getSourceMessageId())
                .source("seckill")
                .message(message)
                .build();
    }

    private void insertSeckillOrder(SeckillOrder seckillOrder) {
        if (useOrderSharding()) {
            seckillOrderDao.insertToTable(orderTableName(seckillOrder.getUserId(), seckillOrder.getOutTradeNo()), seckillOrder);
            return;
        }
        seckillOrderDao.insert(seckillOrder);
    }

    private void insertSeckillOrders(List<SeckillOrder> seckillOrders) {
        if (!useOrderSharding()) {
            seckillOrderDao.insertIgnoreBatch(seckillOrders);
            return;
        }

        Map<String, List<SeckillOrder>> orderMap = new HashMap<>();
        for (SeckillOrder seckillOrder : seckillOrders) {
            String tableName = orderTableName(seckillOrder.getUserId(), seckillOrder.getOutTradeNo());
            orderMap.computeIfAbsent(tableName, key -> new ArrayList<>()).add(seckillOrder);
        }
        for (Map.Entry<String, List<SeckillOrder>> entry : orderMap.entrySet()) {
            seckillOrderDao.insertIgnoreShardBatch(entry.getKey(), entry.getValue());
        }
    }

    private boolean useOrderSharding() {
        return orderShardCount() > 1;
    }

    private int orderShardCount() {
        return Math.max(1, null == orderShardCount ? 1 : orderShardCount);
    }

    private String orderTableName(String userId, String outTradeNo) {
        String tablePrefix = tablePrefix();
        if (!useOrderSharding()) {
            return tablePrefix;
        }
        int shardIndex = (int) (crc32(String.valueOf(userId) + ":" + String.valueOf(outTradeNo)) % orderShardCount());
        return orderTableName(shardIndex);
    }

    private String orderTableName(int shardIndex) {
        String tablePrefix = tablePrefix();
        return tablePrefix + "_" + String.format("%02d", shardIndex);
    }

    private long crc32(String value) {
        CRC32 crc32 = new CRC32();
        crc32.update(String.valueOf(value).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return crc32.getValue();
    }

    private String tablePrefix() {
        String tablePrefix = null == orderTablePrefix || orderTablePrefix.trim().isEmpty() ? "seckill_order" : orderTablePrefix.trim();
        if (!tablePrefix.matches("[a-zA-Z0-9_]+")) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "illegal seckill order table prefix");
        }
        return tablePrefix;
    }

    private String stockFlowNo(SeckillOrderEntity seckillOrderEntity, String changeType) {
        return seckillOrderEntity.getActivityId() + ":" + seckillOrderEntity.getUserId() + ":" + seckillOrderEntity.getOutTradeNo() + ":" + changeType;
    }

    private void initializeStockBuckets(Long activityId, int availableCount) {
        int bucketCount = bucketCount();
        int base = availableCount / bucketCount;
        int remainder = availableCount % bucketCount;
        for (int i = 0; i < bucketCount; i++) {
            int stock = base + (i < remainder ? 1 : 0);
            redisService.setAtomicLong(stockBucketKey(activityId, i), stock);
        }
    }

    private boolean isStockInitialized(Long activityId) {
        Long expireTime = stockInitializedCache.get(activityId);
        if (null == expireTime) {
            return false;
        }
        if (expireTime > System.currentTimeMillis()) {
            return true;
        }
        stockInitializedCache.remove(activityId, expireTime);
        return false;
    }

    private void markStockInitialized(Long activityId) {
        if (stockInitCacheTtlMillis > 0) {
            stockInitializedCache.put(activityId, System.currentTimeMillis() + stockInitCacheTtlMillis);
        }
    }

    private int sumStockBuckets(Long activityId) {
        long stock = 0;
        for (int i = 0; i < bucketCount(); i++) {
            stock += redisService.getAtomicLong(stockBucketKey(activityId, i));
        }
        if (stock > Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }
        return (int) stock;
    }

    private String activityCacheKey(Long activityId, String source, String channel, String goodsId) {
        return String.valueOf(activityId) + ":" + source + ":" + channel + ":" + goodsId;
    }

    private String stockBucketKey(Long activityId, int bucket) {
        return SECKILL_STOCK_KEY + activityId + ":" + bucket;
    }

    private String userLockKey(Long activityId, String userId) {
        return SECKILL_USER_LOCK_KEY + activityId + ":" + userId;
    }

    private String resultKey(Long activityId, String userId, String outTradeNo) {
        return SECKILL_RESULT_KEY + activityId + ":" + userId + ":" + outTradeNo;
    }

    private int bucketOf(String userId, String outTradeNo) {
        return (int) (crc32(userId + ":" + outTradeNo) % bucketCount());
    }

    private int bucketCount() {
        return Math.max(1, null == stockBucketCount ? 64 : stockBucketCount);
    }

    private void setResult(SeckillOrderEntity seckillOrderEntity, String resultStatus, String message) {
        seckillOrderEntity.setResultStatus(resultStatus);
        seckillOrderEntity.setMessage(message);
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

    private void rollbackReservation(SeckillOrderEntity seckillOrderEntity, String stockKey, String userLockKey, String resultKey, boolean removeResult, String reason) {
        long stockAfter = redisService.incr(stockKey);
        seckillOrderEntity.setStockBefore((int) stockAfter - 1);
        seckillOrderEntity.setStockAfter((int) stockAfter);
        redisService.remove(userLockKey);
        if (removeResult && null != resultKey) {
            redisService.remove(resultKey);
        }
        clearLocalSoldOut(seckillOrderEntity.getActivityId());
        try {
            seckillStockFlowDao.insertIgnore(buildStockFlow(seckillOrderEntity, STOCK_FLOW_ROLLBACK, 1, reason));
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
