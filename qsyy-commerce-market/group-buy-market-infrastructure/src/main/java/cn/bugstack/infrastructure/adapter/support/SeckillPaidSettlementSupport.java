package cn.bugstack.infrastructure.adapter.support;

import cn.bugstack.domain.seckill.model.valobj.SeckillOrderStatusEnumVO;
import cn.bugstack.domain.shared.adapter.port.IOrderStateFlowPort;
import cn.bugstack.domain.shared.model.entity.OrderStateTransitionEntity;
import cn.bugstack.infrastructure.dao.po.SeckillOrder;
import cn.bugstack.types.enums.ResponseCode;
import cn.bugstack.types.exception.AppException;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

@Component
public class SeckillPaidSettlementSupport {

    @Resource
    private IOrderStateFlowPort orderStateFlowPort;
    @Resource
    private SeckillOrderTableGateway seckillOrderTableGateway;

    public SeckillOrder settle(SeckillOrder seckillOrder) {
        int updated = seckillOrderTableGateway.paySuccess(seckillOrder.getUserId(), seckillOrder.getOutTradeNo(), seckillOrder.getOrderId());
        if (updated <= 0) {
            SeckillOrder latest = seckillOrderTableGateway.queryByOutTradeNo(seckillOrder.getUserId(), seckillOrder.getOutTradeNo());
            if (null != latest && SeckillOrderStatusEnumVO.COMPLETE.equals(SeckillOrderStatusEnumVO.valueOf(latest.getStatus()))) {
                return latest;
            }
            throw new AppException(ResponseCode.UPDATE_ZERO);
        }

        orderStateFlowPort.record(OrderStateTransitionEntity.seckillOrderPaid(
                seckillOrder.getOutTradeNo(),
                seckillOrder.getOrderId(),
                seckillOrder.getUserId(),
                MDC.get("trace-id")));
        return seckillOrderTableGateway.queryByOutTradeNo(seckillOrder.getUserId(), seckillOrder.getOutTradeNo());
    }

}
