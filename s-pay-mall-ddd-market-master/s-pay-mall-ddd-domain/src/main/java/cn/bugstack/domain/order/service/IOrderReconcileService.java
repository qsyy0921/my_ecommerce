package cn.bugstack.domain.order.service;

import cn.bugstack.domain.order.model.entity.ReconcileCaseEntity;

import java.util.List;

public interface IOrderReconcileService {

    int reconcileMarketSettlementOrders();

    int scanReconcileCases();

    List<ReconcileCaseEntity> queryReconcileCaseList(Integer caseStatus, String caseType, Long lastId, Integer pageSize);

    boolean handleReconcileCase(String caseNo, Integer caseStatus, String handler, String handleNote);

    boolean replayReconcileCase(String caseNo, String operator);

    void recordReconcileOperation(String operator, String operationType, String bizId, String requestBody, String result);

    int importThirdPartyBillCsv(String csvText);

}
