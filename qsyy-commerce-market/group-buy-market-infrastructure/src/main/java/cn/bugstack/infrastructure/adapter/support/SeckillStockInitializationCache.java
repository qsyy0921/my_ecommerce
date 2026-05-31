package cn.bugstack.infrastructure.adapter.support;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

@Component
public class SeckillStockInitializationCache {

    @Value("${app.seckill.stock-init-cache-ttl-millis:60000}")
    private Long stockInitCacheTtlMillis;

    private final ConcurrentHashMap<Long, Long> stockInitializedCache = new ConcurrentHashMap<>();

    public boolean isInitializedRecently(Long activityId) {
        Long expireTime = stockInitializedCache.get(activityId);
        if (null == expireTime) {
            return false;
        }
        if (expireTime > System.currentTimeMillis()) {
            return true;
        }
        stockInitializedCache.remove(activityId, expireTime);
        return false;
    }

    public void markInitialized(Long activityId) {
        if (null != stockInitCacheTtlMillis && stockInitCacheTtlMillis > 0) {
            stockInitializedCache.put(activityId, System.currentTimeMillis() + stockInitCacheTtlMillis);
        }
    }

}
