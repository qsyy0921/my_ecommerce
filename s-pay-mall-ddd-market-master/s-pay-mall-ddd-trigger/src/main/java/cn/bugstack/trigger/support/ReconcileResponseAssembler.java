package cn.bugstack.trigger.support;

import cn.bugstack.api.dto.ReconcileCaseResponseDTO;
import cn.bugstack.api.dto.ReconcileOperationLogResponseDTO;
import cn.bugstack.domain.order.model.entity.ReconcileCaseEntity;
import cn.bugstack.domain.order.model.entity.ReconcileOperationLogEntity;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
public class ReconcileResponseAssembler {

    public List<ReconcileCaseResponseDTO> toCaseResponseList(List<ReconcileCaseEntity> caseList) {
        return caseList.stream().map(this::toCaseResponse).collect(Collectors.toList());
    }

    public List<ReconcileOperationLogResponseDTO> toOperationLogResponseList(List<ReconcileOperationLogEntity> logList) {
        return logList.stream().map(this::toOperationLogResponse).collect(Collectors.toList());
    }

    private ReconcileCaseResponseDTO toCaseResponse(ReconcileCaseEntity entity) {
        ReconcileCaseResponseDTO responseDTO = new ReconcileCaseResponseDTO();
        responseDTO.setId(entity.getId());
        responseDTO.setCaseNo(entity.getCaseNo());
        responseDTO.setBizType(entity.getBizType());
        responseDTO.setBizId(entity.getBizId());
        responseDTO.setUserId(entity.getUserId());
        responseDTO.setCaseType(entity.getCaseType());
        responseDTO.setCaseStatus(entity.getCaseStatus());
        responseDTO.setSeverity(entity.getSeverity());
        responseDTO.setSourceStatus(entity.getSourceStatus());
        responseDTO.setTargetStatus(entity.getTargetStatus());
        responseDTO.setSummary(entity.getSummary());
        responseDTO.setDetail(entity.getDetail());
        responseDTO.setRetryCount(entity.getRetryCount());
        responseDTO.setCreateTime(entity.getCreateTime());
        responseDTO.setUpdateTime(entity.getUpdateTime());
        responseDTO.setHandledTime(entity.getHandledTime());
        responseDTO.setHandler(entity.getHandler());
        responseDTO.setHandleNote(entity.getHandleNote());
        return responseDTO;
    }

    private ReconcileOperationLogResponseDTO toOperationLogResponse(ReconcileOperationLogEntity entity) {
        ReconcileOperationLogResponseDTO responseDTO = new ReconcileOperationLogResponseDTO();
        responseDTO.setId(entity.getId());
        responseDTO.setOperator(entity.getOperator());
        responseDTO.setOperationType(entity.getOperationType());
        responseDTO.setBizId(entity.getBizId());
        responseDTO.setRequestBody(entity.getRequestBody());
        responseDTO.setResult(entity.getResult());
        responseDTO.setCreateTime(entity.getCreateTime());
        return responseDTO;
    }

}
