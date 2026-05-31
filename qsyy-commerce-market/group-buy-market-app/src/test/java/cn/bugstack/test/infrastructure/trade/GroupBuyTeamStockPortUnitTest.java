package cn.bugstack.test.infrastructure.trade;

import cn.bugstack.infrastructure.adapter.port.GroupBuyTeamStockPort;
import cn.bugstack.infrastructure.adapter.support.GroupBuyTeamStockRecoverySupport;
import cn.bugstack.infrastructure.adapter.support.GroupBuyTeamStockReservationSupport;
import cn.bugstack.infrastructure.redis.IRedisService;
import org.junit.Assert;
import org.junit.Test;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class GroupBuyTeamStockPortUnitTest {

    @Test
    public void occupyTeamStockShouldDelegateReserveTeamStockWithTtl() {
        Fixture fixture = new Fixture();
        fixture.redis.reserveResult = 2L;

        long result = fixture.port.occupyTeamStock("team_stock", "recovery_stock", "user_occupy", "trade_001", 3, 20);

        Assert.assertEquals(2L, result);
        Assert.assertEquals("team_stock", fixture.redis.lastReserveTeamStockKey);
        Assert.assertEquals("recovery_stock", fixture.redis.lastReserveRecoveryKey);
        Assert.assertEquals("team_stock_", fixture.redis.lastReservePrefix);
        Assert.assertEquals("user_occupy", fixture.redis.lastReserveUserKey);
        Assert.assertEquals("trade_001", fixture.redis.lastReserveOutTradeNo);
        Assert.assertEquals(3, fixture.redis.lastReserveTarget);
        Assert.assertEquals(80L, fixture.redis.lastReserveTtl);
        Assert.assertEquals(TimeUnit.MINUTES, fixture.redis.lastReserveTimeUnit);
    }

    @Test
    public void recoveryTeamStockShouldIncrementRecoveryKey() {
        Fixture fixture = new Fixture();

        fixture.port.recoveryTeamStock("recovery_stock", 10);

        Assert.assertEquals("recovery_stock", fixture.redis.incrKeys.get(0));
    }

    @Test
    public void releaseUserTeamOccupyShouldRemoveUserKey() {
        Fixture fixture = new Fixture();

        fixture.port.releaseUserTeamOccupy("user_occupy");

        Assert.assertEquals("user_occupy", fixture.redis.removedKeys.get(0));
    }

    @Test
    public void refundRecoveryShouldAcquireIdempotentLockBeforeIncrement() {
        Fixture fixture = new Fixture();

        fixture.port.refund2AddRecovery("recovery_stock", "order_001");

        Assert.assertEquals("refund_lock_order_001", fixture.redis.lastSetNxKey);
        Assert.assertEquals(TimeUnit.DAYS.toMillis(30), fixture.redis.lastSetNxExpired);
        Assert.assertEquals("recovery_stock", fixture.redis.incrKeys.get(0));
    }

    @Test
    public void duplicateRefundRecoveryShouldSkipIncrement() {
        Fixture fixture = new Fixture();
        fixture.redis.setNxResult = false;

        fixture.port.refund2AddRecovery("recovery_stock", "order_001");

        Assert.assertTrue(fixture.redis.incrKeys.isEmpty());
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
        private final GroupBuyTeamStockPort port = new GroupBuyTeamStockPort();
        private final GroupBuyTeamStockReservationSupport reservationSupport = new GroupBuyTeamStockReservationSupport();
        private final GroupBuyTeamStockRecoverySupport recoverySupport = new GroupBuyTeamStockRecoverySupport();
        private final RedisStub redis = new RedisStub();

        private Fixture() {
            IRedisService redisService = redis.proxy();
            setField(port, "groupBuyTeamStockReservationSupport", reservationSupport);
            setField(port, "groupBuyTeamStockRecoverySupport", recoverySupport);
            setField(reservationSupport, "redisService", redisService);
            setField(recoverySupport, "redisService", redisService);
        }
    }

    private static class RedisStub implements InvocationHandler {
        private long reserveResult = 1L;
        private boolean setNxResult = true;
        private String lastReserveTeamStockKey;
        private String lastReserveRecoveryKey;
        private String lastReservePrefix;
        private String lastReserveUserKey;
        private String lastReserveOutTradeNo;
        private int lastReserveTarget;
        private long lastReserveTtl;
        private TimeUnit lastReserveTimeUnit;
        private String lastSetNxKey;
        private long lastSetNxExpired;
        private final List<String> incrKeys = new ArrayList<>();
        private final List<String> removedKeys = new ArrayList<>();

        private IRedisService proxy() {
            return (IRedisService) Proxy.newProxyInstance(
                    IRedisService.class.getClassLoader(),
                    new Class[]{IRedisService.class},
                    this);
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            String methodName = method.getName();
            if ("reserveTeamStock".equals(methodName)) {
                lastReserveTeamStockKey = (String) args[0];
                lastReserveRecoveryKey = (String) args[1];
                lastReservePrefix = (String) args[2];
                lastReserveUserKey = (String) args[3];
                lastReserveOutTradeNo = (String) args[4];
                lastReserveTarget = (Integer) args[5];
                lastReserveTtl = (Long) args[6];
                lastReserveTimeUnit = (TimeUnit) args[7];
                return reserveResult;
            }
            if ("setNx".equals(methodName)) {
                lastSetNxKey = (String) args[0];
                lastSetNxExpired = (Long) args[1];
                return setNxResult;
            }
            if ("incr".equals(methodName)) {
                incrKeys.add((String) args[0]);
                return 1L;
            }
            if ("remove".equals(methodName)) {
                removedKeys.add((String) args[0]);
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
