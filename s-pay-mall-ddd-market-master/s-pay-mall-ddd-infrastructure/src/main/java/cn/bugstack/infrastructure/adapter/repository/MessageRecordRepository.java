package cn.bugstack.infrastructure.adapter.repository;

import cn.bugstack.domain.message.adapter.repository.IMessageRecordRepository;
import cn.bugstack.domain.message.model.entity.MessageRecordEntity;
import cn.bugstack.infrastructure.dao.IMqMessageRecordDao;
import cn.bugstack.infrastructure.dao.po.MqMessageRecord;
import cn.bugstack.infrastructure.event.EventPublisher;
import org.apache.commons.lang3.StringUtils;
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
    private EventPublisher eventPublisher;

    @Override
    public MessageRecordEntity queryByMessageId(String messageId) {
        MqMessageRecord mqMessageRecord = mqMessageRecordDao.queryByMessageId(messageId);
        if (null == mqMessageRecord) return null;
        return MessageRecordEntity.builder()
                .messageId(mqMessageRecord.getMessageId())
                .exchangeName(mqMessageRecord.getExchangeName())
                .queueName(mqMessageRecord.getQueueName())
                .messageBody(mqMessageRecord.getMessageBody())
                .status(mqMessageRecord.getStatus())
                .retryCount(mqMessageRecord.getRetryCount())
                .errorMessage(mqMessageRecord.getErrorMessage())
                .build();
    }

    @Override
    public void insert(MessageRecordEntity messageRecordEntity) {
        mqMessageRecordDao.insert(MqMessageRecord.builder()
                .messageId(messageRecordEntity.getMessageId())
                .exchangeName(messageRecordEntity.getExchangeName())
                .queueName(messageRecordEntity.getQueueName())
                .messageBody(messageRecordEntity.getMessageBody())
                .status(messageRecordEntity.getStatus())
                .retryCount(messageRecordEntity.getRetryCount())
                .errorMessage(messageRecordEntity.getErrorMessage())
                .build());
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
        return mqMessageRecordDao.updateFail(MqMessageRecord.builder()
                .messageId(messageId)
                .errorMessage(errorMessage)
                .build());
    }

    @Override
    public int retryProducerFailedMessages(int limit) {
        List<MqMessageRecord> records = mqMessageRecordDao.queryProducerFailedMessageList(limit);
        if (null == records || records.isEmpty()) {
            return 0;
        }
        int count = 0;
        for (MqMessageRecord record : records) {
            String routingKey = resolveRoutingKey(record.getQueueName());
            if (StringUtils.isBlank(record.getExchangeName())
                    || StringUtils.isBlank(routingKey)
                    || StringUtils.isBlank(record.getMessageBody())) {
                continue;
            }
            try {
                mqMessageRecordDao.updateProcessing(record.getMessageId());
                eventPublisher.publishToExchange(record.getExchangeName(), routingKey, record.getMessageBody());
                mqMessageRecordDao.updateSuccess(record.getMessageId());
                count++;
            } catch (Exception e) {
                mqMessageRecordDao.updateFail(MqMessageRecord.builder()
                        .messageId(record.getMessageId())
                        .errorMessage(left("producer retry failed: " + e.getMessage(), 512))
                        .build());
            }
        }
        return count;
    }

    private String resolveRoutingKey(String queueName) {
        if (StringUtils.isBlank(queueName)) {
            return null;
        }
        if (queueName.startsWith("routing:")) {
            return queueName.substring("routing:".length());
        }
        return queueName;
    }

    private String left(String value, int length) {
        if (null == value || value.length() <= length) {
            return value;
        }
        return value.substring(0, length);
    }

}
