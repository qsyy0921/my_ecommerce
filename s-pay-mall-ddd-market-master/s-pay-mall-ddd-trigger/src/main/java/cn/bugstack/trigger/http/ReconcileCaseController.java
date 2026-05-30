package cn.bugstack.trigger.http;

import cn.bugstack.api.dto.ReconcileCaseResponseDTO;
import cn.bugstack.api.dto.ReconcileOperationLogResponseDTO;
import cn.bugstack.api.response.Response;
import cn.bugstack.trigger.support.ReconcileAlertWebhookSupport;
import cn.bugstack.trigger.support.ReconcileBatchHandleRequest;
import cn.bugstack.trigger.support.ReconcileBatchReplayRequest;
import cn.bugstack.trigger.support.ReconcileBillImportSupport;
import cn.bugstack.trigger.support.ReconcileCaseOperationSupport;
import cn.bugstack.trigger.support.ReconcileCaseQueryEndpointSupport;
import cn.bugstack.trigger.support.ReconcileCaseReplaySupport;
import cn.bugstack.trigger.support.ReconcileHandleRequest;
import cn.bugstack.trigger.support.ReconcileReplayRequest;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.List;

@RestController
@CrossOrigin("*")
@RequestMapping("/api/v1/reconcile/")
public class ReconcileCaseController {

    @Resource
    private ReconcileCaseQueryEndpointSupport queryEndpointSupport;

    @Resource
    private ReconcileCaseOperationSupport operationSupport;

    @Resource
    private ReconcileCaseReplaySupport replaySupport;

    @Resource
    private ReconcileBillImportSupport billImportSupport;

    @Resource
    private ReconcileAlertWebhookSupport alertWebhookSupport;

    @RequestMapping(value = "scan", method = RequestMethod.POST)
    public Response<Integer> scan(@RequestHeader(value = "x-admin-token", required = false) String token,
                                  @RequestHeader(value = "x-admin-operator", required = false) String operator) {
        return queryEndpointSupport.scan(token, operator);
    }

    @RequestMapping(value = "case_list", method = RequestMethod.GET)
    public Response<List<ReconcileCaseResponseDTO>> queryCaseList(@RequestParam(required = false) Integer caseStatus,
                                                                  @RequestParam(required = false) String caseType,
                                                                  @RequestParam(required = false) Long lastId,
                                                                  @RequestParam(required = false, defaultValue = "20") Integer pageSize,
                                                                  @RequestHeader(value = "x-admin-token", required = false) String token,
                                                                  @RequestHeader(value = "x-admin-operator", required = false) String operator) {
        return queryEndpointSupport.queryCaseList(caseStatus, caseType, lastId, pageSize, token, operator);
    }

    @RequestMapping(value = "handle", method = RequestMethod.POST)
    public Response<Boolean> handle(@RequestBody ReconcileHandleRequest request,
                                    @RequestHeader(value = "x-admin-token", required = false) String token,
                                    @RequestHeader(value = "x-admin-operator", required = false) String operator) {
        return operationSupport.handle(request, token, operator);
    }

    @RequestMapping(value = "confirm", method = RequestMethod.POST)
    public Response<Boolean> confirm(@RequestBody ReconcileHandleRequest request,
                                     @RequestHeader(value = "x-admin-token", required = false) String token,
                                     @RequestHeader(value = "x-admin-operator", required = false) String operator) {
        return operationSupport.confirm(request, token, operator);
    }

    @RequestMapping(value = "ignore", method = RequestMethod.POST)
    public Response<Boolean> ignore(@RequestBody ReconcileHandleRequest request,
                                    @RequestHeader(value = "x-admin-token", required = false) String token,
                                    @RequestHeader(value = "x-admin-operator", required = false) String operator) {
        return operationSupport.ignore(request, token, operator);
    }

    @RequestMapping(value = "close", method = RequestMethod.POST)
    public Response<Boolean> close(@RequestBody ReconcileHandleRequest request,
                                   @RequestHeader(value = "x-admin-token", required = false) String token,
                                   @RequestHeader(value = "x-admin-operator", required = false) String operator) {
        return operationSupport.close(request, token, operator);
    }

    @RequestMapping(value = "remark", method = RequestMethod.POST)
    public Response<Boolean> remark(@RequestBody ReconcileHandleRequest request,
                                    @RequestHeader(value = "x-admin-token", required = false) String token,
                                    @RequestHeader(value = "x-admin-operator", required = false) String operator) {
        return operationSupport.remark(request, token, operator);
    }

    @RequestMapping(value = "batch_handle", method = RequestMethod.POST)
    public Response<Integer> batchHandle(@RequestBody ReconcileBatchHandleRequest request,
                                         @RequestHeader(value = "x-admin-token", required = false) String token,
                                         @RequestHeader(value = "x-admin-operator", required = false) String operator) {
        return operationSupport.batchHandle(request, token, operator);
    }

    @RequestMapping(value = "operation_logs", method = RequestMethod.GET)
    public Response<List<ReconcileOperationLogResponseDTO>> queryOperationLogs(@RequestParam(required = false) String bizId,
                                                                               @RequestParam(required = false) Long lastId,
                                                                               @RequestParam(required = false, defaultValue = "50") Integer pageSize,
                                                                               @RequestHeader(value = "x-admin-token", required = false) String token,
                                                                               @RequestHeader(value = "x-admin-operator", required = false) String operator) {
        return queryEndpointSupport.queryOperationLogs(bizId, lastId, pageSize, token, operator);
    }

    @RequestMapping(value = "replay", method = RequestMethod.POST)
    public Response<Boolean> replay(@RequestBody ReconcileReplayRequest request,
                                    @RequestHeader(value = "x-admin-token", required = false) String token,
                                    @RequestHeader(value = "x-admin-operator", required = false) String operator) {
        return replaySupport.replay(request, token, operator);
    }

    @RequestMapping(value = "batch_replay", method = RequestMethod.POST)
    public Response<Integer> batchReplay(@RequestBody ReconcileBatchReplayRequest request,
                                         @RequestHeader(value = "x-admin-token", required = false) String token,
                                         @RequestHeader(value = "x-admin-operator", required = false) String operator) {
        return replaySupport.batchReplay(request, token, operator);
    }

    @RequestMapping(value = "import_bill", method = RequestMethod.POST)
    public Response<Integer> importBill(@RequestBody String csvText,
                                        @RequestHeader(value = "x-admin-token", required = false) String token,
                                        @RequestHeader(value = "x-admin-operator", required = false) String operator) {
        return billImportSupport.importBill(csvText, token, operator);
    }

    @RequestMapping(value = "alert/webhook", method = RequestMethod.POST)
    public String alertWebhook(@RequestBody String body) {
        return alertWebhookSupport.receive(body);
    }

}
