package cn.bugstack.domain.order.service.processor;

import cn.bugstack.domain.order.adapter.repository.IOrderReconcileRepository;
import cn.bugstack.domain.order.adapter.repository.IOrderRepository;
import cn.bugstack.domain.order.model.entity.OrderEntity;
import cn.bugstack.domain.order.model.entity.ReconcileCaseEntity;
import cn.bugstack.domain.order.model.valobj.ReconcileCaseStatusVO;
import cn.bugstack.domain.order.service.IOrderService;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ReconcileCaseReplayProcessor {

    private static final String MARKET_SETTLEMENT_TIMEOUT = "MARKET_SETTLEMENT_TIMEOUT:";
    private static final String PAY_WAIT_TIMEOUT = "PAY_WAIT_TIMEOUT:";
    private static final String REFUND_TIMEOUT = "REFUND_TIMEOUT:";
    private static final String MQ_CONSUME_FAIL = "MQ_CONSUME_FAIL:";

    private final IOrderRepository repository;
    private final IOrderReconcileRepository reconcileRepository;
    private final IOrderService orderService;
    private final MarketSettlementReconcileProcessor marketSettlementReconcileProcessor;

    public ReconcileCaseReplayProcessor(IOrderRepository repository,
                                        IOrderReconcileRepository reconcileRepository,
                                        IOrderService orderService,
                                        MarketSettlementReconcileProcessor marketSettlementReconcileProcessor) {
        this.repository = repository;
        this.reconcileRepository = reconcileRepository;
        this.orderService = orderService;
        this.marketSettlementReconcileProcessor = marketSettlementReconcileProcessor;
    }

    public boolean replay(String caseNo, String operator) {
        if (isBlank(caseNo)) {
            return false;
        }
        String handler = handler(operator);
        try {
            ReconcileCaseEntity reconcileCase = reconcileRepository.queryReconcileCase(caseNo);
            if (null == reconcileCase || !ReconcileCaseStatusVO.OPEN.getCode().equals(reconcileCase.getCaseStatus())) {
                log.warn("reconcile case is not open, skip replay caseNo:{}", caseNo);
                return false;
            }

            if (caseNo.startsWith(MARKET_SETTLEMENT_TIMEOUT)) {
                return replayMarketSettlement(caseNo, handler);
            }
            if (caseNo.startsWith(PAY_WAIT_TIMEOUT)) {
                return replayPayWaitTimeout(caseNo, handler);
            }
            if (caseNo.startsWith(REFUND_TIMEOUT)) {
                return replayRefundTimeout(caseNo, handler);
            }
            if (caseNo.startsWith(MQ_CONSUME_FAIL)) {
                return replayMqConsumeFail(caseNo, handler);
            }

            log.warn("reconcile case does not support auto replay caseNo:{}", caseNo);
            return false;
        } catch (Exception e) {
            log.error("replay reconcile case failed caseNo:{}", caseNo, e);
            reconcileRepository.remarkReconcileCase(caseNo, handler, "replay failed: " + e.getMessage());
            return false;
        }
    }

    private boolean replayMarketSettlement(String caseNo, String handler) {
        OrderEntity orderEntity = repository.queryOrderByOrderId(bizId(caseNo));
        if (null == orderEntity) {
            return false;
        }
        marketSettlementReconcileProcessor.settle(orderEntity);
        return confirm(caseNo, handler, "replay market settlement success");
    }

    private boolean replayPayWaitTimeout(String caseNo, String handler) {
        boolean closed = repository.changeOrderClose(bizId(caseNo));
        if (!closed) {
            return false;
        }
        return confirm(caseNo, handler, "timeout unpaid order closed by replay");
    }

    private boolean replayRefundTimeout(String caseNo, String handler) {
        String orderId = bizId(caseNo);
        OrderEntity orderEntity = repository.queryOrderByOrderId(orderId);
        if (null == orderEntity) {
            return false;
        }
        boolean refunded = orderService.refundPayOrder(orderEntity.getUserId(), orderId);
        if (!refunded) {
            return false;
        }
        return confirm(caseNo, handler, "refund replay success");
    }

    private boolean replayMqConsumeFail(String caseNo, String handler) {
        boolean replayed = reconcileRepository.replayMqFailure(bizId(caseNo));
        if (!replayed) {
            return false;
        }
        return confirm(caseNo, handler, "mq message replayed to original exchange");
    }

    private boolean confirm(String caseNo, String handler, String note) {
        return reconcileRepository.handleReconcileCase(caseNo, ReconcileCaseStatusVO.CONFIRMED.getCode(), handler, note);
    }

    private String handler(String operator) {
        return isBlank(operator) ? "local-admin" : operator.trim();
    }

    private boolean isBlank(String value) {
        return null == value || value.trim().isEmpty();
    }

    private String bizId(String caseNo) {
        int index = caseNo.indexOf(':');
        if (index < 0 || index + 1 >= caseNo.length()) {
            return caseNo;
        }
        return caseNo.substring(index + 1);
    }

}
