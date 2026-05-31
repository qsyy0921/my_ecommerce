package cn.bugstack.trigger.job;

import cn.bugstack.domain.order.service.IOrderReconcileService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

/**
 * Reconciles market orders that paid successfully but did not reach market settlement.
 */
@Slf4j
@Service
public class OrderReconciliationJob {

    @Resource
    private IOrderReconcileService orderReconcileService;
    @Resource
    private JobExecutionRecorder jobExecutionRecorder;

    @Scheduled(cron = "0 */5 * * * ?")
    public void exec() {
        JobExecutionRecorder.Execution execution = jobExecutionRecorder.start("mall_order_reconciliation", 300);
        if (!execution.isLockAcquired()) {
            return;
        }
        try {
            int successCount = orderReconcileService.reconcileMarketSettlementOrders();
            if (successCount > 0) {
                log.warn("order reconciliation repaired market settlement orders count:{}", successCount);
            }
            jobExecutionRecorder.success(execution, successCount, 0, "repairedCount=" + successCount);
        } catch (Exception e) {
            jobExecutionRecorder.fail(execution, e);
            log.error("order reconciliation job failed", e);
        }
    }

}
