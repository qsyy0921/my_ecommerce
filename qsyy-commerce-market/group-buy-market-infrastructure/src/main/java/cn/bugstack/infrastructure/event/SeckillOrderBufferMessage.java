package cn.bugstack.infrastructure.event;

import org.redisson.api.StreamMessageId;

/**
 * 秒杀订单创建缓冲消息，兼容本地队列、Redis 队列和 Redis Stream。
 */
public class SeckillOrderBufferMessage {

    private final StreamMessageId streamMessageId;
    private final String body;
    private final int streamIndex;
    private final String streamKey;

    public SeckillOrderBufferMessage(StreamMessageId streamMessageId, String body, int streamIndex, String streamKey) {
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
