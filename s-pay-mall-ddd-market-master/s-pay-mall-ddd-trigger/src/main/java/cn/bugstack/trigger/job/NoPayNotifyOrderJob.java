package cn.bugstack.trigger.job;

import cn.bugstack.domain.order.service.IOrderService;
import com.alipay.api.AlipayClient;
import com.alipay.api.domain.AlipayTradeQueryModel;
import com.alipay.api.request.AlipayTradeQueryRequest;
import com.alipay.api.response.AlipayTradeQueryResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.List;

/**
 * @author Fuzhengwei bugstack.cn @小傅哥
 * @description 检测未接收到或未正确处理的支付回调通知
 * @create 2024-09-30 09:59
 */
@Slf4j
@Component()
public class NoPayNotifyOrderJob {

    @Resource
    private IOrderService orderService;
    @Resource
    private AlipayClient alipayClient;
    @Value("${mock-pay.enabled:false}")
    private boolean mockPayEnabled;
    @Resource
    private JobExecutionRecorder jobExecutionRecorder;

    @Scheduled(cron = "0 0/30 * * * ?")
    public void exec() {
        JobExecutionRecorder.Execution execution = jobExecutionRecorder.start("mall_no_pay_notify_query", 1800);
        if (!execution.isLockAcquired()) {
            return;
        }
        try {
            if (mockPayEnabled) {
                log.info("mock pay enabled, skip alipay no-pay notify query");
                jobExecutionRecorder.success(execution, 0, 0, "mock pay enabled skipped");
                return;
            }

            log.info("任务；检测未接收到或未正确处理的支付回调通知");
            List<String> orderIds = orderService.queryNoPayNotifyOrder();
            if (null == orderIds || orderIds.isEmpty()) {
                jobExecutionRecorder.success(execution, 0, 0, "no pending pay notify orders");
                return;
            }

            int successCount = 0;
            int failCount = 0;
            for (String orderId : orderIds) {
                AlipayTradeQueryRequest request = new AlipayTradeQueryRequest();
                AlipayTradeQueryModel bizModel = new AlipayTradeQueryModel();
                bizModel.setOutTradeNo(orderId);
                request.setBizModel(bizModel);

                AlipayTradeQueryResponse alipayTradeQueryResponse = alipayClient.execute(request);
                String code = alipayTradeQueryResponse.getCode();

                // 判断状态码
                if ("10000".equals(code)) {
                    orderService.changeOrderPaySuccess(orderId,
                            alipayTradeQueryResponse.getSendPayDate(),
                            "alipay_query",
                            alipayTradeQueryResponse.getTradeNo(),
                            alipayTradeQueryResponse.getBody());
                    successCount++;
                }
            }
            jobExecutionRecorder.success(execution, successCount, failCount, "scannedCount=" + orderIds.size());
        } catch (Exception e) {
            jobExecutionRecorder.fail(execution, e);
            log.error("检测未接收到或未正确处理的支付回调通知失败", e);
        }
    }

}
