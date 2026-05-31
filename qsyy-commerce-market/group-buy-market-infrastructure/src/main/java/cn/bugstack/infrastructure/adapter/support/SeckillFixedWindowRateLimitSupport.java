package cn.bugstack.infrastructure.adapter.support;

import org.redisson.api.RScript;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.Arrays;

@Component
public class SeckillFixedWindowRateLimitSupport {

    private static final String RATE_LIMIT_KEY_PREFIX = "seckill:rate:";
    private static final String FIXED_WINDOW_LUA =
            "local current = redis.call('incr', KEYS[1]) " +
            "if current == 1 then " +
            "  redis.call('pexpire', KEYS[1], ARGV[1]) " +
            "end " +
            "if current > tonumber(ARGV[2]) then " +
            "  return 0 " +
            "end " +
            "return 1";

    @Resource
    private RedissonClient redissonClient;

    public boolean acquire(String dimension, Integer windowSecondsConfig, Integer maxConfig) {
        int windowSeconds = Math.max(1, null == windowSecondsConfig ? 1 : windowSecondsConfig);
        int max = null == maxConfig ? 0 : maxConfig;
        if (max <= 0) {
            return true;
        }
        long windowMillis = windowSeconds * 1000L;
        long windowId = System.currentTimeMillis() / windowMillis;
        String key = RATE_LIMIT_KEY_PREFIX + dimension + ":" + windowId;
        Number result = redissonClient.getScript(StringCodec.INSTANCE).eval(
                RScript.Mode.READ_WRITE,
                FIXED_WINDOW_LUA,
                RScript.ReturnType.INTEGER,
                Arrays.<Object>asList(key),
                String.valueOf(windowMillis * 2),
                String.valueOf(max));
        return result.longValue() == 1L;
    }

}
