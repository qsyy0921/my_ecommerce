package cn.bugstack.domain.seckill.adapter.repository;

import cn.bugstack.domain.seckill.model.entity.SeckillActivityEntity;
import cn.bugstack.domain.seckill.model.entity.SeckillOrderEntity;

import java.util.List;

/**
 * Seckill repository port.
 */
public interface ISeckillRepository {

    SeckillActivityEntity querySeckillActivity(Long activityId, String source, String channel, String goodsId);

    Integer queryAvailableStock(Long activityId);

    SeckillOrderEntity querySeckillOrderByOutTradeNo(String userId, String outTradeNo);

    SeckillOrderEntity lockSeckillOrder(SeckillOrderEntity seckillOrderEntity);

    SeckillOrderEntity querySeckillResult(String userId, Long activityId, String outTradeNo);

    void createSeckillOrder(SeckillOrderEntity seckillOrderEntity);

    void createSeckillOrders(List<SeckillOrderEntity> seckillOrderEntities);

    SeckillOrderEntity settlementSeckillOrder(String userId, String outTradeNo);

    SeckillOrderEntity refundSeckillOrder(String userId, String outTradeNo, String refundReason);

    void syncSeckillActivityStock();

    int releaseTimeoutUnpaidOrders();

    int prewarmUpcomingActivities(Integer beforeMinutes, Integer limit);

}
