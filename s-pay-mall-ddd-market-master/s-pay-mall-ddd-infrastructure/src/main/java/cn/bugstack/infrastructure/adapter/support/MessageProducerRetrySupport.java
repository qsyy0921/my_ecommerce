package cn.bugstack.infrastructure.adapter.support;

import cn.bugstack.infrastructure.dao.IMqMessageRecordDao;
import cn.bugstack.infrastructure.dao.po.MqMessageRecord;
import cn.bugstack.infrastructure.event.EventPublisher;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.List;

/**
 * Replays producer messages that failed before RabbitMQ accepted them.
 */
@Component
public class MessageProducerRetrySupport {

    @Resource
    private IMqMessageRecordDao mqMessageRecordDao;
    @Resource
    private EventPublisher eventPublisher;

    public int retryProducerFailedMessages(int limit) {
        List<MqMessageRecord> records = mqMessageRecordDao.queryProducerFailedMessageList(limit);
        if (null == records || records.isEmpty()) {
            return 0;
        }
        int count = 0;
        for (MqMessageRecord record : records) {
            if (retryOne(record)) {
                count++;
            }
        }
        return count;
    }

    private boolean retryOne(MqMessageRecord record) {
        String routingKey = resolveRoutingKey(record.getQueueName());
        if (StringUtils.isBlank(record.getExchangeName())
                || StringUtils.isBlank(routingKey)
                || StringUtils.isBlank(record.getMessageBody())) {
            return false;
        }
        try {
            mqMessageRecordDao.updateProcessing(record.getMessageId());
            eventPublisher.publishToExchange(record.getExchangeName(), routingKey, record.getMessageBody());
            mqMessageRecordDao.updateSuccess(record.getMessageId());
            return true;
        } catch (Exception e) {
            mqMessageRecordDao.updateFail(MqMessageRecord.builder()
                    .messageId(record.getMessageId())
                    .errorMessage(left("producer retry failed: " + e.getMessage(), 512))
                    .build());
            return false;
        }
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
