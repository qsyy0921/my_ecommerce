package cn.bugstack.domain.seckill.adapter.port;

import cn.bugstack.domain.seckill.model.entity.SeckillOrderEntity;

public interface ISeckillOrderLockPort {

    SeckillOrderEntity lockSeckillOrder(SeckillOrderEntity seckillOrderEntity);

}
