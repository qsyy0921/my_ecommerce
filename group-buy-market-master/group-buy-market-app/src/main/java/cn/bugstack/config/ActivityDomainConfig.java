package cn.bugstack.config;

import cn.bugstack.domain.activity.adapter.repository.IActivityRepository;
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
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;
import java.util.concurrent.ThreadPoolExecutor;

@Configuration
public class ActivityDomainConfig {

    @Bean("N")
    public IDiscountCalculateService nCalculateService(IActivityRepository activityRepository) {
        return new NCalculateService(activityRepository);
    }

    @Bean("MJ")
    public IDiscountCalculateService mjCalculateService(IActivityRepository activityRepository) {
        return new MJCalculateService(activityRepository);
    }

    @Bean("ZK")
    public IDiscountCalculateService zkCalculateService(IActivityRepository activityRepository) {
        return new ZKCalculateService(activityRepository);
    }

    @Bean("ZJ")
    public IDiscountCalculateService zjCalculateService(IActivityRepository activityRepository) {
        return new ZJCalculateService(activityRepository);
    }

    @Bean
    public ErrorNode errorNode(IActivityRepository activityRepository) {
        return new ErrorNode(activityRepository);
    }

    @Bean
    public EndNode endNode(IActivityRepository activityRepository) {
        return new EndNode(activityRepository);
    }

    @Bean
    public TagNode tagNode(IActivityRepository activityRepository, EndNode endNode) {
        return new TagNode(activityRepository, endNode);
    }

    @Bean
    public MarketNode marketNode(IActivityRepository activityRepository,
                                 ThreadPoolExecutor threadPoolExecutor,
                                 Map<String, IDiscountCalculateService> discountCalculateServiceMap,
                                 ErrorNode errorNode,
                                 TagNode tagNode) {
        return new MarketNode(activityRepository, threadPoolExecutor, discountCalculateServiceMap, errorNode, tagNode);
    }

    @Bean
    public SwitchNode switchNode(IActivityRepository activityRepository, MarketNode marketNode) {
        return new SwitchNode(activityRepository, marketNode);
    }

    @Bean
    public RootNode rootNode(IActivityRepository activityRepository, SwitchNode switchNode) {
        return new RootNode(activityRepository, switchNode);
    }

    @Bean
    public DefaultActivityStrategyFactory defaultActivityStrategyFactory(RootNode rootNode) {
        return new DefaultActivityStrategyFactory(rootNode);
    }

    @Bean
    public IIndexGroupBuyMarketService indexGroupBuyMarketService(DefaultActivityStrategyFactory defaultActivityStrategyFactory,
                                                                  IActivityRepository activityRepository) {
        return new IndexGroupBuyMarketServiceImpl(defaultActivityStrategyFactory, activityRepository);
    }

}
