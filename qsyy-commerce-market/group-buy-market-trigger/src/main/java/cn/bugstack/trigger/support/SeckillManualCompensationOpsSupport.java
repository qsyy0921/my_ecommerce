package cn.bugstack.trigger.support;

import cn.bugstack.api.dto.ReplaySeckillManualRequestDTO;
import cn.bugstack.api.dto.SeckillManualCompensationLogResponseDTO;
import cn.bugstack.api.dto.SeckillManualMessageResponseDTO;
import cn.bugstack.api.response.Response;
import cn.bugstack.domain.seckill.adapter.port.ISeckillManualCompensationAuditPort;
import cn.bugstack.domain.seckill.adapter.port.ISeckillManualCompensationPort;
import cn.bugstack.domain.seckill.model.entity.SeckillManualCompensationLogEntity;
import cn.bugstack.domain.seckill.model.entity.SeckillManualMessageEntity;
import cn.bugstack.types.enums.ResponseCode;
import com.alibaba.fastjson.JSON;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.List;

/**
 * Use case support for seckill manual compensation operations.
 */
@Slf4j
@Component
public class SeckillManualCompensationOpsSupport {

    @Resource
    private ISeckillManualCompensationPort seckillManualCompensationPort;
    @Resource
    private ISeckillManualCompensationAuditPort seckillManualCompensationAuditPort;
    @Resource
    private SeckillOpsAdminSupport adminSupport;
    @Resource
    private SeckillManualCompensationResponseAssembler responseAssembler;

    public Response<List<SeckillManualMessageResponseDTO>> queryManualMessages(Integer limit, String token, String operator) {
        if (!adminSupport.authorized(token)) {
            return adminSupport.denied();
        }
        int safeLimit = null == limit ? 20 : limit;
        String actualOperator = adminSupport.operator(operator);
        try {
            List<SeckillManualMessageEntity> messages = seckillManualCompensationPort.queryManualMessages(safeLimit);
            recordAudit(SeckillManualCompensationLogEntity.builder()
                    .operator(actualOperator)
                    .operationType(SeckillManualCompensationLogEntity.OPERATION_QUERY)
                    .requestLimit(safeLimit)
                    .manualStreamKey(seckillManualCompensationPort.manualStreamKey())
                    .resultCount(messages.size())
                    .success(1)
                    .build());
            return success(responseAssembler.toMessageResponses(messages));
        } catch (Exception e) {
            log.error("query seckill manual messages failed", e);
            recordAudit(SeckillManualCompensationLogEntity.builder()
                    .operator(actualOperator)
                    .operationType(SeckillManualCompensationLogEntity.OPERATION_QUERY)
                    .requestLimit(safeLimit)
                    .manualStreamKey(seckillManualCompensationPort.manualStreamKey())
                    .success(0)
                    .errorMessage(e.getMessage())
                    .build());
            return error();
        }
    }

    public Response<Integer> replayManualMessages(ReplaySeckillManualRequestDTO request, String token, String operator) {
        if (!adminSupport.authorized(token)) {
            return adminSupport.denied();
        }
        ReplaySeckillManualRequestDTO safeRequest = null == request ? new ReplaySeckillManualRequestDTO() : request;
        int safeLimit = null == safeRequest.getLimit() ? 20 : safeRequest.getLimit();
        String actualOperator = adminSupport.operator(operator);
        try {
            int count = seckillManualCompensationPort.replayManualMessages(safeRequest.getMessageIds(), safeLimit);
            log.warn("seckill manual messages replayed operator:{} count:{} request:{}",
                    actualOperator, count, JSON.toJSONString(safeRequest));
            recordAudit(SeckillManualCompensationLogEntity.builder()
                    .operator(actualOperator)
                    .operationType(SeckillManualCompensationLogEntity.OPERATION_REPLAY)
                    .messageIds(JSON.toJSONString(safeRequest.getMessageIds()))
                    .requestLimit(safeLimit)
                    .manualStreamKey(seckillManualCompensationPort.manualStreamKey())
                    .resultCount(count)
                    .success(1)
                    .build());
            return success(count);
        } catch (Exception e) {
            log.error("replay seckill manual messages failed request:{}", JSON.toJSONString(safeRequest), e);
            recordAudit(SeckillManualCompensationLogEntity.builder()
                    .operator(actualOperator)
                    .operationType(SeckillManualCompensationLogEntity.OPERATION_REPLAY)
                    .messageIds(JSON.toJSONString(safeRequest.getMessageIds()))
                    .requestLimit(safeLimit)
                    .manualStreamKey(seckillManualCompensationPort.manualStreamKey())
                    .success(0)
                    .errorMessage(e.getMessage())
                    .build());
            return error();
        }
    }

    public Response<List<SeckillManualCompensationLogResponseDTO>> queryManualLogs(Integer limit, String token) {
        if (!adminSupport.authorized(token)) {
            return adminSupport.denied();
        }
        try {
            List<SeckillManualCompensationLogEntity> logs = seckillManualCompensationAuditPort.queryRecentLogs(null == limit ? 50 : limit);
            return success(responseAssembler.toLogResponses(logs));
        } catch (Exception e) {
            log.error("query seckill manual compensation logs failed", e);
            return error();
        }
    }

    private void recordAudit(SeckillManualCompensationLogEntity logEntity) {
        try {
            seckillManualCompensationAuditPort.record(logEntity);
        } catch (Exception e) {
            log.error("record seckill manual compensation audit failed log:{}", JSON.toJSONString(logEntity), e);
        }
    }

    private <T> Response<T> success(T data) {
        return Response.<T>builder()
                .code(ResponseCode.SUCCESS.getCode())
                .info(ResponseCode.SUCCESS.getInfo())
                .data(data)
                .build();
    }

    private <T> Response<T> error() {
        return Response.<T>builder()
                .code(ResponseCode.UN_ERROR.getCode())
                .info(ResponseCode.UN_ERROR.getInfo())
                .build();
    }

}
