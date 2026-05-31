package cn.bugstack.domain.message.adapter.repository;

import cn.bugstack.domain.message.model.entity.MessageRecordEntity;

import java.util.List;

/**
 * MQ consume record repository port.
 */
public interface IMessageRecordRepository {

    MessageRecordEntity queryByMessageId(String messageId);

    void insert(MessageRecordEntity messageRecordEntity);

    int updateProcessing(String messageId);

    int updateSuccess(String messageId);

    int updateFail(String messageId, String errorMessage);

    List<MessageRecordEntity> queryFailedMessages(int limit);

    int retryProducerFailedMessages(int limit);

}
