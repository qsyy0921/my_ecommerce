package cn.bugstack.trigger.http;

import cn.bugstack.api.response.Response;
import cn.bugstack.domain.message.model.entity.MessageRecordEntity;
import cn.bugstack.domain.message.service.IMessageRecordService;
import cn.bugstack.types.enums.ResponseCode;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
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
@RequestMapping("/api/v1/gbm/mq/ops/")
public class MqOpsController {

    @Value("${app.seckill.admin-token:local-admin-token}")
    private String adminToken;

    @Resource
    private IMessageRecordService messageRecordService;

    @RequestMapping(value = "failed_messages", method = RequestMethod.GET)
    public Response<List<MessageRecordEntity>> failedMessages(@RequestParam(required = false, defaultValue = "20") Integer limit,
                                                              @RequestHeader(value = "x-admin-token", required = false) String token) {
        if (!authorized(token)) {
            return Response.<List<MessageRecordEntity>>builder()
                    .code(ResponseCode.ILLEGAL_PARAMETER.getCode())
                    .info("invalid admin token")
                    .build();
        }
        List<MessageRecordEntity> messages = messageRecordService.queryFailedMessages(null == limit ? 20 : limit);
        return Response.<List<MessageRecordEntity>>builder()
                .code(ResponseCode.SUCCESS.getCode())
                .info(ResponseCode.SUCCESS.getInfo())
                .data(messages)
                .build();
    }

    @RequestMapping(value = "mark_handled", method = RequestMethod.POST)
    public Response<Boolean> markHandled(@RequestBody MarkHandledRequest request,
                                         @RequestHeader(value = "x-admin-token", required = false) String token,
                                         @RequestHeader(value = "x-admin-operator", required = false) String operator) {
        if (!authorized(token)) {
            return Response.<Boolean>builder()
                    .code(ResponseCode.ILLEGAL_PARAMETER.getCode())
                    .info("invalid admin token")
                    .build();
        }
        if (null == request || StringUtils.isBlank(request.getMessageId())) {
            return Response.<Boolean>builder()
                    .code(ResponseCode.ILLEGAL_PARAMETER.getCode())
                    .info(ResponseCode.ILLEGAL_PARAMETER.getInfo())
                    .build();
        }
        messageRecordService.consumeSuccess(request.getMessageId());
        log.warn("mq failed message marked handled operator:{} messageId:{}",
                StringUtils.defaultIfBlank(operator, "local-admin"), request.getMessageId());
        return Response.<Boolean>builder()
                .code(ResponseCode.SUCCESS.getCode())
                .info(ResponseCode.SUCCESS.getInfo())
                .data(true)
                .build();
    }

    @RequestMapping(value = "retry_producer_failed", method = RequestMethod.POST)
    public Response<Integer> retryProducerFailed(@RequestHeader(value = "x-admin-token", required = false) String token,
                                                 @RequestHeader(value = "x-admin-operator", required = false) String operator,
                                                 @RequestParam(required = false, defaultValue = "20") Integer limit) {
        if (!authorized(token)) {
            return Response.<Integer>builder()
                    .code(ResponseCode.ILLEGAL_PARAMETER.getCode())
                    .info("invalid admin token")
                    .build();
        }
        int count = messageRecordService.retryProducerFailedMessages(null == limit ? 20 : limit);
        log.warn("mq producer failed messages manual retry operator:{} count:{}",
                StringUtils.defaultIfBlank(operator, "local-admin"), count);
        return Response.<Integer>builder()
                .code(ResponseCode.SUCCESS.getCode())
                .info(ResponseCode.SUCCESS.getInfo())
                .data(count)
                .build();
    }

    private boolean authorized(String token) {
        return StringUtils.isNotBlank(token) && token.equals(adminToken);
    }

    @Data
    public static class MarkHandledRequest {
        private String messageId;
    }

}
