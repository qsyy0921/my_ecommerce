package cn.bugstack.trigger.job;

import cn.bugstack.domain.seckill.service.ISeckillOrderOutboxService;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.concurrent.TimeUnit;

/**
 * Retries seckill order creation messages accepted by DB outbox but not yet published.
 */
@Slf4j
@Service
public class SeckillOrderOutboxRetryJob {

    @Value("${app.seckill.order-outbox.enabled:true}")
    private Boolean enabled;
    @Value("${app.seckill.order-outbox.batch-size:50}")
    private Integer batchSize;

    @Resource
    private ISeckillOrderOutboxService seckillOrderOutboxService;
    @Resource
    private RedissonClient redissonClient;
    @Resource
    private JobExecutionRecorder jobExecutionRecorder;

    @Scheduled(fixedDelayString = "${app.seckill.order-outbox.fixed-delay-millis:30000}")
    public void exec() {
        if (Boolean.FALSE.equals(enabled)) {
            return;
        }
        RLock lock = redissonClient.getLock("group_buy_market_seckill_order_outbox_retry_job_exec");
        JobExecutionRecorder.Execution execution = null;
        try {
            boolean locked = lock.tryLock(1, 30, TimeUnit.SECONDS);
            if (!locked) {
                return;
            }
            execution = jobExecutionRecorder.start("market_seckill_order_outbox_retry", 30);
            if (!execution.isLockAcquired()) {
                return;
            }
            int count = seckillOrderOutboxService.retryDueMessages(batchSize);
            if (count > 0) {
                log.warn("seckill order outbox messages retried count:{}", count);
            }
            jobExecutionRecorder.success(execution, count, 0, "retryCount=" + count);
        } catch (Exception e) {
            if (null != execution) {
                jobExecutionRecorder.fail(execution, e);
            }
            log.error("seckill order outbox retry job failed", e);
        } finally {
            if (lock.isLocked() && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

}
