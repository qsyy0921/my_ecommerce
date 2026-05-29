package cn.bugstack.domain.message.service;

import cn.bugstack.domain.message.model.entity.MessageRecordEntity;

import java.util.List;

/**
 * MQ consume idempotency service.
 */
public interface IMessageRecordService {

    MessageRecordEntity beginConsume(String messageId, String exchangeName, String queueName, String messageBody);

    void consumeSuccess(String messageId);

    void consumeFail(String messageId, String errorMessage);

    List<MessageRecordEntity> queryFailedMessages(int limit);

    int retryProducerFailedMessages(int limit);

}
