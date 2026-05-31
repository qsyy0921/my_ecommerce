package cn.bugstack.infrastructure.adapter.port;

import cn.bugstack.domain.order.adapter.port.IPaymentFlowPort;
import cn.bugstack.domain.order.model.entity.OrderEntity;
import cn.bugstack.domain.order.model.entity.PaymentFlowEntity;
import cn.bugstack.infrastructure.dao.IPaymentFlowDao;
import cn.bugstack.infrastructure.dao.po.PaymentFlow;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class PaymentFlowPort implements IPaymentFlowPort {

    @Resource
    private IPaymentFlowDao paymentFlowDao;

    @Override
    public void recordPaySuccess(OrderEntity orderEntity, String payChannel, String channelTradeNo, String rawMessage, Date payTime) {
        if (null == orderEntity) {
            return;
        }
        paymentFlowDao.insertIgnore(PaymentFlow.builder()
                .flowNo("PAY:" + orderEntity.getOrderId())
                .orderId(orderEntity.getOrderId())
                .userId(orderEntity.getUserId())
                .payChannel(payChannel)
                .channelTradeNo(channelTradeNo)
                .payAmount(orderEntity.getPayAmount())
                .payStatus("SUCCESS")
                .payTime(payTime)
                .rawMessage(rawMessage)
                .build());
    }

    @Override
    public List<PaymentFlowEntity> queryMissThirdPartyBillList(Integer limit) {
        List<PaymentFlow> paymentFlowList = paymentFlowDao.queryMissThirdPartyBillList(limit);
        if (null == paymentFlowList || paymentFlowList.isEmpty()) {
            return new ArrayList<>();
        }
        return paymentFlowList.stream().map(this::toEntity).collect(Collectors.toList());
    }

    private PaymentFlowEntity toEntity(PaymentFlow paymentFlow) {
        return PaymentFlowEntity.builder()
                .id(paymentFlow.getId())
                .flowNo(paymentFlow.getFlowNo())
                .orderId(paymentFlow.getOrderId())
                .userId(paymentFlow.getUserId())
                .payChannel(paymentFlow.getPayChannel())
                .channelTradeNo(paymentFlow.getChannelTradeNo())
                .payAmount(paymentFlow.getPayAmount())
                .payStatus(paymentFlow.getPayStatus())
                .payTime(paymentFlow.getPayTime())
                .rawMessage(paymentFlow.getRawMessage())
                .createTime(paymentFlow.getCreateTime())
                .updateTime(paymentFlow.getUpdateTime())
                .build();
    }

}
