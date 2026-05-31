package cn.bugstack.domain.activity.service;

import cn.bugstack.domain.activity.adapter.port.IGroupBuyDisplayPort;
import cn.bugstack.domain.activity.model.entity.UserGroupBuyOrderDetailEntity;
import cn.bugstack.domain.activity.model.entity.MarketProductEntity;
import cn.bugstack.domain.activity.model.entity.TrialBalanceEntity;
import cn.bugstack.domain.activity.model.valobj.TeamStatisticVO;
import cn.bugstack.domain.activity.service.trial.factory.DefaultActivityStrategyFactory;
import cn.bugstack.wrench.design.framework.tree.StrategyHandler;

import java.util.ArrayList;
import java.util.List;

/**
 * @author qsyy
 * @description 首页营销服务
 * @create 2024-12-14 14:33
 */
public class IndexGroupBuyMarketServiceImpl implements IIndexGroupBuyMarketService {

    private final DefaultActivityStrategyFactory defaultActivityStrategyFactory;
    private final IGroupBuyDisplayPort groupBuyDisplayPort;

    public IndexGroupBuyMarketServiceImpl(DefaultActivityStrategyFactory defaultActivityStrategyFactory,
                                          IGroupBuyDisplayPort groupBuyDisplayPort) {
        this.defaultActivityStrategyFactory = defaultActivityStrategyFactory;
        this.groupBuyDisplayPort = groupBuyDisplayPort;
    }

    @Override
    public TrialBalanceEntity indexMarketTrial(MarketProductEntity marketProductEntity) throws Exception {
        // 获取执行策略
        StrategyHandler<MarketProductEntity, DefaultActivityStrategyFactory.DynamicContext, TrialBalanceEntity> strategyHandler = defaultActivityStrategyFactory.strategyHandler();
        // 受理试算操作
        return strategyHandler.apply(marketProductEntity, new DefaultActivityStrategyFactory.DynamicContext());
    }

    @Override
    public List<UserGroupBuyOrderDetailEntity> queryInProgressUserGroupBuyOrderDetailList(Long activityId, String userId, Integer ownerCount, Integer randomCount) {
        List<UserGroupBuyOrderDetailEntity> unionAllList = new ArrayList<>();

        // 查询个人拼团数据
        if (0 != ownerCount) {
            List<UserGroupBuyOrderDetailEntity> ownerList = groupBuyDisplayPort.queryInProgressUserGroupBuyOrderDetailListByOwner(activityId, userId, ownerCount);
            if (null != ownerList && !ownerList.isEmpty()){
                unionAllList.addAll(ownerList);
            }
        }

        // 查询其他非个人拼团
        if (0 != randomCount) {
            List<UserGroupBuyOrderDetailEntity> randomList = groupBuyDisplayPort.queryInProgressUserGroupBuyOrderDetailListByRandom(activityId, userId, randomCount);
            if (null != randomList && !randomList.isEmpty()){
                unionAllList.addAll(randomList);
            }
        }

        return unionAllList;
    }

    @Override
    public TeamStatisticVO queryTeamStatisticByActivityId(Long activityId) {
        return groupBuyDisplayPort.queryTeamStatisticByActivityId(activityId);
    }

}
