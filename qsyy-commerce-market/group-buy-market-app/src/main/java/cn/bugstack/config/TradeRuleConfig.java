package cn.bugstack.config;

import cn.bugstack.domain.shared.adapter.port.IDomainTaskExecutor;
import cn.bugstack.domain.trade.adapter.port.IGroupBuyQueryPort;
import cn.bugstack.domain.trade.adapter.port.IGroupBuyRefundPort;
import cn.bugstack.domain.trade.adapter.port.IGroupBuyTeamStockPort;
import cn.bugstack.domain.trade.adapter.port.ITradePolicyPort;
import cn.bugstack.domain.trade.model.entity.TradeLockRuleCommandEntity;
import cn.bugstack.domain.trade.model.entity.TradeLockRuleFilterBackEntity;
import cn.bugstack.domain.trade.model.entity.TradeRefundBehaviorEntity;
import cn.bugstack.domain.trade.model.entity.TradeRefundCommandEntity;
import cn.bugstack.domain.trade.model.entity.TradeSettlementRuleCommandEntity;
import cn.bugstack.domain.trade.model.entity.TradeSettlementRuleFilterBackEntity;
import cn.bugstack.domain.trade.service.ITradeTaskService;
import cn.bugstack.domain.trade.service.lock.factory.TradeLockRuleFilterFactory;
import cn.bugstack.domain.trade.service.lock.filter.ActivityUsabilityRuleFilter;
import cn.bugstack.domain.trade.service.lock.filter.TeamStockOccupyRuleFilter;
import cn.bugstack.domain.trade.service.lock.filter.UserTakeLimitRuleFilter;
import cn.bugstack.domain.trade.service.refund.business.IRefundOrderStrategy;
import cn.bugstack.domain.trade.service.refund.business.impl.Paid2RefundStrategy;
import cn.bugstack.domain.trade.service.refund.business.impl.PaidTeam2RefundStrategy;
import cn.bugstack.domain.trade.service.refund.business.impl.Unpaid2RefundStrategy;
import cn.bugstack.domain.trade.service.refund.factory.TradeRefundRuleFilterFactory;
import cn.bugstack.domain.trade.service.refund.filter.DataNodeFilter;
import cn.bugstack.domain.trade.service.refund.filter.RefundOrderNodeFilter;
import cn.bugstack.domain.trade.service.refund.filter.UniqueRefundNodeFilter;
import cn.bugstack.domain.trade.service.settlement.factory.TradeSettlementRuleFilterFactory;
import cn.bugstack.domain.trade.service.settlement.filter.EndRuleFilter;
import cn.bugstack.domain.trade.service.settlement.filter.OutTradeNoRuleFilter;
import cn.bugstack.domain.trade.service.settlement.filter.SCRuleFilter;
import cn.bugstack.domain.trade.service.settlement.filter.SettableRuleFilter;
import cn.bugstack.wrench.design.framework.link.model2.chain.BusinessLinkedList;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

@Configuration
public class TradeRuleConfig {

    @Bean
    public ActivityUsabilityRuleFilter activityUsabilityRuleFilter(IGroupBuyQueryPort groupBuyQueryPort) {
        return new ActivityUsabilityRuleFilter(groupBuyQueryPort);
    }

    @Bean
    public UserTakeLimitRuleFilter userTakeLimitRuleFilter(IGroupBuyQueryPort groupBuyQueryPort) {
        return new UserTakeLimitRuleFilter(groupBuyQueryPort);
    }

    @Bean
    public TeamStockOccupyRuleFilter teamStockOccupyRuleFilter(IGroupBuyQueryPort groupBuyQueryPort,
                                                               IGroupBuyTeamStockPort groupBuyTeamStockPort) {
        return new TeamStockOccupyRuleFilter(groupBuyQueryPort, groupBuyTeamStockPort);
    }

    @Bean("tradeRuleFilter")
    public BusinessLinkedList<TradeLockRuleCommandEntity, TradeLockRuleFilterFactory.DynamicContext, TradeLockRuleFilterBackEntity> tradeRuleFilter(
            ActivityUsabilityRuleFilter activityUsabilityRuleFilter,
            UserTakeLimitRuleFilter userTakeLimitRuleFilter,
            TeamStockOccupyRuleFilter teamStockOccupyRuleFilter) {
        return new TradeLockRuleFilterFactory().tradeRuleFilter(activityUsabilityRuleFilter, userTakeLimitRuleFilter, teamStockOccupyRuleFilter);
    }

