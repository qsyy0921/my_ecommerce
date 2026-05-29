package cn.bugstack.trigger.job;

import cn.bugstack.domain.order.service.IOrderService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

@Slf4j
@Service
public class ReconcileCaseScanJob {

    @Resource
    private IOrderService orderService;
    @Resource
    private JobExecutionRecorder jobExecutionRecorder;

    @Scheduled(cron = "15 */5 * * * ?")
    public void exec() {
        JobExecutionRecorder.Execution execution = jobExecutionRecorder.start("mall_reconcile_case_scan", 300);
        if (!execution.isLockAcquired()) {
            return;
        }
        try {
            int count = orderService.scanReconcileCases();
            if (count > 0) {
                log.warn("reconcile case scan found or refreshed cases count:{}", count);
            }
            jobExecutionRecorder.success(execution, count, 0, "caseCount=" + count);
        } catch (Exception e) {
            jobExecutionRecorder.fail(execution, e);
            log.error("reconcile case scan job failed", e);
        }
    }

}
