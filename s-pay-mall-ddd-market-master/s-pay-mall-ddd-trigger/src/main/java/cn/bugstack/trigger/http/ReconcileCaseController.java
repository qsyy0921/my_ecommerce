package cn.bugstack.trigger.http;

import cn.bugstack.api.response.Response;
import cn.bugstack.domain.order.model.entity.ReconcileCaseEntity;
import cn.bugstack.domain.order.model.entity.ReconcileOperationLogEntity;
import cn.bugstack.domain.order.service.IOrderReconcileService;
import cn.bugstack.trigger.support.ReconcileAdminSupport;
import cn.bugstack.types.common.Constants;
import com.alibaba.fastjson.JSON;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
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
    private IOrderReconcileService orderReconcileService;

    @Resource
    private ReconcileAdminSupport adminSupport;

    @RequestMapping(value = "scan", method = RequestMethod.POST)
    public Response<Integer> scan(@RequestHeader(value = "x-admin-token", required = false) String token,
                                  @RequestHeader(value = "x-admin-operator", required = false) String operator) {
        if (!adminSupport.authorized(token)) {
            return adminSupport.noLogin();
        }
        try {
            int count = orderReconcileService.scanReconcileCases();
            adminSupport.audit(operator, "SCAN", "ALL", null, "count=" + count);
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
        if (!adminSupport.authorized(token)) {
            return adminSupport.noLogin();
        }
        try {
            List<ReconcileCaseEntity> caseList = orderReconcileService.queryReconcileCaseList(caseStatus, caseType, lastId, pageSize);
            adminSupport.audit(operator, "QUERY", caseType, "caseStatus=" + caseStatus + ", lastId=" + lastId + ", pageSize=" + pageSize, "count=" + caseList.size());
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
        if (!adminSupport.authorized(token)) {
            return adminSupport.noLogin();
        }
        try {
            String handler = adminSupport.resolveOperator(operator, request.getHandler());
            boolean result = orderReconcileService.handleReconcileCase(
                    request.getCaseNo(),
                    null == request.getCaseStatus() ? 1 : request.getCaseStatus(),
                    handler,
                    request.getHandleNote());
            adminSupport.audit(handler, "HANDLE", request.getCaseNo(), JSON.toJSONString(request), "result=" + result);
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

    @RequestMapping(value = "confirm", method = RequestMethod.POST)
    public Response<Boolean> confirm(@RequestBody HandleRequest request,
                                     @RequestHeader(value = "x-admin-token", required = false) String token,
                                     @RequestHeader(value = "x-admin-operator", required = false) String operator) {
        return handleWithStatus(request, token, operator, "CONFIRM");
    }

    @RequestMapping(value = "ignore", method = RequestMethod.POST)
    public Response<Boolean> ignore(@RequestBody HandleRequest request,
                                    @RequestHeader(value = "x-admin-token", required = false) String token,
                                    @RequestHeader(value = "x-admin-operator", required = false) String operator) {
        return handleWithStatus(request, token, operator, "IGNORE");
    }

    @RequestMapping(value = "close", method = RequestMethod.POST)
    public Response<Boolean> close(@RequestBody HandleRequest request,
                                   @RequestHeader(value = "x-admin-token", required = false) String token,
                                   @RequestHeader(value = "x-admin-operator", required = false) String operator) {
        return handleWithStatus(request, token, operator, "CLOSE");
    }

    @RequestMapping(value = "remark", method = RequestMethod.POST)
    public Response<Boolean> remark(@RequestBody HandleRequest request,
                                    @RequestHeader(value = "x-admin-token", required = false) String token,
                                    @RequestHeader(value = "x-admin-operator", required = false) String operator) {
        if (!adminSupport.authorized(token)) {
            return adminSupport.noLogin();
        }
        try {
            String handler = adminSupport.resolveOperator(operator, request.getHandler());
            boolean result = orderReconcileService.remarkReconcileCase(request.getCaseNo(), handler, request.getHandleNote());
            adminSupport.audit(handler, "REMARK", request.getCaseNo(), JSON.toJSONString(request), "result=" + result);
            return Response.<Boolean>builder()
                    .code(Constants.ResponseCode.SUCCESS.getCode())
                    .info(Constants.ResponseCode.SUCCESS.getInfo())
                    .data(result)
                    .build();
        } catch (Exception e) {
            log.error("remark reconcile case failed caseNo:{}", request.getCaseNo(), e);
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
        if (!adminSupport.authorized(token)) {
            return adminSupport.noLogin();
        }
        try {
            String handler = adminSupport.resolveOperator(operator, request.getHandler());
            int count = 0;
            if (null != request.getCaseNoList()) {
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

    @RequestMapping(value = "operation_logs", method = RequestMethod.GET)
    public Response<List<ReconcileOperationLogEntity>> queryOperationLogs(@RequestParam(required = false) String bizId,
                                                                          @RequestParam(required = false) Long lastId,
                                                                          @RequestParam(required = false, defaultValue = "50") Integer pageSize,
                                                                          @RequestHeader(value = "x-admin-token", required = false) String token,
                                                                          @RequestHeader(value = "x-admin-operator", required = false) String operator) {
        if (!adminSupport.authorized(token)) {
            return adminSupport.noLogin();
        }
        try {
            List<ReconcileOperationLogEntity> operationLogList = orderReconcileService.queryReconcileOperationLogList(bizId, lastId, pageSize);
            adminSupport.audit(operator, "QUERY_LOG", bizId, "lastId=" + lastId + ", pageSize=" + pageSize, "count=" + operationLogList.size());
            return Response.<List<ReconcileOperationLogEntity>>builder()
                    .code(Constants.ResponseCode.SUCCESS.getCode())
                    .info(Constants.ResponseCode.SUCCESS.getInfo())
                    .data(operationLogList)
                    .build();
        } catch (Exception e) {
            log.error("query reconcile operation logs failed bizId:{}", bizId, e);
            return Response.<List<ReconcileOperationLogEntity>>builder()
                    .code(Constants.ResponseCode.UN_ERROR.getCode())
                    .info(Constants.ResponseCode.UN_ERROR.getInfo())
                    .build();
        }
    }

    @RequestMapping(value = "replay", method = RequestMethod.POST)
    public Response<Boolean> replay(@RequestBody ReplayRequest request,
                                    @RequestHeader(value = "x-admin-token", required = false) String token,
                                    @RequestHeader(value = "x-admin-operator", required = false) String operator) {
        if (!adminSupport.authorized(token)) {
            return adminSupport.noLogin();
        }
        try {
            String handler = adminSupport.resolveOperator(operator, request.getOperator());
            boolean result = orderReconcileService.replayReconcileCase(request.getCaseNo(), handler);
            adminSupport.audit(handler, "REPLAY", request.getCaseNo(), JSON.toJSONString(request), "result=" + result);
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
        if (!adminSupport.authorized(token)) {
            return adminSupport.noLogin();
        }
        try {
            String handler = adminSupport.resolveOperator(operator, request.getOperator());
            int count = 0;
            if (null != request.getCaseNoList()) {
                for (String caseNo : request.getCaseNoList()) {
                    boolean result = orderReconcileService.replayReconcileCase(caseNo, handler);
                    adminSupport.audit(handler, "BATCH_REPLAY_ITEM", caseNo, JSON.toJSONString(request), "result=" + result);
                    if (result) {
                        count++;
                    }
                }
            }
            adminSupport.audit(handler, "BATCH_REPLAY", null, JSON.toJSONString(request), "count=" + count);
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
        if (!adminSupport.authorized(token)) {
            return adminSupport.noLogin();
        }
        try {
            int count = orderReconcileService.importThirdPartyBillCsv(csvText);
            adminSupport.audit(operator, "IMPORT_BILL", "THIRD_PARTY_BILL", adminSupport.preview(csvText, 1024), "count=" + count);
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

    private Response<Boolean> handleWithStatus(HandleRequest request, String token, String operator, String operationType) {
        if (!adminSupport.authorized(token)) {
            return adminSupport.noLogin();
        }
        try {
            String handler = adminSupport.resolveOperator(operator, request.getHandler());
            boolean result;
            if ("CONFIRM".equals(operationType)) {
                result = orderReconcileService.confirmReconcileCase(request.getCaseNo(), handler, request.getHandleNote());
            } else if ("IGNORE".equals(operationType)) {
                result = orderReconcileService.ignoreReconcileCase(request.getCaseNo(), handler, request.getHandleNote());
            } else {
                result = orderReconcileService.closeReconcileCase(request.getCaseNo(), handler, request.getHandleNote());
            }
            adminSupport.audit(handler, operationType, request.getCaseNo(), JSON.toJSONString(request), "result=" + result);
            return Response.<Boolean>builder()
                    .code(Constants.ResponseCode.SUCCESS.getCode())
                    .info(Constants.ResponseCode.SUCCESS.getInfo())
                    .data(result)
                    .build();
        } catch (Exception e) {
            log.error("handle reconcile case failed operationType:{} caseNo:{}", operationType, request.getCaseNo(), e);
            return Response.<Boolean>builder()
                    .code(Constants.ResponseCode.UN_ERROR.getCode())
                    .info(Constants.ResponseCode.UN_ERROR.getInfo())
                    .build();
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
