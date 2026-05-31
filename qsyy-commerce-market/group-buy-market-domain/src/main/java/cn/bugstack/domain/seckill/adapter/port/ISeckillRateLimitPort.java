package cn.bugstack.domain.seckill.adapter.port;

/**
 * Seckill entrance rate-limit port.
 */
public interface ISeckillRateLimitPort {

    boolean tryAcquire(Long activityId, String userId, String clientIp);

}
