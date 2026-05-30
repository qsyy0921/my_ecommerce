package cn.bugstack.infrastructure.adapter.port;

import cn.bugstack.domain.seckill.adapter.port.ISeckillRefundPort;
import cn.bugstack.domain.seckill.adapter.port.ISeckillResultCachePort;
import cn.bugstack.domain.seckill.model.entity.SeckillOrderEntity;
import cn.bugstack.domain.seckill.model.entity.SeckillStockFlowEntity;
import cn.bugstack.domain.seckill.model.valobj.SeckillOrderStatusEnumVO;
import cn.bugstack.domain.shared.adapter.port.IOrderStateFlowPort;
import cn.bugstack.domain.shared.model.entity.OrderStateTransitionEntity;
import cn.bugstack.infrastructure.adapter.support.SeckillOrderAssembler;
import cn.bugstack.infrastructure.adapter.support.SeckillOrderTableGateway;
import cn.bugstack.infrastructure.adapter.support.SeckillStockReleaseSupport;
import cn.bugstack.infrastructure.dao.po.SeckillOrder;
import cn.bugstack.types.enums.ResponseCode;
import cn.bugstack.types.exception.AppException;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;

@Service
public class SeckillRefundPort implements ISeckillRefundPort {

    @Resource
    private IOrderStateFlowPort orderStateFlowPort;
    @Resource
    private ISeckillResultCachePort seckillResultCachePort;
    @Resource
    private SeckillOrderTableGateway seckillOrderTableGateway;
    @Resource
    private SeckillStockReleaseSupport seckillStockReleaseSupport;

    @Transactional(timeout = 5)
    @Override
    public SeckillOrderEntity refundSeckillOrder(String userId, String outTradeNo, String refundReason) {
        SeckillOrder seckillOrder = seckillOrderTableGateway.queryByOutTradeNo(userId, outTradeNo);
        if (null == seckillOrder) {
            throw new AppException(ResponseCode.E0206);
        }

        SeckillOrderStatusEnumVO status = SeckillOrderStatusEnumVO.valueOf(seckillOrder.getStatus());
        if (SeckillOrderStatusEnumVO.REFUND.equals(status) || SeckillOrderStatusEnumVO.CLOSE.equals(status)) {
            SeckillOrderEntity entity = SeckillOrderAssembler.toEntity(seckillOrder);
            seckillResultCachePort.cache(entity, SeckillOrderEntity.RESULT_SUCCESS, "seckill refund already handled");
            return entity;
        }

        if (SeckillOrderStatusEnumVO.CREATE.equals(status)) {
            int updated = seckillOrderTableGateway.closeUnpaid(userId, outTradeNo, seckillOrder.getOrderId());
            if (updated <= 0) {
                throw new AppException(ResponseCode.UPDATE_ZERO);
            }
            String message = null == refundReason ? "unpaid seckill order canceled" : refundReason;
            seckillStockReleaseSupport.releaseByOrder(seckillOrder, SeckillStockFlowEntity.ROLLBACK_CANCEL, 1, message);
            orderStateFlowPort.record(OrderStateTransitionEntity.seckillUnpaidCanceled(
                    seckillOrder.getOutTradeNo(),
                    seckillOrder.getOrderId(),
                    seckillOrder.getUserId(),
                    MDC.get("trace-id"),
                    message));
        } else if (SeckillOrderStatusEnumVO.COMPLETE.equals(status)) {
            int updated = seckillOrderTableGateway.refundPaid(userId, outTradeNo, seckillOrder.getOrderId());
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
        } else {
            throw new AppException(ResponseCode.E0207);
        }

        SeckillOrder updatedOrder = seckillOrderTableGateway.queryByOutTradeNo(userId, outTradeNo);
        SeckillOrderEntity entity = SeckillOrderAssembler.toEntity(updatedOrder);
        seckillResultCachePort.cache(entity, SeckillOrderEntity.RESULT_SUCCESS, "seckill refund handled");
        return entity;
    }

}
