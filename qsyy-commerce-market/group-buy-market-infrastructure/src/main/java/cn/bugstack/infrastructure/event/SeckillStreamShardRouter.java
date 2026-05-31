package cn.bugstack.infrastructure.event;

import org.redisson.api.StreamMessageId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.zip.CRC32;

/**
 * Redis Stream 分片路由，避免缓冲区主编排类直接持有 hash 和 key 拼装细节。
 */
@Component
public class SeckillStreamShardRouter {

    private static final String RETRY_KEY_PREFIX = "seckill:order:create:retry:";

    @Value("${app.seckill.order-create-buffer.stream-key:seckill:order:create:stream}")
    private String streamKey;

    @Value("${app.seckill.order-create-buffer.stream-shard-count:1}")
    private Integer streamShardCount;

    public String baseStreamKey() {
        return streamKey;
    }

    public int shardCount() {
        return Math.max(1, null == streamShardCount ? 1 : streamShardCount);
    }

    public int shardOf(String routeKey) {
        CRC32 crc32 = new CRC32();
        crc32.update(String.valueOf(routeKey).getBytes(StandardCharsets.UTF_8));
        return (int) (crc32.getValue() % shardCount());
    }

    public String streamKey(int shardIndex) {
        if (shardCount() <= 1) {
            return streamKey;
        }
        return streamKey + ":" + shardIndex;
    }

    public String retryKey(int shardIndex, StreamMessageId streamMessageId) {
        return RETRY_KEY_PREFIX + streamKey(shardIndex) + ":" + streamMessageId;
    }

}
