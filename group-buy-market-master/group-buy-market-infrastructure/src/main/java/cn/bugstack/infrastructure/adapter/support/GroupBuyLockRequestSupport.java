package cn.bugstack.infrastructure.adapter.support;

import cn.bugstack.infrastructure.redis.IRedisService;
import cn.bugstack.types.common.Constants;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.concurrent.TimeUnit;

@Component
public class GroupBuyLockRequestSupport {

    private static final String LOCKING_KEY_PREFIX = "group_buy_market_locking_key_";
    private static final long DEFAULT_LOCKING_TTL_MILLIS = TimeUnit.SECONDS.toMillis(30);

    @Resource
    private IRedisService redisService;

    public boolean tryAcquire(String userId, String outTradeNo, Integer validTime) {
        Boolean locked = redisService.setNx(lockingKey(userId, outTradeNo), lockRequestTtlMillis(validTime), TimeUnit.MILLISECONDS);
        return Boolean.TRUE.equals(locked);
    }

    public void release(String userId, String outTradeNo) {
        redisService.remove(lockingKey(userId, outTradeNo));
    }

    private String lockingKey(String userId, String outTradeNo) {
        return LOCKING_KEY_PREFIX + userId + Constants.UNDERLINE + outTradeNo;
    }

    private long lockRequestTtlMillis(Integer validTime) {
        if (null == validTime || validTime <= 0) {
            return DEFAULT_LOCKING_TTL_MILLIS;
        }
        return Math.max(DEFAULT_LOCKING_TTL_MILLIS, TimeUnit.MINUTES.toMillis(validTime + 1L));
    }

}
