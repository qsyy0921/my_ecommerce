package cn.bugstack.domain.seckill.adapter.port;

import cn.bugstack.domain.seckill.model.entity.SeckillOrderEntity;
import cn.bugstack.domain.seckill.model.entity.SeckillStockReservationEntity;

public interface ISeckillStockReservationPort {

    boolean isStockInitialized(Long activityId);

    boolean tryAcquireInitializationLock(Long activityId, long waitMillis, long leaseMillis) throws InterruptedException;

    void releaseInitializationLock(Long activityId);

    void initializeStock(Long activityId, int availableCount);

    int queryStock(Long activityId);

    SeckillStockReservationEntity reserve(SeckillOrderEntity seckillOrderEntity, Integer stockBucketTryCount);

    SeckillOrderEntity rollback(SeckillOrderEntity seckillOrderEntity, boolean removeResult);

    SeckillOrderEntity release(SeckillOrderEntity seckillOrderEntity, int changeCount);

}
