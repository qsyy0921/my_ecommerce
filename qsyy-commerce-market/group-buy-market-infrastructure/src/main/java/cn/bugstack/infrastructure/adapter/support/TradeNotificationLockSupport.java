package cn.bugstack.infrastructure.adapter.support;

import cn.bugstack.domain.trade.model.entity.NotifyTaskEntity;
import cn.bugstack.infrastructure.redis.IRedisService;
import org.redisson.api.RLock;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.concurrent.TimeUnit;

@Component
public class TradeNotificationLockSupport {

    @Resource
    private IRedisService redisService;

    public TradeNotificationLock tryLock(NotifyTaskEntity notifyTask) throws InterruptedException {
        RLock lock = redisService.getLock(notifyTask.lockKey());
        if (lock.tryLock(3, 0, TimeUnit.SECONDS)) {
            return new TradeNotificationLock(lock);
        }
        return null;
    }

    public void unlockIfHeld(TradeNotificationLock tradeNotificationLock) {
        if (null == tradeNotificationLock) {
            return;
        }
        RLock lock = tradeNotificationLock.lock;
        if (lock.isLocked() && lock.isHeldByCurrentThread()) {
            lock.unlock();
        }
    }

    public static class TradeNotificationLock {
        private final RLock lock;

        private TradeNotificationLock(RLock lock) {
            this.lock = lock;
        }
    }

}
