package cn.bugstack.infrastructure.adapter.support;

import cn.bugstack.domain.seckill.adapter.port.ISeckillResultCachePort;
import cn.bugstack.domain.seckill.model.entity.SeckillOrderEntity;
import cn.bugstack.domain.seckill.model.entity.SeckillStockReservationEntity;
import cn.bugstack.infrastructure.event.SeckillFaultInjector;
import cn.bugstack.infrastructure.redis.IRedisService;
import com.alibaba.fastjson.JSON;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.concurrent.TimeUnit;

@Component
public class SeckillQualificationReservationSupport {

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
