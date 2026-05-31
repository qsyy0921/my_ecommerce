package cn.bugstack.infrastructure.event;

import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RScript;
import org.redisson.api.RStream;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.List;

/**
 * Redis Stream pending 和 lag 指标采样器。
 */
@Slf4j
@Component
public class SeckillStreamMetricsSampler {

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

    @Resource
    private RedissonClient redissonClient;

    @Resource
    private SeckillStreamMetrics seckillStreamMetrics;

    @Resource
    private SeckillStreamShardRouter seckillStreamShardRouter;

    public void sample(List<RStream<String, String>> streams, String streamGroup) {
        if (null == streams || streams.isEmpty()) {
            return;
        }
        for (int i = 0; i < streams.size(); i++) {
            String streamKey = seckillStreamShardRouter.streamKey(i);
            try {
                Object result = redissonClient.getScript(StringCodec.INSTANCE).eval(
                        RScript.Mode.READ_ONLY,
                        STREAM_METRICS_LUA,
                        RScript.ReturnType.MULTI,
                        Collections.<Object>singletonList(streamKey),
                        streamGroup);
                if (result instanceof List) {
                    List<?> values = (List<?>) result;
                    if (values.size() > 0) {
                        seckillStreamMetrics.setPending(streamKey, parseLong(values.get(0), 0L));
                    }
                    if (values.size() > 1) {
                        seckillStreamMetrics.setLag(streamKey, parseLong(values.get(1), -1L));
                    }
                }
            } catch (Exception e) {
                log.debug("sample seckill stream metrics failed streamKey:{}", streamKey, e);
            }
        }
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

}
