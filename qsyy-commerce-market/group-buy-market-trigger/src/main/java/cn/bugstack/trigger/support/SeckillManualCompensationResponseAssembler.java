package cn.bugstack.trigger.support;

import cn.bugstack.api.dto.SeckillManualCompensationLogResponseDTO;
import cn.bugstack.api.dto.SeckillManualMessageResponseDTO;
import cn.bugstack.domain.seckill.model.entity.SeckillManualCompensationLogEntity;
import cn.bugstack.domain.seckill.model.entity.SeckillManualMessageEntity;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Maps seckill manual compensation domain objects to HTTP response DTOs.
 */
@Component
public class SeckillManualCompensationResponseAssembler {

    public List<SeckillManualMessageResponseDTO> toMessageResponses(List<SeckillManualMessageEntity> messages) {
        List<SeckillManualMessageResponseDTO> responseList = new ArrayList<>();
        if (null == messages || messages.isEmpty()) {
            return responseList;
        }
        for (SeckillManualMessageEntity message : messages) {
            responseList.add(SeckillManualMessageResponseDTO.builder()
                    .id(message.getId())
                    .body(message.getBody())
                    .originalStreamKey(message.getOriginalStreamKey())
                    .originalMessageId(message.getOriginalMessageId())
                    .retryCount(message.getRetryCount())
                    .error(message.getError())
                    .build());
        }
        return responseList;
    }

    public List<SeckillManualCompensationLogResponseDTO> toLogResponses(List<SeckillManualCompensationLogEntity> logs) {
        List<SeckillManualCompensationLogResponseDTO> responseList = new ArrayList<>();
        if (null == logs || logs.isEmpty()) {
            return responseList;
        }
        for (SeckillManualCompensationLogEntity log : logs) {
            responseList.add(SeckillManualCompensationLogResponseDTO.builder()
                    .id(log.getId())
                    .operator(log.getOperator())
                    .operationType(log.getOperationType())
                    .messageIds(log.getMessageIds())
                    .requestLimit(log.getRequestLimit())
                    .manualStreamKey(log.getManualStreamKey())
                    .resultCount(log.getResultCount())
                    .success(log.getSuccess())
                    .errorMessage(log.getErrorMessage())
                    .createTime(log.getCreateTime())
                    .build());
        }
        return responseList;
    }

}
