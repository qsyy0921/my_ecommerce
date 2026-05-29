package cn.bugstack.trigger.http;

import cn.bugstack.api.response.Response;
import cn.bugstack.domain.seckill.adapter.port.ISeckillManualCompensationPort;
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

    @Value("${app.seckill.admin-token:local-admin-token}")
    private String adminToken;

    @RequestMapping(value = "manual_messages", method = RequestMethod.GET)
    public Response<List<SeckillManualMessageEntity>> queryManualMessages(
            @RequestParam(required = false, defaultValue = "20") Integer limit,
            @RequestHeader(value = "x-admin-token", required = false) String token) {
        if (!authorized(token)) {
            return denied();
        }
        try {
            List<SeckillManualMessageEntity> messages = seckillManualCompensationPort.queryManualMessages(null == limit ? 20 : limit);
            return Response.<List<SeckillManualMessageEntity>>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(messages)
                    .build();
        } catch (Exception e) {
            log.error("query seckill manual messages failed", e);
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
        try {
            int count = seckillManualCompensationPort.replayManualMessages(request.getMessageIds(), null == request.getLimit() ? 20 : request.getLimit());
            log.warn("seckill manual messages replayed operator:{} count:{} request:{}",
                    StringUtils.defaultIfBlank(operator, "local-admin"), count, JSON.toJSONString(request));
            return Response.<Integer>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(count)
                    .build();
        } catch (Exception e) {
            log.error("replay seckill manual messages failed request:{}", JSON.toJSONString(request), e);
            return Response.<Integer>builder()
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

    @Data
    public static class ReplayManualRequest {
        private List<String> messageIds;
        private Integer limit;
    }

}
