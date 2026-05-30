package cn.bugstack.domain.trade.service.task;

import cn.bugstack.domain.trade.adapter.port.ITradeNotificationPort;
import cn.bugstack.domain.trade.adapter.port.ITradeNotifyTaskExecutionPort;
import cn.bugstack.domain.trade.model.entity.NotifyTaskEntity;
import cn.bugstack.domain.trade.service.ITradeTaskService;
import cn.bugstack.types.enums.NotifyTaskHTTPEnumVO;
import com.alibaba.fastjson.JSON;
import lombok.extern.slf4j.Slf4j;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 交易任务（MT/HTTP）服务
 * 
 * @author xiaofuge bugstack.cn @小傅哥
 * 2025/7/12 21:15
 */
@Slf4j
public class TradeTaskService implements ITradeTaskService {

    private final ITradeNotifyTaskExecutionPort notifyTaskExecutionPort;
    private final ITradeNotificationPort notificationPort;

    public TradeTaskService(ITradeNotifyTaskExecutionPort notifyTaskExecutionPort, ITradeNotificationPort notificationPort) {
        this.notifyTaskExecutionPort = notifyTaskExecutionPort;
        this.notificationPort = notificationPort;
    }
    
    @Override
    public Map<String, Integer> execNotifyJob() throws Exception {
        log.info("拼团交易-执行回调通知任务");

        // 查询未执行任务
        List<NotifyTaskEntity> notifyTaskEntityList = notifyTaskExecutionPort.queryUnExecutedNotifyTaskList();

        return execNotifyJob(notifyTaskEntityList);
    }

    @Override
    public Map<String, Integer> execNotifyJob(String teamId) throws Exception {
        log.info("拼团交易-执行回调通知回调，指定 teamId:{}", teamId);
        List<NotifyTaskEntity> notifyTaskEntityList = notifyTaskExecutionPort.queryUnExecutedNotifyTaskList(teamId);
        return execNotifyJob(notifyTaskEntityList);
    }

    @Override
    public Map<String, Integer> execNotifyJob(NotifyTaskEntity notifyTaskEntity) throws Exception {
        log.info("拼团交易-执行回调通知回调，指定 teamId:{} notifyTaskEntity:{}", notifyTaskEntity.getTeamId(), JSON.toJSONString(notifyTaskEntity));
        return execNotifyJob(Collections.singletonList(notifyTaskEntity));
    }

    private Map<String, Integer> execNotifyJob(List<NotifyTaskEntity> notifyTaskEntityList) throws Exception {
        int successCount = 0, errorCount = 0, retryCount = 0;
        for (NotifyTaskEntity notifyTask : notifyTaskEntityList) {
            // 回调处理 success 成功，error 失败
            String response = notificationPort.notify(notifyTask);

            // 更新状态判断&变更数据库表回调任务状态
            if (NotifyTaskHTTPEnumVO.SUCCESS.getCode().equals(response)) {
                int updateCount = notifyTaskExecutionPort.updateNotifyTaskStatusSuccess(notifyTask);
                if (1 == updateCount) {
                    successCount += 1;
                }
            } else if (NotifyTaskHTTPEnumVO.ERROR.getCode().equals(response)) {
                if (notifyTask.getNotifyCount() > 4) {
                    int updateCount = notifyTaskExecutionPort.updateNotifyTaskStatusError(notifyTask);
                    if (1 == updateCount) {
                        errorCount += 1;
                    }
                } else {
                    int updateCount = notifyTaskExecutionPort.updateNotifyTaskStatusRetry(notifyTask);
                    if (1 == updateCount) {
                        retryCount += 1;
                    }
                }
            }
        }

        Map<String, Integer> resultMap = new HashMap<>();
        resultMap.put("waitCount", notifyTaskEntityList.size());
        resultMap.put("successCount", successCount);
        resultMap.put("errorCount", errorCount);
        resultMap.put("retryCount", retryCount);

        return resultMap;
    }
    
}
