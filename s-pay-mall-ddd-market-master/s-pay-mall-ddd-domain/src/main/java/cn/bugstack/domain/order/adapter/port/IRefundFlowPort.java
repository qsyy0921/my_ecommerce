package cn.bugstack.domain.order.adapter.port;

import cn.bugstack.domain.order.model.entity.OrderEntity;
import cn.bugstack.domain.order.model.entity.RefundFlowEntity;

import java.util.List;

public interface IRefundFlowPort {

    void recordRefund(OrderEntity orderEntity, String refundStatus, String refundReason);

    List<RefundFlowEntity> queryMissThirdPartyBillList(Integer limit);

}
