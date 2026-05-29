package cn.bugstack.trigger.job;

import cn.bugstack.domain.seckill.service.ISeckillService;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.concurrent.TimeUnit;

/**
 * Preloads upcoming seckill activities and Redis stock buckets before traffic arrives.
 */
@Slf4j
@Service
public class SeckillPrewarmJob {

    @Value("${app.seckill.prewarm.enabled:true}")
    private Boolean enabled;
    @Value("${app.seckill.prewarm.before-minutes:10}")
    private Integer beforeMinutes;
    @Value("${app.seckill.prewarm.limit:50}")
    private Integer limit;

    @Resource
    private ISeckillService seckillService;
    @Resource
    private RedissonClient redissonClient;
    @Resource
    private JobExecutionRecorder jobExecutionRecorder;

    @Scheduled(fixedDelayString = "${app.seckill.prewarm.fixed-delay-millis:60000}")
    public void exec() {
        if (Boolean.FALSE.equals(enabled)) {
            return;
        }
        RLock lock = redissonClient.getLock("group_buy_market_seckill_prewarm_job_exec");
        JobExecutionRecorder.Execution execution = null;
        try {
            boolean locked = lock.tryLock(1, 30, TimeUnit.SECONDS);
            if (!locked) {
                return;
            }
            execution = jobExecutionRecorder.start("market_seckill_prewarm", 30);
            if (!execution.isLockAcquired()) {
                return;
            }
            int count = seckillService.prewarmUpcomingActivities(beforeMinutes, limit);
            if (count > 0) {
                log.info("seckill prewarm job completed count:{} beforeMinutes:{} limit:{}", count, beforeMinutes, limit);
            }
            jobExecutionRecorder.success(execution, count, 0, "beforeMinutes=" + beforeMinutes + ",limit=" + limit);
        } catch (Exception e) {
            if (null != execution) {
                jobExecutionRecorder.fail(execution, e);
            }
            log.error("seckill prewarm job failed", e);
        } finally {
            if (lock.isLocked() && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

}
