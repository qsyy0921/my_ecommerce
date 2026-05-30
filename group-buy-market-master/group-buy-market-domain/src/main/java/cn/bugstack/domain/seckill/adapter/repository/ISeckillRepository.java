package cn.bugstack.domain.seckill.adapter.repository;

import cn.bugstack.domain.seckill.model.entity.SeckillActivityEntity;
import cn.bugstack.domain.seckill.model.entity.SeckillOrderEntity;

/**
 * Seckill repository port.
 */
public interface ISeckillRepository {

    SeckillActivityEntity querySeckillActivity(Long activityId, String source, String channel, String goodsId);

    Integer queryAvailableStock(Long activityId);

    SeckillOrderEntity querySeckillOrderByOutTradeNo(String userId, String outTradeNo);

    SeckillOrderEntity lockSeckillOrder(SeckillOrderEntity seckillOrderEntity);

    SeckillOrderEntity querySeckillResult(String userId, Long activityId, String outTradeNo);

}
