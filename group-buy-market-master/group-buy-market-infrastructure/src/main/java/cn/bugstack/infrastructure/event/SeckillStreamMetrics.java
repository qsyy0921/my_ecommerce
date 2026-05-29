package cn.bugstack.infrastructure.event;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class SeckillStreamMetrics {

    private static final String TAG_STREAM = "stream";

    @Autowired(required = false)
    private MeterRegistry meterRegistry;

    private final ConcurrentHashMap<String, Counter> counters = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Timer> timers = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, DistributionSummary> summaries = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AtomicLong> gauges = new ConcurrentHashMap<>();

    public void recordEnqueue(String streamKey) {
        counter("seckill_stream_enqueue_total", streamKey).increment();
    }

    public void recordPendingClaim(String streamKey, int size) {
        counter("seckill_stream_pending_claim_total", streamKey).increment(size);
    }

    public void recordAck(String streamKey, int size) {
        counter("seckill_stream_ack_total", streamKey).increment(size);
    }

    public void recordFail(String streamKey, int size) {
        counter("seckill_stream_consume_fail_total", streamKey).increment(size);
    }

    public void recordDlq(String streamKey, int size) {
        counter("seckill_stream_dlq_total", streamKey).increment(size);
    }

    public void recordConsumeBatch(long nanos, int size) {
        timer("seckill_stream_consume_batch_seconds", "all").record(nanos, TimeUnit.NANOSECONDS);
        summary("seckill_stream_consume_batch_size", "all").record(size);
    }

    public void recordBatchInsert(long nanos, int size) {
        timer("seckill_order_batch_insert_seconds", "all").record(nanos, TimeUnit.NANOSECONDS);
        summary("seckill_order_batch_insert_size", "all").record(size);
    }

    public void setPending(String streamKey, long value) {
        gauge("seckill_stream_pending_messages", streamKey).set(value);
    }

    public void setLag(String streamKey, long value) {
        gauge("seckill_stream_lag_messages", streamKey).set(value);
    }

    private Counter counter(String name, String streamKey) {
        String key = name + "|" + streamKey;
        Counter counter = counters.get(key);
        if (null != counter) {
            return counter;
        }
        if (null == meterRegistry) {
            return Counter.builder(name).register(new io.micrometer.core.instrument.simple.SimpleMeterRegistry());
        }
        Counter created = Counter.builder(name).tag(TAG_STREAM, streamKey).register(meterRegistry);
        Counter previous = counters.putIfAbsent(key, created);
        return null == previous ? created : previous;
    }

    private Timer timer(String name, String streamKey) {
        String key = name + "|" + streamKey;
        Timer timer = timers.get(key);
        if (null != timer) {
            return timer;
        }
        if (null == meterRegistry) {
            return Timer.builder(name).register(new io.micrometer.core.instrument.simple.SimpleMeterRegistry());
        }
        Timer created = Timer.builder(name).tag(TAG_STREAM, streamKey).register(meterRegistry);
        Timer previous = timers.putIfAbsent(key, created);
        return null == previous ? created : previous;
    }

    private DistributionSummary summary(String name, String streamKey) {
        String key = name + "|" + streamKey;
        DistributionSummary summary = summaries.get(key);
        if (null != summary) {
            return summary;
        }
        if (null == meterRegistry) {
            return DistributionSummary.builder(name).register(new io.micrometer.core.instrument.simple.SimpleMeterRegistry());
        }
        DistributionSummary created = DistributionSummary.builder(name).tag(TAG_STREAM, streamKey).register(meterRegistry);
        DistributionSummary previous = summaries.putIfAbsent(key, created);
        return null == previous ? created : previous;
    }

    private AtomicLong gauge(String name, String streamKey) {
        String key = name + "|" + streamKey;
        AtomicLong gauge = gauges.get(key);
        if (null != gauge) {
            return gauge;
        }
        AtomicLong created = new AtomicLong(0);
        if (null != meterRegistry) {
            Gauge.builder(name, created, AtomicLong::get).tag(TAG_STREAM, streamKey).register(meterRegistry);
        }
        AtomicLong previous = gauges.putIfAbsent(key, created);
        return null == previous ? created : previous;
    }

}
