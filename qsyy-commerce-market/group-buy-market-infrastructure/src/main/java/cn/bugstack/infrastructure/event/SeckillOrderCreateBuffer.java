package cn.bugstack.infrastructure.event;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class SeckillOrderCreateBuffer {

    public static final String MODE_MQ = "mq";
    public static final String MODE_REDIS_QUEUE = "redis_queue";
    public static final String MODE_REDIS_STREAM = "redis_stream";
    public static final String MODE_LOCAL_QUEUE = "local_queue";

    @Value("${app.seckill.order-create-buffer.mode:mq}")
    private String mode;

    @Value("${app.seckill.order-create-buffer.local-capacity:20000}")
    private Integer localCapacity;

    @Resource
    private SeckillLocalOrderCreateBuffer localOrderCreateBuffer;

    @Resource
    private SeckillRedisQueueOrderCreateBuffer redisQueueOrderCreateBuffer;

    @Resource
    private SeckillRedisStreamOrderCreateBuffer redisStreamOrderCreateBuffer;

    @PostConstruct
    public void init() {
        localOrderCreateBuffer.init(localCapacity);
        if (useRedisStream()) {
            redisStreamOrderCreateBuffer.init();
        }
        log.info("seckill order create buffer init mode:{} localCapacity:{} redisQueueKey:{} stream:{}",
                mode(), localCapacity, redisQueueOrderCreateBuffer.redisQueueKey(), redisStreamOrderCreateBuffer.description());
    }

    public String mode() {
        return null == mode || mode.trim().isEmpty() ? MODE_MQ : mode.trim().toLowerCase();
    }

    public boolean useLocalQueue() {
        return MODE_LOCAL_QUEUE.equals(mode());
    }

    public boolean useRedisQueue() {
        return MODE_REDIS_QUEUE.equals(mode());
    }

    public boolean useRedisStream() {
        return MODE_REDIS_STREAM.equals(mode());
    }

    public boolean useMq() {
        return MODE_MQ.equals(mode());
    }

    public boolean offer(String message) {
        return offer(message, message);
    }

    public boolean offer(String message, String routeKey) {
        if (useLocalQueue()) {
            return localOrderCreateBuffer.offer(message);
        }
        if (useRedisQueue()) {
            return redisQueueOrderCreateBuffer.offer(message);
        }
        if (useRedisStream()) {
            return redisStreamOrderCreateBuffer.offer(message, routeKey);
        }
        return false;
    }

    public List<SeckillOrderBufferMessage> pollBatch(String consumerName, int batchSize, long timeout, TimeUnit timeUnit) throws InterruptedException {
        if (useLocalQueue()) {
            return localOrderCreateBuffer.pollBatch(batchSize, timeout, timeUnit);
        }
        if (useRedisQueue()) {
            return redisQueueOrderCreateBuffer.pollBatch(batchSize, timeout, timeUnit);
        }
        if (useRedisStream()) {
            return redisStreamOrderCreateBuffer.pollBatch(consumerName, batchSize, timeout, timeUnit);
        }
        return Collections.emptyList();
    }

    public void ack(List<SeckillOrderBufferMessage> messages) {
        if (useRedisStream()) {
            redisStreamOrderCreateBuffer.ack(messages);
        }
    }

    public void fail(List<SeckillOrderBufferMessage> messages, Exception exception) {
        if (useRedisStream()) {
            redisStreamOrderCreateBuffer.fail(messages, exception);
        }
    }

    public int localSize() {
        return localOrderCreateBuffer.size();
    }

    public String redisQueueKey() {
        return redisQueueOrderCreateBuffer.redisQueueKey();
    }

}
