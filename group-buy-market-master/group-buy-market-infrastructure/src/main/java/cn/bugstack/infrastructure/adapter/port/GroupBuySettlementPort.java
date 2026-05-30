package cn.bugstack.infrastructure.adapter.port;

import cn.bugstack.domain.trade.adapter.port.IGroupBuySettlementPort;
import cn.bugstack.domain.trade.model.aggregate.GroupBuyTeamSettlementAggregate;
import cn.bugstack.domain.trade.model.entity.GroupBuyTeamEntity;
import cn.bugstack.domain.trade.model.entity.NotifyTaskEntity;
import cn.bugstack.domain.trade.model.entity.TradePaySuccessEntity;
import cn.bugstack.domain.trade.model.entity.UserEntity;
import cn.bugstack.infrastructure.adapter.support.GroupBuyOrderPaidSupport;
import cn.bugstack.infrastructure.adapter.support.GroupBuyTeamFormationSupport;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;

@Service
public class GroupBuySettlementPort implements IGroupBuySettlementPort {

    @Resource
    private GroupBuyOrderPaidSupport groupBuyOrderPaidSupport;
    @Resource
    private GroupBuyTeamFormationSupport groupBuyTeamFormationSupport;

    @Transactional(timeout = 5000)
    @Override
    public NotifyTaskEntity settlementMarketPayOrder(GroupBuyTeamSettlementAggregate groupBuyTeamSettlementAggregate) {
        UserEntity userEntity = groupBuyTeamSettlementAggregate.getUserEntity();
        GroupBuyTeamEntity groupBuyTeamEntity = groupBuyTeamSettlementAggregate.getGroupBuyTeamEntity();
        TradePaySuccessEntity tradePaySuccessEntity = groupBuyTeamSettlementAggregate.getTradePaySuccessEntity();

        groupBuyOrderPaidSupport.markPaid(userEntity, tradePaySuccessEntity);
        return groupBuyTeamFormationSupport.settleTeam(userEntity, groupBuyTeamEntity);
    }

}
