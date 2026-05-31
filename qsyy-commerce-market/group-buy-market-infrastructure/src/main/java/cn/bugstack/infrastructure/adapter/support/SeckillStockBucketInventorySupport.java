package cn.bugstack.infrastructure.adapter.support;

import cn.bugstack.infrastructure.redis.IRedisService;
import org.redisson.api.RLock;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.concurrent.TimeUnit;

@Component
public class SeckillStockBucketInventorySupport {

    @Resource
    private IRedisService redisService;
    @Resource
    private SeckillStockKeyBuilder seckillStockKeyBuilder;
    @Resource
    private SeckillStockBucketRouter seckillStockBucketRouter;
    @Resource
    private SeckillStockInitializationCache seckillStockInitializationCache;

    public boolean isInitialized(Long activityId) {
        if (seckillStockInitializationCache.isInitializedRecently(activityId)) {
            return true;
        }
        if (redisService.isExists(seckillStockKeyBuilder.stockBucketKey(activityId, 0))) {
            seckillStockInitializationCache.markInitialized(activityId);
            return true;
        }
        return false;
    }

    public boolean tryAcquireInitializationLock(Long activityId, long waitMillis, long leaseMillis) throws InterruptedException {
        return redisService.getLock(seckillStockKeyBuilder.initializationLockKey(activityId))
                .tryLock(waitMillis, leaseMillis, TimeUnit.MILLISECONDS);
    }

    public void releaseInitializationLock(Long activityId) {
        RLock lock = redisService.getLock(seckillStockKeyBuilder.initializationLockKey(activityId));
        if (lock.isHeldByCurrentThread()) {
            lock.unlock();
        }
    }

    public void initialize(Long activityId, int availableCount) {
        int bucketCount = seckillStockBucketRouter.bucketCount();
        int base = availableCount / bucketCount;
        int remainder = availableCount % bucketCount;
        for (int i = 0; i < bucketCount; i++) {
            int stock = base + (i < remainder ? 1 : 0);
            redisService.setAtomicLong(seckillStockKeyBuilder.stockBucketKey(activityId, i), stock);
        }
        seckillStockInitializationCache.markInitialized(activityId);
    }

    public int query(Long activityId) {
        long stock = 0;
        for (int i = 0; i < seckillStockBucketRouter.bucketCount(); i++) {
            stock += redisService.getAtomicLong(seckillStockKeyBuilder.stockBucketKey(activityId, i));
        }
        if (stock > Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }
        return (int) stock;
    }

}
