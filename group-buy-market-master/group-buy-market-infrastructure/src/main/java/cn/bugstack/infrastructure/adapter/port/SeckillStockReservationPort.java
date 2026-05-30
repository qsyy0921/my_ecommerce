package cn.bugstack.infrastructure.adapter.port;

import cn.bugstack.domain.seckill.adapter.port.ISeckillStockReservationPort;
import cn.bugstack.domain.seckill.model.entity.SeckillOrderEntity;
import cn.bugstack.domain.seckill.model.entity.SeckillStockReservationEntity;
import cn.bugstack.infrastructure.adapter.support.SeckillQualificationReservationSupport;
import cn.bugstack.infrastructure.adapter.support.SeckillStockBucketInventorySupport;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

@Service
public class SeckillStockReservationPort implements ISeckillStockReservationPort {

    @Resource
    private SeckillStockBucketInventorySupport seckillStockBucketInventorySupport;
    @Resource
    private SeckillQualificationReservationSupport seckillQualificationReservationSupport;

    @Override
    public boolean isStockInitialized(Long activityId) {
        return seckillStockBucketInventorySupport.isInitialized(activityId);
    }

    @Override
    public boolean tryAcquireInitializationLock(Long activityId, long waitMillis, long leaseMillis) throws InterruptedException {
        return seckillStockBucketInventorySupport.tryAcquireInitializationLock(activityId, waitMillis, leaseMillis);
    }

    @Override
    public void releaseInitializationLock(Long activityId) {
        seckillStockBucketInventorySupport.releaseInitializationLock(activityId);
    }

    @Override
    public void initializeStock(Long activityId, int availableCount) {
        seckillStockBucketInventorySupport.initialize(activityId, availableCount);
    }

    @Override
    public int queryStock(Long activityId) {
        return seckillStockBucketInventorySupport.query(activityId);
    }

    @Override
    public SeckillStockReservationEntity reserve(SeckillOrderEntity seckillOrderEntity, Integer stockBucketTryCount) {
        return seckillQualificationReservationSupport.reserve(seckillOrderEntity, stockBucketTryCount);
    }

    @Override
    public SeckillOrderEntity rollback(SeckillOrderEntity seckillOrderEntity, boolean removeResult) {
        return seckillQualificationReservationSupport.rollback(seckillOrderEntity, removeResult);
    }

    @Override
    public SeckillOrderEntity release(SeckillOrderEntity seckillOrderEntity, int changeCount) {
        return seckillQualificationReservationSupport.release(seckillOrderEntity, changeCount);
    }

}
