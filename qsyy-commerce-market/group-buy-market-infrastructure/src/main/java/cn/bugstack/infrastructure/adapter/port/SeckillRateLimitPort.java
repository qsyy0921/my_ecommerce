package cn.bugstack.infrastructure.adapter.port;

import cn.bugstack.domain.seckill.adapter.port.ISeckillRateLimitPort;
import cn.bugstack.infrastructure.adapter.support.SeckillFixedWindowRateLimitSupport;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

/**
 * Redis-backed fixed-window rate limiter for seckill entrance traffic.
 */
@Slf4j
@Component
public class SeckillRateLimitPort implements ISeckillRateLimitPort {

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
    private SeckillFixedWindowRateLimitSupport seckillFixedWindowRateLimitSupport;

    @Override
    public boolean tryAcquire(Long activityId, String userId, String clientIp) {
        if (Boolean.FALSE.equals(enabled)) {
            return true;
        }
        String activity = String.valueOf(activityId);
        String user = safeValue(userId);
        String ip = safeValue(clientIp);

        boolean activityAllowed = seckillFixedWindowRateLimitSupport.acquire("activity:" + activity, activityWindowSeconds, activityMax);
        boolean userAllowed = activityAllowed && seckillFixedWindowRateLimitSupport.acquire("user:" + activity + ":" + user, userWindowSeconds, userMax);
        boolean ipAllowed = userAllowed && seckillFixedWindowRateLimitSupport.acquire("ip:" + activity + ":" + ip, ipWindowSeconds, ipMax);
        if (!ipAllowed) {
            log.warn("seckill rate limited activityId:{} userId:{} clientIp:{}", activityId, userId, clientIp);
        }
        return ipAllowed;
    }

    private String safeValue(String value) {
        if (null == value || value.trim().isEmpty()) {
            return "unknown";
        }
        return value.trim();
    }

}
