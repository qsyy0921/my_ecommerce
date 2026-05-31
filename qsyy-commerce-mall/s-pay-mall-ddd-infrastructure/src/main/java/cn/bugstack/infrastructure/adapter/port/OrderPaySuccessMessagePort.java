package cn.bugstack.infrastructure.adapter.port;

import cn.bugstack.domain.order.adapter.event.PaySuccessMessageEvent;
import cn.bugstack.domain.order.adapter.port.IOrderPaySuccessMessagePort;
import cn.bugstack.infrastructure.event.EventPublisher;
import cn.bugstack.types.event.BaseEvent;
import com.alibaba.fastjson.JSON;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;

@Service
public class OrderPaySuccessMessagePort implements IOrderPaySuccessMessagePort {

    @Resource
    private PaySuccessMessageEvent paySuccessMessageEvent;
    @Resource
    private EventPublisher eventPublisher;

    @Override
    public void publish(String orderId) {
        BaseEvent.EventMessage<PaySuccessMessageEvent.PaySuccessMessage> paySuccessMessageEventMessage = paySuccessMessageEvent.buildEventMessage(
                PaySuccessMessageEvent.PaySuccessMessage.builder()
                        .tradeNo(orderId)
                        .build());
        eventPublisher.publish(paySuccessMessageEvent.topic(), JSON.toJSONString(paySuccessMessageEventMessage.getData()));
    }

    @Override
    public void publishAll(List<String> orderIdList) {
        if (null == orderIdList || orderIdList.isEmpty()) {
            return;
        }
        for (String orderId : orderIdList) {
            publish(orderId);
        }
    }

}
