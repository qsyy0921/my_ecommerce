package cn.bugstack.trigger.job;

import cn.bugstack.domain.message.service.IMessageRecordService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

@Slf4j
@Service
public class MqPublishRetryJob {

    @Value("${mq.publish-retry.enabled:true}")
    private Boolean enabled;
    @Value("${mq.publish-retry.batch-size:20}")
    private Integer batchSize;

    @Resource
    private IMessageRecordService messageRecordService;
    @Resource
    private JobExecutionRecorder jobExecutionRecorder;

    @Scheduled(fixedDelayString = "${mq.publish-retry.fixed-delay-millis:30000}")
    public void exec() {
        if (Boolean.FALSE.equals(enabled)) {
            return;
        }
        JobExecutionRecorder.Execution execution = jobExecutionRecorder.start("mall_mq_publish_retry", 60);
        if (!execution.isLockAcquired()) {
            return;
        }
        try {
            int count = messageRecordService.retryProducerFailedMessages(batchSize);
            if (count > 0) {
                log.warn("mq producer failed messages retried count:{}", count);
            }
            jobExecutionRecorder.success(execution, count, 0, "retryCount=" + count);
        } catch (Exception e) {
            jobExecutionRecorder.fail(execution, e);
            log.error("mq producer failed message retry job failed", e);
        }
    }

}
