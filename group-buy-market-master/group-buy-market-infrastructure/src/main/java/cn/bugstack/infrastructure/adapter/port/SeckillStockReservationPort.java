package cn.bugstack.infrastructure.adapter.port;

import cn.bugstack.domain.seckill.adapter.port.ISeckillResultCachePort;
import cn.bugstack.domain.seckill.adapter.port.ISeckillStockReservationPort;
import cn.bugstack.domain.seckill.model.entity.SeckillOrderEntity;
import cn.bugstack.domain.seckill.model.entity.SeckillStockReservationEntity;
import cn.bugstack.infrastructure.event.SeckillFaultInjector;
import cn.bugstack.infrastructure.redis.IRedisService;
import com.alibaba.fastjson.JSON;
import org.redisson.api.RLock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.zip.CRC32;

@Service
public class SeckillStockReservationPort implements ISeckillStockReservationPort {

    private static final String SECKILL_STOCK_KEY = "seckill:stock:";
    private static final String SECKILL_STOCK_INIT_LOCK_KEY = "seckill:stock:init:";
    private static final String SECKILL_USER_LOCK_KEY = "seckill:user:lock:";
    private static final long SECKILL_RESULT_TTL_HOURS = 24;

    @Value("${app.seckill.stock-init-cache-ttl-millis:60000}")
    private Long stockInitCacheTtlMillis;
    @Value("${app.seckill.stock-bucket-count:64}")
    private Integer stockBucketCount;

    @Resource
    private IRedisService redisService;
    @Resource
    private ISeckillResultCachePort seckillResultCachePort;
    @Resource
    private SeckillFaultInjector seckillFaultInjector;

    private final ConcurrentHashMap<Long, Long> stockInitializedCache = new ConcurrentHashMap<>();

    @Override
    public boolean isStockInitialized(Long activityId) {
        if (isStockInitializedRecently(activityId)) {
            return true;
        }
        if (redisService.isExists(stockBucketKey(activityId, 0))) {
            markStockInitialized(activityId);
            return true;
        }
        return false;
    }

    @Override
    public boolean tryAcquireInitializationLock(Long activityId, long waitMillis, long leaseMillis) throws InterruptedException {
        return redisService.getLock(SECKILL_STOCK_INIT_LOCK_KEY + activityId)
                .tryLock(waitMillis, leaseMillis, TimeUnit.MILLISECONDS);
    }

    @Override
    public void releaseInitializationLock(Long activityId) {
        RLock lock = redisService.getLock(SECKILL_STOCK_INIT_LOCK_KEY + activityId);
        if (lock.isHeldByCurrentThread()) {
            lock.unlock();
        }
    }

    @Override
    public void initializeStock(Long activityId, int availableCount) {
        int bucketCount = bucketCount();
        int base = availableCount / bucketCount;
        int remainder = availableCount % bucketCount;
        for (int i = 0; i < bucketCount; i++) {
            int stock = base + (i < remainder ? 1 : 0);
            redisService.setAtomicLong(stockBucketKey(activityId, i), stock);
        }
        markStockInitialized(activityId);
    }

    @Override
    public int queryStock(Long activityId) {
        long stock = 0;
        for (int i = 0; i < bucketCount(); i++) {
            stock += redisService.getAtomicLong(stockBucketKey(activityId, i));
        }
        if (stock > Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }
        return (int) stock;
    }

    @Override
    public SeckillStockReservationEntity reserve(SeckillOrderEntity seckillOrderEntity, Integer stockBucketTryCount) {
        int startBucket = bucketOf(seckillOrderEntity.getUserId(), seckillOrderEntity.getOutTradeNo());
        int tryCount = Math.min(bucketCount(), Math.max(1, null == stockBucketTryCount ? bucketCount() : stockBucketTryCount));
        for (int i = 0; i < tryCount; i++) {
            int bucket = (startBucket + i) % bucketCount();
            String stockKey = stockBucketKey(seckillOrderEntity.getActivityId(), bucket);
            seckillOrderEntity.setResultStatus(SeckillOrderEntity.RESULT_PROCESSING);
            seckillOrderEntity.setMessage("qualification reserved, waiting for order creation");
            seckillOrderEntity.setStockBucket(bucket);

            seckillFaultInjector.beforeRedisReserve();
            Long reserveResult = redisService.reserveSeckillQualification(
                    stockKey,
                    userLockKey(seckillOrderEntity.getActivityId(), seckillOrderEntity.getUserId()),
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
                ? bucketOf(seckillOrderEntity.getUserId(), seckillOrderEntity.getOutTradeNo())
                : seckillOrderEntity.getStockBucket();
        long stockAfter = redisService.incr(stockBucketKey(seckillOrderEntity.getActivityId(), bucket));
        seckillOrderEntity.setStockBucket(bucket);
        seckillOrderEntity.setStockBefore((int) stockAfter - 1);
        seckillOrderEntity.setStockAfter((int) stockAfter);
        redisService.remove(userLockKey(seckillOrderEntity.getActivityId(), seckillOrderEntity.getUserId()));
        if (removeResult) {
            seckillResultCachePort.remove(seckillOrderEntity.getActivityId(), seckillOrderEntity.getUserId(), seckillOrderEntity.getOutTradeNo());
        }
        return seckillOrderEntity;
    }

    @Override
    public SeckillOrderEntity release(SeckillOrderEntity seckillOrderEntity, int changeCount) {
        int bucket = bucketOf(seckillOrderEntity.getUserId(), seckillOrderEntity.getOutTradeNo());
        long stockAfter = redisService.incr(stockBucketKey(seckillOrderEntity.getActivityId(), bucket));
        seckillOrderEntity.setStockBucket(bucket);
        seckillOrderEntity.setStockBefore((int) stockAfter - changeCount);
        seckillOrderEntity.setStockAfter((int) stockAfter);
        redisService.remove(userLockKey(seckillOrderEntity.getActivityId(), seckillOrderEntity.getUserId()));
        return seckillOrderEntity;
    }

    private boolean isStockInitializedRecently(Long activityId) {
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

    private String stockBucketKey(Long activityId, int bucket) {
        return SECKILL_STOCK_KEY + activityId + ":" + bucket;
    }

    private String userLockKey(Long activityId, String userId) {
        return SECKILL_USER_LOCK_KEY + activityId + ":" + userId;
    }

    private int bucketOf(String userId, String outTradeNo) {
        return (int) (crc32(userId + ":" + outTradeNo) % bucketCount());
    }

    private int bucketCount() {
        return Math.max(1, null == stockBucketCount ? 64 : stockBucketCount);
    }

    private long crc32(String value) {
        CRC32 crc32 = new CRC32();
        crc32.update(String.valueOf(value).getBytes(StandardCharsets.UTF_8));
        return crc32.getValue();
    }

}
