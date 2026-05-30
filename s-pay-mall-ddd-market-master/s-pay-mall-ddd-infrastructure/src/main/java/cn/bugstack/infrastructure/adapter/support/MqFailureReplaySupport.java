package cn.bugstack.infrastructure.adapter.support;

import cn.bugstack.infrastructure.dao.IMqMessageRecordDao;
import cn.bugstack.infrastructure.dao.po.MqMessageRecord;
import cn.bugstack.infrastructure.event.EventPublisher;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

@Component
public class MqFailureReplaySupport {

    @Resource
    private IMqMessageRecordDao mqMessageRecordDao;

    @Resource
    private EventPublisher eventPublisher;

    public boolean replay(String messageId) {
        MqMessageRecord messageRecord = mqMessageRecordDao.queryByMessageId(messageId);
        if (null == messageRecord) {
            return false;
        }
        String routingKey = resolveRoutingKey(messageRecord.getQueueName());
        if (isBlank(messageRecord.getExchangeName()) || isBlank(routingKey) || isBlank(messageRecord.getMessageBody())) {
            return false;
        }
        try {
            mqMessageRecordDao.updateProcessing(messageId);
            eventPublisher.publishToExchange(messageRecord.getExchangeName(), routingKey, messageRecord.getMessageBody());
            mqMessageRecordDao.updateSuccess(messageId);
            return true;
        } catch (Exception e) {
            mqMessageRecordDao.updateFail(MqMessageRecord.builder()
                    .messageId(messageId)
                    .errorMessage("replay failed: " + e.getMessage())
                    .build());
            throw new IllegalStateException(e);
        }
    }

    private String resolveRoutingKey(String queueName) {
        if (isBlank(queueName)) {
            return null;
        }
        if (queueName.startsWith("routing:")) {
            return queueName.substring("routing:".length());
        }
        if (queueName.contains("topic_team_success")) {
            return "topic.team_success";
        }
        if (queueName.contains("topic_team_refund")) {
            return "topic.team_refund";
        }
        if (queueName.contains("order_pay_success")) {
            return "topic.order_pay_success";
        }
        return null;
    }

    private boolean isBlank(String value) {
        return null == value || value.trim().isEmpty();
    }

}
