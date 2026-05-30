package cn.bugstack.domain.order.adapter.repository;

import cn.bugstack.domain.order.model.entity.OrderEntity;
import cn.bugstack.domain.order.model.entity.ReconcileCaseEntity;
import cn.bugstack.domain.order.model.entity.ReconcileOperationLogEntity;

import java.util.List;

public interface IOrderReconcileRepository {

    List<OrderEntity> queryStaleMarketSettlementOrderList();

    int scanReconcileCases();

    List<ReconcileCaseEntity> queryReconcileCaseList(Integer caseStatus, String caseType, Long lastId, Integer pageSize);

    ReconcileCaseEntity queryReconcileCase(String caseNo);

    boolean handleReconcileCase(String caseNo, Integer caseStatus, String handler, String handleNote);

    boolean remarkReconcileCase(String caseNo, String handler, String handleNote);

    boolean replayMqFailure(String messageId);

    void recordReconcileOperation(String operator, String operationType, String bizId, String requestBody, String result);

    List<ReconcileOperationLogEntity> queryReconcileOperationLogList(String bizId, Long lastId, Integer pageSize);

    int importThirdPartyBillCsv(String csvText);

}
