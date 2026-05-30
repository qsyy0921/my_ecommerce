package cn.bugstack.infrastructure.adapter.port;

import cn.bugstack.domain.seckill.adapter.port.ISeckillResultCachePort;
import cn.bugstack.domain.seckill.model.entity.SeckillOrderEntity;
import cn.bugstack.infrastructure.redis.IRedisService;
import com.alibaba.fastjson.JSON;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class SeckillResultCachePort implements ISeckillResultCachePort {

    private static final String SECKILL_RESULT_KEY = "seckill:result:";
    private static final long SECKILL_RESULT_TTL_HOURS = 24;

    @Resource
    private IRedisService redisService;

    @Override
    public SeckillOrderEntity query(Long activityId, String userId, String outTradeNo) {
        try {
            String result = redisService.getValue(resultKey(activityId, userId, outTradeNo));
            if (StringUtils.isNotBlank(result)) {
                return JSON.parseObject(result, SeckillOrderEntity.class);
            }
        } catch (Exception e) {
            log.warn("query seckill result cache failed activityId:{} userId:{} outTradeNo:{}", activityId, userId, outTradeNo, e);
        }
        return null;
    }

    @Override
    public void cache(SeckillOrderEntity seckillOrderEntity, String resultStatus, String message) {
        if (null == seckillOrderEntity) {
            return;
        }
        seckillOrderEntity.setResultStatus(resultStatus);
        seckillOrderEntity.setMessage(message);
        try {
            redisService.setValue(
                    resultKey(seckillOrderEntity.getActivityId(), seckillOrderEntity.getUserId(), seckillOrderEntity.getOutTradeNo()),
                    JSON.toJSONString(seckillOrderEntity),
                    TimeUnit.HOURS.toMillis(SECKILL_RESULT_TTL_HOURS));
        } catch (Exception e) {
            log.warn("cache seckill result failed activityId:{} userId:{} outTradeNo:{}",
                    seckillOrderEntity.getActivityId(), seckillOrderEntity.getUserId(), seckillOrderEntity.getOutTradeNo(), e);
        }
    }

    @Override
    public void remove(Long activityId, String userId, String outTradeNo) {
        try {
            redisService.remove(resultKey(activityId, userId, outTradeNo));
        } catch (Exception e) {
            log.warn("remove seckill result cache failed activityId:{} userId:{} outTradeNo:{}", activityId, userId, outTradeNo, e);
        }
    }

    @Override
    public String resultKey(Long activityId, String userId, String outTradeNo) {
        return SECKILL_RESULT_KEY + activityId + ":" + userId + ":" + outTradeNo;
    }

}
