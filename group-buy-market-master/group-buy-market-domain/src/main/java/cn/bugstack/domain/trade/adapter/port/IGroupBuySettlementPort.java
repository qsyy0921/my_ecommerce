package cn.bugstack.domain.trade.adapter.port;

import cn.bugstack.domain.trade.model.aggregate.GroupBuyTeamSettlementAggregate;
import cn.bugstack.domain.trade.model.entity.NotifyTaskEntity;

public interface IGroupBuySettlementPort {

    NotifyTaskEntity settlementMarketPayOrder(GroupBuyTeamSettlementAggregate groupBuyTeamSettlementAggregate);

}
