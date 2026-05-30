package cn.bugstack.infrastructure.event;

import org.redisson.api.AutoClaimResult;
import org.redisson.api.StreamMessageId;
import org.redisson.api.stream.StreamReadGroupArgs;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class SeckillRedisStreamReader {

    @Value("${app.seckill.order-create-buffer.pending-idle-millis:10000}")
    private Long pendingIdleMillis;

    @Resource
    private SeckillRedisStreamRegistry registry;

    @Resource
    private SeckillStreamMessageMapper seckillStreamMessageMapper;

    @Resource
    private SeckillStreamMetrics seckillStreamMetrics;

    private final ConcurrentHashMap<String, AtomicInteger> consumerCursor = new ConcurrentHashMap<>();

    public List<SeckillOrderBufferMessage> pollBatch(String consumerName, int batchSize, long timeout, TimeUnit timeUnit) {
        registry.init();
        int size = Math.max(1, batchSize);
        List<SeckillOrderBufferMessage> messages = claimPending(consumerName, size);
        if (!messages.isEmpty()) {
            return messages;
        }
        messages = readNeverDelivered(consumerName, size, 1);
        if (!messages.isEmpty()) {
            return messages;
        }
        return readNeverDeliveredFromOneShard(consumerName, size, timeUnit.toMillis(timeout));
    }

    private List<SeckillOrderBufferMessage> claimPending(String consumerName, int batchSize) {
        List<SeckillOrderBufferMessage> result = new ArrayList<>(batchSize);
        int start = nextShardCursor(consumerName);
        for (int i = 0; i < registry.streams().size() && result.size() < batchSize; i++) {
            int shardIndex = (start + i) % registry.streams().size();
            AutoClaimResult<String, String> claimResult = registry.stream(shardIndex).autoClaim(
                    registry.streamGroup(),
                    consumerName,
                    pendingIdleMillis,
                    TimeUnit.MILLISECONDS,
                    StreamMessageId.MIN,
                    batchSize - result.size());
            List<SeckillOrderBufferMessage> messages = seckillStreamMessageMapper.toBufferMessages(
                    claimResult.getMessages(),
                    shardIndex,
                    registry.streamKey(shardIndex));
            if (!messages.isEmpty()) {
                seckillStreamMetrics.recordPendingClaim(registry.streamKey(shardIndex), messages.size());
                result.addAll(messages);
            }
        }
        return result;
    }

    private List<SeckillOrderBufferMessage> readNeverDelivered(String consumerName, int batchSize, long timeoutMillis) {
        List<SeckillOrderBufferMessage> result = new ArrayList<>(batchSize);
        int start = nextShardCursor(consumerName);
        for (int i = 0; i < registry.streams().size() && result.size() < batchSize; i++) {
            int shardIndex = (start + i) % registry.streams().size();
            Map<StreamMessageId, Map<String, String>> entries = registry.stream(shardIndex).readGroup(
                    registry.streamGroup(),
                    consumerName,
                    StreamReadGroupArgs.neverDelivered()
                            .count(batchSize - result.size())
                            .timeout(Duration.ofMillis(timeoutMillis)));
            result.addAll(seckillStreamMessageMapper.toBufferMessages(
                    entries,
                    shardIndex,
                    registry.streamKey(shardIndex)));
        }
        return result;
    }

    private List<SeckillOrderBufferMessage> readNeverDeliveredFromOneShard(String consumerName, int batchSize, long timeoutMillis) {
        int shardIndex = nextShardCursor(consumerName);
        Map<StreamMessageId, Map<String, String>> entries = registry.stream(shardIndex).readGroup(
                registry.streamGroup(),
                consumerName,
                StreamReadGroupArgs.neverDelivered()
                        .count(batchSize)
                        .timeout(Duration.ofMillis(Math.max(1L, timeoutMillis))));
        return seckillStreamMessageMapper.toBufferMessages(
                entries,
                shardIndex,
                registry.streamKey(shardIndex));
    }

    private int nextShardCursor(String consumerName) {
        AtomicInteger cursor = consumerCursor.computeIfAbsent(consumerName, key -> new AtomicInteger(0));
        return Math.floorMod(cursor.getAndIncrement(), Math.max(1, registry.streams().size()));
    }

}
