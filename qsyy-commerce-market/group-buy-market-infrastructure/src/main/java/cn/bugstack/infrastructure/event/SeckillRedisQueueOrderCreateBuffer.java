package cn.bugstack.infrastructure.event;

import cn.bugstack.infrastructure.redis.IRedisService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Component
public class SeckillRedisQueueOrderCreateBuffer {

    @Value("${app.seckill.order-create-buffer.redis-queue-key:seckill:order:create:queue}")
    private String redisQueueKey;

    @Resource
    private IRedisService redisService;

    public boolean offer(String message) {
        return redisService.<String>getBlockingQueue(redisQueueKey).offer(message);
    }

    public List<SeckillOrderBufferMessage> pollBatch(int batchSize, long timeout, TimeUnit timeUnit) throws InterruptedException {
        int size = Math.max(1, batchSize);
        String message = redisService.<String>getBlockingQueue(redisQueueKey).poll(timeout, timeUnit);
        if (null == message) {
            return Collections.emptyList();
        }
        List<SeckillOrderBufferMessage> result = new ArrayList<>(size);
        result.add(new SeckillOrderBufferMessage(null, message, -1, null));
        for (int i = 1; i < size; i++) {
            String item = redisService.<String>getBlockingQueue(redisQueueKey).poll();
            if (null == item) {
                break;
            }
            result.add(new SeckillOrderBufferMessage(null, item, -1, null));
        }
        return result;
    }

    public String redisQueueKey() {
        return redisQueueKey;
    }

}
