package cn.bugstack.config;

import cn.bugstack.domain.message.adapter.repository.IMessageRecordRepository;
import cn.bugstack.domain.message.service.IMessageRecordService;
import cn.bugstack.domain.message.service.MessageRecordService;
import cn.bugstack.domain.seckill.adapter.port.ISeckillMaintenancePort;
import cn.bugstack.domain.seckill.adapter.port.ISeckillOrderCommandPort;
import cn.bugstack.domain.seckill.adapter.port.ISeckillOrderLockPort;
import cn.bugstack.domain.seckill.adapter.port.ISeckillQueryPort;
import cn.bugstack.domain.seckill.adapter.port.ISeckillStockAvailabilityPort;
import cn.bugstack.domain.seckill.service.ISeckillService;
import cn.bugstack.domain.seckill.service.SeckillService;
import cn.bugstack.domain.shared.adapter.port.IDomainTaskExecutor;
import cn.bugstack.domain.tag.adapter.repository.ITagRepository;
import cn.bugstack.domain.tag.service.ITagService;
import cn.bugstack.domain.tag.service.TagService;
import cn.bugstack.domain.trade.adapter.port.IGroupBuyOrderPort;
import cn.bugstack.domain.trade.adapter.port.IGroupBuyQueryPort;
import cn.bugstack.domain.trade.adapter.port.IGroupBuyTeamStockPort;
import cn.bugstack.domain.trade.adapter.port.IGroupBuyTimeoutOrderPort;
import cn.bugstack.domain.trade.adapter.port.IGroupBuySettlementPort;
import cn.bugstack.domain.trade.adapter.port.ITradeLockRequestPort;
import cn.bugstack.domain.trade.adapter.port.ITradeNotifyTaskPort;
import cn.bugstack.domain.trade.adapter.port.ITradePort;
import cn.bugstack.domain.trade.model.entity.TradeLockRuleCommandEntity;
import cn.bugstack.domain.trade.model.entity.TradeLockRuleFilterBackEntity;
import cn.bugstack.domain.trade.model.entity.TradeRefundBehaviorEntity;
import cn.bugstack.domain.trade.model.entity.TradeRefundCommandEntity;
import cn.bugstack.domain.trade.model.entity.TradeSettlementRuleCommandEntity;
import cn.bugstack.domain.trade.model.entity.TradeSettlementRuleFilterBackEntity;
import cn.bugstack.domain.trade.service.ITradeLockOrderService;
import cn.bugstack.domain.trade.service.ITradeRefundOrderService;
import cn.bugstack.domain.trade.service.ITradeSettlementOrderService;
import cn.bugstack.domain.trade.service.ITradeTaskService;
import cn.bugstack.domain.trade.service.lock.TradeLockOrderService;
import cn.bugstack.domain.trade.service.lock.factory.TradeLockRuleFilterFactory;
import cn.bugstack.domain.trade.service.refund.TradeRefundOrderService;
import cn.bugstack.domain.trade.service.refund.business.IRefundOrderStrategy;
import cn.bugstack.domain.trade.service.refund.factory.TradeRefundRuleFilterFactory;
import cn.bugstack.domain.trade.service.settlement.TradeSettlementOrderService;
import cn.bugstack.domain.trade.service.settlement.factory.TradeSettlementRuleFilterFactory;
import cn.bugstack.domain.trade.service.task.TradeTaskService;
import cn.bugstack.wrench.design.framework.link.model2.chain.BusinessLinkedList;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

@Configuration
public class DomainServiceConfig {

    @Bean
    public ISeckillService seckillService(ISeckillQueryPort seckillQueryPort,
                                          ISeckillStockAvailabilityPort seckillStockAvailabilityPort,
                                          ISeckillOrderLockPort seckillOrderLockPort,
                                          ISeckillMaintenancePort seckillMaintenancePort,
                                          ISeckillOrderCommandPort seckillOrderCommandPort,
                                          @Value("${app.seckill.lock.max-concurrent-per-activity:200}") Integer maxConcurrentPerActivity) {
        return new SeckillService(seckillQueryPort, seckillStockAvailabilityPort, seckillOrderLockPort, seckillMaintenancePort, seckillOrderCommandPort, maxConcurrentPerActivity);
    }

    @Bean
    public IMessageRecordService messageRecordService(IMessageRecordRepository messageRecordRepository) {
        return new MessageRecordService(messageRecordRepository);
    }

    @Bean
    public ITagService tagService(ITagRepository tagRepository) {
        return new TagService(tagRepository);
    }

    @Bean
    public ITradeTaskService tradeTaskService(ITradeNotifyTaskPort tradeNotifyTaskPort, ITradePort tradePort) {
        return new TradeTaskService(tradeNotifyTaskPort, tradePort);
    }

    @Bean
    public ITradeLockOrderService tradeLockOrderService(
            IGroupBuyQueryPort groupBuyQueryPort,
            IGroupBuyOrderPort groupBuyOrderPort,
            IGroupBuyTeamStockPort groupBuyTeamStockPort,
            ITradeLockRequestPort tradeLockRequestPort,
            @Qualifier("tradeRuleFilter") BusinessLinkedList<TradeLockRuleCommandEntity, TradeLockRuleFilterFactory.DynamicContext, TradeLockRuleFilterBackEntity> tradeRuleFilter) {
        return new TradeLockOrderService(groupBuyQueryPort, groupBuyOrderPort, groupBuyTeamStockPort, tradeLockRequestPort, tradeRuleFilter);
    }

    @Bean
    public ITradeSettlementOrderService tradeSettlementOrderService(
            IGroupBuySettlementPort groupBuySettlementPort,
            IDomainTaskExecutor domainTaskExecutor,
            ITradeTaskService tradeTaskService,
            @Qualifier("tradeSettlementRuleFilter") BusinessLinkedList<TradeSettlementRuleCommandEntity, TradeSettlementRuleFilterFactory.DynamicContext, TradeSettlementRuleFilterBackEntity> tradeSettlementRuleFilter) {
        return new TradeSettlementOrderService(groupBuySettlementPort, domainTaskExecutor, tradeTaskService, tradeSettlementRuleFilter);
    }

    @Bean
    public ITradeRefundOrderService tradeRefundOrderService(
            IGroupBuyTimeoutOrderPort groupBuyTimeoutOrderPort,
            Map<String, IRefundOrderStrategy> refundOrderStrategyMap,
            @Qualifier("tradeRefundRuleFilter") BusinessLinkedList<TradeRefundCommandEntity, TradeRefundRuleFilterFactory.DynamicContext, TradeRefundBehaviorEntity> tradeRefundRuleFilter) {
        return new TradeRefundOrderService(groupBuyTimeoutOrderPort, refundOrderStrategyMap, tradeRefundRuleFilter);
    }

}
