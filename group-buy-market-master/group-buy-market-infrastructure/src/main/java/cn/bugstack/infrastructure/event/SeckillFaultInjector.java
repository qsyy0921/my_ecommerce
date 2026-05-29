package cn.bugstack.infrastructure.event;

import cn.bugstack.types.enums.ResponseCode;
import cn.bugstack.types.exception.AppException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.ThreadLocalRandom;

@Component
public class SeckillFaultInjector {

    @Value("${app.seckill.fault-injection.enabled:false}")
    private Boolean enabled;

    @Value("${app.seckill.fault-injection.redis-jitter-rate:0}")
    private Double redisJitterRate;

    @Value("${app.seckill.fault-injection.redis-jitter-millis:0}")
    private Long redisJitterMillis;

    @Value("${app.seckill.fault-injection.db-batch-failure-rate:0}")
    private Double dbBatchFailureRate;

    @Value("${app.seckill.fault-injection.stream-ack-failure-rate:0}")
    private Double streamAckFailureRate;

    public void beforeRedisReserve() {
        if (!enabled()) {
            return;
        }
        if (hit(redisJitterRate) && null != redisJitterMillis && redisJitterMillis > 0) {
            sleep(redisJitterMillis);
        }
    }

    public void beforeBatchInsert() {
        if (enabled() && hit(dbBatchFailureRate)) {
            throw new AppException(ResponseCode.UN_ERROR.getCode(), "fault injection: db batch insert failed");
        }
    }

    public void beforeStreamAck() {
        if (enabled() && hit(streamAckFailureRate)) {
            throw new AppException(ResponseCode.UN_ERROR.getCode(), "fault injection: stream ack failed");
        }
    }

    private boolean enabled() {
        return Boolean.TRUE.equals(enabled);
    }

    private boolean hit(Double rate) {
        return null != rate && rate > 0 && ThreadLocalRandom.current().nextDouble() < rate;
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AppException(ResponseCode.UN_ERROR.getCode(), "fault injection interrupted");
        }
    }

}
