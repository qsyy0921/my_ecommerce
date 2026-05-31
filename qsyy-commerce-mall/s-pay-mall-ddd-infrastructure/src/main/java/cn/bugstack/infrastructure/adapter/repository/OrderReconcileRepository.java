package cn.bugstack.infrastructure.adapter.repository;

import cn.bugstack.domain.order.adapter.repository.IOrderReconcileRepository;
import cn.bugstack.domain.order.model.entity.OrderEntity;
import cn.bugstack.domain.order.model.entity.ReconcileCaseEntity;
import cn.bugstack.domain.order.model.entity.ReconcileOperationLogEntity;
import cn.bugstack.infrastructure.adapter.support.MqFailureReplaySupport;
import cn.bugstack.infrastructure.adapter.support.OrderReconcileEntityMapper;
import cn.bugstack.infrastructure.adapter.support.ReconcileCaseScanSupport;
import cn.bugstack.infrastructure.adapter.support.ReconcileOperationLogSupport;
import cn.bugstack.infrastructure.adapter.support.ThirdPartyBillCsvParser;
import cn.bugstack.infrastructure.dao.IOrderDao;
import cn.bugstack.infrastructure.dao.IReconcileCaseDao;
import cn.bugstack.infrastructure.dao.IThirdPartyBillDao;
import cn.bugstack.infrastructure.dao.po.PayOrder;
import cn.bugstack.infrastructure.dao.po.ReconcileCase;
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
    private IReconcileCaseDao reconcileCaseDao;
    @Resource
    private IThirdPartyBillDao thirdPartyBillDao;
    @Resource
    private MqFailureReplaySupport mqFailureReplaySupport;
    @Resource
    private ReconcileCaseScanSupport reconcileCaseScanSupport;
    @Resource
    private ReconcileOperationLogSupport reconcileOperationLogSupport;

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
        return reconcileCaseScanSupport.scanReconcileCases();
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
        reconcileOperationLogSupport.record(operator, operationType, bizId, requestBody, result);
    }

    @Override
    public List<ReconcileOperationLogEntity> queryReconcileOperationLogList(String bizId, Long lastId, Integer pageSize) {
        return reconcileOperationLogSupport.queryLogList(bizId, lastId, pageSize);
    }

    @Override
    public int importThirdPartyBillCsv(String csvText) {
        List<ThirdPartyBill> billList = thirdPartyBillCsvParser.parse(csvText);
        if (billList.isEmpty()) {
            return 0;
        }
        return thirdPartyBillDao.insertIgnoreBatch(billList);
    }

}
