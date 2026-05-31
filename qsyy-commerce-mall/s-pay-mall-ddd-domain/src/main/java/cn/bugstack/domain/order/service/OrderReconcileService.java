package cn.bugstack.domain.order.service;

import cn.bugstack.domain.order.adapter.repository.IOrderReconcileRepository;
import cn.bugstack.domain.order.model.entity.OrderEntity;
import cn.bugstack.domain.order.model.entity.ReconcileCaseEntity;
import cn.bugstack.domain.order.model.entity.ReconcileOperationLogEntity;
import cn.bugstack.domain.order.model.valobj.ReconcileCaseStatusVO;
import cn.bugstack.domain.order.service.processor.MarketSettlementReconcileProcessor;
import cn.bugstack.domain.order.service.processor.ReconcileCaseReplayProcessor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

@Slf4j
public class OrderReconcileService implements IOrderReconcileService {

    private final IOrderReconcileRepository reconcileRepository;
    private final MarketSettlementReconcileProcessor marketSettlementReconcileProcessor;
    private final ReconcileCaseReplayProcessor reconcileCaseReplayProcessor;

    public OrderReconcileService(IOrderReconcileRepository reconcileRepository,
                                 MarketSettlementReconcileProcessor marketSettlementReconcileProcessor,
                                 ReconcileCaseReplayProcessor reconcileCaseReplayProcessor) {
        this.reconcileRepository = reconcileRepository;
        this.marketSettlementReconcileProcessor = marketSettlementReconcileProcessor;
        this.reconcileCaseReplayProcessor = reconcileCaseReplayProcessor;
    }

    @Override
    public int reconcileMarketSettlementOrders() {
        List<OrderEntity> orderEntities = reconcileRepository.queryStaleMarketSettlementOrderList();
        if (null == orderEntities || orderEntities.isEmpty()) {
            return 0;
        }

        int successCount = 0;
        for (OrderEntity orderEntity : orderEntities) {
            try {
                marketSettlementReconcileProcessor.settle(orderEntity);
                successCount++;
            } catch (Exception e) {
                log.error("market settlement reconcile failed userId:{} orderId:{}", orderEntity.getUserId(), orderEntity.getOrderId(), e);
            }
        }
        return successCount;
    }

    @Override
    public int scanReconcileCases() {
        return reconcileRepository.scanReconcileCases();
    }

    @Override
    public List<ReconcileCaseEntity> queryReconcileCaseList(Integer caseStatus, String caseType, Long lastId, Integer pageSize) {
        return reconcileRepository.queryReconcileCaseList(caseStatus, caseType, lastId, null == pageSize ? 20 : pageSize);
    }

    @Override
    public boolean handleReconcileCase(String caseNo, Integer caseStatus, String handler, String handleNote) {
        ReconcileCaseStatusVO statusVO = ReconcileCaseStatusVO.valueOfCode(caseStatus);
        if (null == statusVO || ReconcileCaseStatusVO.OPEN.equals(statusVO)) {
            return false;
        }
        return reconcileRepository.handleReconcileCase(caseNo, statusVO.getCode(), handler, handleNote);
    }

    @Override
    public boolean confirmReconcileCase(String caseNo, String handler, String handleNote) {
        return handleReconcileCase(caseNo, ReconcileCaseStatusVO.CONFIRMED.getCode(), handler, handleNote);
    }

    @Override
    public boolean ignoreReconcileCase(String caseNo, String handler, String handleNote) {
        return handleReconcileCase(caseNo, ReconcileCaseStatusVO.IGNORED.getCode(), handler, handleNote);
    }

    @Override
    public boolean closeReconcileCase(String caseNo, String handler, String handleNote) {
        return handleReconcileCase(caseNo, ReconcileCaseStatusVO.CLOSED.getCode(), handler, handleNote);
    }

    @Override
    public boolean remarkReconcileCase(String caseNo, String handler, String handleNote) {
        if (null == caseNo || caseNo.trim().isEmpty() || null == handleNote || handleNote.trim().isEmpty()) {
            return false;
        }
        return reconcileRepository.remarkReconcileCase(caseNo, handler, handleNote);
    }

    @Override
    public boolean replayReconcileCase(String caseNo, String operator) {
        return reconcileCaseReplayProcessor.replay(caseNo, operator);
    }

    @Override
    public void recordReconcileOperation(String operator, String operationType, String bizId, String requestBody, String result) {
        reconcileRepository.recordReconcileOperation(operator, operationType, bizId, requestBody, result);
    }

    @Override
    public List<ReconcileOperationLogEntity> queryReconcileOperationLogList(String bizId, Long lastId, Integer pageSize) {
        return reconcileRepository.queryReconcileOperationLogList(bizId, lastId, null == pageSize ? 20 : pageSize);
    }

    @Override
    public int importThirdPartyBillCsv(String csvText) {
        return reconcileRepository.importThirdPartyBillCsv(csvText);
    }

}
