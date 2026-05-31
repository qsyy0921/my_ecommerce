package cn.bugstack.infrastructure.event;

import cn.bugstack.domain.seckill.model.entity.SeckillManualMessageEntity;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import org.redisson.api.StreamMessageId;
import org.redisson.api.stream.StreamAddArgs;
import org.redisson.api.stream.TrimStrategy;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Redis Stream 消息映射，集中处理消息体、DLQ 载荷和人工补偿消息解析。
 */
@Component
public class SeckillStreamMessageMapper {

    private static final String STREAM_FIELD_BODY = "body";

    public StreamAddArgs<String, String> toOrderMessage(String message, Integer streamMaxLen) {
        StreamAddArgs<String, String> args = StreamAddArgs.entry(STREAM_FIELD_BODY, message);
        if (null != streamMaxLen && streamMaxLen > 0) {
            args = args.trim(TrimStrategy.MAXLEN, streamMaxLen);
        }
        return args;
    }

    public List<SeckillOrderBufferMessage> toBufferMessages(Map<StreamMessageId, Map<String, String>> entries, int streamIndex, String streamKey) {
        if (null == entries || entries.isEmpty()) {
            return Collections.emptyList();
        }
        List<SeckillOrderBufferMessage> result = new ArrayList<>(entries.size());
        for (Map.Entry<StreamMessageId, Map<String, String>> entry : entries.entrySet()) {
            String body = entry.getValue().get(STREAM_FIELD_BODY);
            if (null != body) {
                result.add(new SeckillOrderBufferMessage(entry.getKey(), body, streamIndex, streamKey));
            }
        }
        return result;
    }

    public StreamAddArgs<String, String> toManualMessage(SeckillOrderBufferMessage message, long retryCount, Exception exception) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("body", message.getBody());
        payload.put("streamKey", message.getStreamKey());
        payload.put("messageId", String.valueOf(message.getStreamMessageId()));
        payload.put("retryCount", retryCount);
        payload.put("error", null == exception ? null : exception.getMessage());
        return StreamAddArgs.entry(STREAM_FIELD_BODY, JSON.toJSONString(payload));
    }

    public SeckillManualMessageEntity toDeadMessage(StreamMessageId streamMessageId, Map<String, String> fields) {
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

    public StreamMessageId parseStreamMessageId(String messageId) {
        String[] parts = String.valueOf(messageId).split("-");
        if (parts.length != 2) {
            throw new IllegalArgumentException("invalid stream message id: " + messageId);
        }
        return new StreamMessageId(Long.parseLong(parts[0]), Long.parseLong(parts[1]));
    }

}
