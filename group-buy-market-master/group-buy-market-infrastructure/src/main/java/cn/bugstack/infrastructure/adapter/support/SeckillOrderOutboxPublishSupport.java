package cn.bugstack.infrastructure.adapter.support;

import cn.bugstack.domain.seckill.adapter.port.ISeckillOrderOutboxPort;
import cn.bugstack.domain.seckill.model.entity.SeckillOrderCreateMessageEntity;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

/**
 * Publishes seckill order messages with an optional reliable outbox.
 */
@Slf4j
@Component
public class SeckillOrderOutboxPublishSupport {

    @Value("${app.seckill.order-outbox.enabled:true}")
    private Boolean outboxEnabled;

    @Resource
    private SeckillOrderMessagePublisherSupport messagePublisherSupport;
    @Resource
    private ISeckillOrderOutboxPort seckillOrderOutboxPort;

    public boolean publish(SeckillOrderCreateMessageEntity messageEnvelope, String topic, String messageBody) {
        if (!Boolean.TRUE.equals(outboxEnabled)) {
            return messagePublisherSupport.publish(messageBody, messageEnvelope.stableRouteKey());
        }
        return publishWithOutbox(messageEnvelope, topic, messageBody);
    }

    private boolean publishWithOutbox(SeckillOrderCreateMessageEntity messageEnvelope, String topic, String messageBody) {
        try {
            seckillOrderOutboxPort.recordInit(messageEnvelope, topic, messageBody);
        } catch (Exception e) {
            log.error("seckill order outbox record failed messageId:{}", messageEnvelope.getMessageId(), e);
            return false;
        }

        try {
            if (messagePublisherSupport.publish(messageBody, messageEnvelope.stableRouteKey())) {
                markSent(messageEnvelope.getMessageId());
            } else {
                markFailed(messageEnvelope.getMessageId(), "publish returned false");
            }
        } catch (Exception e) {
            markFailed(messageEnvelope.getMessageId(), StringUtils.left(e.getMessage(), 512));
        }
        return true;
    }

    private void markSent(String messageId) {
        try {
            seckillOrderOutboxPort.markSent(messageId);
        } catch (Exception e) {
            log.warn("seckill order outbox mark sent failed messageId:{}", messageId, e);
        }
    }

    private void markFailed(String messageId, String errorMessage) {
        try {
            seckillOrderOutboxPort.markFailed(messageId, errorMessage);
        } catch (Exception e) {
            log.warn("seckill order outbox mark failed failed messageId:{}", messageId, e);
        }
    }

}
