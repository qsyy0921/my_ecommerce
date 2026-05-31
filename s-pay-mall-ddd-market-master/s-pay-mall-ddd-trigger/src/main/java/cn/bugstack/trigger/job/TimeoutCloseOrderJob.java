package cn.bugstack.trigger.job;

import cn.bugstack.domain.order.service.IOrderService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.List;

/**
 * @author qsyy
 * @description 超时关单
 * @create 2024-09-30 09:59
 */
@Slf4j
@Component()
public class TimeoutCloseOrderJob {

    @Resource
    private IOrderService orderService;
    @Resource
    private JobExecutionRecorder jobExecutionRecorder;

    @Scheduled(cron = "0 0/30 * * * ?")
    public void exec() {
        JobExecutionRecorder.Execution execution = jobExecutionRecorder.start("mall_timeout_close_order", 1800);
        if (!execution.isLockAcquired()) {
            return;
        }
        try {
            log.info("任务；超时30分钟订单关闭");
            List<String> orderIds = orderService.queryTimeoutCloseOrderList();
            if (null == orderIds || orderIds.isEmpty()) {
                log.info("定时任务，超时30分钟订单关闭，暂无超时未支付订单 orderIds is null");
                jobExecutionRecorder.success(execution, 0, 0, "no timeout orders");
                return;
            }
            int successCount = 0;
            int failCount = 0;
            for (String orderId : orderIds) {
                boolean status = orderService.changeOrderClose(orderId);
                if (status) {
                    successCount++;
                } else {
                    failCount++;
                }
                log.info("定时任务，超时30分钟订单关闭 orderId: {} status：{}", orderId, status);
            }
            jobExecutionRecorder.success(execution, successCount, failCount, "scannedCount=" + orderIds.size());
        } catch (Exception e) {
            jobExecutionRecorder.fail(execution, e);
            log.error("定时任务，超时15分钟订单关闭失败", e);
        }
    }

}
