package cn.bugstack.infrastructure.adapter.port;

import cn.bugstack.domain.seckill.adapter.port.ISeckillOrderCreatePort;
import cn.bugstack.domain.seckill.adapter.port.ISeckillResultCachePort;
import cn.bugstack.domain.seckill.adapter.port.ISeckillStockFlowPort;
import cn.bugstack.domain.seckill.model.entity.SeckillOrderEntity;
import cn.bugstack.domain.seckill.model.entity.SeckillStockFlowEntity;
import cn.bugstack.domain.shared.adapter.port.IOrderStateFlowPort;
import cn.bugstack.domain.shared.model.entity.OrderStateTransitionEntity;
import cn.bugstack.infrastructure.adapter.support.SeckillOrderAssembler;
import cn.bugstack.infrastructure.adapter.support.SeckillOrderTableGateway;
import cn.bugstack.infrastructure.adapter.support.SeckillStockReleaseSupport;
import cn.bugstack.infrastructure.dao.po.SeckillOrder;
import cn.bugstack.infrastructure.event.SeckillFaultInjector;
import cn.bugstack.infrastructure.event.SeckillStreamMetrics;
import cn.bugstack.types.exception.AppException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;

@Service
public class SeckillOrderCreatePort implements ISeckillOrderCreatePort {

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

    @Transactional(timeout = 5)
    @Override
    public void createSeckillOrder(SeckillOrderEntity seckillOrderEntity) {
        SeckillOrderEntity existsOrder = seckillOrderTableGateway.queryEntityByOutTradeNo(seckillOrderEntity.getUserId(), seckillOrderEntity.getOutTradeNo());
        if (null != existsOrder) {
            seckillResultCachePort.cache(existsOrder, SeckillOrderEntity.RESULT_SUCCESS, "order already created");
            return;
        }

        try {
            seckillOrderTableGateway.insert(SeckillOrderAssembler.toPo(seckillOrderEntity));
            seckillStockFlowPort.record(SeckillStockFlowEntity.reserved(seckillOrderEntity, "order created"));
            orderStateFlowPort.record(OrderStateTransitionEntity.seckillOrderCreated(
                    seckillOrderEntity.getOutTradeNo(),
                    seckillOrderEntity.getOrderId(),
                    seckillOrderEntity.getUserId(),
                    seckillOrderEntity.getTraceId(),
                    seckillOrderEntity.getSourceMessageId()));
            seckillResultCachePort.cache(seckillOrderEntity, SeckillOrderEntity.RESULT_SUCCESS, "order created");
        } catch (DuplicateKeyException e) {
            SeckillOrderEntity duplicateOrder = seckillOrderTableGateway.queryEntityByOutTradeNo(seckillOrderEntity.getUserId(), seckillOrderEntity.getOutTradeNo());
            if (null != duplicateOrder) {
                seckillResultCachePort.cache(duplicateOrder, SeckillOrderEntity.RESULT_SUCCESS, "order already created");
                return;
            }
            seckillResultCachePort.cache(seckillOrderEntity, SeckillOrderEntity.RESULT_DUPLICATE, "duplicate seckill order");
            seckillStockReleaseSupport.rollbackReservation(seckillOrderEntity, false, "duplicate seckill order");
        } catch (AppException e) {
            throw e;
        } catch (RuntimeException e) {
            seckillResultCachePort.cache(seckillOrderEntity, SeckillOrderEntity.RESULT_FAIL, e.getMessage());
            seckillStockReleaseSupport.rollbackReservation(seckillOrderEntity, false, e.getMessage());
            throw e;
        }
    }

    @Transactional(timeout = 10)
    @Override
    public void createSeckillOrders(List<SeckillOrderEntity> seckillOrderEntities) {
        if (null == seckillOrderEntities || seckillOrderEntities.isEmpty()) {
            return;
        }

        List<SeckillOrder> seckillOrders = new ArrayList<>(seckillOrderEntities.size());
        for (SeckillOrderEntity seckillOrderEntity : seckillOrderEntities) {
            seckillOrders.add(SeckillOrderAssembler.toPo(seckillOrderEntity));
        }

        seckillFaultInjector.beforeBatchInsert();
        long startNanos = System.nanoTime();
        try {
            seckillOrderTableGateway.insertIgnoreBatch(seckillOrders);
        } finally {
            seckillStreamMetrics.recordBatchInsert(System.nanoTime() - startNanos, seckillOrderEntities.size());
        }

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
