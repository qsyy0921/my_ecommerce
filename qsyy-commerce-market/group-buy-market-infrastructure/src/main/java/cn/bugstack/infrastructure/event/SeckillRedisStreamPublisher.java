package cn.bugstack.infrastructure.event;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

@Component
public class SeckillRedisStreamPublisher {

    @Value("${app.seckill.order-create-buffer.stream-max-len:0}")
    private Integer streamMaxLen;

    @Resource
    private SeckillRedisStreamRegistry registry;

    @Resource
    private SeckillStreamMessageMapper seckillStreamMessageMapper;

    @Resource
    private SeckillStreamMetrics seckillStreamMetrics;

    public boolean offer(String message, String routeKey) {
        registry.init();
        int shardIndex = registry.shardOf(routeKey);
        registry.stream(shardIndex).add(seckillStreamMessageMapper.toOrderMessage(message, streamMaxLen));
        seckillStreamMetrics.recordEnqueue(registry.streamKey(shardIndex));
        return true;
    }

}
