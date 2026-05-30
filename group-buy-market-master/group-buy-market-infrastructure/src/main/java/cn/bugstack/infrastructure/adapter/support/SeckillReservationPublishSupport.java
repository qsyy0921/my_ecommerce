package cn.bugstack.infrastructure.adapter.support;

import cn.bugstack.domain.seckill.adapter.port.ISeckillMetricsPort;
import cn.bugstack.domain.seckill.adapter.port.ISeckillOrderMessagePort;
import cn.bugstack.domain.seckill.adapter.port.ISeckillStockReservationPort;
import cn.bugstack.domain.seckill.model.entity.SeckillOrderEntity;
import cn.bugstack.domain.seckill.model.entity.SeckillStockReservationEntity;
import cn.bugstack.types.enums.ResponseCode;
import cn.bugstack.types.exception.AppException;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

@Component
public class SeckillReservationPublishSupport {

    @Resource
    private ISeckillStockReservationPort seckillStockReservationPort;
    @Resource
    private ISeckillOrderMessagePort seckillOrderMessagePort;
    @Resource
    private ISeckillMetricsPort seckillMetricsPort;
    @Resource
    private SeckillStockGuardSupport seckillStockGuardSupport;
    @Resource
    private SeckillStockReleaseSupport seckillStockReleaseSupport;

    public SeckillOrderEntity reserveAndPublish(SeckillOrderEntity seckillOrderEntity, Integer stockBucketTryCount) {
        Long activityId = seckillOrderEntity.getActivityId();
        seckillOrderEntity.setTraceId(MDC.get("trace-id"));
        seckillStockGuardSupport.ensureAvailable(activityId);

        SeckillStockReservationEntity reservation = seckillStockReservationPort.reserve(seckillOrderEntity, stockBucketTryCount);
        if (reservation.isDuplicate()) {
            seckillMetricsPort.recordDuplicate();
            throw new AppException(ResponseCode.E0204);
        }
        if (!reservation.isSuccess()) {
            seckillStockGuardSupport.markSoldOutIfEmpty(activityId);
            throw new AppException(ResponseCode.E0203);
        }

        try {
            publishOrderCreate(seckillOrderEntity);
            return seckillOrderEntity;
        } catch (RuntimeException e) {
            seckillStockReleaseSupport.rollbackReservation(seckillOrderEntity, true, "enqueue order create failed");
            throw e;
        }
    }

    private void publishOrderCreate(SeckillOrderEntity seckillOrderEntity) {
        if (!seckillOrderMessagePort.publishOrderCreate(seckillOrderEntity)) {
            throw new AppException(ResponseCode.RATE_LIMITER);
        }
    }

}
