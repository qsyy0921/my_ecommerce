package cn.bugstack.infrastructure.event;

import org.redisson.api.StreamMessageId;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class SeckillRedisStreamAcknowledger {

    @Resource
    private SeckillRedisStreamRegistry registry;

    @Resource
    private SeckillStreamMetrics seckillStreamMetrics;

    public void ack(List<SeckillOrderBufferMessage> messages) {
        if (null == messages || messages.isEmpty()) {
            return;
        }
        registry.init();
        Map<Integer, List<StreamMessageId>> shardIdMap = groupMessageIds(messages);
        for (Map.Entry<Integer, List<StreamMessageId>> entry : shardIdMap.entrySet()) {
            StreamMessageId[] ids = entry.getValue().toArray(new StreamMessageId[0]);
            registry.stream(entry.getKey()).ack(registry.streamGroup(), ids);
            registry.stream(entry.getKey()).remove(ids);
            seckillStreamMetrics.recordAck(registry.streamKey(entry.getKey()), ids.length);
            for (StreamMessageId id : ids) {
                registry.deleteRetryKey(entry.getKey(), id);
            }
        }
    }

    private Map<Integer, List<StreamMessageId>> groupMessageIds(List<SeckillOrderBufferMessage> messages) {
        Map<Integer, List<StreamMessageId>> shardIdMap = new HashMap<>();
        for (SeckillOrderBufferMessage message : messages) {
            if (null == message.getStreamMessageId() || message.getStreamIndex() < 0) {
                continue;
            }
            shardIdMap.computeIfAbsent(message.getStreamIndex(), key -> new ArrayList<>()).add(message.getStreamMessageId());
        }
        return shardIdMap;
    }

}
