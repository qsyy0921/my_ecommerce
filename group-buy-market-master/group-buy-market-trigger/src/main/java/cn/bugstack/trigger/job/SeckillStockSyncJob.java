package cn.bugstack.trigger.job;

import cn.bugstack.domain.seckill.service.ISeckillService;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.concurrent.TimeUnit;

/**
 * Periodically syncs seckill activity stock from created orders.
 */
@Slf4j
@Service
public class SeckillStockSyncJob {

    @Resource
    private ISeckillService seckillService;
    @Resource
    private RedissonClient redissonClient;
    @Resource
    private JobExecutionRecorder jobExecutionRecorder;

    @Scheduled(cron = "*/5 * * * * ?")
    public void exec() {
        RLock lock = redissonClient.getLock("group_buy_market_seckill_stock_sync_job_exec");
        JobExecutionRecorder.Execution execution = null;
        try {
            boolean locked = lock.tryLock(1, 10, TimeUnit.SECONDS);
            if (!locked) {
                return;
            }
            execution = jobExecutionRecorder.start("market_seckill_stock_sync", 4);
            if (!execution.isLockAcquired()) {
                return;
            }
            seckillService.syncSeckillActivityStock();
            jobExecutionRecorder.success(execution, 0, 0, "stock synced");
        } catch (Exception e) {
            if (null != execution) {
                jobExecutionRecorder.fail(execution, e);
            }
            log.error("seckill stock sync job failed", e);
        } finally {
            if (lock.isLocked() && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

}
