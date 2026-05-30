package cn.bugstack.domain.order.service;

import cn.bugstack.domain.order.adapter.port.IMarketSettlementPort;
import cn.bugstack.domain.order.adapter.repository.IOrderReconcileRepository;
import cn.bugstack.domain.order.adapter.repository.IOrderRepository;
import cn.bugstack.domain.order.model.entity.OrderEntity;
import cn.bugstack.domain.order.model.entity.ReconcileCaseEntity;
import cn.bugstack.domain.order.model.entity.ReconcileOperationLogEntity;
import cn.bugstack.domain.order.model.valobj.MarketTypeVO;
import cn.bugstack.domain.order.model.valobj.ReconcileCaseStatusVO;
import lombok.extern.slf4j.Slf4j;

import java.util.Date;
import java.util.List;

@Slf4j
public class OrderReconcileService implements IOrderReconcileService {

    private final IOrderRepository repository;
    private final IOrderReconcileRepository reconcileRepository;
    private final IMarketSettlementPort marketSettlementPort;
    private final IOrderService orderService;

    public OrderReconcileService(IOrderRepository repository,
                                 IOrderReconcileRepository reconcileRepository,
                                 IMarketSettlementPort marketSettlementPort,
                                 IOrderService orderService) {
        this.repository = repository;
        this.reconcileRepository = reconcileRepository;
        this.marketSettlementPort = marketSettlementPort;
        this.orderService = orderService;
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
                Date payTime = null == orderEntity.getPayTime() ? new Date() : orderEntity.getPayTime();
                if (MarketTypeVO.SECKILL_MARKET.getCode().equals(orderEntity.getMarketType())) {
                    marketSettlementPort.settlementSeckillPayOrder(orderEntity.getUserId(), orderEntity.getOrderId(), payTime);
                    orderService.changeOrderMarketSettlement(java.util.Collections.singletonList(orderEntity.getOrderId()));
                } else {
                    marketSettlementPort.settlementGroupBuyMarketPayOrder(orderEntity.getUserId(), orderEntity.getOrderId(), payTime);
                }
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
        if (null == caseNo || caseNo.trim().isEmpty()) {
            return false;
        }
        String handler = null == operator || operator.trim().isEmpty() ? "local-admin" : operator.trim();
        try {
            ReconcileCaseEntity reconcileCase = reconcileRepository.queryReconcileCase(caseNo);
            if (null == reconcileCase || !ReconcileCaseStatusVO.OPEN.getCode().equals(reconcileCase.getCaseStatus())) {
                log.warn("reconcile case is not open, skip replay caseNo:{}", caseNo);
                return false;
            }

            if (caseNo.startsWith("MARKET_SETTLEMENT_TIMEOUT:")) {
                String orderId = bizId(caseNo);
                OrderEntity orderEntity = repository.queryOrderByOrderId(orderId);
                if (null == orderEntity) {
                    return false;
                }
                Date payTime = null == orderEntity.getPayTime() ? new Date() : orderEntity.getPayTime();
                if (MarketTypeVO.SECKILL_MARKET.getCode().equals(orderEntity.getMarketType())) {
                    marketSettlementPort.settlementSeckillPayOrder(orderEntity.getUserId(), orderEntity.getOrderId(), payTime);
                    orderService.changeOrderMarketSettlement(java.util.Collections.singletonList(orderEntity.getOrderId()));
                } else {
                    marketSettlementPort.settlementGroupBuyMarketPayOrder(orderEntity.getUserId(), orderEntity.getOrderId(), payTime);
                }
                return confirmReconcileCase(caseNo, handler, "replay market settlement success");
            }

            if (caseNo.startsWith("PAY_WAIT_TIMEOUT:")) {
                String orderId = bizId(caseNo);
                boolean closed = repository.changeOrderClose(orderId);
                if (!closed) {
                    return false;
                }
                return confirmReconcileCase(caseNo, handler, "timeout unpaid order closed by replay");
            }

            if (caseNo.startsWith("REFUND_TIMEOUT:")) {
                String orderId = bizId(caseNo);
                OrderEntity orderEntity = repository.queryOrderByOrderId(orderId);
                if (null == orderEntity) {
                    return false;
                }
                boolean refunded = orderService.refundPayOrder(orderEntity.getUserId(), orderId);
                if (!refunded) {
                    return false;
                }
                return confirmReconcileCase(caseNo, handler, "refund replay success");
            }

            if (caseNo.startsWith("MQ_CONSUME_FAIL:")) {
                String messageId = bizId(caseNo);
                boolean replayed = reconcileRepository.replayMqFailure(messageId);
                if (!replayed) {
                    return false;
                }
                return confirmReconcileCase(caseNo, handler, "mq message replayed to original exchange");
            }

            log.warn("reconcile case does not support auto replay caseNo:{}", caseNo);
            return false;
        } catch (Exception e) {
            log.error("replay reconcile case failed caseNo:{}", caseNo, e);
            remarkReconcileCase(caseNo, handler, "replay failed: " + e.getMessage());
            return false;
        }
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

    private String bizId(String caseNo) {
        int index = caseNo.indexOf(':');
        if (index < 0 || index + 1 >= caseNo.length()) {
            return caseNo;
        }
        return caseNo.substring(index + 1);
    }

}
