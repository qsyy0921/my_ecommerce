package cn.bugstack.infrastructure.event;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Component
public class SeckillRedisStreamOrderCreateBuffer {

    @Resource
    private SeckillRedisStreamRegistry registry;

    @Resource
    private SeckillRedisStreamPublisher publisher;

    @Resource
    private SeckillRedisStreamReader reader;

    @Resource
    private SeckillRedisStreamAcknowledger acknowledger;

    @Resource
    private SeckillRedisStreamFailureIsolator failureIsolator;

    @Resource
    private SeckillStreamMetricsSampler seckillStreamMetricsSampler;

    public void init() {
        registry.init();
    }

    public boolean offer(String message, String routeKey) {
        return publisher.offer(message, routeKey);
    }

    public List<SeckillOrderBufferMessage> pollBatch(String consumerName, int batchSize, long timeout, TimeUnit timeUnit) {
        return reader.pollBatch(consumerName, batchSize, timeout, timeUnit);
    }

    public void ack(List<SeckillOrderBufferMessage> messages) {
        acknowledger.ack(messages);
    }

    public void fail(List<SeckillOrderBufferMessage> messages, Exception exception) {
        failureIsolator.fail(messages, exception);
    }

    @Scheduled(fixedDelayString = "${app.seckill.order-create-buffer.metrics-sample-interval-millis:5000}")
    public void sampleStreamMetrics() {
        registry.sample(seckillStreamMetricsSampler);
    }

    public String description() {
        return registry.description();
    }

}
