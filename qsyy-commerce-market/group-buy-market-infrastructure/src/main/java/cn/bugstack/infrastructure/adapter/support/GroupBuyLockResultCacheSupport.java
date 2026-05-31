package cn.bugstack.infrastructure.adapter.support;

import cn.bugstack.domain.trade.model.entity.MarketPayOrderEntity;
import cn.bugstack.infrastructure.redis.IRedisService;
import cn.bugstack.types.common.Constants;
import com.alibaba.fastjson.JSON;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class GroupBuyLockResultCacheSupport {

    private static final String LOCK_RESULT_KEY_PREFIX = "group_buy_market_lock_result_key_";
    private static final long DEFAULT_LOCK_RESULT_TTL_MILLIS = TimeUnit.HOURS.toMillis(24);

    @Resource
    private IRedisService redisService;

    public MarketPayOrderEntity query(String userId, String outTradeNo) {
        try {
            String result = redisService.getValue(lockResultKey(userId, outTradeNo));
            if (StringUtils.isNotBlank(result)) {
                return JSON.parseObject(result, MarketPayOrderEntity.class);
            }
        } catch (Exception e) {
            log.warn("query group-buy lock result cache failed userId:{} outTradeNo:{}", userId, outTradeNo, e);
        }
        return null;
    }

    public void cache(String userId, String outTradeNo, MarketPayOrderEntity marketPayOrderEntity, Integer validTime) {
        if (null == marketPayOrderEntity) {
            return;
        }
        try {
            redisService.setValue(lockResultKey(userId, outTradeNo), JSON.toJSONString(marketPayOrderEntity), lockResultTtlMillis(validTime));
        } catch (Exception e) {
            log.warn("cache group-buy lock result failed userId:{} outTradeNo:{}", userId, outTradeNo, e);
        }
    }

    public void remove(String userId, String outTradeNo) {
        if (StringUtils.isBlank(userId) || StringUtils.isBlank(outTradeNo)) {
            return;
        }
        try {
            redisService.remove(lockResultKey(userId, outTradeNo));
        } catch (Exception e) {
            log.warn("remove group-buy lock result cache failed userId:{} outTradeNo:{}", userId, outTradeNo, e);
        }
    }

    private String lockResultKey(String userId, String outTradeNo) {
        return LOCK_RESULT_KEY_PREFIX + userId + Constants.UNDERLINE + outTradeNo;
    }

    private long lockResultTtlMillis(Integer validTime) {
        if (null == validTime || validTime <= 0) {
            return DEFAULT_LOCK_RESULT_TTL_MILLIS;
        }
        return Math.max(DEFAULT_LOCK_RESULT_TTL_MILLIS, TimeUnit.MINUTES.toMillis(validTime + 60L));
    }

}
