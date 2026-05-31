package cn.bugstack.infrastructure.event;

import org.redisson.api.StreamMessageId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class SeckillRedisStreamFailureIsolator {

    @Value("${app.seckill.order-create-buffer.pending-max-retry:5}")
    private Integer pendingMaxRetry;

    @Resource
    private SeckillRedisStreamRegistry registry;

    @Resource
    private SeckillStreamMetrics seckillStreamMetrics;

    @Resource
    private SeckillManualCompensationStream seckillManualCompensationStream;

    private final SeckillPendingRetryPolicy pendingRetryPolicy = new SeckillPendingRetryPolicy();

    public void fail(List<SeckillOrderBufferMessage> messages, Exception exception) {
        if (null == messages || messages.isEmpty()) {
            return;
        }
        registry.init();
        Map<Integer, List<SeckillOrderBufferMessage>> isolatedMessages = new HashMap<>();
        for (SeckillOrderBufferMessage message : messages) {
            if (null == message.getStreamMessageId() || message.getStreamIndex() < 0) {
                continue;
            }
            int shardIndex = message.getStreamIndex();
            long retryCount = registry.incrementRetry(shardIndex, message.getStreamMessageId());
            seckillStreamMetrics.recordFail(registry.streamKey(shardIndex), 1);
            if (pendingRetryPolicy.shouldIsolate(retryCount, pendingMaxRetry)) {
                isolatedMessages.computeIfAbsent(shardIndex, key -> new ArrayList<>()).add(message);
                seckillManualCompensationStream.isolate(message, retryCount, exception);
                seckillStreamMetrics.recordDlq(registry.streamKey(shardIndex), 1);
            }
        }
        removeIsolatedMessages(isolatedMessages);
    }

    private void removeIsolatedMessages(Map<Integer, List<SeckillOrderBufferMessage>> isolatedMessages) {
        for (Map.Entry<Integer, List<SeckillOrderBufferMessage>> entry : isolatedMessages.entrySet()) {
            List<StreamMessageId> ids = new ArrayList<>(entry.getValue().size());
            for (SeckillOrderBufferMessage message : entry.getValue()) {
                ids.add(message.getStreamMessageId());
            }
            StreamMessageId[] idArray = ids.toArray(new StreamMessageId[0]);
            registry.stream(entry.getKey()).ack(registry.streamGroup(), idArray);
            registry.stream(entry.getKey()).remove(idArray);
            for (StreamMessageId id : idArray) {
                registry.deleteRetryKey(entry.getKey(), id);
            }
        }
    }

}
