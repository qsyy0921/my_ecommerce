package cn.bugstack.infrastructure.adapter.support;

import org.springframework.stereotype.Component;

@Component
public class SeckillStockKeyBuilder {

    private static final String SECKILL_STOCK_KEY = "seckill:stock:";
    private static final String SECKILL_STOCK_INIT_LOCK_KEY = "seckill:stock:init:";
    private static final String SECKILL_USER_LOCK_KEY = "seckill:user:lock:";

    public String stockBucketKey(Long activityId, int bucket) {
        return SECKILL_STOCK_KEY + activityId + ":" + bucket;
    }

    public String initializationLockKey(Long activityId) {
        return SECKILL_STOCK_INIT_LOCK_KEY + activityId;
    }

    public String userLockKey(Long activityId, String userId) {
        return SECKILL_USER_LOCK_KEY + activityId + ":" + userId;
    }

}
