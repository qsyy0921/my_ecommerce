package cn.bugstack.trigger.support;

import cn.bugstack.api.dto.MqFailedMessageResponseDTO;
import cn.bugstack.domain.message.model.entity.MessageRecordEntity;
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

}
