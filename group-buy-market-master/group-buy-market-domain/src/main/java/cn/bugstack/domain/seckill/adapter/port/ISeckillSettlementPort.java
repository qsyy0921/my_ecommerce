package cn.bugstack.domain.seckill.adapter.port;

import cn.bugstack.domain.seckill.model.entity.SeckillOrderEntity;

public interface ISeckillSettlementPort {

    SeckillOrderEntity settlementSeckillOrder(String userId, String outTradeNo);

}
