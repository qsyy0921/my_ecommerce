package cn.bugstack.domain.seckill.adapter.port;

import cn.bugstack.domain.seckill.model.entity.SeckillOrderEntity;

public interface ISeckillResultCachePort {

    SeckillOrderEntity query(Long activityId, String userId, String outTradeNo);

    void cache(SeckillOrderEntity seckillOrderEntity, String resultStatus, String message);

    void remove(Long activityId, String userId, String outTradeNo);

    String resultKey(Long activityId, String userId, String outTradeNo);

}
