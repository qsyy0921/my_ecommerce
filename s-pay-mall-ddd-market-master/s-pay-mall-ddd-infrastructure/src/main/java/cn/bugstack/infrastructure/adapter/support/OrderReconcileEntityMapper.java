package cn.bugstack.infrastructure.adapter.support;

import cn.bugstack.domain.order.model.entity.OrderEntity;
import cn.bugstack.domain.order.model.entity.ReconcileCaseEntity;
import cn.bugstack.domain.order.model.entity.ReconcileOperationLogEntity;
import cn.bugstack.infrastructure.dao.po.PayOrder;
import cn.bugstack.infrastructure.dao.po.ReconcileCase;
import cn.bugstack.infrastructure.dao.po.ReconcileOperationLog;

public class OrderReconcileEntityMapper {

    private final PayOrderEntityMapper payOrderEntityMapper = new PayOrderEntityMapper();

    public OrderEntity toOrderEntity(PayOrder payOrder) {
        return payOrderEntityMapper.toOrderEntity(payOrder);
    }

    public ReconcileCaseEntity toReconcileCaseEntity(ReconcileCase reconcileCase) {
        return ReconcileCaseEntity.builder()
                .id(reconcileCase.getId())
                .caseNo(reconcileCase.getCaseNo())
                .bizType(reconcileCase.getBizType())
                .bizId(reconcileCase.getBizId())
                .userId(reconcileCase.getUserId())
                .caseType(reconcileCase.getCaseType())
                .caseStatus(reconcileCase.getCaseStatus())
                .severity(reconcileCase.getSeverity())
                .sourceStatus(reconcileCase.getSourceStatus())
                .targetStatus(reconcileCase.getTargetStatus())
                .summary(reconcileCase.getSummary())
                .detail(reconcileCase.getDetail())
                .retryCount(reconcileCase.getRetryCount())
                .createTime(reconcileCase.getCreateTime())
                .updateTime(reconcileCase.getUpdateTime())
                .handledTime(reconcileCase.getHandledTime())
                .handler(reconcileCase.getHandler())
                .handleNote(reconcileCase.getHandleNote())
                .build();
    }

    public ReconcileOperationLogEntity toReconcileOperationLogEntity(ReconcileOperationLog operationLog) {
        return ReconcileOperationLogEntity.builder()
                .id(operationLog.getId())
                .operator(operationLog.getOperator())
                .operationType(operationLog.getOperationType())
                .bizId(operationLog.getBizId())
                .requestBody(operationLog.getRequestBody())
                .result(operationLog.getResult())
                .createTime(operationLog.getCreateTime())
                .build();
    }

}
