package cn.bugstack.trigger.listener;

import cn.bugstack.domain.goods.service.IGoodsService;
import cn.bugstack.domain.message.model.entity.MessageRecordEntity;
import cn.bugstack.domain.message.service.IMessageRecordService;
import cn.bugstack.domain.order.adapter.event.PaySuccessMessageEvent;
import com.alibaba.fastjson.JSON;
import com.rabbitmq.client.Channel;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.MDC;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.ExchangeTypes;
import org.springframework.amqp.rabbit.annotation.Argument;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

/**
 * Order pay success message listener.
 */
@Slf4j
@Component
public class OrderPaySuccessListener {

    @Resource
    private IGoodsService goodsService;
    @Resource
    private IMessageRecordService messageRecordService;

    @RabbitListener(
            bindings = @QueueBinding(
                    value = @Queue(value = "${spring.rabbitmq.config.consumer.topic_order_pay_success.queue}", durable = "true",
                            arguments = {
                                    @Argument(name = "x-dead-letter-exchange", value = "${spring.rabbitmq.config.dlx.exchange}"),
                                    @Argument(name = "x-dead-letter-routing-key", value = "${spring.rabbitmq.config.dlx.routing_key}")
                            }),
                    exchange = @Exchange(value = "${spring.rabbitmq.config.consumer.topic_order_pay_success.exchange}", type = ExchangeTypes.TOPIC),
                    key = "${spring.rabbitmq.config.consumer.topic_order_pay_success.routing_key}"
            )
    )
    public void listener(String paySuccessMessageJson, Message amqpMessage, Channel channel) throws Exception {
        long deliveryTag = amqpMessage.getMessageProperties().getDeliveryTag();
        String traceId = traceId(amqpMessage);
        MessageRecordEntity messageRecord = null;
        try {
            putTraceId(traceId);
            messageRecord = messageRecordService.beginConsume(
                    amqpMessage.getMessageProperties().getMessageId(),
                    amqpMessage.getMessageProperties().getReceivedExchange(),
                    amqpMessage.getMessageProperties().getConsumerQueue(),
                    paySuccessMessageJson);

            if (messageRecord.consumed()) {
                channel.basicAck(deliveryTag, false);
                return;
            }

            log.info("receive pay success message messageId:{} body:{}", messageRecord.getMessageId(), paySuccessMessageJson);
            PaySuccessMessageEvent.PaySuccessMessage paySuccessMessage = JSON.parseObject(paySuccessMessageJson, PaySuccessMessageEvent.PaySuccessMessage.class);
            goodsService.changeOrderDealDone(paySuccessMessage.getTradeNo());
            messageRecordService.consumeSuccess(messageRecord.getMessageId());
            channel.basicAck(deliveryTag, false);
        } catch (Exception e) {
            String messageId = resolveMessageId(messageRecord, amqpMessage);
            if (StringUtils.isNotBlank(messageId)) {
                messageRecordService.consumeFail(messageId, e.getMessage());
            }
            log.error("consume pay success message failed messageId:{} body:{}", messageId, paySuccessMessageJson, e);
            channel.basicNack(deliveryTag, false, false);
        } finally {
            clearTraceId(traceId);
        }
    }

    private String traceId(Message amqpMessage) {
        Object traceId = amqpMessage.getMessageProperties().getHeaders().get("trace-id");
        return null == traceId ? null : String.valueOf(traceId);
    }

    private void putTraceId(String traceId) {
        if (StringUtils.isNotBlank(traceId)) {
            MDC.put("trace-id", traceId);
        }
    }

    private void clearTraceId(String traceId) {
        if (StringUtils.isNotBlank(traceId)) {
            MDC.remove("trace-id");
        }
    }

    private String resolveMessageId(MessageRecordEntity messageRecord, Message amqpMessage) {
        return null != messageRecord ? messageRecord.getMessageId() : amqpMessage.getMessageProperties().getMessageId();
    }

}
