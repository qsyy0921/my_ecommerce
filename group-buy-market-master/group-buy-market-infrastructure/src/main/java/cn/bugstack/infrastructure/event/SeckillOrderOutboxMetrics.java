package cn.bugstack.infrastructure.event;

import cn.bugstack.domain.seckill.model.entity.SeckillOrderOutboxEntity;
import cn.bugstack.infrastructure.dao.ISeckillOrderOutboxDao;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Micrometer metrics for seckill order reliable outbox operations.
 */
@Component
public class SeckillOrderOutboxMetrics {

    @Autowired(required = false)
    private MeterRegistry meterRegistry;
    @Resource
    private ISeckillOrderOutboxDao seckillOrderOutboxDao;

    private final ConcurrentHashMap<String, Timer> timers = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        registerStatusGauge(SeckillOrderOutboxEntity.STATUS_INIT);
        registerStatusGauge(SeckillOrderOutboxEntity.STATUS_SENT);
        registerStatusGauge(SeckillOrderOutboxEntity.STATUS_FAILED);
        registerStatusGauge(SeckillOrderOutboxEntity.STATUS_DEAD);
    }

    public void recordRetry(String mode, String outcome, long nanos) {
        timer("market_seckill_order_outbox_retry_seconds", mode, outcome).record(nanos, TimeUnit.NANOSECONDS);
    }

    private void registerStatusGauge(final int status) {
        if (null == meterRegistry) {
            return;
        }
        Gauge.builder("market_seckill_order_outbox_messages", this, metrics -> metrics.countByStatus(status))
                .tag("status", SeckillOrderOutboxEntity.statusName(status))
                .register(meterRegistry);
    }

    private double countByStatus(Integer status) {
        try {
            return seckillOrderOutboxDao.countByStatus(status);
        } catch (Exception e) {
            return 0;
        }
    }

    private Timer timer(String name, String mode, String outcome) {
        String key = name + "|" + mode + "|" + outcome;
        Timer timer = timers.get(key);
        if (null != timer) {
            return timer;
        }
        Timer created = null == meterRegistry
                ? Timer.builder(name).register(new io.micrometer.core.instrument.simple.SimpleMeterRegistry())
                : Timer.builder(name).tag("mode", mode).tag("outcome", outcome).register(meterRegistry);
        Timer previous = timers.putIfAbsent(key, created);
        return null == previous ? created : previous;
    }

}
