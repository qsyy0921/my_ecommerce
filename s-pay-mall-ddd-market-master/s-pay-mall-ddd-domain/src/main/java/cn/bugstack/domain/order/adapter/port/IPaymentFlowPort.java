package cn.bugstack.domain.order.adapter.port;

import cn.bugstack.domain.order.model.entity.OrderEntity;
import cn.bugstack.domain.order.model.entity.PaymentFlowEntity;

import java.util.Date;
import java.util.List;

public interface IPaymentFlowPort {

    void recordPaySuccess(OrderEntity orderEntity, String payChannel, String channelTradeNo, String rawMessage, Date payTime);

    List<PaymentFlowEntity> queryMissThirdPartyBillList(Integer limit);

}
