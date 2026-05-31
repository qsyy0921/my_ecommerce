package cn.bugstack.infrastructure.adapter.support;

import cn.bugstack.domain.seckill.adapter.port.ISeckillResultCachePort;
import cn.bugstack.domain.seckill.adapter.port.ISeckillStockFlowPort;
import cn.bugstack.domain.seckill.model.entity.SeckillOrderEntity;
import cn.bugstack.domain.seckill.model.entity.SeckillStockFlowEntity;
import cn.bugstack.domain.shared.adapter.port.IOrderStateFlowPort;
import cn.bugstack.domain.shared.model.entity.OrderStateTransitionEntity;
import cn.bugstack.types.exception.AppException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

@Component
public class SeckillSingleOrderCreateSupport {

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

    public void create(SeckillOrderEntity seckillOrderEntity) {
        SeckillOrderEntity existsOrder = seckillOrderTableGateway.queryEntityByOutTradeNo(seckillOrderEntity.getUserId(), seckillOrderEntity.getOutTradeNo());
        if (null != existsOrder) {
            seckillResultCachePort.cache(existsOrder, SeckillOrderEntity.RESULT_SUCCESS, "order already created");
            return;
        }

        try {
            seckillOrderTableGateway.insert(SeckillOrderAssembler.toPo(seckillOrderEntity));
            recordCreated(seckillOrderEntity);
            seckillResultCachePort.cache(seckillOrderEntity, SeckillOrderEntity.RESULT_SUCCESS, "order created");
        } catch (DuplicateKeyException e) {
            handleDuplicate(seckillOrderEntity);
        } catch (AppException e) {
            throw e;
        } catch (RuntimeException e) {
            seckillResultCachePort.cache(seckillOrderEntity, SeckillOrderEntity.RESULT_FAIL, e.getMessage());
            seckillStockReleaseSupport.rollbackReservation(seckillOrderEntity, false, e.getMessage());
            throw e;
        }
    }

    private void handleDuplicate(SeckillOrderEntity seckillOrderEntity) {
        SeckillOrderEntity duplicateOrder = seckillOrderTableGateway.queryEntityByOutTradeNo(seckillOrderEntity.getUserId(), seckillOrderEntity.getOutTradeNo());
        if (null != duplicateOrder) {
            seckillResultCachePort.cache(duplicateOrder, SeckillOrderEntity.RESULT_SUCCESS, "order already created");
            return;
        }
        seckillResultCachePort.cache(seckillOrderEntity, SeckillOrderEntity.RESULT_DUPLICATE, "duplicate seckill order");
        seckillStockReleaseSupport.rollbackReservation(seckillOrderEntity, false, "duplicate seckill order");
    }

    private void recordCreated(SeckillOrderEntity seckillOrderEntity) {
        seckillStockFlowPort.record(SeckillStockFlowEntity.reserved(seckillOrderEntity, "order created"));
        orderStateFlowPort.record(OrderStateTransitionEntity.seckillOrderCreated(
                seckillOrderEntity.getOutTradeNo(),
                seckillOrderEntity.getOrderId(),
                seckillOrderEntity.getUserId(),
                seckillOrderEntity.getTraceId(),
                seckillOrderEntity.getSourceMessageId()));
    }

}
