package cn.bugstack.domain.order.adapter.repository;

import cn.bugstack.domain.order.model.entity.OrderEntity;
import cn.bugstack.domain.order.model.entity.ReconcileCaseEntity;

import java.util.List;

public interface IOrderReconcileRepository {

    List<OrderEntity> queryStaleMarketSettlementOrderList();

    int scanReconcileCases();

    List<ReconcileCaseEntity> queryReconcileCaseList(Integer caseStatus, String caseType, Long lastId, Integer pageSize);

    boolean handleReconcileCase(String caseNo, Integer caseStatus, String handler, String handleNote);

    boolean replayMqFailure(String messageId);

    void recordReconcileOperation(String operator, String operationType, String bizId, String requestBody, String result);

    int importThirdPartyBillCsv(String csvText);

}
