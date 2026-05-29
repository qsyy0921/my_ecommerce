package cn.bugstack.config;

import cn.bugstack.domain.trade.adapter.repository.ITradeRepository;
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
import java.util.concurrent.ThreadPoolExecutor;

@Configuration
public class TradeRuleConfig {

    @Bean
    public ActivityUsabilityRuleFilter activityUsabilityRuleFilter(ITradeRepository tradeRepository) {
        return new ActivityUsabilityRuleFilter(tradeRepository);
    }

    @Bean
    public UserTakeLimitRuleFilter userTakeLimitRuleFilter(ITradeRepository tradeRepository) {
        return new UserTakeLimitRuleFilter(tradeRepository);
    }

    @Bean
    public TeamStockOccupyRuleFilter teamStockOccupyRuleFilter(ITradeRepository tradeRepository) {
        return new TeamStockOccupyRuleFilter(tradeRepository);
    }

    @Bean("tradeRuleFilter")
    public BusinessLinkedList<TradeLockRuleCommandEntity, TradeLockRuleFilterFactory.DynamicContext, TradeLockRuleFilterBackEntity> tradeRuleFilter(
            ActivityUsabilityRuleFilter activityUsabilityRuleFilter,
            UserTakeLimitRuleFilter userTakeLimitRuleFilter,
            TeamStockOccupyRuleFilter teamStockOccupyRuleFilter) {
        return new TradeLockRuleFilterFactory().tradeRuleFilter(activityUsabilityRuleFilter, userTakeLimitRuleFilter, teamStockOccupyRuleFilter);
    }

    @Bean
    public SCRuleFilter scRuleFilter(ITradeRepository tradeRepository) {
        return new SCRuleFilter(tradeRepository);
    }

    @Bean
    public OutTradeNoRuleFilter outTradeNoRuleFilter(ITradeRepository tradeRepository) {
        return new OutTradeNoRuleFilter(tradeRepository);
    }

    @Bean
    public SettableRuleFilter settableRuleFilter(ITradeRepository tradeRepository) {
        return new SettableRuleFilter(tradeRepository);
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
    public DataNodeFilter dataNodeFilter(ITradeRepository tradeRepository) {
        return new DataNodeFilter(tradeRepository);
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
    public IRefundOrderStrategy unpaid2RefundStrategy(ITradeRepository tradeRepository,
                                                      ITradeTaskService tradeTaskService,
                                                      ThreadPoolExecutor threadPoolExecutor) {
        return new Unpaid2RefundStrategy(tradeRepository, tradeTaskService, threadPoolExecutor);
    }

    @Bean("paid2RefundStrategy")
    public IRefundOrderStrategy paid2RefundStrategy(ITradeRepository tradeRepository,
                                                    ITradeTaskService tradeTaskService,
                                                    ThreadPoolExecutor threadPoolExecutor) {
        return new Paid2RefundStrategy(tradeRepository, tradeTaskService, threadPoolExecutor);
    }

    @Bean("paidTeam2RefundStrategy")
    public IRefundOrderStrategy paidTeam2RefundStrategy(ITradeRepository tradeRepository,
                                                        ITradeTaskService tradeTaskService,
                                                        ThreadPoolExecutor threadPoolExecutor) {
        return new PaidTeam2RefundStrategy(tradeRepository, tradeTaskService, threadPoolExecutor);
    }

}
