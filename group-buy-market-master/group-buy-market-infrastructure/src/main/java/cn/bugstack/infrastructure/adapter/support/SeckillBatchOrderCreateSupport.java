package cn.bugstack.infrastructure.adapter.support;

import cn.bugstack.domain.seckill.adapter.port.ISeckillResultCachePort;
import cn.bugstack.domain.seckill.adapter.port.ISeckillStockFlowPort;
import cn.bugstack.domain.seckill.model.entity.SeckillOrderEntity;
import cn.bugstack.domain.seckill.model.entity.SeckillStockFlowEntity;
import cn.bugstack.domain.shared.adapter.port.IOrderStateFlowPort;
import cn.bugstack.domain.shared.model.entity.OrderStateTransitionEntity;
import cn.bugstack.infrastructure.dao.po.SeckillOrder;
import cn.bugstack.infrastructure.event.SeckillFaultInjector;
import cn.bugstack.infrastructure.event.SeckillStreamMetrics;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;

@Component
public class SeckillBatchOrderCreateSupport {

    @Resource
    private IOrderStateFlowPort orderStateFlowPort;
    @Resource
    private ISeckillStockFlowPort seckillStockFlowPort;
    @Resource
    private ISeckillResultCachePort seckillResultCachePort;
    @Resource
    private SeckillOrderTableGateway seckillOrderTableGateway;
    @Resource
    private SeckillStockReleaseSupport seckillStockReleaseSupport;
    @Resource
    private SeckillStreamMetrics seckillStreamMetrics;
    @Resource
    private SeckillFaultInjector seckillFaultInjector;

    public void create(List<SeckillOrderEntity> seckillOrderEntities) {
        if (null == seckillOrderEntities || seckillOrderEntities.isEmpty()) {
            return;
        }

        seckillFaultInjector.beforeBatchInsert();
        long startNanos = System.nanoTime();
        try {
            seckillOrderTableGateway.insertIgnoreBatch(toPos(seckillOrderEntities));
        } finally {
            seckillStreamMetrics.recordBatchInsert(System.nanoTime() - startNanos, seckillOrderEntities.size());
        }

        recordCreatedOrders(seckillOrderEntities);
    }

    private List<SeckillOrder> toPos(List<SeckillOrderEntity> seckillOrderEntities) {
        List<SeckillOrder> seckillOrders = new ArrayList<>(seckillOrderEntities.size());
        for (SeckillOrderEntity seckillOrderEntity : seckillOrderEntities) {
            seckillOrders.add(SeckillOrderAssembler.toPo(seckillOrderEntity));
        }
        return seckillOrders;
    }

    private void recordCreatedOrders(List<SeckillOrderEntity> seckillOrderEntities) {
        List<SeckillStockFlowEntity> stockFlows = new ArrayList<>(seckillOrderEntities.size());
        for (SeckillOrderEntity seckillOrderEntity : seckillOrderEntities) {
            SeckillOrderEntity existsOrder = seckillOrderTableGateway.queryEntityByOutTradeNo(seckillOrderEntity.getUserId(), seckillOrderEntity.getOutTradeNo());
            if (null == existsOrder) {
                seckillResultCachePort.cache(seckillOrderEntity, SeckillOrderEntity.RESULT_DUPLICATE, "order create ignored by unique constraint");
                seckillStockReleaseSupport.rollbackReservation(seckillOrderEntity, false, "batch order create ignored by unique constraint");
                continue;
            }

            stockFlows.add(SeckillStockFlowEntity.reserved(seckillOrderEntity, "order created"));
            orderStateFlowPort.record(OrderStateTransitionEntity.seckillOrderCreated(
                    seckillOrderEntity.getOutTradeNo(),
                    seckillOrderEntity.getOrderId(),
                    seckillOrderEntity.getUserId(),
                    seckillOrderEntity.getTraceId(),
                    seckillOrderEntity.getSourceMessageId()));
            seckillResultCachePort.cache(existsOrder, SeckillOrderEntity.RESULT_SUCCESS, "order created");
        }
        if (!stockFlows.isEmpty()) {
            seckillStockFlowPort.recordBatch(stockFlows);
        }
    }

}
