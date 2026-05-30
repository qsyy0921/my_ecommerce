package cn.bugstack.infrastructure.adapter.port;

import cn.bugstack.domain.seckill.adapter.port.ISeckillMetricsPort;
import cn.bugstack.domain.seckill.adapter.port.ISeckillOrderLockPort;
import cn.bugstack.domain.seckill.adapter.port.ISeckillStockAvailabilityPort;
import cn.bugstack.domain.seckill.adapter.port.ISeckillStockFlowPort;
import cn.bugstack.domain.seckill.adapter.port.ISeckillStockReservationPort;
import cn.bugstack.domain.seckill.model.entity.SeckillOrderEntity;
import cn.bugstack.domain.seckill.model.entity.SeckillStockFlowEntity;
import cn.bugstack.domain.seckill.model.entity.SeckillStockReservationEntity;
import cn.bugstack.infrastructure.adapter.support.SeckillSoldOutCache;
import cn.bugstack.infrastructure.event.EventPublisher;
import cn.bugstack.infrastructure.event.SeckillOrderCreateBuffer;
import cn.bugstack.types.enums.ResponseCode;
import cn.bugstack.types.exception.AppException;
import com.alibaba.fastjson.JSON;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

@Slf4j
@Service
public class SeckillOrderLockPort implements ISeckillOrderLockPort {

    @Value("${app.seckill.stock-bucket-try-count:64}")
    private Integer stockBucketTryCount;
    @Value("${spring.rabbitmq.config.producer.topic_seckill_order_create.routing_key}")
    private String topicSeckillOrderCreate;

    @Resource
    private ISeckillStockAvailabilityPort seckillStockAvailabilityPort;
    @Resource
    private ISeckillStockReservationPort seckillStockReservationPort;
    @Resource
    private ISeckillStockFlowPort seckillStockFlowPort;
    @Resource
    private SeckillSoldOutCache seckillSoldOutCache;
    @Resource
    private EventPublisher eventPublisher;
    @Resource
    private SeckillOrderCreateBuffer seckillOrderCreateBuffer;
    @Resource
    private ISeckillMetricsPort seckillMetricsPort;

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

        if (seckillStockAvailabilityPort.queryAvailableStock(activityId) <= 0) {
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
        seckillStockAvailabilityPort.queryAvailableStock(activityId);
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

}
