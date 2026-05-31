package cn.bugstack.domain.seckill.adapter.port;

import cn.bugstack.domain.seckill.model.entity.SeckillStockFlowEntity;

import java.util.List;

public interface ISeckillStockFlowPort {

    void record(SeckillStockFlowEntity seckillStockFlowEntity);

    void recordBatch(List<SeckillStockFlowEntity> seckillStockFlowEntities);

}
