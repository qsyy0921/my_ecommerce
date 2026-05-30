package cn.bugstack.infrastructure.adapter.support;

import cn.bugstack.domain.order.model.entity.ReconcileOperationLogEntity;
import cn.bugstack.infrastructure.dao.IReconcileOperationLogDao;
import cn.bugstack.infrastructure.dao.po.ReconcileOperationLog;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Records and queries reconciliation operation audit logs.
 */
@Component
public class ReconcileOperationLogSupport {

    @Resource
    private IReconcileOperationLogDao reconcileOperationLogDao;

    private final OrderReconcileEntityMapper entityMapper = new OrderReconcileEntityMapper();

    public void record(String operator, String operationType, String bizId, String requestBody, String result) {
        reconcileOperationLogDao.insert(ReconcileOperationLog.builder()
                .operator(isBlank(operator) ? "unknown" : operator)
                .operationType(operationType)
                .bizId(bizId)
                .requestBody(left(requestBody, 1024))
                .result(left(result, 512))
                .build());
    }

    public List<ReconcileOperationLogEntity> queryLogList(String bizId, Long lastId, Integer pageSize) {
        List<ReconcileOperationLog> operationLogList = reconcileOperationLogDao.queryLogList(bizId, lastId, pageSize);
        if (null == operationLogList || operationLogList.isEmpty()) {
            return new ArrayList<>();
        }
        return operationLogList.stream().map(entityMapper::toReconcileOperationLogEntity).collect(Collectors.toList());
    }

    private boolean isBlank(String value) {
        return null == value || value.trim().isEmpty();
    }

    private String left(String value, int length) {
        if (null == value || value.length() <= length) {
            return value;
        }
        return value.substring(0, length);
    }

}
