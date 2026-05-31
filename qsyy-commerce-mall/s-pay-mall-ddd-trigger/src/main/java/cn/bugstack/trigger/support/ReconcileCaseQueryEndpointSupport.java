package cn.bugstack.trigger.support;

import cn.bugstack.api.dto.ReconcileCaseResponseDTO;
import cn.bugstack.api.dto.ReconcileOperationLogResponseDTO;
import cn.bugstack.api.response.Response;
import cn.bugstack.domain.order.service.IOrderReconcileService;
import cn.bugstack.types.common.Constants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.List;

@Slf4j
@Component
public class ReconcileCaseQueryEndpointSupport {

    @Resource
    private IOrderReconcileService orderReconcileService;

    @Resource
    private ReconcileAdminSupport adminSupport;

    @Resource
    private ReconcileQuerySupport querySupport;

    public Response<Integer> scan(String token, String operator) {
        if (!adminSupport.authorized(token)) {
            return adminSupport.noLogin();
        }
        try {
            int count = orderReconcileService.scanReconcileCases();
            adminSupport.audit(operator, "SCAN", "ALL", null, "count=" + count);
            return success(count);
        } catch (Exception e) {
            log.error("scan reconcile cases failed", e);
            return error();
        }
    }

    public Response<List<ReconcileCaseResponseDTO>> queryCaseList(Integer caseStatus,
                                                                  String caseType,
                                                                  Long lastId,
                                                                  Integer pageSize,
                                                                  String token,
                                                                  String operator) {
        if (!adminSupport.authorized(token)) {
            return adminSupport.noLogin();
        }
        try {
            List<ReconcileCaseResponseDTO> caseList = querySupport.queryCaseList(caseStatus, caseType, lastId, pageSize);
            adminSupport.audit(operator, "QUERY", caseType, "caseStatus=" + caseStatus + ", lastId=" + lastId + ", pageSize=" + pageSize, "count=" + caseList.size());
            return success(caseList);
        } catch (Exception e) {
            log.error("query reconcile case list failed", e);
            return error();
        }
    }

    public Response<List<ReconcileOperationLogResponseDTO>> queryOperationLogs(String bizId,
                                                                               Long lastId,
                                                                               Integer pageSize,
                                                                               String token,
                                                                               String operator) {
        if (!adminSupport.authorized(token)) {
            return adminSupport.noLogin();
        }
        try {
            List<ReconcileOperationLogResponseDTO> operationLogList = querySupport.queryOperationLogList(bizId, lastId, pageSize);
            adminSupport.audit(operator, "QUERY_LOG", bizId, "lastId=" + lastId + ", pageSize=" + pageSize, "count=" + operationLogList.size());
            return success(operationLogList);
        } catch (Exception e) {
            log.error("query reconcile operation logs failed bizId:{}", bizId, e);
            return error();
        }
    }

    private <T> Response<T> success(T data) {
        return Response.<T>builder()
                .code(Constants.ResponseCode.SUCCESS.getCode())
                .info(Constants.ResponseCode.SUCCESS.getInfo())
                .data(data)
                .build();
    }

    private <T> Response<T> error() {
        return Response.<T>builder()
                .code(Constants.ResponseCode.UN_ERROR.getCode())
                .info(Constants.ResponseCode.UN_ERROR.getInfo())
                .build();
    }

}
