package cn.bugstack.config;

import cn.bugstack.domain.activity.adapter.port.IActivitySwitchPort;
import cn.bugstack.domain.activity.adapter.port.IActivityTrialQueryPort;
import cn.bugstack.domain.activity.adapter.port.ICrowdTagPort;
import cn.bugstack.domain.activity.adapter.port.IGroupBuyDisplayPort;
import cn.bugstack.domain.activity.service.IIndexGroupBuyMarketService;
import cn.bugstack.domain.activity.service.IndexGroupBuyMarketServiceImpl;
import cn.bugstack.domain.activity.service.discount.IDiscountCalculateService;
import cn.bugstack.domain.activity.service.discount.impl.MJCalculateService;
import cn.bugstack.domain.activity.service.discount.impl.NCalculateService;
import cn.bugstack.domain.activity.service.discount.impl.ZJCalculateService;
import cn.bugstack.domain.activity.service.discount.impl.ZKCalculateService;
import cn.bugstack.domain.activity.service.trial.factory.DefaultActivityStrategyFactory;
import cn.bugstack.domain.activity.service.trial.node.EndNode;
import cn.bugstack.domain.activity.service.trial.node.ErrorNode;
import cn.bugstack.domain.activity.service.trial.node.MarketNode;
import cn.bugstack.domain.activity.service.trial.node.RootNode;
import cn.bugstack.domain.activity.service.trial.node.SwitchNode;
import cn.bugstack.domain.activity.service.trial.node.TagNode;
import cn.bugstack.domain.shared.adapter.port.IDomainTaskExecutor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

@Configuration
public class ActivityDomainConfig {

    @Bean("N")
    public IDiscountCalculateService nCalculateService(ICrowdTagPort crowdTagPort) {
        return new NCalculateService(crowdTagPort);
    }

    @Bean("MJ")
    public IDiscountCalculateService mjCalculateService(ICrowdTagPort crowdTagPort) {
        return new MJCalculateService(crowdTagPort);
    }

    @Bean("ZK")
    public IDiscountCalculateService zkCalculateService(ICrowdTagPort crowdTagPort) {
        return new ZKCalculateService(crowdTagPort);
    }

    @Bean("ZJ")
    public IDiscountCalculateService zjCalculateService(ICrowdTagPort crowdTagPort) {
        return new ZJCalculateService(crowdTagPort);
    }

    @Bean
    public ErrorNode errorNode() {
        return new ErrorNode();
    }

    @Bean
    public EndNode endNode() {
        return new EndNode();
    }

    @Bean
    public TagNode tagNode(ICrowdTagPort crowdTagPort, EndNode endNode) {
        return new TagNode(crowdTagPort, endNode);
    }

    @Bean
    public MarketNode marketNode(IActivityTrialQueryPort activityTrialQueryPort,
                                 IDomainTaskExecutor domainTaskExecutor,
                                 Map<String, IDiscountCalculateService> discountCalculateServiceMap,
                                 ErrorNode errorNode,
                                 TagNode tagNode) {
        return new MarketNode(activityTrialQueryPort, domainTaskExecutor, discountCalculateServiceMap, errorNode, tagNode);
    }

    @Bean
    public SwitchNode switchNode(IActivitySwitchPort activitySwitchPort, MarketNode marketNode) {
        return new SwitchNode(activitySwitchPort, marketNode);
    }

    @Bean
    public RootNode rootNode(SwitchNode switchNode) {
        return new RootNode(switchNode);
    }

    @Bean
    public DefaultActivityStrategyFactory defaultActivityStrategyFactory(RootNode rootNode) {
        return new DefaultActivityStrategyFactory(rootNode);
    }

    @Bean
    public IIndexGroupBuyMarketService indexGroupBuyMarketService(DefaultActivityStrategyFactory defaultActivityStrategyFactory,
                                                                  IGroupBuyDisplayPort groupBuyDisplayPort) {
        return new IndexGroupBuyMarketServiceImpl(defaultActivityStrategyFactory, groupBuyDisplayPort);
    }

}
