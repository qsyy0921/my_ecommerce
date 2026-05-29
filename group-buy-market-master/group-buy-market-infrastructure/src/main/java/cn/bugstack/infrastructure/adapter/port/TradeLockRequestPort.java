package cn.bugstack.infrastructure.adapter.port;

import cn.bugstack.domain.trade.adapter.port.ITradeLockRequestPort;
import cn.bugstack.domain.trade.model.entity.MarketPayOrderEntity;
import cn.bugstack.infrastructure.redis.IRedisService;
import cn.bugstack.types.common.Constants;
import com.alibaba.fastjson.JSON;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class TradeLockRequestPort implements ITradeLockRequestPort {

    private static final String LOCKING_KEY_PREFIX = "group_buy_market_locking_key_";
    private static final String LOCK_RESULT_KEY_PREFIX = "group_buy_market_lock_result_key_";
    private static final long DEFAULT_LOCKING_TTL_MILLIS = TimeUnit.SECONDS.toMillis(30);
    private static final long DEFAULT_LOCK_RESULT_TTL_MILLIS = TimeUnit.HOURS.toMillis(24);

    @Resource
    private IRedisService redisService;

    @Override
    public MarketPayOrderEntity queryLockResult(String userId, String outTradeNo) {
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

    @Override
    public boolean tryAcquireLockRequest(String userId, String outTradeNo, Integer validTime) {
        Boolean locked = redisService.setNx(lockingKey(userId, outTradeNo), lockRequestTtlMillis(validTime), TimeUnit.MILLISECONDS);
        return Boolean.TRUE.equals(locked);
    }

    @Override
    public void releaseLockRequest(String userId, String outTradeNo) {
        redisService.remove(lockingKey(userId, outTradeNo));
    }

    @Override
    public void cacheLockResult(String userId, String outTradeNo, MarketPayOrderEntity marketPayOrderEntity, Integer validTime) {
        if (null == marketPayOrderEntity) {
            return;
        }
        try {
            redisService.setValue(lockResultKey(userId, outTradeNo), JSON.toJSONString(marketPayOrderEntity), lockResultTtlMillis(validTime));
        } catch (Exception e) {
            log.warn("cache group-buy lock result failed userId:{} outTradeNo:{}", userId, outTradeNo, e);
        }
    }

    @Override
    public void removeLockResult(String userId, String outTradeNo) {
        if (StringUtils.isBlank(userId) || StringUtils.isBlank(outTradeNo)) {
            return;
        }
        try {
            redisService.remove(lockResultKey(userId, outTradeNo));
        } catch (Exception e) {
            log.warn("remove group-buy lock result cache failed userId:{} outTradeNo:{}", userId, outTradeNo, e);
        }
    }

    private String lockingKey(String userId, String outTradeNo) {
        return LOCKING_KEY_PREFIX + userId + Constants.UNDERLINE + outTradeNo;
    }

    private String lockResultKey(String userId, String outTradeNo) {
        return LOCK_RESULT_KEY_PREFIX + userId + Constants.UNDERLINE + outTradeNo;
    }

    private long lockRequestTtlMillis(Integer validTime) {
        if (null == validTime || validTime <= 0) {
            return DEFAULT_LOCKING_TTL_MILLIS;
        }
        return Math.max(DEFAULT_LOCKING_TTL_MILLIS, TimeUnit.MINUTES.toMillis(validTime + 1L));
    }

    private long lockResultTtlMillis(Integer validTime) {
        if (null == validTime || validTime <= 0) {
            return DEFAULT_LOCK_RESULT_TTL_MILLIS;
        }
        return Math.max(DEFAULT_LOCK_RESULT_TTL_MILLIS, TimeUnit.MINUTES.toMillis(validTime + 60L));
    }

}
