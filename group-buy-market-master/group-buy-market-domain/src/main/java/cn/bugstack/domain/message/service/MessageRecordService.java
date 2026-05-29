package cn.bugstack.domain.message.service;

import cn.bugstack.domain.message.adapter.repository.IMessageRecordRepository;
import cn.bugstack.domain.message.model.entity.MessageRecordEntity;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.List;

/**
 * MQ consume idempotency service implementation.
 */
@Slf4j
public class MessageRecordService implements IMessageRecordService {

    private final IMessageRecordRepository messageRecordRepository;

    public MessageRecordService(IMessageRecordRepository messageRecordRepository) {
        this.messageRecordRepository = messageRecordRepository;
    }

    @Override
    public MessageRecordEntity beginConsume(String messageId, String exchangeName, String queueName, String messageBody) {
        String stableMessageId = stableMessageId(messageId, exchangeName, queueName, messageBody);
        MessageRecordEntity exists = messageRecordRepository.queryByMessageId(stableMessageId);
        if (null != exists) {
            if (exists.consumed()) {
                return exists;
            }
            messageRecordRepository.updateProcessing(stableMessageId);
            exists.setStatus(MessageRecordEntity.STATUS_PROCESSING);
            return exists;
        }

        MessageRecordEntity record = MessageRecordEntity.builder()
                .messageId(stableMessageId)
                .exchangeName(exchangeName)
                .queueName(queueName)
                .messageBody(messageBody)
                .status(MessageRecordEntity.STATUS_PROCESSING)
                .retryCount(0)
                .build();
        try {
            messageRecordRepository.insert(record);
        } catch (RuntimeException e) {
            MessageRecordEntity duplicate = messageRecordRepository.queryByMessageId(stableMessageId);
            if (null != duplicate) {
                return duplicate;
            }
            throw e;
        }
        return record;
    }

    @Override
    public void consumeSuccess(String messageId) {
        messageRecordRepository.updateSuccess(messageId);
    }

    @Override
    public void consumeFail(String messageId, String errorMessage) {
        messageRecordRepository.updateFail(messageId, StringUtils.left(errorMessage, 512));
    }

    @Override
    public List<MessageRecordEntity> queryFailedMessages(int limit) {
        return messageRecordRepository.queryFailedMessages(Math.max(1, Math.min(limit, 200)));
    }

    @Override
    public int retryProducerFailedMessages(int limit) {
        return messageRecordRepository.retryProducerFailedMessages(Math.max(1, Math.min(limit, 100)));
    }

    private String stableMessageId(String messageId, String exchangeName, String queueName, String messageBody) {
        String consumer = StringUtils.defaultIfBlank(queueName, "unknown_consumer");
        if (StringUtils.isNotBlank(messageId)) {
            return DigestUtils.sha256Hex(consumer + ":" + messageId);
        }
        return DigestUtils.sha256Hex(consumer + ":" + exchangeName + ":" + messageBody);
    }

}
