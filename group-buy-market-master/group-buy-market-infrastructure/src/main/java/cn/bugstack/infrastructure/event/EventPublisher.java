package cn.bugstack.infrastructure.event;

import cn.bugstack.infrastructure.dao.IMqMessageRecordDao;
import cn.bugstack.infrastructure.dao.po.MqMessageRecord;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.slf4j.MDC;
import org.springframework.dao.DuplicateKeyException;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
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
    private IMqMessageRecordDao mqMessageRecordDao;

    @Value("${spring.rabbitmq.config.producer.exchange}")
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
            messageId = buildMessageId(exchange, routingKey, message);
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
            recordPublishFailure(exchange, routingKey, message, messageId, e.getMessage());
            log.error("发送MQ消息失败 exchange:{} routingKey:{} message:{}", exchange, routingKey, message, e);
            throw new IllegalStateException(e);
        }
    }

    public void publishWithoutConfirm(String routingKey, String message) {
        try {
            String messageId = buildMessageId(exchangeName, routingKey, message);
            CorrelationData correlationData = new CorrelationData(messageId);
            rabbitTemplate.convertAndSend(exchangeName, routingKey, message, m -> {
                m.getMessageProperties().setMessageId(messageId);
                m.getMessageProperties().setCorrelationId(messageId);
                m.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT);
                m.getMessageProperties().setHeader("trace-id", MDC.get("trace-id"));
                return m;
            }, correlationData);
        } catch (Exception e) {
            log.error("发送MQ消息失败 routingKey:{} message:{}", routingKey, message, e);
            throw new IllegalStateException(e);
        }
    }

    private String buildMessageId(String exchange, String routingKey, String message) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest((exchange + ":" + routingKey + ":" + message).getBytes(StandardCharsets.UTF_8));
        StringBuilder builder = new StringBuilder(hash.length * 2);
        for (byte b : hash) {
            builder.append(String.format("%02x", b));
        }
        return builder.toString();
    }

    private void recordPublishFailure(String exchange, String routingKey, String message, String messageId, String errorMessage) {
        try {
            String stableMessageId = null == messageId ? buildMessageId(exchange, routingKey, message) : messageId;
            MqMessageRecord exists = mqMessageRecordDao.queryByMessageId(stableMessageId);
            if (null == exists) {
                mqMessageRecordDao.insert(MqMessageRecord.builder()
                        .messageId(stableMessageId)
                        .exchangeName(exchange)
                        .queueName("routing:" + routingKey)
                        .messageBody(message)
                        .status(2)
                        .retryCount(0)
                        .errorMessage(left("producer publish failed: " + errorMessage, 512))
                        .build());
                return;
            }
            mqMessageRecordDao.updateFail(MqMessageRecord.builder()
                    .messageId(stableMessageId)
                    .errorMessage(left("producer publish failed: " + errorMessage, 512))
                    .build());
        } catch (DuplicateKeyException ignore) {
            log.warn("MQ发送失败记录已存在 exchange:{} routingKey:{}", exchange, routingKey);
        } catch (Exception recordException) {
            log.error("记录MQ发送失败台账失败 exchange:{} routingKey:{} message:{}", exchange, routingKey, message, recordException);
        }
    }

    private String left(String value, int length) {
        if (null == value || value.length() <= length) {
            return value;
        }
        return value.substring(0, length);
    }

}
