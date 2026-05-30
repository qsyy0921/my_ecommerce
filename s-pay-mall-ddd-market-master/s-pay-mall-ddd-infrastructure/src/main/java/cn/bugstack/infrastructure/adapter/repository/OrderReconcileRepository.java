package cn.bugstack.infrastructure.adapter.repository;

import cn.bugstack.domain.order.adapter.port.IPaymentFlowPort;
import cn.bugstack.domain.order.adapter.port.IRefundFlowPort;
import cn.bugstack.domain.order.adapter.repository.IOrderReconcileRepository;
import cn.bugstack.domain.order.model.entity.OrderEntity;
import cn.bugstack.domain.order.model.entity.PaymentFlowEntity;
import cn.bugstack.domain.order.model.entity.ReconcileCaseEntity;
import cn.bugstack.domain.order.model.entity.RefundFlowEntity;
import cn.bugstack.domain.order.model.valobj.OrderStatusVO;
import cn.bugstack.infrastructure.dao.IMqMessageRecordDao;
import cn.bugstack.infrastructure.dao.IOrderDao;
import cn.bugstack.infrastructure.dao.IReconcileCaseDao;
import cn.bugstack.infrastructure.dao.IReconcileOperationLogDao;
import cn.bugstack.infrastructure.dao.IThirdPartyBillDao;
import cn.bugstack.infrastructure.dao.po.MqMessageRecord;
import cn.bugstack.infrastructure.dao.po.PayOrder;
import cn.bugstack.infrastructure.dao.po.ReconcileCase;
import cn.bugstack.infrastructure.dao.po.ReconcileOperationLog;
import cn.bugstack.infrastructure.dao.po.ThirdPartyBill;
import cn.bugstack.infrastructure.event.EventPublisher;
import org.springframework.stereotype.Repository;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

@Repository
public class OrderReconcileRepository implements IOrderReconcileRepository {

    @Resource
    private IOrderDao orderDao;
    @Resource
    private IMqMessageRecordDao mqMessageRecordDao;
    @Resource
    private IReconcileCaseDao reconcileCaseDao;
    @Resource
    private IReconcileOperationLogDao reconcileOperationLogDao;
    @Resource
    private IThirdPartyBillDao thirdPartyBillDao;
    @Resource
    private IPaymentFlowPort paymentFlowPort;
    @Resource
    private IRefundFlowPort refundFlowPort;
    @Resource
    private EventPublisher eventPublisher;

    @Override
    public List<OrderEntity> queryStaleMarketSettlementOrderList() {
        List<PayOrder> payOrderList = orderDao.queryStaleMarketSettlementOrderList();
        if (null == payOrderList || payOrderList.isEmpty()) {
            return new ArrayList<>();
        }

        return payOrderList.stream().map(this::toOrderEntity).collect(Collectors.toList());
    }

    @Override
    public int scanReconcileCases() {
        int count = 0;
        count += upsertOrderCases(orderDao.queryStaleMarketSettlementOrderList(), "MARKET_SETTLEMENT_TIMEOUT", "critical", "PAY_SUCCESS", "MARKET",
                "商城订单已支付，但营销结算长时间未完成");
        count += upsertOrderCases(orderDao.queryStaleWaitRefundOrderList(), "REFUND_TIMEOUT", "warning", "WAIT_REFUND", "CLOSE",
                "商城订单处于待退款中间态超过 30 分钟");
        count += upsertOrderCases(orderDao.queryStalePayWaitOrderList(), "PAY_WAIT_TIMEOUT", "warning", "PAY_WAIT", "CLOSE",
                "商城订单超过 30 分钟仍等待支付或未正常关单");

        List<MqMessageRecord> failedMessageList = mqMessageRecordDao.queryFailedMessageList();
        if (null != failedMessageList) {
            for (MqMessageRecord messageRecord : failedMessageList) {
                ReconcileCase reconcileCase = ReconcileCase.builder()
                        .caseNo("MQ_CONSUME_FAIL:" + messageRecord.getMessageId())
                        .bizType("MQ_MESSAGE")
                        .bizId(messageRecord.getMessageId())
                        .caseType("MQ_CONSUME_FAIL")
                        .caseStatus(0)
                        .severity("critical")
                        .sourceStatus("FAIL")
                        .targetStatus("SUCCESS")
                        .summary("MQ 消费失败，需要重放或人工补偿")
                        .detail(null == messageRecord.getErrorMessage() ? messageRecord.getMessageBody() : messageRecord.getErrorMessage())
                        .retryCount(null == messageRecord.getRetryCount() ? 0 : messageRecord.getRetryCount())
                        .build();
                count += reconcileCaseDao.upsert(reconcileCase);
            }
        }
        count += upsertPaymentFlowMissBillCases(paymentFlowPort.queryMissThirdPartyBillList(50));
        count += upsertRefundFlowMissBillCases(refundFlowPort.queryMissThirdPartyBillList(50));
        count += upsertThirdPartyBillMissLocalCases(thirdPartyBillDao.queryUnmatchedBillList(50));
        return count;
    }

