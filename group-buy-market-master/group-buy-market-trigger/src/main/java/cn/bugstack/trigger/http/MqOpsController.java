package cn.bugstack.trigger.http;

import cn.bugstack.api.dto.MarkMqMessageHandledRequestDTO;
import cn.bugstack.api.dto.MqFailedMessageResponseDTO;
import cn.bugstack.api.response.Response;
import cn.bugstack.trigger.support.MqOpsSupport;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.List;

@RestController
@RequestMapping("/api/v1/gbm/mq/ops/")
public class MqOpsController {

    @Resource
    private MqOpsSupport mqOpsSupport;

    @RequestMapping(value = "failed_messages", method = RequestMethod.GET)
    public Response<List<MqFailedMessageResponseDTO>> failedMessages(@RequestParam(required = false, defaultValue = "20") Integer limit,
                                                                     @RequestHeader(value = "x-admin-token", required = false) String token) {
        return mqOpsSupport.failedMessages(limit, token);
    }

    @RequestMapping(value = "mark_handled", method = RequestMethod.POST)
    public Response<Boolean> markHandled(@RequestBody MarkMqMessageHandledRequestDTO request,
                                         @RequestHeader(value = "x-admin-token", required = false) String token,
                                         @RequestHeader(value = "x-admin-operator", required = false) String operator) {
        return mqOpsSupport.markHandled(request, token, operator);
    }

    @RequestMapping(value = "retry_producer_failed", method = RequestMethod.POST)
    public Response<Integer> retryProducerFailed(@RequestHeader(value = "x-admin-token", required = false) String token,
                                                 @RequestHeader(value = "x-admin-operator", required = false) String operator,
                                                 @RequestParam(required = false, defaultValue = "20") Integer limit) {
        return mqOpsSupport.retryProducerFailed(token, operator, limit);
    }

}
