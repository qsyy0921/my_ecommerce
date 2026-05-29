package cn.bugstack.domain.message.adapter.repository;

import cn.bugstack.domain.message.model.entity.MessageRecordEntity;

/**
 * MQ consume record repository port.
 */
public interface IMessageRecordRepository {

    MessageRecordEntity queryByMessageId(String messageId);

    void insert(MessageRecordEntity messageRecordEntity);

    int updateProcessing(String messageId);

    int updateSuccess(String messageId);

    int updateFail(String messageId, String errorMessage);

    int retryProducerFailedMessages(int limit);

}