    @Override
    public List<ReconcileCaseEntity> queryReconcileCaseList(Integer caseStatus, String caseType, Long lastId, Integer pageSize) {
        List<ReconcileCase> reconcileCaseList = reconcileCaseDao.queryCaseList(caseStatus, caseType, lastId, pageSize);
        if (null == reconcileCaseList || reconcileCaseList.isEmpty()) {
            return new ArrayList<>();
        }
        return reconcileCaseList.stream().map(this::toReconcileCaseEntity).collect(Collectors.toList());
    }

    @Override
    public boolean handleReconcileCase(String caseNo, Integer caseStatus, String handler, String handleNote) {
        return reconcileCaseDao.updateCaseHandled(caseNo, caseStatus, handler, handleNote) > 0;
    }

    @Override
    public boolean replayMqFailure(String messageId) {
        MqMessageRecord messageRecord = mqMessageRecordDao.queryByMessageId(messageId);
        if (null == messageRecord) {
            return false;
        }
        String routingKey = resolveRoutingKey(messageRecord.getQueueName());
        if (isBlank(messageRecord.getExchangeName()) || isBlank(routingKey) || isBlank(messageRecord.getMessageBody())) {
            return false;
        }
        try {
            mqMessageRecordDao.updateProcessing(messageId);
            eventPublisher.publishToExchange(messageRecord.getExchangeName(), routingKey, messageRecord.getMessageBody());
            mqMessageRecordDao.updateSuccess(messageId);
            return true;
        } catch (Exception e) {
            mqMessageRecordDao.updateFail(MqMessageRecord.builder()
                    .messageId(messageId)
                    .errorMessage("replay failed: " + e.getMessage())
                    .build());
            throw new IllegalStateException(e);
        }
    }

    @Override
    public void recordReconcileOperation(String operator, String operationType, String bizId, String requestBody, String result) {
        reconcileOperationLogDao.insert(ReconcileOperationLog.builder()
                .operator(isBlank(operator) ? "unknown" : operator)
                .operationType(operationType)
                .bizId(bizId)
                .requestBody(null == requestBody ? null : (requestBody.length() > 1024 ? requestBody.substring(0, 1024) : requestBody))
                .result(null == result ? null : (result.length() > 512 ? result.substring(0, 512) : result))
                .build());
    }

    @Override
    public int importThirdPartyBillCsv(String csvText) {
        if (null == csvText || csvText.trim().isEmpty()) {
            return 0;
        }
        String[] lines = csvText.split("\\r?\\n");
        List<ThirdPartyBill> billList = new ArrayList<>();
        for (String line : lines) {
            if (null == line || line.trim().isEmpty() || line.startsWith("billNo,")) {
                continue;
            }
            String[] columns = line.split(",", -1);
            if (columns.length < 8) {
                continue;
            }
            billList.add(ThirdPartyBill.builder()
                    .billNo(columns[0].trim())
                    .orderId(columns[1].trim())
                    .channel(columns[2].trim())
                    .channelTradeNo(columns[3].trim())
                    .billType(columns[4].trim())
                    .amount(new BigDecimal(columns[5].trim()))
                    .billStatus(columns[6].trim())
                    .billTime(parseBillTime(columns[7].trim()))
                    .rawLine(line)
                    .build());
        }
        if (billList.isEmpty()) {
            return 0;
        }
        return thirdPartyBillDao.insertIgnoreBatch(billList);
    }

    private String resolveRoutingKey(String queueName) {
        if (isBlank(queueName)) {
            return null;
        }
        if (queueName.startsWith("routing:")) {
            return queueName.substring("routing:".length());
        }
        if (queueName.contains("topic_team_success")) {
            return "topic.team_success";
        }
        if (queueName.contains("topic_team_refund")) {
            return "topic.team_refund";
        }
        if (queueName.contains("order_pay_success")) {
            return "topic.order_pay_success";
        }
        return null;
    }

    private boolean isBlank(String value) {
        return null == value || value.trim().isEmpty();
    }

    private int upsertOrderCases(List<PayOrder> payOrderList, String caseType, String severity,
                                 String sourceStatus, String targetStatus, String summary) {
        if (null == payOrderList || payOrderList.isEmpty()) {
            return 0;
        }
        int count = 0;
        for (PayOrder payOrder : payOrderList) {
            ReconcileCase reconcileCase = ReconcileCase.builder()
                    .caseNo(caseType + ":" + payOrder.getOrderId())
                    .bizType("PAY_ORDER")
                    .bizId(payOrder.getOrderId())
                    .userId(payOrder.getUserId())
                    .caseType(caseType)
                    .caseStatus(0)
                    .severity(severity)
                    .sourceStatus(null == payOrder.getStatus() ? sourceStatus : payOrder.getStatus())
                    .targetStatus(targetStatus)
                    .summary(summary)
                    .detail("productId=" + payOrder.getProductId() + ", marketType=" + payOrder.getMarketType() + ", payAmount=" + payOrder.getPayAmount())
                    .retryCount(0)
                    .build();
            count += reconcileCaseDao.upsert(reconcileCase);
        }
        return count;
    }

