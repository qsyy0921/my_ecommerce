package cn.bugstack.domain.trade.service.lock.filter;

import cn.bugstack.domain.trade.adapter.port.IGroupBuyTeamStockPort;
import cn.bugstack.domain.trade.adapter.port.IGroupBuyQueryPort;
import cn.bugstack.domain.trade.model.entity.GroupBuyActivityEntity;
import cn.bugstack.domain.trade.model.entity.GroupBuyTeamEntity;
import cn.bugstack.domain.trade.model.entity.TradeLockRuleCommandEntity;
import cn.bugstack.domain.trade.model.entity.TradeLockRuleFilterBackEntity;
import cn.bugstack.domain.trade.service.lock.factory.TradeLockRuleFilterFactory;
import cn.bugstack.wrench.design.framework.link.model2.handler.ILogicHandler;
import cn.bugstack.types.enums.GroupBuyOrderEnumVO;
import cn.bugstack.types.enums.ResponseCode;
import cn.bugstack.types.exception.AppException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.util.Date;
import java.util.Objects;

/**
 * @author qsyy
 * @description 组队库存占用规则过滤
 * @create 2025-04-05 09:41
 */
@Slf4j
public class TeamStockOccupyRuleFilter implements ILogicHandler<TradeLockRuleCommandEntity, TradeLockRuleFilterFactory.DynamicContext, TradeLockRuleFilterBackEntity> {

    private final IGroupBuyQueryPort groupBuyQueryPort;
    private final IGroupBuyTeamStockPort groupBuyTeamStockPort;

    public TeamStockOccupyRuleFilter(IGroupBuyQueryPort groupBuyQueryPort, IGroupBuyTeamStockPort groupBuyTeamStockPort) {
        this.groupBuyQueryPort = groupBuyQueryPort;
        this.groupBuyTeamStockPort = groupBuyTeamStockPort;
    }

    @Override
    public TradeLockRuleFilterBackEntity apply(TradeLockRuleCommandEntity requestParameter, TradeLockRuleFilterFactory.DynamicContext dynamicContext) throws Exception {
        log.info("交易规则过滤-组队库存校验{} activityId:{}", requestParameter.getUserId(), requestParameter.getActivityId());

        // 1. teamId 为空，则为首次开团，不做拼团组队目标量库存限制
        String teamId = requestParameter.getTeamId();
        if (StringUtils.isBlank(teamId)) {
            return TradeLockRuleFilterBackEntity.builder()
                    .userTakeOrderCount(dynamicContext.getUserTakeOrderCount())
                    .build();
        }

        // 2. 抢占库存；通过抢占 Redis 缓存库存，来降低对数据库的操作压力。
        GroupBuyActivityEntity groupBuyActivity = dynamicContext.getGroupBuyActivity();
        GroupBuyTeamEntity groupBuyTeam = groupBuyQueryPort.queryGroupBuyTeamByTeamId(teamId);
        if (null == groupBuyTeam || !Objects.equals(groupBuyActivity.getActivityId(), groupBuyTeam.getActivityId())) {
            log.warn("交易规则过滤-组队库存校验{} activityId:{} 队伍不存在或活动不匹配:{}", requestParameter.getUserId(), requestParameter.getActivityId(), teamId);
            throw new AppException(ResponseCode.E0107);
        }
        if (!groupBuyTeam.getStatus().canJoin()
                || null == groupBuyTeam.getValidEndTime()
                || new Date().after(groupBuyTeam.getValidEndTime())
                || groupBuyTeam.getLockCount() >= groupBuyTeam.getTargetCount()) {
            log.warn("交易规则过滤-组队库存校验{} activityId:{} 队伍已失效或已满:{}", requestParameter.getUserId(), requestParameter.getActivityId(), teamId);
            throw new AppException(ResponseCode.E0107);
        }

        Integer target = groupBuyActivity.getTarget();
        Integer validTime = groupBuyActivity.getValidTime();
        String teamStockKey = dynamicContext.generateTeamStockKey(teamId);
        String recoveryTeamStockKey = dynamicContext.generateRecoveryTeamStockKey(teamId);
        String userTeamOccupyKey = dynamicContext.generateUserTeamOccupyKey(teamId, requestParameter.getUserId());

        long occupy = groupBuyTeamStockPort.occupyTeamStock(teamStockKey, recoveryTeamStockKey, userTeamOccupyKey, requestParameter.getOutTradeNo(), target, validTime);

        if (-3 == occupy) {
            log.warn("交易规则过滤-组队库存校验{} activityId:{} 用户已占用队伍名额:{}", requestParameter.getUserId(), requestParameter.getActivityId(), userTeamOccupyKey);
            throw new AppException(ResponseCode.E0009);
        }
        if (-4 == occupy) {
            log.warn("交易规则过滤-组队库存校验{} activityId:{} 锁单请求处理中:{}", requestParameter.getUserId(), requestParameter.getActivityId(), userTeamOccupyKey);
            throw new AppException(ResponseCode.E0010);
        }
        if (0 > occupy) {
            log.warn("交易规则过滤-组队库存校验{} activityId:{} 抢占失败:{} result:{}", requestParameter.getUserId(), requestParameter.getActivityId(), teamStockKey, occupy);
            throw new AppException(ResponseCode.E0008);
        }

        return TradeLockRuleFilterBackEntity.builder()
                .userTakeOrderCount(dynamicContext.getUserTakeOrderCount())
                .recoveryTeamStockKey(recoveryTeamStockKey)
                .userTeamOccupyKey(userTeamOccupyKey)
                .build();
    }

}
