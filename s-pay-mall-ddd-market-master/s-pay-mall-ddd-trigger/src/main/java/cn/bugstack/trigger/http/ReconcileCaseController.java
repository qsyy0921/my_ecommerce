package cn.bugstack.trigger.http;

import cn.bugstack.api.response.Response;
import cn.bugstack.domain.order.model.entity.ReconcileCaseEntity;
import cn.bugstack.domain.order.service.IOrderService;
import cn.bugstack.types.common.Constants;
import com.alibaba.fastjson.JSON;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.List;

@Slf4j
@RestController
@CrossOrigin("*")
@RequestMapping("/api/v1/reconcile/")
public class ReconcileCaseController {

    @Resource
    private IOrderService orderService;

    @Value("${reconcile.admin-token:local-admin-token}")
    private String adminToken;

    @RequestMapping(value = "scan", method = RequestMethod.POST)
    public Response<Integer> scan(@RequestHeader(value = "x-admin-token", required = false) String token,
                                  @RequestHeader(value = "x-admin-operator", required = false) String operator) {
        if (!authorized(token)) {
            return noLogin();
        }
        try {
            int count = orderService.scanReconcileCases();
            audit(operator, "SCAN", "ALL", null, "count=" + count);
            return Response.<Integer>builder()
                    .code(Constants.ResponseCode.SUCCESS.getCode())
                    .info(Constants.ResponseCode.SUCCESS.getInfo())
                    .data(count)
                    .build();
        } catch (Exception e) {
            log.error("scan reconcile cases failed", e);
            return Response.<Integer>builder()
                    .code(Constants.ResponseCode.UN_ERROR.getCode())
                    .info(Constants.ResponseCode.UN_ERROR.getInfo())
                    .build();
        }
    }

    @RequestMapping(value = "case_list", method = RequestMethod.GET)
    public Response<List<ReconcileCaseEntity>> queryCaseList(@RequestParam(required = false) Integer caseStatus,
                                                             @RequestParam(required = false) String caseType,
                                                             @RequestParam(required = false) Long lastId,
                                                             @RequestParam(required = false, defaultValue = "20") Integer pageSize,
                                                             @RequestHeader(value = "x-admin-token", required = false) String token,
                                                             @RequestHeader(value = "x-admin-operator", required = false) String operator) {
        if (!authorized(token)) {
            return noLogin();
        }
        try {
            List<ReconcileCaseEntity> caseList = orderService.queryReconcileCaseList(caseStatus, caseType, lastId, pageSize);
            audit(operator, "QUERY", caseType, "caseStatus=" + caseStatus + ", lastId=" + lastId + ", pageSize=" + pageSize, "count=" + caseList.size());
            return Response.<List<ReconcileCaseEntity>>builder()
                    .code(Constants.ResponseCode.SUCCESS.getCode())
                    .info(Constants.ResponseCode.SUCCESS.getInfo())
                    .data(caseList)
                    .build();
        } catch (Exception e) {
            log.error("query reconcile case list failed", e);
            return Response.<List<ReconcileCaseEntity>>builder()
                    .code(Constants.ResponseCode.UN_ERROR.getCode())
                    .info(Constants.ResponseCode.UN_ERROR.getInfo())
                    .build();
        }
    }

    @RequestMapping(value = "handle", method = RequestMethod.POST)
    public Response<Boolean> handle(@RequestBody HandleRequest request,
                                    @RequestHeader(value = "x-admin-token", required = false) String token,
                                    @RequestHeader(value = "x-admin-operator", required = false) String operator) {
        if (!authorized(token)) {
            return noLogin();
        }
        try {
            String handler = resolveOperator(operator, request.getHandler());
            boolean result = orderService.handleReconcileCase(
                    request.getCaseNo(),
                    null == request.getCaseStatus() ? 1 : request.getCaseStatus(),
                    handler,
                    request.getHandleNote());
            audit(handler, "HANDLE", request.getCaseNo(), JSON.toJSONString(request), "result=" + result);
            return Response.<Boolean>builder()
                    .code(Constants.ResponseCode.SUCCESS.getCode())
                    .info(Constants.ResponseCode.SUCCESS.getInfo())
                    .data(result)
                    .build();
        } catch (Exception e) {
            log.error("handle reconcile case failed caseNo:{}", request.getCaseNo(), e);
            return Response.<Boolean>builder()
                    .code(Constants.ResponseCode.UN_ERROR.getCode())
                    .info(Constants.ResponseCode.UN_ERROR.getInfo())
                    .build();
        }
    }

    @RequestMapping(value = "batch_handle", method = RequestMethod.POST)
    public Response<Integer> batchHandle(@RequestBody BatchHandleRequest request,
                                         @RequestHeader(value = "x-admin-token", required = false) String token,
                                         @RequestHeader(value = "x-admin-operator", required = false) String operator) {
        if (!authorized(token)) {
            return noLogin();
        }
        try {
            String handler = resolveOperator(operator, request.getHandler());
            int count = 0;
            if (null != request.getCaseNoList()) {
                for (String caseNo : request.getCaseNoList()) {
                    boolean result = orderService.handleReconcileCase(
                            caseNo,
                            null == request.getCaseStatus() ? 1 : request.getCaseStatus(),
                            handler,
                            request.getHandleNote());
                    if (result) {
                        count++;
                    }
                }
            }
            audit(handler, "BATCH_HANDLE", null, JSON.toJSONString(request), "count=" + count);
            return Response.<Integer>builder()
                    .code(Constants.ResponseCode.SUCCESS.getCode())
                    .info(Constants.ResponseCode.SUCCESS.getInfo())
                    .data(count)
                    .build();
        } catch (Exception e) {
            log.error("batch handle reconcile case failed", e);
            return Response.<Integer>builder()
                    .code(Constants.ResponseCode.UN_ERROR.getCode())
                    .info(Constants.ResponseCode.UN_ERROR.getInfo())
                    .build();
        }
    }

