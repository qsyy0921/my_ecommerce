package cn.bugstack.trigger.support;

import cn.bugstack.api.dto.MqFailedMessageResponseDTO;
import cn.bugstack.api.dto.SeckillOrderOutboxResponseDTO;
import cn.bugstack.api.dto.SeckillOrderOutboxStatusCountResponseDTO;
import cn.bugstack.domain.message.model.entity.MessageRecordEntity;
import cn.bugstack.domain.seckill.model.entity.SeckillOrderOutboxEntity;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Maps MQ domain records to operations API responses.
 */
@Component
public class MqOpsResponseAssembler {

    public List<MqFailedMessageResponseDTO> toFailedMessageResponses(List<MessageRecordEntity> messages) {
        List<MqFailedMessageResponseDTO> responseList = new ArrayList<>();
        if (null == messages || messages.isEmpty()) {
            return responseList;
        }
        for (MessageRecordEntity message : messages) {
            responseList.add(MqFailedMessageResponseDTO.builder()
                    .messageId(message.getMessageId())
                    .exchangeName(message.getExchangeName())
                    .queueName(message.getQueueName())
                    .messageBody(message.getMessageBody())
                    .status(message.getStatus())
                    .retryCount(message.getRetryCount())
                    .errorMessage(message.getErrorMessage())
                    .build());
        }
        return responseList;
    }

    public List<SeckillOrderOutboxResponseDTO> toSeckillOrderOutboxResponses(List<SeckillOrderOutboxEntity> messages) {
        List<SeckillOrderOutboxResponseDTO> responseList = new ArrayList<>();
        if (null == messages || messages.isEmpty()) {
            return responseList;
        }
        for (SeckillOrderOutboxEntity message : messages) {
            responseList.add(SeckillOrderOutboxResponseDTO.builder()
                    .id(message.getId())
                    .messageId(message.getMessageId())
                    .routeKey(message.getRouteKey())
                    .topic(message.getTopic())
                    .messageBody(message.getMessageBody())
                    .status(message.getStatus())
                    .statusName(SeckillOrderOutboxEntity.statusName(message.getStatus()))
                    .retryCount(message.getRetryCount())
                    .nextRetryTime(message.getNextRetryTime())
                    .errorMessage(message.getErrorMessage())
                    .traceId(message.getTraceId())
                    .createTime(message.getCreateTime())
                    .updateTime(message.getUpdateTime())
                    .build());
        }
        return responseList;
    }

    public List<SeckillOrderOutboxStatusCountResponseDTO> toSeckillOrderOutboxStatusCounts(int initCount,
                                                                                            int sentCount,
                                                                                            int failedCount,
                                                                                            int deadCount) {
        List<SeckillOrderOutboxStatusCountResponseDTO> responseList = new ArrayList<>();
        responseList.add(statusCount(SeckillOrderOutboxEntity.STATUS_INIT, initCount));
        responseList.add(statusCount(SeckillOrderOutboxEntity.STATUS_SENT, sentCount));
        responseList.add(statusCount(SeckillOrderOutboxEntity.STATUS_FAILED, failedCount));
        responseList.add(statusCount(SeckillOrderOutboxEntity.STATUS_DEAD, deadCount));
        return responseList;
    }

    private SeckillOrderOutboxStatusCountResponseDTO statusCount(Integer status, Integer count) {
        return SeckillOrderOutboxStatusCountResponseDTO.builder()
                .status(status)
                .statusName(SeckillOrderOutboxEntity.statusName(status))
                .count(count)
                .build();
    }

}
