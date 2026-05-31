package cn.bugstack.infrastructure.adapter.support;

import cn.bugstack.infrastructure.redis.IRedisService;
import cn.bugstack.types.common.Constants;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class GroupBuyTeamStockReservationSupport {

    @Resource
    private IRedisService redisService;

    public long occupy(String teamStockKey, String recoveryTeamStockKey, String userTeamOccupyKey, String outTradeNo, Integer target, Integer validTime) {
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

    public void releaseUserOccupy(String userTeamOccupyKey) {
        if (StringUtils.isBlank(userTeamOccupyKey)) {
            return;
        }
        redisService.remove(userTeamOccupyKey);
    }

}