    @RequestMapping(value = "replay", method = RequestMethod.POST)
    public Response<Boolean> replay(@RequestBody ReplayRequest request,
                                    @RequestHeader(value = "x-admin-token", required = false) String token,
                                    @RequestHeader(value = "x-admin-operator", required = false) String operator) {
        if (!authorized(token)) {
            return noLogin();
        }
        try {
            String handler = resolveOperator(operator, request.getOperator());
            boolean result = orderService.replayReconcileCase(request.getCaseNo(), handler);
            audit(handler, "REPLAY", request.getCaseNo(), JSON.toJSONString(request), "result=" + result);
            return Response.<Boolean>builder()
                    .code(Constants.ResponseCode.SUCCESS.getCode())
                    .info(Constants.ResponseCode.SUCCESS.getInfo())
                    .data(result)
                    .build();
        } catch (Exception e) {
            log.error("replay reconcile case failed caseNo:{}", request.getCaseNo(), e);
            return Response.<Boolean>builder()
                    .code(Constants.ResponseCode.UN_ERROR.getCode())
                    .info(Constants.ResponseCode.UN_ERROR.getInfo())
                    .build();
        }
    }

    @RequestMapping(value = "batch_replay", method = RequestMethod.POST)
    public Response<Integer> batchReplay(@RequestBody BatchReplayRequest request,
                                         @RequestHeader(value = "x-admin-token", required = false) String token,
                                         @RequestHeader(value = "x-admin-operator", required = false) String operator) {
        if (!authorized(token)) {
            return noLogin();
        }
        try {
            String handler = resolveOperator(operator, request.getOperator());
            int count = 0;
            if (null != request.getCaseNoList()) {
                for (String caseNo : request.getCaseNoList()) {
                    if (orderService.replayReconcileCase(caseNo, handler)) {
                        count++;
                    }
                }
            }
            audit(handler, "BATCH_REPLAY", null, JSON.toJSONString(request), "count=" + count);
            return Response.<Integer>builder()
                    .code(Constants.ResponseCode.SUCCESS.getCode())
                    .info(Constants.ResponseCode.SUCCESS.getInfo())
                    .data(count)
                    .build();
        } catch (Exception e) {
            log.error("batch replay reconcile case failed", e);
            return Response.<Integer>builder()
                    .code(Constants.ResponseCode.UN_ERROR.getCode())
                    .info(Constants.ResponseCode.UN_ERROR.getInfo())
                    .build();
        }
    }

    @RequestMapping(value = "import_bill", method = RequestMethod.POST)
    public Response<Integer> importBill(@RequestBody String csvText,
                                        @RequestHeader(value = "x-admin-token", required = false) String token,
                                        @RequestHeader(value = "x-admin-operator", required = false) String operator) {
        if (!authorized(token)) {
            return noLogin();
        }
        try {
            int count = orderService.importThirdPartyBillCsv(csvText);
            audit(operator, "IMPORT_BILL", "THIRD_PARTY_BILL", null == csvText ? null : csvText.substring(0, Math.min(csvText.length(), 1024)), "count=" + count);
            return Response.<Integer>builder()
                    .code(Constants.ResponseCode.SUCCESS.getCode())
                    .info(Constants.ResponseCode.SUCCESS.getInfo())
                    .data(count)
                    .build();
        } catch (Exception e) {
            log.error("import third party bill failed", e);
            return Response.<Integer>builder()
                    .code(Constants.ResponseCode.UN_ERROR.getCode())
                    .info(Constants.ResponseCode.UN_ERROR.getInfo())
                    .build();
        }
    }

    @RequestMapping(value = "alert/webhook", method = RequestMethod.POST)
    public String alertWebhook(@RequestBody String body) {
        log.warn("alertmanager webhook received body:{}", body);
        return "success";
    }

    private boolean authorized(String token) {
        return null != token && token.equals(adminToken);
    }

    private <T> Response<T> noLogin() {
        return Response.<T>builder()
                .code(Constants.ResponseCode.NO_LOGIN.getCode())
                .info(Constants.ResponseCode.NO_LOGIN.getInfo())
                .build();
    }

    private String resolveOperator(String headerOperator, String requestOperator) {
        if (null != headerOperator && !headerOperator.trim().isEmpty()) {
            return headerOperator.trim();
        }
        if (null != requestOperator && !requestOperator.trim().isEmpty()) {
            return requestOperator.trim();
        }
        return "local-admin";
    }

    private void audit(String operator, String operationType, String bizId, String requestBody, String result) {
        try {
            orderService.recordReconcileOperation(resolveOperator(operator, null), operationType, bizId, requestBody, result);
        } catch (Exception e) {
            log.warn("record reconcile operation failed operationType:{} bizId:{}", operationType, bizId, e);
        }
    }

    @Data
    public static class HandleRequest {
        private String caseNo;
        private Integer caseStatus;
        private String handler;
        private String handleNote;
    }

    @Data
    public static class BatchHandleRequest {
        private List<String> caseNoList;
        private Integer caseStatus;
        private String handler;
        private String handleNote;
    }

    @Data
    public static class ReplayRequest {
        private String caseNo;
        private String operator;
    }

    @Data
    public static class BatchReplayRequest {
        private List<String> caseNoList;
        private String operator;
    }

}
