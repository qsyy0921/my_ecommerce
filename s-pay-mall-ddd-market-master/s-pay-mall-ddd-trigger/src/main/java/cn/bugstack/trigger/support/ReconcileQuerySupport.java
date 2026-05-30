package cn.bugstack.trigger.support;

import cn.bugstack.api.dto.ReconcileCaseResponseDTO;
import cn.bugstack.api.dto.ReconcileOperationLogResponseDTO;
import cn.bugstack.domain.order.model.entity.ReconcileCaseEntity;
import cn.bugstack.domain.order.model.entity.ReconcileOperationLogEntity;
import cn.bugstack.domain.order.service.IOrderReconcileService;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.List;

@Component
public class ReconcileQuerySupport {

    @Resource
    private IOrderReconcileService orderReconcileService;

    @Resource
    private ReconcileResponseAssembler responseAssembler;

    public List<ReconcileCaseResponseDTO> queryCaseList(Integer caseStatus, String caseType, Long lastId, Integer pageSize) {
        List<ReconcileCaseEntity> caseList = orderReconcileService.queryReconcileCaseList(caseStatus, caseType, lastId, pageSize);
        return responseAssembler.toCaseResponseList(caseList);
    }

    public List<ReconcileOperationLogResponseDTO> queryOperationLogList(String bizId, Long lastId, Integer pageSize) {
        List<ReconcileOperationLogEntity> logList = orderReconcileService.queryReconcileOperationLogList(bizId, lastId, pageSize);
        return responseAssembler.toOperationLogResponseList(logList);
    }

}
