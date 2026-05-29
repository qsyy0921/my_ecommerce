package cn.bugstack.trigger.job;

import cn.bugstack.domain.message.service.IMessageRecordService;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class MqPublishRetryJob {

    @Value("${app.mq.publish-retry.enabled:true}")
    private Boolean enabled;
    @Value("${app.mq.publish-retry.batch-size:20}")
    private Integer batchSize;

    @Resource
    private IMessageRecordService messageRecordService;
    @Resource
    private RedissonClient redissonClient;
    @Resource
    private JobExecutionRecorder jobExecutionRecorder;

    @Scheduled(fixedDelayString = "${app.mq.publish-retry.fixed-delay-millis:30000}")
    public void exec() {
        if (Boolean.FALSE.equals(enabled)) {
            return;
        }
        RLock lock = redissonClient.getLock("group_buy_market_mq_publish_retry_job_exec");
        JobExecutionRecorder.Execution execution = null;
        try {
            boolean locked = lock.tryLock(1, 20, TimeUnit.SECONDS);
            if (!locked) {
                return;
            }
            execution = jobExecutionRecorder.start("market_mq_publish_retry", 20);
            if (!execution.isLockAcquired()) {
                return;
            }
            int count = messageRecordService.retryProducerFailedMessages(batchSize);
            if (count > 0) {
                log.warn("mq producer failed messages retried count:{}", count);
            }
            jobExecutionRecorder.success(execution, count, 0, "retryCount=" + count);
        } catch (Exception e) {
            if (null != execution) {
                jobExecutionRecorder.fail(execution, e);
            }
            log.error("mq producer failed message retry job failed", e);
        } finally {
            if (lock.isLocked() && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

}
