package cn.bugstack.infrastructure.adapter.port;

import cn.bugstack.domain.seckill.adapter.port.ISeckillRateLimitPort;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RScript;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.Arrays;

/**
 * Redis-backed fixed-window rate limiter for seckill entrance traffic.
 */
@Slf4j
@Component
public class SeckillRateLimitPort implements ISeckillRateLimitPort {

    private static final String RATE_LIMIT_KEY_PREFIX = "seckill:rate:";

    @Value("${app.seckill.rate-limit.enabled:true}")
    private Boolean enabled;
    @Value("${app.seckill.rate-limit.activity-window-seconds:1}")
    private Integer activityWindowSeconds;
    @Value("${app.seckill.rate-limit.activity-max:800}")
    private Integer activityMax;
    @Value("${app.seckill.rate-limit.user-window-seconds:1}")
    private Integer userWindowSeconds;
    @Value("${app.seckill.rate-limit.user-max:3}")
    private Integer userMax;
    @Value("${app.seckill.rate-limit.ip-window-seconds:1}")
    private Integer ipWindowSeconds;
    @Value("${app.seckill.rate-limit.ip-max:200}")
    private Integer ipMax;

    @Resource
    private RedissonClient redissonClient;

    @Override
    public boolean tryAcquire(Long activityId, String userId, String clientIp) {
        if (Boolean.FALSE.equals(enabled)) {
            return true;
        }
        String activity = String.valueOf(activityId);
        String user = safeValue(userId);
        String ip = safeValue(clientIp);

        boolean activityAllowed = acquire("activity:" + activity, activityWindowSeconds, activityMax);
        boolean userAllowed = activityAllowed && acquire("user:" + activity + ":" + user, userWindowSeconds, userMax);
        boolean ipAllowed = userAllowed && acquire("ip:" + activity + ":" + ip, ipWindowSeconds, ipMax);
        if (!ipAllowed) {
            log.warn("seckill rate limited activityId:{} userId:{} clientIp:{}", activityId, userId, clientIp);
        }
        return ipAllowed;
    }

    private boolean acquire(String dimension, Integer windowSecondsConfig, Integer maxConfig) {
        int windowSeconds = Math.max(1, null == windowSecondsConfig ? 1 : windowSecondsConfig);
        int max = null == maxConfig ? 0 : maxConfig;
        if (max <= 0) {
            return true;
        }
        long windowMillis = windowSeconds * 1000L;
        long windowId = System.currentTimeMillis() / windowMillis;
        String key = RATE_LIMIT_KEY_PREFIX + dimension + ":" + windowId;
        String luaScript =
                "local current = redis.call('incr', KEYS[1]) " +
                "if current == 1 then " +
                "  redis.call('pexpire', KEYS[1], ARGV[1]) " +
                "end " +
                "if current > tonumber(ARGV[2]) then " +
                "  return 0 " +
                "end " +
                "return 1";
        Number result = redissonClient.getScript(StringCodec.INSTANCE).eval(
                RScript.Mode.READ_WRITE,
                luaScript,
                RScript.ReturnType.INTEGER,
                Arrays.<Object>asList(key),
                String.valueOf(windowMillis * 2),
                String.valueOf(max));
        return result.longValue() == 1L;
    }

    private String safeValue(String value) {
        if (null == value || value.trim().isEmpty()) {
            return "unknown";
        }
        return value.trim();
    }

}
