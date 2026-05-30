package cn.bugstack.infrastructure.adapter.port;

import cn.bugstack.domain.order.adapter.port.IRefundFlowPort;
import cn.bugstack.domain.order.model.entity.OrderEntity;
import cn.bugstack.domain.order.model.entity.RefundFlowEntity;
import cn.bugstack.infrastructure.dao.IRefundFlowDao;
import cn.bugstack.infrastructure.dao.po.RefundFlow;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class RefundFlowPort implements IRefundFlowPort {

    @Resource
    private IRefundFlowDao refundFlowDao;

    @Override
    public void recordRefund(OrderEntity orderEntity, String refundStatus, String refundReason) {
        if (null == orderEntity) {
            return;
        }
        refundFlowDao.insertIgnore(RefundFlow.builder()
                .flowNo("REFUND:" + orderEntity.getOrderId() + ":" + refundStatus)
                .orderId(orderEntity.getOrderId())
                .userId(orderEntity.getUserId())
                .refundChannel("mock")
                .refundAmount(orderEntity.getPayAmount())
                .refundStatus(refundStatus)
                .refundReason(refundReason)
                .refundTime(new Date())
                .build());
    }

    @Override
    public List<RefundFlowEntity> queryMissThirdPartyBillList(Integer limit) {
        List<RefundFlow> refundFlowList = refundFlowDao.queryMissThirdPartyBillList(limit);
        if (null == refundFlowList || refundFlowList.isEmpty()) {
            return new ArrayList<>();
        }
        return refundFlowList.stream().map(this::toEntity).collect(Collectors.toList());
    }

    private RefundFlowEntity toEntity(RefundFlow refundFlow) {
        return RefundFlowEntity.builder()
                .id(refundFlow.getId())
                .flowNo(refundFlow.getFlowNo())
                .orderId(refundFlow.getOrderId())
                .userId(refundFlow.getUserId())
                .refundChannel(refundFlow.getRefundChannel())
                .channelRefundNo(refundFlow.getChannelRefundNo())
                .refundAmount(refundFlow.getRefundAmount())
                .refundStatus(refundFlow.getRefundStatus())
                .refundReason(refundFlow.getRefundReason())
                .refundTime(refundFlow.getRefundTime())
                .rawMessage(refundFlow.getRawMessage())
                .createTime(refundFlow.getCreateTime())
                .updateTime(refundFlow.getUpdateTime())
                .build();
    }

}
