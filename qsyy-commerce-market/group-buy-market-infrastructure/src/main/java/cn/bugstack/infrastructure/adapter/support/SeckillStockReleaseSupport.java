package cn.bugstack.infrastructure.adapter.support;

import cn.bugstack.domain.seckill.adapter.port.ISeckillStockFlowPort;
import cn.bugstack.domain.seckill.adapter.port.ISeckillStockReservationPort;
import cn.bugstack.domain.seckill.model.entity.SeckillOrderEntity;
import cn.bugstack.domain.seckill.model.entity.SeckillStockFlowEntity;
import cn.bugstack.infrastructure.dao.ISeckillActivityDao;
import cn.bugstack.infrastructure.dao.po.SeckillOrder;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

@Slf4j
@Component
public class SeckillStockReleaseSupport {

    @Resource
    private ISeckillActivityDao seckillActivityDao;
    @Resource
    private ISeckillStockFlowPort seckillStockFlowPort;
    @Resource
    private ISeckillStockReservationPort seckillStockReservationPort;
    @Resource
    private SeckillSoldOutCache seckillSoldOutCache;

    public void releaseByOrder(SeckillOrder order, String changeType, int changeCount, String message) {
        seckillActivityDao.updateReleaseStock(order.getActivityId());
        SeckillOrderEntity entity = SeckillOrderAssembler.toEntity(order);
        entity.setTraceId(MDC.get("trace-id"));
        seckillStockReservationPort.release(entity, changeCount);
        seckillStockFlowPort.record(SeckillStockFlowEntity.rollback(entity, changeType, changeCount, message));
        seckillSoldOutCache.clear(order.getActivityId());
    }

    public void rollbackReservation(SeckillOrderEntity seckillOrderEntity, boolean removeResult, String reason) {
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
