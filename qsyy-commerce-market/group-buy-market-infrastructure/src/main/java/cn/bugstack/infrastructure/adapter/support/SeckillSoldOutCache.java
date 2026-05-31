package cn.bugstack.infrastructure.adapter.support;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

@Component
public class SeckillSoldOutCache {

    @Value("${app.seckill.sold-out-cache-ttl-millis:5000}")
    private Long soldOutCacheTtlMillis;

    private final ConcurrentHashMap<Long, Long> soldOutCache = new ConcurrentHashMap<>();

    public boolean isSoldOut(Long activityId) {
        Long expireTime = soldOutCache.get(activityId);
        if (null == expireTime) {
            return false;
        }
        if (expireTime <= System.currentTimeMillis()) {
            soldOutCache.remove(activityId);
            return false;
        }
        return true;
    }

    public void markSoldOut(Long activityId) {
        if (soldOutCacheTtlMillis > 0) {
            soldOutCache.put(activityId, System.currentTimeMillis() + soldOutCacheTtlMillis);
        }
    }

    public void clear(Long activityId) {
        soldOutCache.remove(activityId);
    }

}
