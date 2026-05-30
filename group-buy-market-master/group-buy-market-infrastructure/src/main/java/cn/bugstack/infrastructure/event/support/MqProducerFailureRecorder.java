package cn.bugstack.infrastructure.event.support;

import cn.bugstack.infrastructure.dao.IMqMessageRecordDao;
import cn.bugstack.infrastructure.dao.po.MqMessageRecord;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

/**
 * Records producer publish failures for later retry.
 */
@Slf4j
@Component
public class MqProducerFailureRecorder {

    @Resource
    private IMqMessageRecordDao mqMessageRecordDao;
    @Resource
    private MqMessageIdGenerator mqMessageIdGenerator;

    public void recordPublishFailure(String exchange, String routingKey, String message, String messageId, String errorMessage) {
        try {
            String stableMessageId = null == messageId ? mqMessageIdGenerator.build(exchange, routingKey, message) : messageId;
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
