package cn.bugstack.infrastructure.adapter.port;

import cn.bugstack.domain.seckill.adapter.port.ISeckillResultCachePort;
import cn.bugstack.domain.seckill.adapter.port.ISeckillSettlementPort;
import cn.bugstack.domain.seckill.model.entity.SeckillOrderEntity;
import cn.bugstack.domain.seckill.model.valobj.SeckillOrderStatusEnumVO;
import cn.bugstack.domain.shared.adapter.port.IOrderStateFlowPort;
import cn.bugstack.domain.shared.model.entity.OrderStateTransitionEntity;
import cn.bugstack.infrastructure.adapter.support.SeckillOrderAssembler;
import cn.bugstack.infrastructure.adapter.support.SeckillOrderTableGateway;
import cn.bugstack.infrastructure.dao.po.SeckillOrder;
import cn.bugstack.types.enums.ResponseCode;
import cn.bugstack.types.exception.AppException;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;

@Service
public class SeckillSettlementPort implements ISeckillSettlementPort {

    @Resource
    private IOrderStateFlowPort orderStateFlowPort;
    @Resource
    private ISeckillResultCachePort seckillResultCachePort;
    @Resource
    private SeckillOrderTableGateway seckillOrderTableGateway;

    @Transactional(timeout = 5)
    @Override
    public SeckillOrderEntity settlementSeckillOrder(String userId, String outTradeNo) {
        SeckillOrder seckillOrder = seckillOrderTableGateway.queryByOutTradeNo(userId, outTradeNo);
        if (null == seckillOrder) {
            throw new AppException(ResponseCode.E0206);
        }

        SeckillOrderStatusEnumVO status = SeckillOrderStatusEnumVO.valueOf(seckillOrder.getStatus());
        if (SeckillOrderStatusEnumVO.COMPLETE.equals(status)) {
            SeckillOrderEntity entity = SeckillOrderAssembler.toEntity(seckillOrder);
            seckillResultCachePort.cache(entity, SeckillOrderEntity.RESULT_SUCCESS, "seckill order already paid");
            return entity;
        }
        if (!status.canPay()) {
            throw new AppException(ResponseCode.E0207);
        }

        int updated = seckillOrderTableGateway.paySuccess(userId, outTradeNo, seckillOrder.getOrderId());
        if (updated <= 0) {
            SeckillOrder latest = seckillOrderTableGateway.queryByOutTradeNo(userId, outTradeNo);
            if (null != latest && SeckillOrderStatusEnumVO.COMPLETE.equals(SeckillOrderStatusEnumVO.valueOf(latest.getStatus()))) {
                SeckillOrderEntity entity = SeckillOrderAssembler.toEntity(latest);
                seckillResultCachePort.cache(entity, SeckillOrderEntity.RESULT_SUCCESS, "seckill order already paid");
                return entity;
            }
            throw new AppException(ResponseCode.UPDATE_ZERO);
        }

        orderStateFlowPort.record(OrderStateTransitionEntity.seckillOrderPaid(
                seckillOrder.getOutTradeNo(),
                seckillOrder.getOrderId(),
                seckillOrder.getUserId(),
                MDC.get("trace-id")));
        SeckillOrder updatedOrder = seckillOrderTableGateway.queryByOutTradeNo(userId, outTradeNo);
        SeckillOrderEntity entity = SeckillOrderAssembler.toEntity(updatedOrder);
        seckillResultCachePort.cache(entity, SeckillOrderEntity.RESULT_SUCCESS, "seckill order paid");
        return entity;
    }

}
