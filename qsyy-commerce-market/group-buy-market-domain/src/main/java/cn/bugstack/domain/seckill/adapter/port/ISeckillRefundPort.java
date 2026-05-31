package cn.bugstack.domain.seckill.adapter.port;

import cn.bugstack.domain.seckill.model.entity.SeckillOrderEntity;

public interface ISeckillRefundPort {

    SeckillOrderEntity refundSeckillOrder(String userId, String outTradeNo, String refundReason);

}
