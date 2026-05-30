package cn.bugstack.domain.seckill.adapter.port;

import cn.bugstack.domain.seckill.model.entity.SeckillActivityEntity;
import cn.bugstack.domain.seckill.model.entity.SeckillOrderEntity;

public interface ISeckillQueryPort {

    SeckillActivityEntity querySeckillActivity(Long activityId, String source, String channel, String goodsId);

    SeckillOrderEntity querySeckillOrderByOutTradeNo(String userId, String outTradeNo);

    SeckillOrderEntity querySeckillResult(String userId, Long activityId, String outTradeNo);

}
