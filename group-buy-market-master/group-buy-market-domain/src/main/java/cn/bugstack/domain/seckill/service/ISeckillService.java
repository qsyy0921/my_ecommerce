package cn.bugstack.domain.seckill.service;

import cn.bugstack.domain.seckill.model.entity.SeckillActivityEntity;
import cn.bugstack.domain.seckill.model.entity.SeckillOrderEntity;

import java.util.List;

/**
 * Seckill domain service.
 */
public interface ISeckillService {

    SeckillActivityEntity querySeckillActivity(Long activityId, String source, String channel, String goodsId);

    SeckillOrderEntity querySeckillOrderByOutTradeNo(String userId, String outTradeNo);

    SeckillOrderEntity querySeckillResult(String userId, Long activityId, String outTradeNo);

    SeckillOrderEntity lockSeckillOrder(String userId, Long activityId, String source, String channel, String goodsId, String outTradeNo);

    void createSeckillOrder(SeckillOrderEntity seckillOrderEntity);

    void createSeckillOrders(List<SeckillOrderEntity> seckillOrderEntities);

    void syncSeckillActivityStock();

    int releaseTimeoutUnpaidOrders();

    int prewarmUpcomingActivities(Integer beforeMinutes, Integer limit);

}
