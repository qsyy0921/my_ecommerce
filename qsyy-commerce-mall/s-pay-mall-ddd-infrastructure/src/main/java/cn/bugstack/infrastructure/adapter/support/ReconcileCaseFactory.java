package cn.bugstack.infrastructure.adapter.support;

import cn.bugstack.domain.order.model.entity.PaymentFlowEntity;
import cn.bugstack.domain.order.model.entity.RefundFlowEntity;
import cn.bugstack.infrastructure.dao.po.MqMessageRecord;
import cn.bugstack.infrastructure.dao.po.PayOrder;
import cn.bugstack.infrastructure.dao.po.ReconcileCase;
import cn.bugstack.infrastructure.dao.po.ThirdPartyBill;

public class ReconcileCaseFactory {

    public ReconcileCase orderCase(PayOrder payOrder, String caseType, String severity,
                                   String sourceStatus, String targetStatus, String summary) {
        return ReconcileCase.builder()
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
    }

    public ReconcileCase failedMqCase(MqMessageRecord messageRecord) {
        return ReconcileCase.builder()
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
    }

    public ReconcileCase paymentFlowMissBillCase(PaymentFlowEntity paymentFlow) {
        return ReconcileCase.builder()
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
    }

    public ReconcileCase refundFlowMissBillCase(RefundFlowEntity refundFlow) {
        return ReconcileCase.builder()
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
    }

    public ReconcileCase thirdPartyBillMissLocalCase(ThirdPartyBill bill) {
        return ReconcileCase.builder()
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
    }

}
