package cn.bugstack.trigger.job;

import cn.bugstack.domain.trade.service.ITradeTaskService;
import com.alibaba.fastjson.JSON;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Map;

/**
 * Reconciles pending local notification tasks.
 */
@Slf4j
@Service
public class TradeReconciliationJob {

    @Resource
    private ITradeTaskService tradeTaskService;
    @Resource
    private JobExecutionRecorder jobExecutionRecorder;

    @Scheduled(cron = "30 */5 * * * ?")
    public void exec() {
        JobExecutionRecorder.Execution execution = jobExecutionRecorder.start("market_trade_reconciliation", 300);
        if (!execution.isLockAcquired()) {
            return;
        }
        try {
            Map<String, Integer> result = tradeTaskService.execNotifyJob();
            Integer retryCount = result.get("retryCount");
            Integer errorCount = result.get("errorCount");
            if ((null != retryCount && retryCount > 0) || (null != errorCount && errorCount > 0)) {
                log.warn("trade reconciliation found pending notify tasks result:{}", JSON.toJSONString(result));
            }
            jobExecutionRecorder.success(execution,
                    null == retryCount ? 0 : retryCount,
                    null == errorCount ? 0 : errorCount,
                    JSON.toJSONString(result));
        } catch (Exception e) {
            jobExecutionRecorder.fail(execution, e);
            log.error("trade reconciliation job failed", e);
        }
    }

}
