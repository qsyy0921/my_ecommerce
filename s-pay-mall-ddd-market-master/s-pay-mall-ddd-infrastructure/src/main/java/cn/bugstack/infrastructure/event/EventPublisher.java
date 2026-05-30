package cn.bugstack.infrastructure.event;

import cn.bugstack.infrastructure.event.support.MqMessageIdGenerator;
import cn.bugstack.infrastructure.event.support.MqProducerFailureRecorder;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/**
 * @author Fuzhengwei bugstack.cn @小傅哥
 * @description 消息发送
 * @create 2024-03-30 12:40
 */
@Slf4j
@Component
public class EventPublisher {

    @Autowired
    private RabbitTemplate rabbitTemplate;
    @Resource
    private MqMessageIdGenerator mqMessageIdGenerator;
    @Resource
    private MqProducerFailureRecorder mqProducerFailureRecorder;

    @Value("${spring.rabbitmq.config.producer.topic_order_pay_success.exchange}")
    private String exchangeName;

    @PostConstruct
    public void init() {
        rabbitTemplate.setMandatory(true);
        rabbitTemplate.setConfirmCallback((correlationData, ack, cause) -> {
            if (!ack) {
                String id = null == correlationData ? null : correlationData.getId();
                log.error("MQ消息未被Broker确认 messageId:{} cause:{}", id, cause);
            }
        });
        rabbitTemplate.setReturnsCallback(returned -> log.error("MQ消息路由失败 exchange:{} routingKey:{} replyCode:{} replyText:{} message:{}",
                returned.getExchange(),
                returned.getRoutingKey(),
                returned.getReplyCode(),
                returned.getReplyText(),
                new String(returned.getMessage().getBody(), StandardCharsets.UTF_8)));
    }

    public void publish(String routingKey, String message) {
        publishToExchange(exchangeName, routingKey, message);
    }

    public void publishToExchange(String exchange, String routingKey, String message) {
        String messageId = null;
        try {
            messageId = mqMessageIdGenerator.build(exchange, routingKey, message);
            final String sendMessageId = messageId;
            CorrelationData correlationData = new CorrelationData(sendMessageId);
            rabbitTemplate.convertAndSend(exchange, routingKey, message, m -> {
                // 持久化消息配置
                m.getMessageProperties().setMessageId(sendMessageId);
                m.getMessageProperties().setCorrelationId(sendMessageId);
                m.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT);
                m.getMessageProperties().setHeader("trace-id", MDC.get("trace-id"));
                return m;
            }, correlationData);

            CorrelationData.Confirm confirm = correlationData.getFuture().get(5, TimeUnit.SECONDS);
            if (!confirm.isAck()) {
                throw new IllegalStateException("MQ消息未确认: " + confirm.getReason());
            }
        } catch (Exception e) {
            mqProducerFailureRecorder.recordPublishFailure(exchange, routingKey, message, messageId, e.getMessage());
            log.error("发送MQ消息失败 exchange:{} routingKey:{} message:{}", exchange, routingKey, message, e);
            throw new IllegalStateException(e);
        }
    }

}
