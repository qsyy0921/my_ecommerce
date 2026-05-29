package cn.bugstack.trigger.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class PaymentCallbackMetrics {

    @Resource
    private MeterRegistry meterRegistry;

    private final Map<String, Counter> failCounterMap = new ConcurrentHashMap<>();

    public void recordFail(String reason) {
        failCounter(reason).increment();
    }

    private Counter failCounter(String reason) {
        Counter counter = failCounterMap.get(reason);
        if (null != counter) {
            return counter;
        }
        Counter created = Counter.builder("mall_payment_callback_fail_total")
                .tag("reason", reason)
                .register(meterRegistry);
        Counter previous = failCounterMap.putIfAbsent(reason, created);
        return null == previous ? created : previous;
    }

}
