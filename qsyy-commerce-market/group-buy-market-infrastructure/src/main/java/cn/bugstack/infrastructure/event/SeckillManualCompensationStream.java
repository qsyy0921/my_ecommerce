package cn.bugstack.infrastructure.event;

import cn.bugstack.domain.seckill.model.entity.SeckillManualMessageEntity;
import org.redisson.api.RStream;
import org.redisson.api.RedissonClient;
import org.redisson.api.StreamMessageId;
import org.redisson.client.codec.StringCodec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 秒杀人工补偿 Stream 支撑组件，集中处理隔离消息的读写和删除。
 */
@Component
public class SeckillManualCompensationStream {

    @Value("${app.seckill.order-create-buffer.dead-stream-key:seckill:order:create:manual}")
    private String deadStreamKey;

    @Resource
    private RedissonClient redissonClient;

    @Resource
    private SeckillStreamMessageMapper seckillStreamMessageMapper;

    private RStream<String, String> deadStream;

    @PostConstruct
    public void init() {
        deadStream = redissonClient.getStream(deadStreamKey, StringCodec.INSTANCE);
    }

    public void isolate(SeckillOrderBufferMessage message, long retryCount, Exception exception) {
        stream().add(seckillStreamMessageMapper.toManualMessage(message, retryCount, exception));
    }

    public List<SeckillManualMessageEntity> queryMessages(int limit) {
        Map<StreamMessageId, Map<String, String>> entries = stream().range(Math.max(1, limit), StreamMessageId.MIN, StreamMessageId.MAX);
        if (null == entries || entries.isEmpty()) {
            return Collections.emptyList();
        }
        List<SeckillManualMessageEntity> result = new ArrayList<>(entries.size());
        for (Map.Entry<StreamMessageId, Map<String, String>> entry : entries.entrySet()) {
            SeckillManualMessageEntity deadMessage = seckillStreamMessageMapper.toDeadMessage(entry.getKey(), entry.getValue());
            if (null != deadMessage) {
                result.add(deadMessage);
            }
        }
        return result;
    }

    public SeckillManualMessageEntity queryMessage(String messageId) {
        if (null == messageId || messageId.trim().isEmpty()) {
            return null;
        }
        StreamMessageId streamMessageId = seckillStreamMessageMapper.parseStreamMessageId(messageId);
        Map<StreamMessageId, Map<String, String>> entries = stream().range(1, streamMessageId, streamMessageId);
        if (null == entries || entries.isEmpty()) {
            return null;
        }
        Map.Entry<StreamMessageId, Map<String, String>> entry = entries.entrySet().iterator().next();
        return seckillStreamMessageMapper.toDeadMessage(entry.getKey(), entry.getValue());
    }

    public void remove(String messageId) {
        stream().remove(seckillStreamMessageMapper.parseStreamMessageId(messageId));
    }

    public String streamKey() {
        return deadStreamKey;
    }

    private RStream<String, String> stream() {
        if (null == deadStream) {
            deadStream = redissonClient.getStream(deadStreamKey, StringCodec.INSTANCE);
        }
        return deadStream;
    }

}
