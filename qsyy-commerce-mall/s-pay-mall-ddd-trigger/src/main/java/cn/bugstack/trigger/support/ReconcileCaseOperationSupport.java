package cn.bugstack.trigger.support;

import cn.bugstack.api.response.Response;
import cn.bugstack.domain.order.service.IOrderReconcileService;
import cn.bugstack.types.common.Constants;
import com.alibaba.fastjson.JSON;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

@Slf4j
@Component
public class ReconcileCaseOperationSupport {

    @Resource
    private IOrderReconcileService orderReconcileService;

    @Resource
    private ReconcileAdminSupport adminSupport;

    public Response<Boolean> handle(ReconcileHandleRequest request, String token, String operator) {
        if (!adminSupport.authorized(token)) {
            return adminSupport.noLogin();
        }
        try {
            String handler = adminSupport.resolveOperator(operator, null == request ? null : request.getHandler());
            boolean result = orderReconcileService.handleReconcileCase(
                    request.getCaseNo(),
                    null == request.getCaseStatus() ? 1 : request.getCaseStatus(),
                    handler,
                    request.getHandleNote());
            adminSupport.audit(handler, "HANDLE", request.getCaseNo(), JSON.toJSONString(request), "result=" + result);
            return success(result);
        } catch (Exception e) {
            log.error("handle reconcile case failed caseNo:{}", null == request ? null : request.getCaseNo(), e);
            return error();
        }
    }

    public Response<Boolean> confirm(ReconcileHandleRequest request, String token, String operator) {
        return handleWithStatus(request, token, operator, "CONFIRM");
    }

    public Response<Boolean> ignore(ReconcileHandleRequest request, String token, String operator) {
        return handleWithStatus(request, token, operator, "IGNORE");
    }

    public Response<Boolean> close(ReconcileHandleRequest request, String token, String operator) {
        return handleWithStatus(request, token, operator, "CLOSE");
    }

    public Response<Boolean> remark(ReconcileHandleRequest request, String token, String operator) {
        if (!adminSupport.authorized(token)) {
            return adminSupport.noLogin();
        }
        try {
            String handler = adminSupport.resolveOperator(operator, null == request ? null : request.getHandler());
            boolean result = orderReconcileService.remarkReconcileCase(request.getCaseNo(), handler, request.getHandleNote());
            adminSupport.audit(handler, "REMARK", request.getCaseNo(), JSON.toJSONString(request), "result=" + result);
            return success(result);
        } catch (Exception e) {
            log.error("remark reconcile case failed caseNo:{}", null == request ? null : request.getCaseNo(), e);
            return error();
        }
    }

    public Response<Integer> batchHandle(ReconcileBatchHandleRequest request, String token, String operator) {
        if (!adminSupport.authorized(token)) {
            return adminSupport.noLogin();
        }
        try {
            String handler = adminSupport.resolveOperator(operator, null == request ? null : request.getHandler());
            int count = 0;
            if (null != request && null != request.getCaseNoList()) {
                for (String caseNo : request.getCaseNoList()) {
                    boolean result = orderReconcileService.handleReconcileCase(
                            caseNo,
                            null == request.getCaseStatus() ? 1 : request.getCaseStatus(),
                            handler,
                            request.getHandleNote());
                    adminSupport.audit(handler, "BATCH_HANDLE_ITEM", caseNo, "caseStatus=" + request.getCaseStatus(), "result=" + result);
                    if (result) {
                        count++;
                    }
                }
            }
            adminSupport.audit(handler, "BATCH_HANDLE", null, JSON.toJSONString(request), "count=" + count);
            return success(count);
        } catch (Exception e) {
            log.error("batch handle reconcile case failed", e);
            return error();
        }
    }

    private Response<Boolean> handleWithStatus(ReconcileHandleRequest request, String token, String operator, String operationType) {
        if (!adminSupport.authorized(token)) {
            return adminSupport.noLogin();
        }
        try {
            String handler = adminSupport.resolveOperator(operator, null == request ? null : request.getHandler());
            boolean result;
            if ("CONFIRM".equals(operationType)) {
                result = orderReconcileService.confirmReconcileCase(request.getCaseNo(), handler, request.getHandleNote());
            } else if ("IGNORE".equals(operationType)) {
                result = orderReconcileService.ignoreReconcileCase(request.getCaseNo(), handler, request.getHandleNote());
            } else {
                result = orderReconcileService.closeReconcileCase(request.getCaseNo(), handler, request.getHandleNote());
            }
            adminSupport.audit(handler, operationType, request.getCaseNo(), JSON.toJSONString(request), "result=" + result);
            return success(result);
        } catch (Exception e) {
            log.error("handle reconcile case failed operationType:{} caseNo:{}", operationType, null == request ? null : request.getCaseNo(), e);
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
