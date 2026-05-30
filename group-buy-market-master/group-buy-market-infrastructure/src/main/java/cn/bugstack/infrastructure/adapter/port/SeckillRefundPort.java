package cn.bugstack.infrastructure.adapter.port;

import cn.bugstack.domain.seckill.adapter.port.ISeckillRefundPort;
import cn.bugstack.domain.seckill.adapter.port.ISeckillResultCachePort;
import cn.bugstack.domain.seckill.model.entity.SeckillOrderEntity;
import cn.bugstack.domain.seckill.model.valobj.SeckillOrderStatusEnumVO;
import cn.bugstack.infrastructure.adapter.support.SeckillOrderAssembler;
import cn.bugstack.infrastructure.adapter.support.SeckillOrderTableGateway;
import cn.bugstack.infrastructure.adapter.support.SeckillPaidRefundSupport;
import cn.bugstack.infrastructure.adapter.support.SeckillUnpaidCancelSupport;
import cn.bugstack.infrastructure.dao.po.SeckillOrder;
import cn.bugstack.types.enums.ResponseCode;
import cn.bugstack.types.exception.AppException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;

@Service
public class SeckillRefundPort implements ISeckillRefundPort {

    @Resource
    private ISeckillResultCachePort seckillResultCachePort;
    @Resource
    private SeckillOrderTableGateway seckillOrderTableGateway;
    @Resource
    private SeckillUnpaidCancelSupport seckillUnpaidCancelSupport;
    @Resource
    private SeckillPaidRefundSupport seckillPaidRefundSupport;

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
            seckillUnpaidCancelSupport.cancel(seckillOrder, refundReason);
        } else if (SeckillOrderStatusEnumVO.COMPLETE.equals(status)) {
            seckillPaidRefundSupport.refund(seckillOrder, refundReason);
        } else {
            throw new AppException(ResponseCode.E0207);
        }

        SeckillOrder updatedOrder = seckillOrderTableGateway.queryByOutTradeNo(userId, outTradeNo);
        SeckillOrderEntity entity = SeckillOrderAssembler.toEntity(updatedOrder);
        seckillResultCachePort.cache(entity, SeckillOrderEntity.RESULT_SUCCESS, "seckill refund handled");
        return entity;
    }

}
