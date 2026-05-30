package cn.bugstack.infrastructure.adapter.support;

import cn.bugstack.domain.seckill.model.entity.SeckillStockFlowEntity;
import cn.bugstack.domain.shared.adapter.port.IOrderStateFlowPort;
import cn.bugstack.domain.shared.model.entity.OrderStateTransitionEntity;
import cn.bugstack.infrastructure.dao.po.SeckillOrder;
import cn.bugstack.types.enums.ResponseCode;
import cn.bugstack.types.exception.AppException;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

@Component
public class SeckillPaidRefundSupport {

    @Resource
    private IOrderStateFlowPort orderStateFlowPort;
    @Resource
    private SeckillOrderTableGateway seckillOrderTableGateway;
    @Resource
    private SeckillStockReleaseSupport seckillStockReleaseSupport;

    public void refund(SeckillOrder seckillOrder, String refundReason) {
        int updated = seckillOrderTableGateway.refundPaid(seckillOrder.getUserId(), seckillOrder.getOutTradeNo(), seckillOrder.getOrderId());
        if (updated <= 0) {
            throw new AppException(ResponseCode.UPDATE_ZERO);
        }

        String message = null == refundReason ? "paid seckill order refunded" : refundReason;
        seckillStockReleaseSupport.releaseByOrder(seckillOrder, SeckillStockFlowEntity.ROLLBACK_REFUND, 1, message);
        orderStateFlowPort.record(OrderStateTransitionEntity.seckillOrderRefunded(
                seckillOrder.getOutTradeNo(),
                seckillOrder.getOrderId(),
                seckillOrder.getUserId(),
                MDC.get("trace-id"),
                message));
    }

}
