package cn.bugstack.test.infrastructure.seckill;

import cn.bugstack.infrastructure.adapter.port.SeckillRateLimitPort;
import cn.bugstack.infrastructure.adapter.support.SeckillFixedWindowRateLimitSupport;
import org.junit.Assert;
import org.junit.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

public class SeckillRateLimitPortUnitTest {

    @Test
    public void disabledRateLimitShouldBypassAllDimensions() {
        Fixture fixture = new Fixture();
        fixture.enabled = false;
        fixture.apply();

        boolean allowed = fixture.port.tryAcquire(100001L, "user_001", "127.0.0.1");

        Assert.assertTrue(allowed);
        Assert.assertTrue(fixture.support.dimensions.isEmpty());
    }

    @Test
    public void activityDeniedShouldShortCircuitUserAndIpDimensions() {
        Fixture fixture = new Fixture();
        fixture.support.nextResults.add(false);
        fixture.apply();

        boolean allowed = fixture.port.tryAcquire(100001L, "user_001", "127.0.0.1");

        Assert.assertFalse(allowed);
        Assert.assertEquals(1, fixture.support.dimensions.size());
        Assert.assertEquals("activity:100001", fixture.support.dimensions.get(0));
    }

    @Test
    public void userDeniedShouldShortCircuitIpDimension() {
        Fixture fixture = new Fixture();
        fixture.support.nextResults.add(true);
        fixture.support.nextResults.add(false);
        fixture.apply();

        boolean allowed = fixture.port.tryAcquire(100001L, " user_001 ", "127.0.0.1");

        Assert.assertFalse(allowed);
        Assert.assertEquals(2, fixture.support.dimensions.size());
        Assert.assertEquals("activity:100001", fixture.support.dimensions.get(0));
        Assert.assertEquals("user:100001:user_001", fixture.support.dimensions.get(1));
    }

    @Test
    public void allDimensionsAllowedShouldUseUnknownForBlankValues() {
        Fixture fixture = new Fixture();
        fixture.support.nextResults.add(true);
        fixture.support.nextResults.add(true);
        fixture.support.nextResults.add(true);
        fixture.apply();

        boolean allowed = fixture.port.tryAcquire(100001L, " ", null);

        Assert.assertTrue(allowed);
        Assert.assertEquals(3, fixture.support.dimensions.size());
        Assert.assertEquals("user:100001:unknown", fixture.support.dimensions.get(1));
        Assert.assertEquals("ip:100001:unknown", fixture.support.dimensions.get(2));
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
        private final SeckillRateLimitPort port = new SeckillRateLimitPort();
        private final FakeFixedWindowRateLimitSupport support = new FakeFixedWindowRateLimitSupport();
        private Boolean enabled = true;

        private void apply() {
            setField(port, "enabled", enabled);
            setField(port, "activityWindowSeconds", 1);
            setField(port, "activityMax", 800);
            setField(port, "userWindowSeconds", 1);
            setField(port, "userMax", 3);
            setField(port, "ipWindowSeconds", 1);
            setField(port, "ipMax", 200);
            setField(port, "seckillFixedWindowRateLimitSupport", support);
        }
    }

    private static class FakeFixedWindowRateLimitSupport extends SeckillFixedWindowRateLimitSupport {
        private final List<Boolean> nextResults = new ArrayList<>();
        private final List<String> dimensions = new ArrayList<>();

        @Override
        public boolean acquire(String dimension, Integer windowSecondsConfig, Integer maxConfig) {
            dimensions.add(dimension);
            if (nextResults.isEmpty()) {
                return true;
            }
            return nextResults.remove(0);
        }
    }

}
