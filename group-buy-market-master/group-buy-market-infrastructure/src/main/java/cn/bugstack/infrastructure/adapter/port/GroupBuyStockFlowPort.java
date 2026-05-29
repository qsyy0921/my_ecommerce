package cn.bugstack.infrastructure.adapter.port;

import cn.bugstack.domain.trade.adapter.port.IGroupBuyStockFlowPort;
import cn.bugstack.domain.trade.model.entity.GroupBuyStockFlowEntity;
import cn.bugstack.infrastructure.dao.IGroupBuyStockFlowDao;
import cn.bugstack.infrastructure.dao.po.GroupBuyStockFlow;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

@Service
public class GroupBuyStockFlowPort implements IGroupBuyStockFlowPort {

    @Resource
    private IGroupBuyStockFlowDao groupBuyStockFlowDao;

    @Override
    public void record(GroupBuyStockFlowEntity groupBuyStockFlowEntity) {
        if (null == groupBuyStockFlowEntity) {
            return;
        }
        groupBuyStockFlowDao.insertIgnore(GroupBuyStockFlow.builder()
                .flowNo(groupBuyStockFlowEntity.flowNo())
                .userId(groupBuyStockFlowEntity.getUserId())
                .activityId(groupBuyStockFlowEntity.getActivityId())
                .teamId(groupBuyStockFlowEntity.getTeamId())
                .orderId(groupBuyStockFlowEntity.getOrderId())
                .outTradeNo(groupBuyStockFlowEntity.getOutTradeNo())
                .changeType(groupBuyStockFlowEntity.getChangeType())
                .changeCount(groupBuyStockFlowEntity.getChangeCount())
                .stockBefore(groupBuyStockFlowEntity.getStockBefore())
                .stockAfter(groupBuyStockFlowEntity.getStockAfter())
                .bizEvent(groupBuyStockFlowEntity.getBizEvent())
                .traceId(groupBuyStockFlowEntity.getTraceId())
                .sourceMessageId(groupBuyStockFlowEntity.getSourceMessageId())
                .source(groupBuyStockFlowEntity.getSource())
                .message(groupBuyStockFlowEntity.getMessage())
                .build());
    }

}
