package cn.bugstack.test.infrastructure.trade;

import cn.bugstack.domain.trade.model.entity.MarketPayOrderEntity;
import cn.bugstack.domain.trade.model.valobj.TradeOrderStatusEnumVO;
import cn.bugstack.infrastructure.adapter.port.TradeLockRequestPort;
import cn.bugstack.infrastructure.adapter.support.GroupBuyLockRequestSupport;
import cn.bugstack.infrastructure.adapter.support.GroupBuyLockResultCacheSupport;
import cn.bugstack.infrastructure.redis.IRedisService;
import org.junit.Assert;
import org.junit.Test;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class TradeLockRequestPortUnitTest {

    private static final String USER_ID = "user_001";
    private static final String OUT_TRADE_NO = "trade_001";

    @Test
    public void tryAcquireShouldUseRequestLockKeyAndValidTimeTtl() {
        Fixture fixture = new Fixture();

        boolean acquired = fixture.port.tryAcquireLockRequest(USER_ID, OUT_TRADE_NO, 10);

        Assert.assertTrue(acquired);
        Assert.assertEquals("group_buy_market_locking_key_user_001_trade_001", fixture.redis.lastSetNxKey);
        Assert.assertEquals(TimeUnit.MINUTES.toMillis(11), fixture.redis.lastSetNxExpired);
        Assert.assertEquals(TimeUnit.MILLISECONDS, fixture.redis.lastSetNxTimeUnit);
    }

    @Test
    public void cacheAndQueryLockResultShouldRoundTripJson() {
        Fixture fixture = new Fixture();
        MarketPayOrderEntity order = MarketPayOrderEntity.builder()
                .teamId("team_001")
                .orderId("order_001")
                .originalPrice(new BigDecimal("100.00"))
                .deductionPrice(new BigDecimal("20.00"))
                .payPrice(new BigDecimal("80.00"))
                .tradeOrderStatusEnumVO(TradeOrderStatusEnumVO.CREATE)
                .build();

        fixture.port.cacheLockResult(USER_ID, OUT_TRADE_NO, order, 15);
        MarketPayOrderEntity cached = fixture.port.queryLockResult(USER_ID, OUT_TRADE_NO);

        Assert.assertEquals("order_001", cached.getOrderId());
        Assert.assertEquals("team_001", cached.getTeamId());
        Assert.assertEquals(TimeUnit.HOURS.toMillis(24), fixture.redis.lastSetValueExpired);
        Assert.assertTrue(fixture.redis.values.containsKey("group_buy_market_lock_result_key_user_001_trade_001"));
    }

    @Test
    public void releaseAndRemoveShouldDeleteSeparateKeys() {
        Fixture fixture = new Fixture();

        fixture.port.releaseLockRequest(USER_ID, OUT_TRADE_NO);
        fixture.port.removeLockResult(USER_ID, OUT_TRADE_NO);

        Assert.assertTrue(fixture.redis.removedKeys.contains("group_buy_market_locking_key_user_001_trade_001"));
        Assert.assertTrue(fixture.redis.removedKeys.contains("group_buy_market_lock_result_key_user_001_trade_001"));
    }

    @Test
    public void blankRemoveLockResultShouldSkipRedisCall() {
        Fixture fixture = new Fixture();

        fixture.port.removeLockResult("", OUT_TRADE_NO);

        Assert.assertTrue(fixture.redis.removedKeys.isEmpty());
    }

    private static void setField(Object target, String fieldName, Object value) {
        try {
            Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static class Fixture {
        private final TradeLockRequestPort port = new TradeLockRequestPort();
        private final GroupBuyLockRequestSupport lockRequestSupport = new GroupBuyLockRequestSupport();
        private final GroupBuyLockResultCacheSupport resultCacheSupport = new GroupBuyLockResultCacheSupport();
        private final RedisStub redis = new RedisStub();

        private Fixture() {
            IRedisService redisService = redis.proxy();
            setField(port, "groupBuyLockRequestSupport", lockRequestSupport);
            setField(port, "groupBuyLockResultCacheSupport", resultCacheSupport);
            setField(lockRequestSupport, "redisService", redisService);
            setField(resultCacheSupport, "redisService", redisService);
        }
    }

    private static class RedisStub implements InvocationHandler {
        private final Map<String, Object> values = new HashMap<>();
        private final List<String> removedKeys = new ArrayList<>();
        private String lastSetNxKey;
        private long lastSetNxExpired;
        private TimeUnit lastSetNxTimeUnit;
        private long lastSetValueExpired;

        private IRedisService proxy() {
            return (IRedisService) Proxy.newProxyInstance(
                    IRedisService.class.getClassLoader(),
                    new Class[]{IRedisService.class},
                    this);
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            String methodName = method.getName();
            if ("setNx".equals(methodName)) {
                lastSetNxKey = (String) args[0];
                lastSetNxExpired = (Long) args[1];
                lastSetNxTimeUnit = (TimeUnit) args[2];
                return true;
            }
            if ("setValue".equals(methodName)) {
                values.put((String) args[0], args[1]);
                if (args.length > 2) {
                    lastSetValueExpired = (Long) args[2];
                }
                return null;
            }
            if ("getValue".equals(methodName)) {
                return values.get((String) args[0]);
            }
            if ("remove".equals(methodName)) {
                String key = (String) args[0];
                removedKeys.add(key);
                values.remove(key);
                return null;
            }
            if (method.getReturnType().equals(Boolean.TYPE)) {
                return false;
            }
            if (method.getReturnType().equals(Long.TYPE)) {
                return 0L;
            }
            if (method.getReturnType().equals(Integer.TYPE)) {
                return 0;
            }
            return null;
        }
    }

}
