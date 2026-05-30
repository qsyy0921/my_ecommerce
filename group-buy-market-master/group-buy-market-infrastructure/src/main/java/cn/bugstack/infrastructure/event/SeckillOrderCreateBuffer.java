package cn.bugstack.infrastructure.event;

import cn.bugstack.domain.seckill.adapter.port.ISeckillManualCompensationPort;
import cn.bugstack.domain.seckill.model.entity.SeckillManualMessageEntity;
import cn.bugstack.infrastructure.redis.IRedisService;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.AutoClaimResult;
import org.redisson.api.RScript;
import org.redisson.api.RStream;
import org.redisson.api.RedissonClient;
import org.redisson.api.StreamMessageId;
import org.redisson.api.stream.StreamAddArgs;
import org.redisson.api.stream.StreamCreateGroupArgs;
import org.redisson.api.stream.StreamReadGroupArgs;
import org.redisson.api.stream.TrimStrategy;
import org.redisson.client.RedisException;
import org.redisson.client.codec.StringCodec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.CRC32;

@Slf4j
@Component
public class SeckillOrderCreateBuffer implements ISeckillManualCompensationPort {

    public static final String MODE_MQ = "mq";
    public static final String MODE_REDIS_QUEUE = "redis_queue";
    public static final String MODE_REDIS_STREAM = "redis_stream";
    public static final String MODE_LOCAL_QUEUE = "local_queue";

    private static final String STREAM_FIELD_BODY = "body";
    private static final String STREAM_METRICS_LUA =
            "local pending = redis.call('XPENDING', KEYS[1], ARGV[1]) " +
            "local pending_count = 0 " +
            "if pending and pending[1] then pending_count = pending[1] end " +
            "local lag = -1 " +
            "local groups = redis.call('XINFO', 'GROUPS', KEYS[1]) " +
            "for i, group in ipairs(groups) do " +
            "  local matched = false " +
            "  for j = 1, #group, 2 do " +
            "    if group[j] == 'name' and group[j + 1] == ARGV[1] then matched = true end " +
            "  end " +
            "  if matched then " +
            "    for j = 1, #group, 2 do " +
            "      if group[j] == 'lag' then lag = group[j + 1] end " +
            "    end " +
            "  end " +
            "end " +
            "return {pending_count, lag}";

    @Value("${app.seckill.order-create-buffer.mode:mq}")
    private String mode;

    @Value("${app.seckill.order-create-buffer.local-capacity:20000}")
    private Integer localCapacity;

    @Value("${app.seckill.order-create-buffer.redis-queue-key:seckill:order:create:queue}")
    private String redisQueueKey;

    @Value("${app.seckill.order-create-buffer.stream-key:seckill:order:create:stream}")
    private String streamKey;

    @Value("${app.seckill.order-create-buffer.stream-shard-count:1}")
    private Integer streamShardCount;

    @Value("${app.seckill.order-create-buffer.stream-group:seckill-order-create-group}")
    private String streamGroup;

    @Value("${app.seckill.order-create-buffer.stream-max-len:0}")
    private Integer streamMaxLen;

    @Value("${app.seckill.order-create-buffer.pending-idle-millis:10000}")
    private Long pendingIdleMillis;

    @Value("${app.seckill.order-create-buffer.pending-max-retry:5}")
    private Integer pendingMaxRetry;

    @Value("${app.seckill.order-create-buffer.dead-stream-key:seckill:order:create:manual}")
    private String deadStreamKey;

    @Resource
    private IRedisService redisService;

    @Resource
    private RedissonClient redissonClient;

    @Resource
    private SeckillStreamMetrics seckillStreamMetrics;

    private BlockingQueue<String> localQueue;
    private List<RStream<String, String>> streams = Collections.emptyList();
    private RStream<String, String> deadStream;
    private final ConcurrentHashMap<String, AtomicInteger> consumerCursor = new ConcurrentHashMap<>();
    private final SeckillPendingRetryPolicy pendingRetryPolicy = new SeckillPendingRetryPolicy();

