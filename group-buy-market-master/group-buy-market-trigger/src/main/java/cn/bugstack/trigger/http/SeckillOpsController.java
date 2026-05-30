package cn.bugstack.trigger.http;

import cn.bugstack.api.response.Response;
import cn.bugstack.domain.seckill.adapter.port.ISeckillManualCompensationAuditPort;
import cn.bugstack.domain.seckill.adapter.port.ISeckillManualCompensationPort;
import cn.bugstack.domain.seckill.model.entity.SeckillManualCompensationLogEntity;
import cn.bugstack.domain.seckill.model.entity.SeckillManualMessageEntity;
import cn.bugstack.types.enums.ResponseCode;
import com.alibaba.fastjson.JSON;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
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
@RequestMapping("/api/v1/gbm/seckill/ops/")
public class SeckillOpsController {

    @Resource
    private ISeckillManualCompensationPort seckillManualCompensationPort;
    @Resource
    private ISeckillManualCompensationAuditPort seckillManualCompensationAuditPort;

    @Value("${app.seckill.admin-token:local-admin-token}")
    private String adminToken;

    @RequestMapping(value = "manual_messages", method = RequestMethod.GET)
    public Response<List<SeckillManualMessageEntity>> queryManualMessages(
            @RequestParam(required = false, defaultValue = "20") Integer limit,
            @RequestHeader(value = "x-admin-token", required = false) String token,
            @RequestHeader(value = "x-admin-operator", required = false) String operator) {
        if (!authorized(token)) {
            return denied();
        }
        int safeLimit = null == limit ? 20 : limit;
        try {
            List<SeckillManualMessageEntity> messages = seckillManualCompensationPort.queryManualMessages(safeLimit);
            recordAudit(SeckillManualCompensationLogEntity.builder()
                    .operator(operator(operator))
                    .operationType(SeckillManualCompensationLogEntity.OPERATION_QUERY)
                    .requestLimit(safeLimit)
                    .manualStreamKey(seckillManualCompensationPort.manualStreamKey())
                    .resultCount(messages.size())
                    .success(1)
                    .build());
            return Response.<List<SeckillManualMessageEntity>>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(messages)
                    .build();
        } catch (Exception e) {
            log.error("query seckill manual messages failed", e);
            recordAudit(SeckillManualCompensationLogEntity.builder()
                    .operator(operator(operator))
                    .operationType(SeckillManualCompensationLogEntity.OPERATION_QUERY)
                    .requestLimit(safeLimit)
                    .manualStreamKey(seckillManualCompensationPort.manualStreamKey())
                    .success(0)
                    .errorMessage(e.getMessage())
                    .build());
            return Response.<List<SeckillManualMessageEntity>>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info(ResponseCode.UN_ERROR.getInfo())
                    .build();
        }
    }

    @RequestMapping(value = "replay_manual", method = RequestMethod.POST)
    public Response<Integer> replayManualMessages(@RequestBody ReplayManualRequest request,
                                                  @RequestHeader(value = "x-admin-token", required = false) String token,
                                                  @RequestHeader(value = "x-admin-operator", required = false) String operator) {
        if (!authorized(token)) {
            return denied();
        }
        ReplayManualRequest safeRequest = null == request ? new ReplayManualRequest() : request;
        int safeLimit = null == safeRequest.getLimit() ? 20 : safeRequest.getLimit();
        try {
            int count = seckillManualCompensationPort.replayManualMessages(safeRequest.getMessageIds(), safeLimit);
            log.warn("seckill manual messages replayed operator:{} count:{} request:{}",
                    operator(operator), count, JSON.toJSONString(safeRequest));
            recordAudit(SeckillManualCompensationLogEntity.builder()
                    .operator(operator(operator))
                    .operationType(SeckillManualCompensationLogEntity.OPERATION_REPLAY)
                    .messageIds(JSON.toJSONString(safeRequest.getMessageIds()))
                    .requestLimit(safeLimit)
                    .manualStreamKey(seckillManualCompensationPort.manualStreamKey())
                    .resultCount(count)
                    .success(1)
                    .build());
            return Response.<Integer>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(count)
                    .build();
        } catch (Exception e) {
            log.error("replay seckill manual messages failed request:{}", JSON.toJSONString(safeRequest), e);
            recordAudit(SeckillManualCompensationLogEntity.builder()
                    .operator(operator(operator))
                    .operationType(SeckillManualCompensationLogEntity.OPERATION_REPLAY)
                    .messageIds(JSON.toJSONString(safeRequest.getMessageIds()))
                    .requestLimit(safeLimit)
                    .manualStreamKey(seckillManualCompensationPort.manualStreamKey())
                    .success(0)
                    .errorMessage(e.getMessage())
                    .build());
            return Response.<Integer>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info(ResponseCode.UN_ERROR.getInfo())
                    .build();
        }
    }

    @RequestMapping(value = "manual_logs", method = RequestMethod.GET)
    public Response<List<SeckillManualCompensationLogEntity>> queryManualLogs(
            @RequestParam(required = false, defaultValue = "50") Integer limit,
            @RequestHeader(value = "x-admin-token", required = false) String token) {
        if (!authorized(token)) {
            return denied();
        }
        try {
            List<SeckillManualCompensationLogEntity> logs = seckillManualCompensationAuditPort.queryRecentLogs(null == limit ? 50 : limit);
            return Response.<List<SeckillManualCompensationLogEntity>>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(logs)
                    .build();
        } catch (Exception e) {
            log.error("query seckill manual compensation logs failed", e);
            return Response.<List<SeckillManualCompensationLogEntity>>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info(ResponseCode.UN_ERROR.getInfo())
                    .build();
        }
    }

    private boolean authorized(String token) {
        return StringUtils.isNotBlank(token) && token.equals(adminToken);
    }

    private <T> Response<T> denied() {
        return Response.<T>builder()
                .code(ResponseCode.ILLEGAL_PARAMETER.getCode())
                .info("unauthorized")
                .build();
    }

    private String operator(String operator) {
        return StringUtils.defaultIfBlank(operator, "local-admin");
    }

    private void recordAudit(SeckillManualCompensationLogEntity logEntity) {
        try {
            seckillManualCompensationAuditPort.record(logEntity);
        } catch (Exception e) {
            log.error("record seckill manual compensation audit failed log:{}", JSON.toJSONString(logEntity), e);
        }
    }

    @Data
    public static class ReplayManualRequest {
        private List<String> messageIds;
        private Integer limit;
    }

}
