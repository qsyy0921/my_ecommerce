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
public class ReconcileCaseReplaySupport {

    @Resource
    private IOrderReconcileService orderReconcileService;

    @Resource
    private ReconcileAdminSupport adminSupport;

    public Response<Boolean> replay(ReconcileReplayRequest request, String token, String operator) {
        if (!adminSupport.authorized(token)) {
            return adminSupport.noLogin();
        }
        try {
            String handler = adminSupport.resolveOperator(operator, null == request ? null : request.getOperator());
            boolean result = orderReconcileService.replayReconcileCase(request.getCaseNo(), handler);
            adminSupport.audit(handler, "REPLAY", request.getCaseNo(), JSON.toJSONString(request), "result=" + result);
            return success(result);
        } catch (Exception e) {
            log.error("replay reconcile case failed caseNo:{}", null == request ? null : request.getCaseNo(), e);
            return error();
        }
    }

    public Response<Integer> batchReplay(ReconcileBatchReplayRequest request, String token, String operator) {
        if (!adminSupport.authorized(token)) {
            return adminSupport.noLogin();
        }
        try {
            String handler = adminSupport.resolveOperator(operator, null == request ? null : request.getOperator());
            int count = 0;
            if (null != request && null != request.getCaseNoList()) {
                for (String caseNo : request.getCaseNoList()) {
                    boolean result = orderReconcileService.replayReconcileCase(caseNo, handler);
                    adminSupport.audit(handler, "BATCH_REPLAY_ITEM", caseNo, JSON.toJSONString(request), "result=" + result);
                    if (result) {
                        count++;
                    }
                }
            }
            adminSupport.audit(handler, "BATCH_REPLAY", null, JSON.toJSONString(request), "count=" + count);
            return success(count);
        } catch (Exception e) {
            log.error("batch replay reconcile case failed", e);
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
