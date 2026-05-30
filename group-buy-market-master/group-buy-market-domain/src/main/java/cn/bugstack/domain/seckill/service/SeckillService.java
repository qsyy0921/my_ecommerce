package cn.bugstack.domain.seckill.service;

import cn.bugstack.domain.seckill.adapter.port.ISeckillMaintenancePort;
import cn.bugstack.domain.seckill.adapter.port.ISeckillOrderCommandPort;
import cn.bugstack.domain.seckill.adapter.port.ISeckillOrderLockPort;
import cn.bugstack.domain.seckill.adapter.port.ISeckillQueryPort;
import cn.bugstack.domain.seckill.adapter.port.ISeckillStockAvailabilityPort;
import cn.bugstack.domain.seckill.model.entity.SeckillActivityEntity;
import cn.bugstack.domain.seckill.model.entity.SeckillOrderEntity;
import cn.bugstack.domain.seckill.model.valobj.SeckillOrderStatusEnumVO;
import cn.bugstack.types.enums.ResponseCode;
import cn.bugstack.types.exception.AppException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.RandomStringUtils;

import java.util.Date;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;

/**
 * Seckill domain service implementation.
 */
@Slf4j
public class SeckillService implements ISeckillService {

    private final ISeckillQueryPort seckillQueryPort;
    private final ISeckillStockAvailabilityPort seckillStockAvailabilityPort;
    private final ISeckillOrderLockPort seckillOrderLockPort;
    private final ISeckillMaintenancePort seckillMaintenancePort;
    private final ISeckillOrderCommandPort seckillOrderCommandPort;
    private final Integer maxConcurrentPerActivity;

    public SeckillService(ISeckillQueryPort seckillQueryPort,
                          ISeckillStockAvailabilityPort seckillStockAvailabilityPort,
                          ISeckillOrderLockPort seckillOrderLockPort,
                          ISeckillMaintenancePort seckillMaintenancePort,
                          ISeckillOrderCommandPort seckillOrderCommandPort,
                          Integer maxConcurrentPerActivity) {
        this.seckillQueryPort = seckillQueryPort;
        this.seckillStockAvailabilityPort = seckillStockAvailabilityPort;
        this.seckillOrderLockPort = seckillOrderLockPort;
        this.seckillMaintenancePort = seckillMaintenancePort;
        this.seckillOrderCommandPort = seckillOrderCommandPort;
        this.maxConcurrentPerActivity = maxConcurrentPerActivity;
    }

    private final ConcurrentHashMap<Long, Semaphore> activitySemaphores = new ConcurrentHashMap<>();

    @Override
    public SeckillActivityEntity querySeckillActivity(Long activityId, String source, String channel, String goodsId) {
        SeckillActivityEntity seckillActivityEntity = seckillQueryPort.querySeckillActivity(activityId, source, channel, goodsId);
        if (null == seckillActivityEntity) {
            throw new AppException(ResponseCode.E0201);
        }
        Integer availableStock = seckillStockAvailabilityPort.queryAvailableStock(seckillActivityEntity.getActivityId());
        return SeckillActivityEntity.builder()
                .activityId(seckillActivityEntity.getActivityId())
                .activityName(seckillActivityEntity.getActivityName())
                .source(seckillActivityEntity.getSource())
                .channel(seckillActivityEntity.getChannel())
                .goodsId(seckillActivityEntity.getGoodsId())
                .goodsName(seckillActivityEntity.getGoodsName())
                .originalPrice(seckillActivityEntity.getOriginalPrice())
                .seckillPrice(seckillActivityEntity.getSeckillPrice())
                .totalCount(seckillActivityEntity.getTotalCount())
                .availableCount(availableStock)
                .lockCount(seckillActivityEntity.getLockCount())
                .takeLimitCount(seckillActivityEntity.getTakeLimitCount())
                .status(seckillActivityEntity.getStatus())
                .startTime(seckillActivityEntity.getStartTime())
                .endTime(seckillActivityEntity.getEndTime())
                .build();
    }

    @Override
    public SeckillOrderEntity querySeckillOrderByOutTradeNo(String userId, String outTradeNo) {
        return seckillQueryPort.querySeckillOrderByOutTradeNo(userId, outTradeNo);
    }

    @Override
    public SeckillOrderEntity querySeckillResult(String userId, Long activityId, String outTradeNo) {
        return seckillQueryPort.querySeckillResult(userId, activityId, outTradeNo);
    }

    @Override
    public SeckillOrderEntity lockSeckillOrder(String userId, Long activityId, String source, String channel, String goodsId, String outTradeNo) {
        log.debug("seckill order lock start userId:{} activityId:{} goodsId:{} outTradeNo:{}", userId, activityId, goodsId, outTradeNo);

        Semaphore semaphore = activitySemaphores.computeIfAbsent(activityId, key -> new Semaphore(maxConcurrentPerActivity));
        if (!semaphore.tryAcquire()) {
            throw new AppException(ResponseCode.RATE_LIMITER);
        }
        try {
            SeckillActivityEntity seckillActivityEntity = seckillQueryPort.querySeckillActivity(activityId, source, channel, goodsId);
            if (null == seckillActivityEntity) {
                throw new AppException(ResponseCode.E0201);
            }
            if (!seckillActivityEntity.enabled(new Date())) {
                throw new AppException(ResponseCode.E0202);
            }

            SeckillOrderEntity seckillOrderEntity = SeckillOrderEntity.builder()
                    .userId(userId)
                    .activityId(seckillActivityEntity.getActivityId())
                    .activityName(seckillActivityEntity.getActivityName())
                    .goodsId(seckillActivityEntity.getGoodsId())
                    .goodsName(seckillActivityEntity.getGoodsName())
                    .source(seckillActivityEntity.getSource())
                    .channel(seckillActivityEntity.getChannel())
                    .orderId(RandomStringUtils.randomNumeric(12))
                    .outTradeNo(outTradeNo)
                    .originalPrice(seckillActivityEntity.getOriginalPrice())
                    .seckillPrice(seckillActivityEntity.getSeckillPrice())
                    .status(SeckillOrderStatusEnumVO.CREATE.getCode())
                    .build();

            return seckillOrderLockPort.lockSeckillOrder(seckillOrderEntity);
        } finally {
            semaphore.release();
        }
    }

    @Override
    public void createSeckillOrder(SeckillOrderEntity seckillOrderEntity) {
        seckillOrderCommandPort.createSeckillOrder(seckillOrderEntity);
    }

    @Override
    public void createSeckillOrders(List<SeckillOrderEntity> seckillOrderEntities) {
        seckillOrderCommandPort.createSeckillOrders(seckillOrderEntities);
    }

    @Override
    public SeckillOrderEntity settlementSeckillOrder(String userId, String outTradeNo) {
        return seckillOrderCommandPort.settlementSeckillOrder(userId, outTradeNo);
    }

    @Override
    public SeckillOrderEntity refundSeckillOrder(String userId, String outTradeNo, String refundReason) {
        return seckillOrderCommandPort.refundSeckillOrder(userId, outTradeNo, refundReason);
    }

    @Override
    public void syncSeckillActivityStock() {
        seckillMaintenancePort.syncSeckillActivityStock();
    }

    @Override
    public int releaseTimeoutUnpaidOrders() {
        return seckillMaintenancePort.releaseTimeoutUnpaidOrders();
    }

    @Override
    public int prewarmUpcomingActivities(Integer beforeMinutes, Integer limit) {
        return seckillMaintenancePort.prewarmUpcomingActivities(beforeMinutes, limit);
    }

}