    private int upsertPaymentFlowMissBillCases(List<PaymentFlowEntity> paymentFlowList) {
        if (null == paymentFlowList || paymentFlowList.isEmpty()) {
            return 0;
        }
        int count = 0;
        for (PaymentFlowEntity paymentFlow : paymentFlowList) {
            ReconcileCase reconcileCase = ReconcileCase.builder()
                    .caseNo("PAY_FLOW_MISS_BILL:" + paymentFlow.getFlowNo())
                    .bizType("PAY_FLOW")
                    .bizId(paymentFlow.getFlowNo())
                    .userId(paymentFlow.getUserId())
                    .caseType("PAY_FLOW_MISS_BILL")
                    .caseStatus(0)
                    .severity("warning")
                    .sourceStatus(paymentFlow.getPayStatus())
                    .targetStatus("THIRD_PARTY_BILL")
                    .summary("本地支付流水成功，但未匹配到三方支付账单")
                    .detail("orderId=" + paymentFlow.getOrderId() + ", amount=" + paymentFlow.getPayAmount() + ", channel=" + paymentFlow.getPayChannel())
                    .retryCount(0)
                    .build();
            count += reconcileCaseDao.upsert(reconcileCase);
        }
        return count;
    }

    private int upsertRefundFlowMissBillCases(List<RefundFlowEntity> refundFlowList) {
        if (null == refundFlowList || refundFlowList.isEmpty()) {
            return 0;
        }
        int count = 0;
        for (RefundFlowEntity refundFlow : refundFlowList) {
            ReconcileCase reconcileCase = ReconcileCase.builder()
                    .caseNo("REFUND_FLOW_MISS_BILL:" + refundFlow.getFlowNo())
                    .bizType("REFUND_FLOW")
                    .bizId(refundFlow.getFlowNo())
                    .userId(refundFlow.getUserId())
                    .caseType("REFUND_FLOW_MISS_BILL")
                    .caseStatus(0)
                    .severity("warning")
                    .sourceStatus(refundFlow.getRefundStatus())
                    .targetStatus("THIRD_PARTY_BILL")
                    .summary("本地退款流水成功，但未匹配到三方退款账单")
                    .detail("orderId=" + refundFlow.getOrderId() + ", amount=" + refundFlow.getRefundAmount() + ", channel=" + refundFlow.getRefundChannel())
                    .retryCount(0)
                    .build();
            count += reconcileCaseDao.upsert(reconcileCase);
        }
        return count;
    }

    private int upsertThirdPartyBillMissLocalCases(List<ThirdPartyBill> billList) {
        if (null == billList || billList.isEmpty()) {
            return 0;
        }
        int count = 0;
        for (ThirdPartyBill bill : billList) {
            ReconcileCase reconcileCase = ReconcileCase.builder()
                    .caseNo("BILL_MISS_LOCAL_FLOW:" + bill.getBillNo())
                    .bizType("THIRD_PARTY_BILL")
                    .bizId(bill.getBillNo())
                    .caseType("BILL_MISS_LOCAL_FLOW")
                    .caseStatus(0)
                    .severity("critical")
                    .sourceStatus(bill.getBillType() + ":" + bill.getBillStatus())
                    .targetStatus("LOCAL_FLOW")
                    .summary("三方账单存在，但本地支付或退款流水缺失")
                    .detail("orderId=" + bill.getOrderId() + ", billType=" + bill.getBillType() + ", amount=" + bill.getAmount() + ", channel=" + bill.getChannel())
                    .retryCount(0)
                    .build();
            count += reconcileCaseDao.upsert(reconcileCase);
        }
        return count;
    }

    private OrderEntity toOrderEntity(PayOrder payOrder) {
        return OrderEntity.builder()
                .id(payOrder.getId())
                .userId(payOrder.getUserId())
                .productId(payOrder.getProductId())
                .productName(payOrder.getProductName())
                .orderId(payOrder.getOrderId())
                .orderTime(payOrder.getOrderTime())
                .totalAmount(payOrder.getTotalAmount())
                .orderStatusVO(OrderStatusVO.valueOf(payOrder.getStatus()))
                .payUrl(payOrder.getPayUrl())
                .payTime(payOrder.getPayTime())
                .marketType(payOrder.getMarketType())
                .marketDeductionAmount(payOrder.getMarketDeductionAmount())
                .payAmount(payOrder.getPayAmount())
                .build();
    }

    private ReconcileCaseEntity toReconcileCaseEntity(ReconcileCase reconcileCase) {
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

    private Date parseBillTime(String billTime) {
        try {
            return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse(billTime);
        } catch (ParseException e) {
            return new Date();
        }
    }

}
