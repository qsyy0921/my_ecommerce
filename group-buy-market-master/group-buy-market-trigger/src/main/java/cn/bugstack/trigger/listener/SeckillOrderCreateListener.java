package cn.bugstack.trigger.listener;

import cn.bugstack.domain.message.model.entity.MessageRecordEntity;
import cn.bugstack.domain.message.service.IMessageRecordService;
import cn.bugstack.domain.seckill.model.entity.SeckillOrderCreateMessageEntity;
import cn.bugstack.domain.seckill.model.entity.SeckillOrderEntity;
import cn.bugstack.domain.seckill.service.ISeckillService;
import com.alibaba.fastjson.JSON;
import com.rabbitmq.client.Channel;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.MDC;
import org.springframework.amqp.core.ExchangeTypes;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.Argument;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

/**
 * Seckill order async creation listener.
 */
@Slf4j
@Component
public class SeckillOrderCreateListener {

    @Resource
    private ISeckillService seckillService;
    @Resource
    private IMessageRecordService messageRecordService;

    @RabbitListener(
            bindings = @QueueBinding(
                    value = @Queue(value = "${spring.rabbitmq.config.producer.topic_seckill_order_create.queue}", durable = "true",
                            arguments = {
                                    @Argument(name = "x-dead-letter-exchange", value = "${spring.rabbitmq.config.dlx.exchange}"),
                                    @Argument(name = "x-dead-letter-routing-key", value = "${spring.rabbitmq.config.dlx.routing_key}")
                            }),
                    exchange = @Exchange(value = "${spring.rabbitmq.config.producer.exchange}", type = ExchangeTypes.TOPIC),
                    key = "${spring.rabbitmq.config.producer.topic_seckill_order_create.routing_key}"
            )
    )
    public void listener(String message, Message amqpMessage, Channel channel) throws Exception {
        long deliveryTag = amqpMessage.getMessageProperties().getDeliveryTag();
        String traceId = traceId(amqpMessage);
        MessageRecordEntity messageRecord = null;
        SeckillOrderCreateMessageEntity messageEnvelope = null;
        try {
            putTraceId(traceId);
            messageEnvelope = JSON.parseObject(message, SeckillOrderCreateMessageEntity.class);
            messageRecord = messageRecordService.beginConsume(
                    resolveMessageId(messageEnvelope, amqpMessage),
                    amqpMessage.getMessageProperties().getReceivedExchange(),
                    amqpMessage.getMessageProperties().getConsumerQueue(),
                    message);

            if (messageRecord.consumed()) {
                channel.basicAck(deliveryTag, false);
                return;
            }

            log.debug("receive seckill order create message messageId:{}", messageRecord.getMessageId());
            SeckillOrderEntity seckillOrderEntity = messageEnvelope.toOrderEntity();
            seckillService.createSeckillOrder(seckillOrderEntity);
            messageRecordService.consumeSuccess(messageRecord.getMessageId());
            channel.basicAck(deliveryTag, false);
        } catch (Exception e) {
            String messageId = resolveMessageId(messageRecord, messageEnvelope, amqpMessage);
            if (StringUtils.isNotBlank(messageId)) {
                messageRecordService.consumeFail(messageId, e.getMessage());
            }
            log.error("consume seckill order create message failed messageId:{} body:{}", messageId, message, e);
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

    private String resolveMessageId(SeckillOrderCreateMessageEntity messageEnvelope, Message amqpMessage) {
        if (null != messageEnvelope && StringUtils.isNotBlank(messageEnvelope.getMessageId())) {
            return messageEnvelope.getMessageId();
        }
        return amqpMessage.getMessageProperties().getMessageId();
    }

    private String resolveMessageId(MessageRecordEntity messageRecord, SeckillOrderCreateMessageEntity messageEnvelope, Message amqpMessage) {
        if (null != messageRecord) {
            return messageRecord.getMessageId();
        }
        return resolveMessageId(messageEnvelope, amqpMessage);
    }

}
