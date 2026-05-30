package cn.bugstack.infrastructure.adapter.port;

import cn.bugstack.domain.seckill.adapter.port.ISeckillResultCachePort;
import cn.bugstack.domain.seckill.adapter.port.ISeckillStockReservationPort;
import cn.bugstack.domain.seckill.model.entity.SeckillOrderEntity;
import cn.bugstack.domain.seckill.model.entity.SeckillStockReservationEntity;
import cn.bugstack.infrastructure.adapter.support.SeckillStockBucketRouter;
import cn.bugstack.infrastructure.adapter.support.SeckillStockInitializationCache;
import cn.bugstack.infrastructure.adapter.support.SeckillStockKeyBuilder;
import cn.bugstack.infrastructure.event.SeckillFaultInjector;
import cn.bugstack.infrastructure.redis.IRedisService;
import com.alibaba.fastjson.JSON;
import org.redisson.api.RLock;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.concurrent.TimeUnit;

@Service
public class SeckillStockReservationPort implements ISeckillStockReservationPort {

    private static final long SECKILL_RESULT_TTL_HOURS = 24;

    @Resource
    private IRedisService redisService;
    @Resource
    private ISeckillResultCachePort seckillResultCachePort;
    @Resource
    private SeckillFaultInjector seckillFaultInjector;
    @Resource
    private SeckillStockKeyBuilder seckillStockKeyBuilder;
    @Resource
    private SeckillStockBucketRouter seckillStockBucketRouter;
    @Resource
    private SeckillStockInitializationCache seckillStockInitializationCache;

    @Override
    public boolean isStockInitialized(Long activityId) {
        if (seckillStockInitializationCache.isInitializedRecently(activityId)) {
            return true;
        }
        if (redisService.isExists(seckillStockKeyBuilder.stockBucketKey(activityId, 0))) {
            seckillStockInitializationCache.markInitialized(activityId);
            return true;
        }
        return false;
    }

    @Override
    public boolean tryAcquireInitializationLock(Long activityId, long waitMillis, long leaseMillis) throws InterruptedException {
        return redisService.getLock(seckillStockKeyBuilder.initializationLockKey(activityId))
                .tryLock(waitMillis, leaseMillis, TimeUnit.MILLISECONDS);
    }

    @Override
    public void releaseInitializationLock(Long activityId) {
        RLock lock = redisService.getLock(seckillStockKeyBuilder.initializationLockKey(activityId));
        if (lock.isHeldByCurrentThread()) {
            lock.unlock();
        }
    }

    @Override
    public void initializeStock(Long activityId, int availableCount) {
        int bucketCount = seckillStockBucketRouter.bucketCount();
        int base = availableCount / bucketCount;
        int remainder = availableCount % bucketCount;
        for (int i = 0; i < bucketCount; i++) {
            int stock = base + (i < remainder ? 1 : 0);
            redisService.setAtomicLong(seckillStockKeyBuilder.stockBucketKey(activityId, i), stock);
        }
        seckillStockInitializationCache.markInitialized(activityId);
    }

    @Override
    public int queryStock(Long activityId) {
        long stock = 0;
        for (int i = 0; i < seckillStockBucketRouter.bucketCount(); i++) {
            stock += redisService.getAtomicLong(seckillStockKeyBuilder.stockBucketKey(activityId, i));
        }
        if (stock > Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }
        return (int) stock;
    }

    @Override
    public SeckillStockReservationEntity reserve(SeckillOrderEntity seckillOrderEntity, Integer stockBucketTryCount) {
        int bucketCount = seckillStockBucketRouter.bucketCount();
        int startBucket = seckillStockBucketRouter.bucketOf(seckillOrderEntity.getUserId(), seckillOrderEntity.getOutTradeNo());
        int tryCount = seckillStockBucketRouter.tryCount(stockBucketTryCount);
        for (int i = 0; i < tryCount; i++) {
            int bucket = (startBucket + i) % bucketCount;
            String stockKey = seckillStockKeyBuilder.stockBucketKey(seckillOrderEntity.getActivityId(), bucket);
            seckillOrderEntity.setResultStatus(SeckillOrderEntity.RESULT_PROCESSING);
            seckillOrderEntity.setMessage("qualification reserved, waiting for order creation");
            seckillOrderEntity.setStockBucket(bucket);

            seckillFaultInjector.beforeRedisReserve();
            Long reserveResult = redisService.reserveSeckillQualification(
                    stockKey,
                    seckillStockKeyBuilder.userLockKey(seckillOrderEntity.getActivityId(), seckillOrderEntity.getUserId()),
                    seckillResultCachePort.resultKey(seckillOrderEntity.getActivityId(), seckillOrderEntity.getUserId(), seckillOrderEntity.getOutTradeNo()),
                    JSON.toJSONString(seckillOrderEntity),
                    SECKILL_RESULT_TTL_HOURS,
                    TimeUnit.HOURS);
            if (Long.valueOf(-2L).equals(reserveResult)) {
                return SeckillStockReservationEntity.duplicate();
            }
            if (Long.valueOf(-1L).equals(reserveResult)) {
                continue;
            }
            int stockAfter = reserveResult.intValue();
            int stockBefore = stockAfter + 1;
            seckillOrderEntity.setStockBefore(stockBefore);
            seckillOrderEntity.setStockAfter(stockAfter);
            return SeckillStockReservationEntity.success(bucket, stockBefore, stockAfter);
        }
        return SeckillStockReservationEntity.stockNotEnough();
    }

    @Override
    public SeckillOrderEntity rollback(SeckillOrderEntity seckillOrderEntity, boolean removeResult) {
        int bucket = null == seckillOrderEntity.getStockBucket()
                ? seckillStockBucketRouter.bucketOf(seckillOrderEntity.getUserId(), seckillOrderEntity.getOutTradeNo())
                : seckillOrderEntity.getStockBucket();
        long stockAfter = redisService.incr(seckillStockKeyBuilder.stockBucketKey(seckillOrderEntity.getActivityId(), bucket));
        seckillOrderEntity.setStockBucket(bucket);
        seckillOrderEntity.setStockBefore((int) stockAfter - 1);
        seckillOrderEntity.setStockAfter((int) stockAfter);
        redisService.remove(seckillStockKeyBuilder.userLockKey(seckillOrderEntity.getActivityId(), seckillOrderEntity.getUserId()));
        if (removeResult) {
            seckillResultCachePort.remove(seckillOrderEntity.getActivityId(), seckillOrderEntity.getUserId(), seckillOrderEntity.getOutTradeNo());
        }
        return seckillOrderEntity;
    }

    @Override
    public SeckillOrderEntity release(SeckillOrderEntity seckillOrderEntity, int changeCount) {
        int bucket = seckillStockBucketRouter.bucketOf(seckillOrderEntity.getUserId(), seckillOrderEntity.getOutTradeNo());
        long stockAfter = redisService.incr(seckillStockKeyBuilder.stockBucketKey(seckillOrderEntity.getActivityId(), bucket));
        seckillOrderEntity.setStockBucket(bucket);
        seckillOrderEntity.setStockBefore((int) stockAfter - changeCount);
        seckillOrderEntity.setStockAfter((int) stockAfter);
        redisService.remove(seckillStockKeyBuilder.userLockKey(seckillOrderEntity.getActivityId(), seckillOrderEntity.getUserId()));
        return seckillOrderEntity;
    }

}
