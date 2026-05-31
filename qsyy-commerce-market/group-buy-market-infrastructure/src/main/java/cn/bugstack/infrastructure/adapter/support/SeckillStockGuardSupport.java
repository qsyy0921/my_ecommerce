package cn.bugstack.infrastructure.adapter.support;

import cn.bugstack.domain.seckill.adapter.port.ISeckillMetricsPort;
import cn.bugstack.domain.seckill.adapter.port.ISeckillStockAvailabilityPort;
import cn.bugstack.domain.seckill.adapter.port.ISeckillStockReservationPort;
import cn.bugstack.types.enums.ResponseCode;
import cn.bugstack.types.exception.AppException;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

@Component
public class SeckillStockGuardSupport {

    @Resource
    private ISeckillStockAvailabilityPort seckillStockAvailabilityPort;
    @Resource
    private ISeckillStockReservationPort seckillStockReservationPort;
    @Resource
    private SeckillSoldOutCache seckillSoldOutCache;
    @Resource
    private ISeckillMetricsPort seckillMetricsPort;

    public void ensureAvailable(Long activityId) {
        if (seckillSoldOutCache.isSoldOut(activityId)) {
            seckillMetricsPort.recordStockNotEnough();
            throw new AppException(ResponseCode.E0203);
        }
        if (!seckillStockReservationPort.isStockInitialized(activityId)) {
            seckillStockAvailabilityPort.queryAvailableStock(activityId);
        }
    }

    public void markSoldOutIfEmpty(Long activityId) {
        if (seckillStockAvailabilityPort.queryAvailableStock(activityId) > 0) {
            return;
        }
        seckillSoldOutCache.markSoldOut(activityId);
        seckillMetricsPort.recordStockNotEnough();
    }

}
