package cn.bugstack.test.infrastructure.seckill;

import cn.bugstack.infrastructure.adapter.port.SeckillOrderIdPort;
import org.junit.Assert;
import org.junit.Test;

import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.Set;

public class SeckillOrderIdPortUnitTest {

    @Test
    public void nextOrderIdShouldReturnTwelveDigitNumericIds() {
        SeckillOrderIdPort port = new SeckillOrderIdPort();
        setField(port, "epochMillis", 1767225600000L);

        String orderId = port.nextOrderId();

        Assert.assertEquals(12, orderId.length());
        Assert.assertTrue(orderId.matches("\\d{12}"));
    }

    @Test
    public void nextOrderIdShouldBeUniqueWithinSingleJvm() {
        SeckillOrderIdPort port = new SeckillOrderIdPort();
        setField(port, "epochMillis", 1767225600000L);

        Set<String> orderIds = new HashSet<>();
        for (int i = 0; i < 5000; i++) {
            orderIds.add(port.nextOrderId());
        }

        Assert.assertEquals(5000, orderIds.size());
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

}
