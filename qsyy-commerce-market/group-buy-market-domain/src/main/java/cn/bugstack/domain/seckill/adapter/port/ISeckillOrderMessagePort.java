package cn.bugstack.domain.seckill.adapter.port;

import cn.bugstack.domain.seckill.model.entity.SeckillOrderEntity;

public interface ISeckillOrderMessagePort {

    boolean publishOrderCreate(SeckillOrderEntity seckillOrderEntity);

}
