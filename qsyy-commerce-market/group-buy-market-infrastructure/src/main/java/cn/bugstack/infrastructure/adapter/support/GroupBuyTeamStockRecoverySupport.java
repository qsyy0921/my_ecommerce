package cn.bugstack.infrastructure.adapter.support;

import cn.bugstack.infrastructure.redis.IRedisService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class GroupBuyTeamStockRecoverySupport {

    @Resource
    private IRedisService redisService;

    public void recovery(String recoveryTeamStockKey) {
        if (StringUtils.isBlank(recoveryTeamStockKey)) {
            return;
        }
        redisService.incr(recoveryTeamStockKey);
    }

    public void refundRecovery(String recoveryTeamStockKey, String orderId) {
        if (StringUtils.isBlank(recoveryTeamStockKey) || StringUtils.isBlank(orderId)) {
            return;
        }

        String lockKey = "refund_lock_" + orderId;
        Boolean lockAcquired = redisService.setNx(lockKey, TimeUnit.DAYS.toMillis(30), TimeUnit.MILLISECONDS);

        if (!Boolean.TRUE.equals(lockAcquired)) {
            log.warn("订单 {} 恢复库存操作已在进行中，跳过重复操作", orderId);
            return;
        }

        try {
            redisService.incr(recoveryTeamStockKey);
            log.info("订单 {} 恢复库存成功，恢复库存key: {}", orderId, recoveryTeamStockKey);
        } catch (Exception e) {
            log.error("订单 {} 恢复库存失败，恢复库存key: {}", orderId, recoveryTeamStockKey, e);
            redisService.remove(lockKey);
            throw e;
        }
    }

}
