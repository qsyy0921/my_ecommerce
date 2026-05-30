package cn.bugstack.trigger.http;

import cn.bugstack.api.dto.ReplaySeckillManualRequestDTO;
import cn.bugstack.api.dto.SeckillManualCompensationLogResponseDTO;
import cn.bugstack.api.dto.SeckillManualMessageResponseDTO;
import cn.bugstack.api.response.Response;
import cn.bugstack.trigger.support.SeckillManualCompensationOpsSupport;
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
@RequestMapping("/api/v1/gbm/seckill/ops/")
public class SeckillOpsController {

    @Resource
    private SeckillManualCompensationOpsSupport seckillManualCompensationOpsSupport;

    @RequestMapping(value = "manual_messages", method = RequestMethod.GET)
    public Response<List<SeckillManualMessageResponseDTO>> queryManualMessages(
            @RequestParam(required = false, defaultValue = "20") Integer limit,
            @RequestHeader(value = "x-admin-token", required = false) String token,
            @RequestHeader(value = "x-admin-operator", required = false) String operator) {
        return seckillManualCompensationOpsSupport.queryManualMessages(limit, token, operator);
    }

    @RequestMapping(value = "replay_manual", method = RequestMethod.POST)
    public Response<Integer> replayManualMessages(@RequestBody ReplaySeckillManualRequestDTO request,
                                                  @RequestHeader(value = "x-admin-token", required = false) String token,
                                                  @RequestHeader(value = "x-admin-operator", required = false) String operator) {
        return seckillManualCompensationOpsSupport.replayManualMessages(request, token, operator);
    }

    @RequestMapping(value = "manual_logs", method = RequestMethod.GET)
    public Response<List<SeckillManualCompensationLogResponseDTO>> queryManualLogs(
            @RequestParam(required = false, defaultValue = "50") Integer limit,
            @RequestHeader(value = "x-admin-token", required = false) String token) {
        return seckillManualCompensationOpsSupport.queryManualLogs(limit, token);
    }

}
