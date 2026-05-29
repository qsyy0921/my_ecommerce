package cn.bugstack.trigger.job;

import cn.bugstack.domain.seckill.service.ISeckillService;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class SeckillTimeoutReleaseJob {

    @Resource
    private ISeckillService seckillService;

    @Resource
    private RedissonClient redissonClient;
    @Resource
    private JobExecutionRecorder jobExecutionRecorder;

    @Scheduled(cron = "45 */5 * * * ?")
    public void exec() {
        RLock lock = redissonClient.getLock("group_buy_market_seckill_timeout_release_job_exec");
        JobExecutionRecorder.Execution execution = null;
        try {
            boolean locked = lock.tryLock(3, 120, TimeUnit.SECONDS);
            if (!locked) {
                log.info("seckill timeout release job skipped because lock is held by another instance");
                return;
            }
            execution = jobExecutionRecorder.start("market_seckill_timeout_release", 120);
            if (!execution.isLockAcquired()) {
                return;
            }
            int count = seckillService.releaseTimeoutUnpaidOrders();
            if (count > 0) {
                log.warn("seckill timeout unpaid orders released count:{}", count);
            }
            jobExecutionRecorder.success(execution, count, 0, "releasedCount=" + count);
        } catch (Exception e) {
            if (null != execution) {
                jobExecutionRecorder.fail(execution, e);
            }
            log.error("seckill timeout release job failed", e);
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

}
