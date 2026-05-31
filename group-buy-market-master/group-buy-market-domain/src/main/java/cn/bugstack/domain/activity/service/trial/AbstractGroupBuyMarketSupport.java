package cn.bugstack.domain.activity.service.trial;

import cn.bugstack.domain.activity.service.trial.factory.DefaultActivityStrategyFactory;
import cn.bugstack.wrench.design.framework.tree.AbstractMultiThreadStrategyRouter;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

/**
 * @author qsyy
 * @description 抽象的拼团营销支撑类
 * @create 2024-12-14 13:42
 */
public abstract class AbstractGroupBuyMarketSupport<MarketProductEntity, DynamicContext, TrialBalanceEntity> extends AbstractMultiThreadStrategyRouter<cn.bugstack.domain.activity.model.entity.MarketProductEntity, DefaultActivityStrategyFactory.DynamicContext, cn.bugstack.domain.activity.model.entity.TrialBalanceEntity> {

    protected long timeout = 5000;

    protected AbstractGroupBuyMarketSupport() {
    }

    @Override
    protected void multiThread(cn.bugstack.domain.activity.model.entity.MarketProductEntity requestParameter, DefaultActivityStrategyFactory.DynamicContext dynamicContext) throws ExecutionException, InterruptedException, TimeoutException {
        // 缺省的方法
    }

}
