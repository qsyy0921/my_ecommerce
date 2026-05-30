package cn.bugstack.trigger.support;

import cn.bugstack.api.dto.MarkMqMessageHandledRequestDTO;
import cn.bugstack.api.dto.MqFailedMessageResponseDTO;
import cn.bugstack.api.response.Response;
import cn.bugstack.domain.message.model.entity.MessageRecordEntity;
import cn.bugstack.domain.message.service.IMessageRecordService;
import cn.bugstack.types.enums.ResponseCode;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.List;

/**
 * Use case support for MQ operations endpoints.
 */
@Slf4j
@Component
public class MqOpsSupport {

    @Resource
    private IMessageRecordService messageRecordService;
    @Resource
    private MqOpsAdminSupport adminSupport;
    @Resource
    private MqOpsResponseAssembler responseAssembler;

    public Response<List<MqFailedMessageResponseDTO>> failedMessages(Integer limit, String token) {
        if (!adminSupport.authorized(token)) {
            return adminSupport.denied();
        }
        List<MessageRecordEntity> messages = messageRecordService.queryFailedMessages(null == limit ? 20 : limit);
        return success(responseAssembler.toFailedMessageResponses(messages));
    }

    public Response<Boolean> markHandled(MarkMqMessageHandledRequestDTO request, String token, String operator) {
        if (!adminSupport.authorized(token)) {
            return adminSupport.denied();
        }
        if (null == request || StringUtils.isBlank(request.getMessageId())) {
            return illegalParameter();
        }
        messageRecordService.consumeSuccess(request.getMessageId());
        log.warn("mq failed message marked handled operator:{} messageId:{}",
                adminSupport.operator(operator), request.getMessageId());
        return success(true);
    }

    public Response<Integer> retryProducerFailed(String token, String operator, Integer limit) {
        if (!adminSupport.authorized(token)) {
            return adminSupport.denied();
        }
        int count = messageRecordService.retryProducerFailedMessages(null == limit ? 20 : limit);
        log.warn("mq producer failed messages manual retry operator:{} count:{}",
                adminSupport.operator(operator), count);
        return success(count);
    }

    private <T> Response<T> success(T data) {
        return Response.<T>builder()
                .code(ResponseCode.SUCCESS.getCode())
                .info(ResponseCode.SUCCESS.getInfo())
                .data(data)
                .build();
    }

    private <T> Response<T> illegalParameter() {
        return Response.<T>builder()
                .code(ResponseCode.ILLEGAL_PARAMETER.getCode())
                .info(ResponseCode.ILLEGAL_PARAMETER.getInfo())
                .build();
    }

}
