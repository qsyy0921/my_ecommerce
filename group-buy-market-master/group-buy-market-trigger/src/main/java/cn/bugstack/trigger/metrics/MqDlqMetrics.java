package cn.bugstack.trigger.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

@Component
public class MqDlqMetrics {

    @Autowired(required = false)
    private MeterRegistry meterRegistry;

    private final ConcurrentHashMap<String, Counter> counters = new ConcurrentHashMap<>();

    public void record(String queueName) {
        counter(null == queueName || queueName.trim().isEmpty() ? "unknown" : queueName.trim()).increment();
    }

    private Counter counter(String queueName) {
        Counter counter = counters.get(queueName);
        if (null != counter) {
            return counter;
        }
        Counter created = null == meterRegistry
                ? Counter.builder("market_mq_dlq_total").register(new io.micrometer.core.instrument.simple.SimpleMeterRegistry())
                : Counter.builder("market_mq_dlq_total").tag("queue", queueName).register(meterRegistry);
        Counter previous = counters.putIfAbsent(queueName, created);
        return null == previous ? created : previous;
    }

}
