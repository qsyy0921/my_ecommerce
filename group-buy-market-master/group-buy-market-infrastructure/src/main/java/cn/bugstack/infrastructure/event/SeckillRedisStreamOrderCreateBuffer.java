package cn.bugstack.infrastructure.event;

import lombok.extern.slf4j.Slf4j;
import org.redisson.api.AutoClaimResult;
import org.redisson.api.RStream;
import org.redisson.api.RedissonClient;
import org.redisson.api.StreamMessageId;
import org.redisson.api.stream.StreamCreateGroupArgs;
import org.redisson.api.stream.StreamReadGroupArgs;
import org.redisson.client.RedisException;
import org.redisson.client.codec.StringCodec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Component
public class SeckillRedisStreamOrderCreateBuffer {

    @Value("${app.seckill.order-create-buffer.stream-group:seckill-order-create-group}")
    private String streamGroup;

    @Value("${app.seckill.order-create-buffer.stream-max-len:0}")
    private Integer streamMaxLen;

    @Value("${app.seckill.order-create-buffer.pending-idle-millis:10000}")
    private Long pendingIdleMillis;

    @Value("${app.seckill.order-create-buffer.pending-max-retry:5}")
    private Integer pendingMaxRetry;

    @Resource
    private RedissonClient redissonClient;

    @Resource
    private SeckillStreamMetrics seckillStreamMetrics;

    @Resource
    private SeckillStreamShardRouter seckillStreamShardRouter;

    @Resource
    private SeckillStreamMessageMapper seckillStreamMessageMapper;

    @Resource
    private SeckillStreamMetricsSampler seckillStreamMetricsSampler;

    @Resource
    private SeckillManualCompensationStream seckillManualCompensationStream;

    private volatile List<RStream<String, String>> streams = Collections.emptyList();
    private final ConcurrentHashMap<String, AtomicInteger> consumerCursor = new ConcurrentHashMap<>();
    private final SeckillPendingRetryPolicy pendingRetryPolicy = new SeckillPendingRetryPolicy();

    public void init() {
        if (!streams.isEmpty()) {
            return;
        }
        int shards = seckillStreamShardRouter.shardCount();
        List<RStream<String, String>> streamList = new ArrayList<>(shards);
        for (int i = 0; i < shards; i++) {
            RStream<String, String> stream = redissonClient.getStream(seckillStreamShardRouter.streamKey(i), StringCodec.INSTANCE);
            createGroupIfAbsent(stream);
            streamList.add(stream);
        }
        streams = Collections.unmodifiableList(streamList);
        log.info("seckill redis stream order create buffer init streamKey:{} streamShards:{} streamGroup:{} manualStream:{}",
                seckillStreamShardRouter.baseStreamKey(), shards, streamGroup, seckillManualCompensationStream.streamKey());
    }

    public boolean offer(String message, String routeKey) {
        ensureInitialized();
        int shardIndex = seckillStreamShardRouter.shardOf(routeKey);
        streams.get(shardIndex).add(seckillStreamMessageMapper.toOrderMessage(message, streamMaxLen));
        seckillStreamMetrics.recordEnqueue(seckillStreamShardRouter.streamKey(shardIndex));
        return true;
    }

