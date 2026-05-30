package cn.bugstack.infrastructure.adapter.repository;

import cn.bugstack.domain.message.adapter.repository.IMessageRecordRepository;
import cn.bugstack.domain.message.model.entity.MessageRecordEntity;
import cn.bugstack.infrastructure.adapter.support.MessageProducerRetrySupport;
import cn.bugstack.infrastructure.adapter.support.MessageRecordMapper;
import cn.bugstack.infrastructure.dao.IMqMessageRecordDao;
import cn.bugstack.infrastructure.dao.po.MqMessageRecord;
import org.springframework.stereotype.Repository;

import javax.annotation.Resource;
import java.util.List;

/**
 * MQ consume record repository adapter.
 */
@Repository
public class MessageRecordRepository implements IMessageRecordRepository {

    @Resource
    private IMqMessageRecordDao mqMessageRecordDao;
    @Resource
    private MessageRecordMapper messageRecordMapper;
    @Resource
    private MessageProducerRetrySupport messageProducerRetrySupport;

    @Override
    public MessageRecordEntity queryByMessageId(String messageId) {
        MqMessageRecord mqMessageRecord = mqMessageRecordDao.queryByMessageId(messageId);
        return messageRecordMapper.toEntity(mqMessageRecord);
    }

    @Override
    public void insert(MessageRecordEntity messageRecordEntity) {
        mqMessageRecordDao.insert(messageRecordMapper.toRecord(messageRecordEntity));
    }

    @Override
    public int updateProcessing(String messageId) {
        return mqMessageRecordDao.updateProcessing(messageId);
    }

    @Override
    public int updateSuccess(String messageId) {
        return mqMessageRecordDao.updateSuccess(messageId);
    }

    @Override
    public int updateFail(String messageId, String errorMessage) {
        return mqMessageRecordDao.updateFail(messageRecordMapper.failRecord(messageId, errorMessage));
    }

    @Override
    public List<MessageRecordEntity> queryFailedMessages(int limit) {
        List<MqMessageRecord> records = mqMessageRecordDao.queryFailedMessageList(limit);
        return messageRecordMapper.toEntityList(records);
    }

    @Override
    public int retryProducerFailedMessages(int limit) {
        return messageProducerRetrySupport.retryProducerFailedMessages(limit);
    }

}
