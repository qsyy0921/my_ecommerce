package cn.bugstack.infrastructure.adapter.port;

import cn.bugstack.domain.seckill.adapter.port.ISeckillStockFlowPort;
import cn.bugstack.domain.seckill.model.entity.SeckillStockFlowEntity;
import cn.bugstack.infrastructure.dao.ISeckillStockFlowDao;
import cn.bugstack.infrastructure.dao.po.SeckillStockFlow;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;

@Service
public class SeckillStockFlowPort implements ISeckillStockFlowPort {

    @Resource
    private ISeckillStockFlowDao seckillStockFlowDao;

    @Override
    public void record(SeckillStockFlowEntity seckillStockFlowEntity) {
        if (null == seckillStockFlowEntity) {
            return;
        }
        seckillStockFlowDao.insertIgnore(buildSeckillStockFlow(seckillStockFlowEntity));
    }

    @Override
    public void recordBatch(List<SeckillStockFlowEntity> seckillStockFlowEntities) {
        if (null == seckillStockFlowEntities || seckillStockFlowEntities.isEmpty()) {
            return;
        }
        List<SeckillStockFlow> seckillStockFlows = new ArrayList<>(seckillStockFlowEntities.size());
        for (SeckillStockFlowEntity seckillStockFlowEntity : seckillStockFlowEntities) {
            if (null != seckillStockFlowEntity) {
                seckillStockFlows.add(buildSeckillStockFlow(seckillStockFlowEntity));
            }
        }
        if (!seckillStockFlows.isEmpty()) {
            seckillStockFlowDao.insertIgnoreBatch(seckillStockFlows);
        }
    }

    private SeckillStockFlow buildSeckillStockFlow(SeckillStockFlowEntity seckillStockFlowEntity) {
        return SeckillStockFlow.builder()
                .flowNo(seckillStockFlowEntity.flowNo())
                .userId(seckillStockFlowEntity.getUserId())
                .activityId(seckillStockFlowEntity.getActivityId())
                .orderId(seckillStockFlowEntity.getOrderId())
                .outTradeNo(seckillStockFlowEntity.getOutTradeNo())
                .stockBucket(seckillStockFlowEntity.getStockBucket())
                .changeType(seckillStockFlowEntity.getChangeType())
                .changeCount(seckillStockFlowEntity.getChangeCount())
                .stockBefore(seckillStockFlowEntity.getStockBefore())
                .stockAfter(seckillStockFlowEntity.getStockAfter())
                .bizEvent(seckillStockFlowEntity.getBizEvent())
                .traceId(seckillStockFlowEntity.getTraceId())
                .sourceMessageId(seckillStockFlowEntity.getSourceMessageId())
                .source(seckillStockFlowEntity.getSource())
                .message(seckillStockFlowEntity.getMessage())
                .build();
    }

}