    @PostConstruct
    public void init() {
        localQueue = new ArrayBlockingQueue<>(Math.max(1, localCapacity));
        if (useRedisStream()) {
            int shards = shardCount();
            List<RStream<String, String>> streamList = new ArrayList<>(shards);
            for (int i = 0; i < shards; i++) {
                RStream<String, String> stream = redissonClient.getStream(streamKey(i), StringCodec.INSTANCE);
                createGroupIfAbsent(stream);
                streamList.add(stream);
            }
            streams = Collections.unmodifiableList(streamList);
            deadStream = redissonClient.getStream(deadStreamKey, StringCodec.INSTANCE);
        }
        log.info("seckill order create buffer init mode:{} localCapacity:{} redisQueueKey:{} streamKey:{} streamShards:{} streamGroup:{} deadStream:{}",
                mode(), localCapacity, redisQueueKey, streamKey, shardCount(), streamGroup, deadStreamKey);
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
            return localQueue.offer(message);
        }
        if (useRedisQueue()) {
            return redisService.<String>getBlockingQueue(redisQueueKey).offer(message);
        }
        if (useRedisStream()) {
            int shardIndex = shardOf(routeKey);
            StreamAddArgs<String, String> args = StreamAddArgs.entry(STREAM_FIELD_BODY, message);
            if (null != streamMaxLen && streamMaxLen > 0) {
                args = args.trim(TrimStrategy.MAXLEN, streamMaxLen);
            }
            streams.get(shardIndex).add(args);
            seckillStreamMetrics.recordEnqueue(streamKey(shardIndex));
            return true;
        }
        return false;
    }

    public List<BufferMessage> pollBatch(String consumerName, int batchSize, long timeout, TimeUnit timeUnit) throws InterruptedException {
        int size = Math.max(1, batchSize);
        if (useLocalQueue()) {
            return pollLocalBatch(size, timeout, timeUnit);
        }
        if (useRedisQueue()) {
            return pollRedisQueueBatch(size, timeout, timeUnit);
        }
        if (useRedisStream()) {
            List<BufferMessage> messages = claimPending(consumerName, size);
            if (!messages.isEmpty()) {
                return messages;
            }
            messages = readNeverDelivered(consumerName, size, 1);
            if (!messages.isEmpty()) {
                return messages;
            }
            return readNeverDeliveredFromOneShard(consumerName, size, timeUnit.toMillis(timeout));
        }
        return Collections.emptyList();
    }

    public void ack(List<BufferMessage> messages) {
        if (!useRedisStream() || null == messages || messages.isEmpty()) {
            return;
        }
        Map<Integer, List<StreamMessageId>> shardIdMap = new HashMap<>();
        for (BufferMessage message : messages) {
            if (null == message.getStreamMessageId() || message.getStreamIndex() < 0) {
                continue;
            }
            shardIdMap.computeIfAbsent(message.getStreamIndex(), key -> new ArrayList<>()).add(message.getStreamMessageId());
        }
        for (Map.Entry<Integer, List<StreamMessageId>> entry : shardIdMap.entrySet()) {
            StreamMessageId[] ids = entry.getValue().toArray(new StreamMessageId[0]);
            streams.get(entry.getKey()).ack(streamGroup, ids);
            streams.get(entry.getKey()).remove(ids);
            seckillStreamMetrics.recordAck(streamKey(entry.getKey()), ids.length);
            for (StreamMessageId id : ids) {
                redissonClient.getAtomicLong(retryKey(entry.getKey(), id)).delete();
            }
        }
    }

    public void fail(List<BufferMessage> messages, Exception exception) {
        if (!useRedisStream() || null == messages || messages.isEmpty()) {
            return;
        }
        Map<Integer, List<BufferMessage>> deadMessages = new HashMap<>();
        for (BufferMessage message : messages) {
            if (null == message.getStreamMessageId() || message.getStreamIndex() < 0) {
                continue;
            }
            int shardIndex = message.getStreamIndex();
            long retryCount = redissonClient.getAtomicLong(retryKey(shardIndex, message.getStreamMessageId())).incrementAndGet();
            seckillStreamMetrics.recordFail(streamKey(shardIndex), 1);
            if (pendingRetryPolicy.shouldIsolate(retryCount, pendingMaxRetry)) {
                deadMessages.computeIfAbsent(shardIndex, key -> new ArrayList<>()).add(message);
                addToDeadStream(message, retryCount, exception);
                seckillStreamMetrics.recordDlq(streamKey(shardIndex), 1);
            }
        }
        for (Map.Entry<Integer, List<BufferMessage>> entry : deadMessages.entrySet()) {
            List<StreamMessageId> ids = new ArrayList<>(entry.getValue().size());
            for (BufferMessage message : entry.getValue()) {
                ids.add(message.getStreamMessageId());
            }
            StreamMessageId[] idArray = ids.toArray(new StreamMessageId[0]);
            streams.get(entry.getKey()).ack(streamGroup, idArray);
            streams.get(entry.getKey()).remove(idArray);
            for (StreamMessageId id : idArray) {
                redissonClient.getAtomicLong(retryKey(entry.getKey(), id)).delete();
            }
        }
    }

    @Scheduled(fixedDelayString = "${app.seckill.order-create-buffer.metrics-sample-interval-millis:5000}")
    public void sampleStreamMetrics() {
        if (!useRedisStream() || streams.isEmpty()) {
            return;
        }
        for (int i = 0; i < streams.size(); i++) {
            try {
                Object result = redissonClient.getScript(StringCodec.INSTANCE).eval(
                        RScript.Mode.READ_ONLY,
                        STREAM_METRICS_LUA,
                        RScript.ReturnType.MULTI,
                        Collections.<Object>singletonList(streamKey(i)),
                        streamGroup);
                if (result instanceof List) {
                    List<?> values = (List<?>) result;
                    if (values.size() > 0) {
                        seckillStreamMetrics.setPending(streamKey(i), parseLong(values.get(0), 0L));
                    }
                    if (values.size() > 1) {
                        seckillStreamMetrics.setLag(streamKey(i), parseLong(values.get(1), -1L));
                    }
                }
            } catch (Exception e) {
                log.debug("sample seckill stream metrics failed streamKey:{}", streamKey(i), e);
            }
        }
    }

    public int localSize() {
        return null == localQueue ? 0 : localQueue.size();
    }

    public String redisQueueKey() {
        return redisQueueKey;
    }

    @Override
    public List<SeckillManualMessageEntity> queryManualMessages(int limit) {
        RStream<String, String> stream = null == deadStream ? redissonClient.getStream(deadStreamKey, StringCodec.INSTANCE) : deadStream;
        Map<StreamMessageId, Map<String, String>> entries = stream.range(Math.max(1, limit), StreamMessageId.MIN, StreamMessageId.MAX);
        if (null == entries || entries.isEmpty()) {
            return Collections.emptyList();
        }
        List<SeckillManualMessageEntity> result = new ArrayList<>(entries.size());
        for (Map.Entry<StreamMessageId, Map<String, String>> entry : entries.entrySet()) {
            SeckillManualMessageEntity deadMessage = toDeadMessage(entry.getKey(), entry.getValue());
            if (null != deadMessage) {
                result.add(deadMessage);
            }
        }
        return result;
    }

    @Override
    public int replayManualMessages(List<String> messageIds, int limit) {
        List<SeckillManualMessageEntity> messages = new ArrayList<>();
        if (null != messageIds && !messageIds.isEmpty()) {
            for (String messageId : messageIds) {
                SeckillManualMessageEntity deadMessage = queryDeadMessage(messageId);
                if (null != deadMessage) {
                    messages.add(deadMessage);
                }
            }
        } else {
            messages.addAll(queryManualMessages(Math.max(1, limit)));
        }

        int count = 0;
        RStream<String, String> stream = null == deadStream ? redissonClient.getStream(deadStreamKey, StringCodec.INSTANCE) : deadStream;
        for (SeckillManualMessageEntity deadMessage : messages) {
            if (null == deadMessage.getBody() || deadMessage.getBody().trim().isEmpty()) {
                continue;
            }
            offer(deadMessage.getBody(), deadMessage.getBody());
            stream.remove(parseStreamMessageId(deadMessage.getId()));
            count++;
        }
        return count;
    }

    @Override
    public String manualStreamKey() {
        return deadStreamKey;
    }

    private List<BufferMessage> pollLocalBatch(int size, long timeout, TimeUnit timeUnit) throws InterruptedException {
        String message = localQueue.poll(timeout, timeUnit);
        if (null == message) {
            return Collections.emptyList();
        }
        List<BufferMessage> result = new ArrayList<>(size);
        result.add(new BufferMessage(null, message, -1, null));
        List<String> drained = new ArrayList<>(size - 1);
        localQueue.drainTo(drained, size - 1);
        for (String item : drained) {
            result.add(new BufferMessage(null, item, -1, null));
        }
        return result;
    }

    private List<BufferMessage> pollRedisQueueBatch(int size, long timeout, TimeUnit timeUnit) throws InterruptedException {
        String message = redisService.<String>getBlockingQueue(redisQueueKey).poll(timeout, timeUnit);
        if (null == message) {
            return Collections.emptyList();
        }
        List<BufferMessage> result = new ArrayList<>(size);
        result.add(new BufferMessage(null, message, -1, null));
        for (int i = 1; i < size; i++) {
            String item = redisService.<String>getBlockingQueue(redisQueueKey).poll();
            if (null == item) {
                break;
            }
            result.add(new BufferMessage(null, item, -1, null));
        }
        return result;
    }

    private List<BufferMessage> claimPending(String consumerName, int batchSize) {
        List<BufferMessage> result = new ArrayList<>(batchSize);
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
            List<BufferMessage> messages = toBufferMessages(claimResult.getMessages(), shardIndex);
            if (!messages.isEmpty()) {
                seckillStreamMetrics.recordPendingClaim(streamKey(shardIndex), messages.size());
                result.addAll(messages);
            }
        }
        return result;
    }

    private List<BufferMessage> readNeverDelivered(String consumerName, int batchSize, long timeoutMillis) {
        List<BufferMessage> result = new ArrayList<>(batchSize);
        int start = nextShardCursor(consumerName);
        for (int i = 0; i < streams.size() && result.size() < batchSize; i++) {
            int shardIndex = (start + i) % streams.size();
            Map<StreamMessageId, Map<String, String>> entries = streams.get(shardIndex).readGroup(
                    streamGroup,
                    consumerName,
                    StreamReadGroupArgs.neverDelivered()
                            .count(batchSize - result.size())
                            .timeout(Duration.ofMillis(timeoutMillis)));
            result.addAll(toBufferMessages(entries, shardIndex));
        }
        return result;
    }

    private List<BufferMessage> readNeverDeliveredFromOneShard(String consumerName, int batchSize, long timeoutMillis) {
        int shardIndex = nextShardCursor(consumerName);
        Map<StreamMessageId, Map<String, String>> entries = streams.get(shardIndex).readGroup(
                streamGroup,
                consumerName,
                StreamReadGroupArgs.neverDelivered()
                        .count(batchSize)
                        .timeout(Duration.ofMillis(Math.max(1L, timeoutMillis))));
        return toBufferMessages(entries, shardIndex);
    }

    private List<BufferMessage> toBufferMessages(Map<StreamMessageId, Map<String, String>> entries, int streamIndex) {
        if (null == entries || entries.isEmpty()) {
            return Collections.emptyList();
        }
        List<BufferMessage> result = new ArrayList<>(entries.size());
        for (Map.Entry<StreamMessageId, Map<String, String>> entry : entries.entrySet()) {
            String body = entry.getValue().get(STREAM_FIELD_BODY);
            if (null != body) {
                result.add(new BufferMessage(entry.getKey(), body, streamIndex, streamKey(streamIndex)));
            }
        }
        return result;
    }

    private void addToDeadStream(BufferMessage message, long retryCount, Exception exception) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("body", message.getBody());
        payload.put("streamKey", message.getStreamKey());
        payload.put("messageId", String.valueOf(message.getStreamMessageId()));
        payload.put("retryCount", retryCount);
        payload.put("error", null == exception ? null : exception.getMessage());
        deadStream.add(StreamAddArgs.entry(STREAM_FIELD_BODY, JSON.toJSONString(payload)));
    }

    private SeckillManualMessageEntity queryDeadMessage(String messageId) {
        if (null == messageId || messageId.trim().isEmpty()) {
            return null;
        }
        StreamMessageId streamMessageId = parseStreamMessageId(messageId);
        RStream<String, String> stream = null == deadStream ? redissonClient.getStream(deadStreamKey, StringCodec.INSTANCE) : deadStream;
        Map<StreamMessageId, Map<String, String>> entries = stream.range(1, streamMessageId, streamMessageId);
        if (null == entries || entries.isEmpty()) {
            return null;
        }
        Map.Entry<StreamMessageId, Map<String, String>> entry = entries.entrySet().iterator().next();
        return toDeadMessage(entry.getKey(), entry.getValue());
    }

    private SeckillManualMessageEntity toDeadMessage(StreamMessageId streamMessageId, Map<String, String> fields) {
        if (null == fields) {
            return null;
        }
        String payloadText = fields.get(STREAM_FIELD_BODY);
        if (null == payloadText) {
            return null;
        }
        JSONObject payload = JSON.parseObject(payloadText);
        return new SeckillManualMessageEntity(
                String.valueOf(streamMessageId),
                payload.getString("body"),
                payload.getString("streamKey"),
                payload.getString("messageId"),
                payload.getLong("retryCount"),
                payload.getString("error"));
    }

    private StreamMessageId parseStreamMessageId(String messageId) {
        String[] parts = String.valueOf(messageId).split("-");
        if (parts.length != 2) {
            throw new IllegalArgumentException("invalid stream message id: " + messageId);
        }
        return new StreamMessageId(Long.parseLong(parts[0]), Long.parseLong(parts[1]));
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

    private int shardOf(String routeKey) {
        CRC32 crc32 = new CRC32();
        crc32.update(String.valueOf(routeKey).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return (int) (crc32.getValue() % shardCount());
    }

    private int shardCount() {
        return Math.max(1, null == streamShardCount ? 1 : streamShardCount);
    }

    private int maxRetry() {
        return pendingRetryPolicy.maxRetry(pendingMaxRetry);
    }

    private String streamKey(int shardIndex) {
        if (shardCount() <= 1) {
            return streamKey;
        }
        return streamKey + ":" + shardIndex;
    }

    private String retryKey(int shardIndex, StreamMessageId streamMessageId) {
        return "seckill:order:create:retry:" + streamKey(shardIndex) + ":" + streamMessageId;
    }

    private long parseLong(Object value, long defaultValue) {
        if (null == value) {
            return defaultValue;
        }
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    public static class BufferMessage {

        private final StreamMessageId streamMessageId;
        private final String body;
        private final int streamIndex;
        private final String streamKey;

        public BufferMessage(StreamMessageId streamMessageId, String body, int streamIndex, String streamKey) {
            this.streamMessageId = streamMessageId;
            this.body = body;
            this.streamIndex = streamIndex;
            this.streamKey = streamKey;
        }

        public StreamMessageId getStreamMessageId() {
            return streamMessageId;
        }

        public String getBody() {
            return body;
        }

        public int getStreamIndex() {
            return streamIndex;
        }

        public String getStreamKey() {
            return streamKey;
        }
    }

}
