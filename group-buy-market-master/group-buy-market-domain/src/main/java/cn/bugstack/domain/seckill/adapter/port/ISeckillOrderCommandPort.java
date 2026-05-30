package cn.bugstack.domain.seckill.adapter.port;

import cn.bugstack.domain.seckill.model.entity.SeckillOrderEntity;

import java.util.List;

public interface ISeckillOrderCommandPort {

    void createSeckillOrder(SeckillOrderEntity seckillOrderEntity);

    void createSeckillOrders(List<SeckillOrderEntity> seckillOrderEntities);

    SeckillOrderEntity settlementSeckillOrder(String userId, String outTradeNo);

    SeckillOrderEntity refundSeckillOrder(String userId, String outTradeNo, String refundReason);

}
