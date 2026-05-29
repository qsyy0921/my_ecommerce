package cn.bugstack.config;

import cn.bugstack.infrastructure.dao.INotifyTaskDao;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.annotation.Configuration;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;

@Configuration
public class TradeNotifyMetricsConfig {

    @Resource
    private MeterRegistry meterRegistry;
    @Resource
    private INotifyTaskDao notifyTaskDao;

    @PostConstruct
    public void init() {
        Gauge.builder("market_notify_task_pending", notifyTaskDao, INotifyTaskDao::countPendingNotifyTask)
                .register(meterRegistry);
        Gauge.builder("market_notify_task_failed", notifyTaskDao, INotifyTaskDao::countFailedNotifyTask)
                .register(meterRegistry);
    }

}