    public List<SeckillOrderBufferMessage> pollBatch(String consumerName, int batchSize, long timeout, TimeUnit timeUnit) {
        ensureInitialized();
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

    public void ack(List<SeckillOrderBufferMessage> messages) {
        if (null == messages || messages.isEmpty()) {
            return;
        }
        ensureInitialized();
        Map<Integer, List<StreamMessageId>> shardIdMap = new HashMap<>();
        for (SeckillOrderBufferMessage message : messages) {
            if (null == message.getStreamMessageId() || message.getStreamIndex() < 0) {
                continue;
            }
            shardIdMap.computeIfAbsent(message.getStreamIndex(), key -> new ArrayList<>()).add(message.getStreamMessageId());
        }
        for (Map.Entry<Integer, List<StreamMessageId>> entry : shardIdMap.entrySet()) {
            StreamMessageId[] ids = entry.getValue().toArray(new StreamMessageId[0]);
            streams.get(entry.getKey()).ack(streamGroup, ids);
            streams.get(entry.getKey()).remove(ids);
            seckillStreamMetrics.recordAck(seckillStreamShardRouter.streamKey(entry.getKey()), ids.length);
            for (StreamMessageId id : ids) {
                redissonClient.getAtomicLong(seckillStreamShardRouter.retryKey(entry.getKey(), id)).delete();
            }
        }
    }

    public void fail(List<SeckillOrderBufferMessage> messages, Exception exception) {
        if (null == messages || messages.isEmpty()) {
            return;
        }
        ensureInitialized();
        Map<Integer, List<SeckillOrderBufferMessage>> deadMessages = new HashMap<>();
        for (SeckillOrderBufferMessage message : messages) {
            if (null == message.getStreamMessageId() || message.getStreamIndex() < 0) {
                continue;
            }
            int shardIndex = message.getStreamIndex();
            long retryCount = redissonClient.getAtomicLong(seckillStreamShardRouter.retryKey(shardIndex, message.getStreamMessageId())).incrementAndGet();
            seckillStreamMetrics.recordFail(seckillStreamShardRouter.streamKey(shardIndex), 1);
            if (pendingRetryPolicy.shouldIsolate(retryCount, pendingMaxRetry)) {
                deadMessages.computeIfAbsent(shardIndex, key -> new ArrayList<>()).add(message);
                seckillManualCompensationStream.isolate(message, retryCount, exception);
                seckillStreamMetrics.recordDlq(seckillStreamShardRouter.streamKey(shardIndex), 1);
            }
        }
        removeDeadMessages(deadMessages);
    }

    @Scheduled(fixedDelayString = "${app.seckill.order-create-buffer.metrics-sample-interval-millis:5000}")
    public void sampleStreamMetrics() {
        if (streams.isEmpty()) {
            return;
        }
        seckillStreamMetricsSampler.sample(streams, streamGroup);
    }

    public String description() {
        return "streamKey=" + seckillStreamShardRouter.baseStreamKey()
                + ", streamShards=" + seckillStreamShardRouter.shardCount()
                + ", streamGroup=" + streamGroup
                + ", manualStream=" + seckillManualCompensationStream.streamKey();
    }

    private void ensureInitialized() {
        if (streams.isEmpty()) {
            init();
        }
    }

    private void removeDeadMessages(Map<Integer, List<SeckillOrderBufferMessage>> deadMessages) {
        for (Map.Entry<Integer, List<SeckillOrderBufferMessage>> entry : deadMessages.entrySet()) {
            List<StreamMessageId> ids = new ArrayList<>(entry.getValue().size());
            for (SeckillOrderBufferMessage message : entry.getValue()) {
                ids.add(message.getStreamMessageId());
            }
            StreamMessageId[] idArray = ids.toArray(new StreamMessageId[0]);
            streams.get(entry.getKey()).ack(streamGroup, idArray);
            streams.get(entry.getKey()).remove(idArray);
            for (StreamMessageId id : idArray) {
                redissonClient.getAtomicLong(seckillStreamShardRouter.retryKey(entry.getKey(), id)).delete();
            }
        }
    }

    private List<SeckillOrderBufferMessage> claimPending(String consumerName, int batchSize) {
        List<SeckillOrderBufferMessage> result = new ArrayList<>(batchSize);
        int start = nextShardCursor(consumerName);
        for (int i = 0; i < streams.size() && result.size() < batchSize; i++) {
            int shardIndex = (start + i) % streams.size();
            AutoClaimResult<String, String> claimResult = streams.get(shardIndex).autoClaim(
                    streamGroup,
                    consumerName,
                    pendingIdleMillis,
                    TimeUnit.MILLISECONDS,
                    StreamMessageId.MIN,
                    batchSize - result.size());
            List<SeckillOrderBufferMessage> messages = seckillStreamMessageMapper.toBufferMessages(
                    claimResult.getMessages(),
                    shardIndex,
                    seckillStreamShardRouter.streamKey(shardIndex));
            if (!messages.isEmpty()) {
                seckillStreamMetrics.recordPendingClaim(seckillStreamShardRouter.streamKey(shardIndex), messages.size());
                result.addAll(messages);
            }
        }
        return result;
    }

    private List<SeckillOrderBufferMessage> readNeverDelivered(String consumerName, int batchSize, long timeoutMillis) {
        List<SeckillOrderBufferMessage> result = new ArrayList<>(batchSize);
        int start = nextShardCursor(consumerName);
        for (int i = 0; i < streams.size() && result.size() < batchSize; i++) {
            int shardIndex = (start + i) % streams.size();
            Map<StreamMessageId, Map<String, String>> entries = streams.get(shardIndex).readGroup(
                    streamGroup,
                    consumerName,
                    StreamReadGroupArgs.neverDelivered()
                            .count(batchSize - result.size())
                            .timeout(Duration.ofMillis(timeoutMillis)));
            result.addAll(seckillStreamMessageMapper.toBufferMessages(
                    entries,
                    shardIndex,
                    seckillStreamShardRouter.streamKey(shardIndex)));
        }
        return result;
    }

    private List<SeckillOrderBufferMessage> readNeverDeliveredFromOneShard(String consumerName, int batchSize, long timeoutMillis) {
        int shardIndex = nextShardCursor(consumerName);
        Map<StreamMessageId, Map<String, String>> entries = streams.get(shardIndex).readGroup(
                streamGroup,
                consumerName,
                StreamReadGroupArgs.neverDelivered()
                        .count(batchSize)
                        .timeout(Duration.ofMillis(Math.max(1L, timeoutMillis))));
        return seckillStreamMessageMapper.toBufferMessages(
                entries,
                shardIndex,
                seckillStreamShardRouter.streamKey(shardIndex));
    }

    private void createGroupIfAbsent(RStream<String, String> stream) {
        try {
            stream.createGroup(StreamCreateGroupArgs.name(streamGroup).id(StreamMessageId.NEWEST).makeStream());
        } catch (RedisException e) {
            if (null == e.getMessage() || !e.getMessage().contains("BUSYGROUP")) {
                throw e;
            }
        }
    }

    private int nextShardCursor(String consumerName) {
        AtomicInteger cursor = consumerCursor.computeIfAbsent(consumerName, key -> new AtomicInteger(0));
        return Math.floorMod(cursor.getAndIncrement(), Math.max(1, streams.size()));
    }

}
