package cn.bugstack.infrastructure.adapter.repository;

import cn.bugstack.domain.order.adapter.port.IPaymentFlowPort;
import cn.bugstack.domain.order.adapter.port.IRefundFlowPort;
import cn.bugstack.domain.order.adapter.repository.IOrderReconcileRepository;
import cn.bugstack.domain.order.model.entity.OrderEntity;
import cn.bugstack.domain.order.model.entity.PaymentFlowEntity;
import cn.bugstack.domain.order.model.entity.ReconcileCaseEntity;
import cn.bugstack.domain.order.model.entity.ReconcileOperationLogEntity;
import cn.bugstack.domain.order.model.entity.RefundFlowEntity;
import cn.bugstack.infrastructure.adapter.support.MqFailureReplaySupport;
import cn.bugstack.infrastructure.adapter.support.OrderReconcileEntityMapper;
import cn.bugstack.infrastructure.adapter.support.ReconcileCaseFactory;
import cn.bugstack.infrastructure.adapter.support.ThirdPartyBillCsvParser;
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
import org.springframework.stereotype.Repository;

import javax.annotation.Resource;
import java.util.ArrayList;
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
    private MqFailureReplaySupport mqFailureReplaySupport;

    private final ReconcileCaseFactory reconcileCaseFactory = new ReconcileCaseFactory();
    private final OrderReconcileEntityMapper entityMapper = new OrderReconcileEntityMapper();
    private final ThirdPartyBillCsvParser thirdPartyBillCsvParser = new ThirdPartyBillCsvParser();

    @Override
    public List<OrderEntity> queryStaleMarketSettlementOrderList() {
        List<PayOrder> payOrderList = orderDao.queryStaleMarketSettlementOrderList();
        if (null == payOrderList || payOrderList.isEmpty()) {
            return new ArrayList<>();
        }

        return payOrderList.stream().map(entityMapper::toOrderEntity).collect(Collectors.toList());
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
                count += reconcileCaseDao.upsert(reconcileCaseFactory.failedMqCase(messageRecord));
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
        return reconcileCaseList.stream().map(entityMapper::toReconcileCaseEntity).collect(Collectors.toList());
    }

    @Override
    public ReconcileCaseEntity queryReconcileCase(String caseNo) {
        ReconcileCase reconcileCase = reconcileCaseDao.queryByCaseNo(caseNo);
        if (null == reconcileCase) {
            return null;
        }
        return entityMapper.toReconcileCaseEntity(reconcileCase);
    }

    @Override
    public boolean handleReconcileCase(String caseNo, Integer caseStatus, String handler, String handleNote) {
        return reconcileCaseDao.updateCaseHandled(caseNo, caseStatus, handler, handleNote) > 0;
    }

    @Override
    public boolean remarkReconcileCase(String caseNo, String handler, String handleNote) {
        return reconcileCaseDao.updateCaseRemark(caseNo, handler, handleNote) > 0;
    }

    @Override
    public boolean replayMqFailure(String messageId) {
        return mqFailureReplaySupport.replay(messageId);
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
    public List<ReconcileOperationLogEntity> queryReconcileOperationLogList(String bizId, Long lastId, Integer pageSize) {
        List<ReconcileOperationLog> operationLogList = reconcileOperationLogDao.queryLogList(bizId, lastId, pageSize);
        if (null == operationLogList || operationLogList.isEmpty()) {
            return new ArrayList<>();
        }
        return operationLogList.stream().map(entityMapper::toReconcileOperationLogEntity).collect(Collectors.toList());
    }

    @Override
    public int importThirdPartyBillCsv(String csvText) {
        List<ThirdPartyBill> billList = thirdPartyBillCsvParser.parse(csvText);
        if (billList.isEmpty()) {
            return 0;
        }
        return thirdPartyBillDao.insertIgnoreBatch(billList);
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
            count += reconcileCaseDao.upsert(reconcileCaseFactory.orderCase(payOrder, caseType, severity, sourceStatus, targetStatus, summary));
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
