package cn.bugstack.infrastructure.adapter.support;

import cn.bugstack.domain.message.model.entity.MessageRecordEntity;
import cn.bugstack.infrastructure.dao.po.MqMessageRecord;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Maps MQ message record persistence objects and domain entities.
 */
@Component
public class MessageRecordMapper {

    public MessageRecordEntity toEntity(MqMessageRecord record) {
        if (null == record) {
            return null;
        }
        return MessageRecordEntity.builder()
                .messageId(record.getMessageId())
                .exchangeName(record.getExchangeName())
                .queueName(record.getQueueName())
                .messageBody(record.getMessageBody())
                .status(record.getStatus())
                .retryCount(record.getRetryCount())
                .errorMessage(record.getErrorMessage())
                .build();
    }

    public List<MessageRecordEntity> toEntityList(List<MqMessageRecord> records) {
        List<MessageRecordEntity> result = new ArrayList<>();
        if (null == records || records.isEmpty()) {
            return result;
        }
        for (MqMessageRecord record : records) {
            result.add(toEntity(record));
        }
        return result;
    }

    public MqMessageRecord toRecord(MessageRecordEntity entity) {
        if (null == entity) {
            return null;
        }
        return MqMessageRecord.builder()
                .messageId(entity.getMessageId())
                .exchangeName(entity.getExchangeName())
                .queueName(entity.getQueueName())
                .messageBody(entity.getMessageBody())
                .status(entity.getStatus())
                .retryCount(entity.getRetryCount())
                .errorMessage(entity.getErrorMessage())
                .build();
    }

    public MqMessageRecord failRecord(String messageId, String errorMessage) {
        return MqMessageRecord.builder()
                .messageId(messageId)
                .errorMessage(errorMessage)
                .build();
    }

}
