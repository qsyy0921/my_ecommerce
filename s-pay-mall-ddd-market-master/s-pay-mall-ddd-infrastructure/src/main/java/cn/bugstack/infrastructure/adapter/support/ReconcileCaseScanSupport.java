package cn.bugstack.infrastructure.adapter.support;

import cn.bugstack.domain.order.adapter.port.IPaymentFlowPort;
import cn.bugstack.domain.order.adapter.port.IRefundFlowPort;
import cn.bugstack.domain.order.model.entity.PaymentFlowEntity;
import cn.bugstack.domain.order.model.entity.RefundFlowEntity;
import cn.bugstack.infrastructure.dao.IMqMessageRecordDao;
import cn.bugstack.infrastructure.dao.IOrderDao;
import cn.bugstack.infrastructure.dao.IReconcileCaseDao;
import cn.bugstack.infrastructure.dao.IThirdPartyBillDao;
import cn.bugstack.infrastructure.dao.po.MqMessageRecord;
import cn.bugstack.infrastructure.dao.po.PayOrder;
import cn.bugstack.infrastructure.dao.po.ThirdPartyBill;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.List;

/**
 * Scans local reconciliation sources and upserts reconciliation cases.
 */
@Component
public class ReconcileCaseScanSupport {

    @Resource
    private IOrderDao orderDao;
    @Resource
    private IMqMessageRecordDao mqMessageRecordDao;
    @Resource
    private IReconcileCaseDao reconcileCaseDao;
    @Resource
    private IThirdPartyBillDao thirdPartyBillDao;
    @Resource
    private IPaymentFlowPort paymentFlowPort;
    @Resource
    private IRefundFlowPort refundFlowPort;

    private final ReconcileCaseFactory reconcileCaseFactory = new ReconcileCaseFactory();

    public int scanReconcileCases() {
        int count = 0;
        count += upsertOrderCases(orderDao.queryStaleMarketSettlementOrderList(), "MARKET_SETTLEMENT_TIMEOUT", "critical", "PAY_SUCCESS", "MARKET",
                "商城订单已支付，但营销结算长时间未完成");
        count += upsertOrderCases(orderDao.queryStaleWaitRefundOrderList(), "REFUND_TIMEOUT", "warning", "WAIT_REFUND", "CLOSE",
                "商城订单处于待退款中间态超过 30 分钟");
        count += upsertOrderCases(orderDao.queryStalePayWaitOrderList(), "PAY_WAIT_TIMEOUT", "warning", "PAY_WAIT", "CLOSE",
                "商城订单超过 30 分钟仍等待支付或未正常关单");
        count += upsertFailedMqCases(mqMessageRecordDao.queryFailedMessageList());
        count += upsertPaymentFlowMissBillCases(paymentFlowPort.queryMissThirdPartyBillList(50));
        count += upsertRefundFlowMissBillCases(refundFlowPort.queryMissThirdPartyBillList(50));
        count += upsertThirdPartyBillMissLocalCases(thirdPartyBillDao.queryUnmatchedBillList(50));
        return count;
    }

    private int upsertOrderCases(List<PayOrder> payOrderList, String caseType, String severity,
                                 String sourceStatus, String targetStatus, String summary) {
        if (null == payOrderList || payOrderList.isEmpty()) {
            return 0;
        }
        int count = 0;
        for (PayOrder payOrder : payOrderList) {
            count += reconcileCaseDao.upsert(reconcileCaseFactory.orderCase(payOrder, caseType, severity, sourceStatus, targetStatus, summary));
        }
        return count;
    }

    private int upsertFailedMqCases(List<MqMessageRecord> failedMessageList) {
        if (null == failedMessageList || failedMessageList.isEmpty()) {
            return 0;
        }
        int count = 0;
        for (MqMessageRecord messageRecord : failedMessageList) {
            count += reconcileCaseDao.upsert(reconcileCaseFactory.failedMqCase(messageRecord));
        }
        return count;
    }

    private int upsertPaymentFlowMissBillCases(List<PaymentFlowEntity> paymentFlowList) {
        if (null == paymentFlowList || paymentFlowList.isEmpty()) {
            return 0;
        }
        int count = 0;
        for (PaymentFlowEntity paymentFlow : paymentFlowList) {
            count += reconcileCaseDao.upsert(reconcileCaseFactory.paymentFlowMissBillCase(paymentFlow));
        }
        return count;
    }

    private int upsertRefundFlowMissBillCases(List<RefundFlowEntity> refundFlowList) {
        if (null == refundFlowList || refundFlowList.isEmpty()) {
            return 0;
        }
        int count = 0;
        for (RefundFlowEntity refundFlow : refundFlowList) {
            count += reconcileCaseDao.upsert(reconcileCaseFactory.refundFlowMissBillCase(refundFlow));
        }
        return count;
    }

    private int upsertThirdPartyBillMissLocalCases(List<ThirdPartyBill> billList) {
        if (null == billList || billList.isEmpty()) {
            return 0;
        }
        int count = 0;
        for (ThirdPartyBill bill : billList) {
            count += reconcileCaseDao.upsert(reconcileCaseFactory.thirdPartyBillMissLocalCase(bill));
        }
        return count;
    }

}
