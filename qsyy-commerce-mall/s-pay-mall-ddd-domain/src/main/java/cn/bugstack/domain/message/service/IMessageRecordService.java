package cn.bugstack.domain.message.service;

import cn.bugstack.domain.message.model.entity.MessageRecordEntity;

/**
 * MQ consume idempotency service.
 */
public interface IMessageRecordService {

    MessageRecordEntity beginConsume(String messageId, String exchangeName, String queueName, String messageBody);

    void consumeSuccess(String messageId);

    void consumeFail(String messageId, String errorMessage);

    int retryProducerFailedMessages(int limit);

}
