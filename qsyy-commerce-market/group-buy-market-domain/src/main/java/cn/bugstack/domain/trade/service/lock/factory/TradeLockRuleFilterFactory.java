package cn.bugstack.domain.trade.service.lock.factory;

import cn.bugstack.domain.trade.model.entity.GroupBuyActivityEntity;
import cn.bugstack.domain.trade.model.entity.TradeLockRuleCommandEntity;
import cn.bugstack.domain.trade.model.entity.TradeLockRuleFilterBackEntity;
import cn.bugstack.domain.trade.service.lock.filter.ActivityUsabilityRuleFilter;
import cn.bugstack.domain.trade.service.lock.filter.TeamStockOccupyRuleFilter;
import cn.bugstack.domain.trade.service.lock.filter.UserTakeLimitRuleFilter;
import cn.bugstack.wrench.design.framework.link.model2.LinkArmory;
import cn.bugstack.wrench.design.framework.link.model2.chain.BusinessLinkedList;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

/**
 * @author qsyy
 * @description 交易规则过滤工厂
 * @create 2025-01-25 08:41
 */
@Slf4j
public class TradeLockRuleFilterFactory {

    private static final String teamStockKey = "group_buy_market_team_stock_key_";
    private static final String teamUserKey = "group_buy_market_team_user_key_";

    public BusinessLinkedList<TradeLockRuleCommandEntity, DynamicContext, TradeLockRuleFilterBackEntity> tradeRuleFilter(
            ActivityUsabilityRuleFilter activityUsabilityRuleFilter,
            UserTakeLimitRuleFilter userTakeLimitRuleFilter,
            TeamStockOccupyRuleFilter teamStockOccupyRuleFilter) {

        // 组装链
        LinkArmory<TradeLockRuleCommandEntity, DynamicContext, TradeLockRuleFilterBackEntity> linkArmory =
                new LinkArmory<>("交易规则过滤链",
                        activityUsabilityRuleFilter,
                        userTakeLimitRuleFilter,
                        teamStockOccupyRuleFilter);

        // 链对象
        return linkArmory.getLogicLink();
    }

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class DynamicContext {

        private GroupBuyActivityEntity groupBuyActivity;

        private Integer userTakeOrderCount;

        public String generateTeamStockKey(String teamId) {
            if (StringUtils.isBlank(teamId)) return null;
            return TradeLockRuleFilterFactory.generateTeamStockKey(groupBuyActivity.getActivityId(), teamId);
        }

        public String generateRecoveryTeamStockKey(String teamId) {
            if (StringUtils.isBlank(teamId)) return null;
            return TradeLockRuleFilterFactory.generateRecoveryTeamStockKey(groupBuyActivity.getActivityId(), teamId);
        }

        public String generateUserTeamOccupyKey(String teamId, String userId) {
            if (StringUtils.isBlank(teamId) || StringUtils.isBlank(userId)) return null;
            return TradeLockRuleFilterFactory.generateUserTeamOccupyKey(groupBuyActivity.getActivityId(), teamId, userId);
        }

    }

    public static String generateTeamStockKey(Long activityId, String teamId){
        return teamStockKey + activityId + "_" + teamId;
    }

    public static String generateRecoveryTeamStockKey(Long activityId, String teamId) {
        return teamStockKey + activityId + "_" + teamId + "_recovery";
    }

    public static String generateUserTeamOccupyKey(Long activityId, String teamId, String userId) {
        return teamUserKey + activityId + "_" + teamId + "_" + userId;
    }

}
