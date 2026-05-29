package cn.bugstack.infrastructure.adapter.port;

import cn.bugstack.domain.seckill.adapter.port.ISeckillMetricsPort;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Micrometer metrics for seckill entrance business outcomes.
 */
@Component
public class SeckillMetricsPort implements ISeckillMetricsPort {

    @Autowired(required = false)
    private MeterRegistry meterRegistry;

    private final ConcurrentHashMap<String, Counter> counters = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Timer> timers = new ConcurrentHashMap<>();

    @Override
    public void recordLock(long nanos, String outcome) {
        timer("seckill_lock_seconds", null == outcome ? "unknown" : outcome).record(nanos, TimeUnit.NANOSECONDS);
    }

    @Override
    public void recordRateLimited() {
        counter("seckill_rate_limited_total", "entrance").increment();
    }

    @Override
    public void recordStockNotEnough() {
        counter("seckill_stock_not_enough_total", "stock").increment();
    }

    @Override
    public void recordDuplicate() {
        counter("seckill_duplicate_total", "user").increment();
    }

    private Counter counter(String name, String reason) {
        String key = name + "|" + reason;
        Counter counter = counters.get(key);
        if (null != counter) {
            return counter;
        }
        Counter created = null == meterRegistry
                ? Counter.builder(name).register(new io.micrometer.core.instrument.simple.SimpleMeterRegistry())
                : Counter.builder(name).tag("reason", reason).register(meterRegistry);
        Counter previous = counters.putIfAbsent(key, created);
        return null == previous ? created : previous;
    }

    private Timer timer(String name, String outcome) {
        String key = name + "|" + outcome;
        Timer timer = timers.get(key);
        if (null != timer) {
            return timer;
        }
        Timer created = null == meterRegistry
                ? Timer.builder(name).register(new io.micrometer.core.instrument.simple.SimpleMeterRegistry())
                : Timer.builder(name).tag("outcome", outcome).register(meterRegistry);
        Timer previous = timers.putIfAbsent(key, created);
        return null == previous ? created : previous;
    }

}