    @Bean
    public SCRuleFilter scRuleFilter(ITradePolicyPort tradePolicyPort) {
        return new SCRuleFilter(tradePolicyPort);
    }

    @Bean
    public OutTradeNoRuleFilter outTradeNoRuleFilter(IGroupBuyQueryPort groupBuyQueryPort) {
        return new OutTradeNoRuleFilter(groupBuyQueryPort);
    }

    @Bean
    public SettableRuleFilter settableRuleFilter(IGroupBuyQueryPort groupBuyQueryPort) {
        return new SettableRuleFilter(groupBuyQueryPort);
    }

    @Bean
    public EndRuleFilter endRuleFilter() {
        return new EndRuleFilter();
    }

    @Bean("tradeSettlementRuleFilter")
    public BusinessLinkedList<TradeSettlementRuleCommandEntity, TradeSettlementRuleFilterFactory.DynamicContext, TradeSettlementRuleFilterBackEntity> tradeSettlementRuleFilter(
            SCRuleFilter scRuleFilter,
            OutTradeNoRuleFilter outTradeNoRuleFilter,
            SettableRuleFilter settableRuleFilter,
            EndRuleFilter endRuleFilter) {
        return new TradeSettlementRuleFilterFactory().tradeSettlementRuleFilter(scRuleFilter, outTradeNoRuleFilter, settableRuleFilter, endRuleFilter);
    }

    @Bean
    public DataNodeFilter dataNodeFilter(IGroupBuyQueryPort groupBuyQueryPort) {
        return new DataNodeFilter(groupBuyQueryPort);
    }

    @Bean
    public UniqueRefundNodeFilter uniqueRefundNodeFilter() {
        return new UniqueRefundNodeFilter();
    }

    @Bean
    public RefundOrderNodeFilter refundOrderNodeFilter(Map<String, IRefundOrderStrategy> refundOrderStrategyMap) {
        return new RefundOrderNodeFilter(refundOrderStrategyMap);
    }

    @Bean("tradeRefundRuleFilter")
    public BusinessLinkedList<TradeRefundCommandEntity, TradeRefundRuleFilterFactory.DynamicContext, TradeRefundBehaviorEntity> tradeRefundRuleFilter(
            DataNodeFilter dataNodeFilter,
            UniqueRefundNodeFilter uniqueRefundNodeFilter,
            RefundOrderNodeFilter refundOrderNodeFilter) {
        return new TradeRefundRuleFilterFactory().tradeRefundRuleFilter(dataNodeFilter, uniqueRefundNodeFilter, refundOrderNodeFilter);
    }

    @Bean("unpaid2RefundStrategy")
    public IRefundOrderStrategy unpaid2RefundStrategy(IGroupBuyRefundPort groupBuyRefundPort,
                                                      IGroupBuyTeamStockPort groupBuyTeamStockPort,
                                                      ITradeTaskService tradeTaskService,
                                                      IDomainTaskExecutor domainTaskExecutor) {
        return new Unpaid2RefundStrategy(groupBuyRefundPort, groupBuyTeamStockPort, tradeTaskService, domainTaskExecutor);
    }

    @Bean("paid2RefundStrategy")
    public IRefundOrderStrategy paid2RefundStrategy(IGroupBuyRefundPort groupBuyRefundPort,
                                                    IGroupBuyTeamStockPort groupBuyTeamStockPort,
                                                    ITradeTaskService tradeTaskService,
                                                    IDomainTaskExecutor domainTaskExecutor) {
        return new Paid2RefundStrategy(groupBuyRefundPort, groupBuyTeamStockPort, tradeTaskService, domainTaskExecutor);
    }

    @Bean("paidTeam2RefundStrategy")
    public IRefundOrderStrategy paidTeam2RefundStrategy(IGroupBuyQueryPort groupBuyQueryPort,
                                                        IGroupBuyRefundPort groupBuyRefundPort,
                                                        IGroupBuyTeamStockPort groupBuyTeamStockPort,
                                                        ITradeTaskService tradeTaskService,
                                                        IDomainTaskExecutor domainTaskExecutor) {
        return new PaidTeam2RefundStrategy(groupBuyQueryPort, groupBuyRefundPort, groupBuyTeamStockPort, tradeTaskService, domainTaskExecutor);
    }

}
