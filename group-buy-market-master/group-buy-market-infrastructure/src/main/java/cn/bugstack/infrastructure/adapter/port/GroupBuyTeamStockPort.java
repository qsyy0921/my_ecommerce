package cn.bugstack.infrastructure.adapter.port;

import cn.bugstack.domain.trade.adapter.port.IGroupBuyTeamStockPort;
import cn.bugstack.infrastructure.redis.IRedisService;
import cn.bugstack.types.common.Constants;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class GroupBuyTeamStockPort implements IGroupBuyTeamStockPort {

    @Resource
    private IRedisService redisService;

    @Override
    public long occupyTeamStock(String teamStockKey, String recoveryTeamStockKey, String userTeamOccupyKey, String outTradeNo, Integer target, Integer validTime) {
        int ttlMinutes = null == validTime ? 60 : validTime + 60;
        long occupy = redisService.reserveTeamStock(
                teamStockKey,
                recoveryTeamStockKey,
                teamStockKey + Constants.UNDERLINE,
                userTeamOccupyKey,
                outTradeNo,
                target,
                ttlMinutes,
                TimeUnit.MINUTES);

        if (-2 == occupy) {
            log.info("Team stock reservation lock failed. teamStockKey:{} occupy:{}", teamStockKey, occupy);
        }

        return occupy;
    }

    @Override
    public void recoveryTeamStock(String recoveryTeamStockKey, Integer validTime) {
        if (StringUtils.isBlank(recoveryTeamStockKey)) {
            return;
        }
        redisService.incr(recoveryTeamStockKey);
    }

    @Override
    public void releaseUserTeamOccupy(String userTeamOccupyKey) {
        if (StringUtils.isBlank(userTeamOccupyKey)) {
            return;
        }
        redisService.remove(userTeamOccupyKey);
    }

    @Override
    public void refund2AddRecovery(String recoveryTeamStockKey, String orderId) {
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
