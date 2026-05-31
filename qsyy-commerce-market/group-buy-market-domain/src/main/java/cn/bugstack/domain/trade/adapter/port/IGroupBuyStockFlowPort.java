package cn.bugstack.domain.trade.adapter.port;

import cn.bugstack.domain.trade.model.entity.GroupBuyStockFlowEntity;

public interface IGroupBuyStockFlowPort {

    void record(GroupBuyStockFlowEntity groupBuyStockFlowEntity);

}
