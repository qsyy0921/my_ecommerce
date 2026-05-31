package cn.bugstack.infrastructure.event;

import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RStream;
import org.redisson.api.RedissonClient;
import org.redisson.api.StreamMessageId;
import org.redisson.api.stream.StreamCreateGroupArgs;
import org.redisson.client.RedisException;
import org.redisson.client.codec.StringCodec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Slf4j
@Component
public class SeckillRedisStreamRegistry {

    @Value("${app.seckill.order-create-buffer.stream-group:seckill-order-create-group}")
    private String streamGroup;

    @Resource
    private RedissonClient redissonClient;

    @Resource
    private SeckillStreamShardRouter seckillStreamShardRouter;

    @Resource
    private SeckillManualCompensationStream seckillManualCompensationStream;

    private volatile List<RStream<String, String>> streams = Collections.emptyList();

    public synchronized void init() {
        if (!streams.isEmpty()) {
            return;
        }
        int shards = seckillStreamShardRouter.shardCount();
        List<RStream<String, String>> streamList = new ArrayList<>(shards);
        for (int i = 0; i < shards; i++) {
            RStream<String, String> stream = redissonClient.getStream(streamKey(i), StringCodec.INSTANCE);
            createGroupIfAbsent(stream);
            streamList.add(stream);
        }
        streams = Collections.unmodifiableList(streamList);
        log.info("seckill redis stream registry init streamKey:{} streamShards:{} streamGroup:{} manualStream:{}",
                seckillStreamShardRouter.baseStreamKey(), shards, streamGroup, seckillManualCompensationStream.streamKey());
    }

    public boolean initialized() {
        return !streams.isEmpty();
    }

    public List<RStream<String, String>> streams() {
        return streams;
    }

    public RStream<String, String> stream(int shardIndex) {
        return streams.get(shardIndex);
    }

    public String streamGroup() {
        return streamGroup;
    }

    public int shardOf(String routeKey) {
        return seckillStreamShardRouter.shardOf(routeKey);
    }

    public String streamKey(int shardIndex) {
        return seckillStreamShardRouter.streamKey(shardIndex);
    }

    public long incrementRetry(int shardIndex, StreamMessageId messageId) {
        return redissonClient.getAtomicLong(seckillStreamShardRouter.retryKey(shardIndex, messageId)).incrementAndGet();
    }

    public void deleteRetryKey(int shardIndex, StreamMessageId messageId) {
        redissonClient.getAtomicLong(seckillStreamShardRouter.retryKey(shardIndex, messageId)).delete();
    }

    public void sample(SeckillStreamMetricsSampler sampler) {
        if (!streams.isEmpty()) {
            sampler.sample(streams, streamGroup);
        }
    }

    public String description() {
        return "streamKey=" + seckillStreamShardRouter.baseStreamKey()
                + ", streamShards=" + seckillStreamShardRouter.shardCount()
                + ", streamGroup=" + streamGroup
                + ", manualStream=" + seckillManualCompensationStream.streamKey();
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

}
