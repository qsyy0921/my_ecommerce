package cn.bugstack.domain.seckill.adapter.port;

import cn.bugstack.domain.seckill.model.entity.SeckillOrderEntity;

import java.util.List;

public interface ISeckillOrderCreatePort {

    void createSeckillOrder(SeckillOrderEntity seckillOrderEntity);

    void createSeckillOrders(List<SeckillOrderEntity> seckillOrderEntities);

}
