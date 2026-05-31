package cn.bugstack.domain.seckill.adapter.port;

/**
 * Seckill business metrics port.
 */
public interface ISeckillMetricsPort {

    void recordLock(long nanos, String outcome);

    void recordRateLimited();

    void recordStockNotEnough();

    void recordDuplicate();

}
