package cn.bugstack.infrastructure.adapter.port;

import cn.bugstack.domain.seckill.adapter.port.ISeckillOrderIdPort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Keeps the existing 12-digit order id schema while avoiding random collisions in a single JVM.
 */
@Component
public class SeckillOrderIdPort implements ISeckillOrderIdPort {

    private static final long DEFAULT_EPOCH_MILLIS = 1767225600000L;
    private static final long MAX_ORDER_ID = 999999999999L;

    @Value("${app.seckill.order-id.epoch-millis:1767225600000}")
    private Long epochMillis;

    private final AtomicLong lastOrderSequence = new AtomicLong(0L);

    @Override
    public String nextOrderId() {
        long candidate = Math.max(0L, System.currentTimeMillis() - safeEpochMillis());
        long next = lastOrderSequence.updateAndGet(last -> candidate > last ? candidate : last + 1L);
        if (next > MAX_ORDER_ID) {
            throw new IllegalStateException("seckill order id exceeded 12 digit compatibility range");
        }
        return String.format("%012d", next);
    }

    private long safeEpochMillis() {
        return null == epochMillis ? DEFAULT_EPOCH_MILLIS : epochMillis;
    }

}
