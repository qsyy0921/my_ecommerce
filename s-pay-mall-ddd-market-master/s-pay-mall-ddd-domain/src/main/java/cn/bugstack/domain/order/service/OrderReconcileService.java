package cn.bugstack.domain.order.service;

import cn.bugstack.domain.order.adapter.port.IProductPort;
import cn.bugstack.domain.order.adapter.repository.IOrderRepository;
import cn.bugstack.domain.order.model.entity.OrderEntity;
import cn.bugstack.domain.order.model.entity.ReconcileCaseEntity;
import cn.bugstack.domain.order.model.valobj.MarketTypeVO;
import lombok.extern.slf4j.Slf4j;

import java.util.Date;
import java.util.List;

@Slf4j
public class OrderReconcileService implements IOrderReconcileService {

    private final IOrderRepository repository;
    private final IProductPort productPort;
    private final IOrderService orderService;

    public OrderReconcileService(IOrderRepository repository, IProductPort productPort, IOrderService orderService) {
        this.repository = repository;
        this.productPort = productPort;
        this.orderService = orderService;
    }

    @Override
    public int reconcileMarketSettlementOrders() {
        List<OrderEntity> orderEntities = repository.queryStaleMarketSettlementOrderList();
        if (null == orderEntities || orderEntities.isEmpty()) {
            return 0;
        }

        int successCount = 0;
        for (OrderEntity orderEntity : orderEntities) {
            try {
                Date payTime = null == orderEntity.getPayTime() ? new Date() : orderEntity.getPayTime();
                if (MarketTypeVO.SECKILL_MARKET.getCode().equals(orderEntity.getMarketType())) {
                    productPort.settlementSeckillPayOrder(orderEntity.getUserId(), orderEntity.getOrderId(), payTime);
                    repository.changeOrderMarketSettlement(java.util.Collections.singletonList(orderEntity.getOrderId()));
                } else {
                    productPort.settlementMarketPayOrder(orderEntity.getUserId(), orderEntity.getOrderId(), payTime);
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
        return repository.scanReconcileCases();
    }

    @Override
    public List<ReconcileCaseEntity> queryReconcileCaseList(Integer caseStatus, String caseType, Long lastId, Integer pageSize) {
        return repository.queryReconcileCaseList(caseStatus, caseType, lastId, null == pageSize ? 20 : pageSize);
    }

    @Override
    public boolean handleReconcileCase(String caseNo, Integer caseStatus, String handler, String handleNote) {
        return repository.handleReconcileCase(caseNo, caseStatus, handler, handleNote);
    }

    @Override
    public boolean replayReconcileCase(String caseNo, String operator) {
        if (null == caseNo || caseNo.trim().isEmpty()) {
            return false;
        }
        String handler = null == operator || operator.trim().isEmpty() ? "local-admin" : operator.trim();
        try {
            if (caseNo.startsWith("MARKET_SETTLEMENT_TIMEOUT:")) {
                String orderId = bizId(caseNo);
                OrderEntity orderEntity = repository.queryOrderByOrderId(orderId);
                if (null == orderEntity) {
                    return false;
                }
                Date payTime = null == orderEntity.getPayTime() ? new Date() : orderEntity.getPayTime();
                if (MarketTypeVO.SECKILL_MARKET.getCode().equals(orderEntity.getMarketType())) {
                    productPort.settlementSeckillPayOrder(orderEntity.getUserId(), orderEntity.getOrderId(), payTime);
                    repository.changeOrderMarketSettlement(java.util.Collections.singletonList(orderEntity.getOrderId()));
                } else {
                    productPort.settlementMarketPayOrder(orderEntity.getUserId(), orderEntity.getOrderId(), payTime);
                }
                return repository.handleReconcileCase(caseNo, 1, handler, "replay market settlement success");
            }

            if (caseNo.startsWith("PAY_WAIT_TIMEOUT:")) {
                String orderId = bizId(caseNo);
                boolean closed = repository.changeOrderClose(orderId);
                if (!closed) {
                    return false;
                }
                return repository.handleReconcileCase(caseNo, 1, handler, "timeout unpaid order closed by replay");
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
                return repository.handleReconcileCase(caseNo, 1, handler, "refund replay success");
            }

            if (caseNo.startsWith("MQ_CONSUME_FAIL:")) {
                String messageId = bizId(caseNo);
                boolean replayed = repository.replayMqFailure(messageId);
                if (!replayed) {
                    return false;
                }
                return repository.handleReconcileCase(caseNo, 1, handler, "mq message replayed to original exchange");
            }

            log.warn("reconcile case does not support auto replay caseNo:{}", caseNo);
            return false;
        } catch (Exception e) {
            log.error("replay reconcile case failed caseNo:{}", caseNo, e);
            repository.handleReconcileCase(caseNo, 0, handler, "replay failed: " + e.getMessage());
            return false;
        }
    }

    @Override
    public void recordReconcileOperation(String operator, String operationType, String bizId, String requestBody, String result) {
        repository.recordReconcileOperation(operator, operationType, bizId, requestBody, result);
    }

    @Override
    public int importThirdPartyBillCsv(String csvText) {
        return repository.importThirdPartyBillCsv(csvText);
    }

    private String bizId(String caseNo) {
        int index = caseNo.indexOf(':');
        if (index < 0 || index + 1 >= caseNo.length()) {
            return caseNo;
        }
        return caseNo.substring(index + 1);
    }

}
