package cn.bugstack.trigger.support;

import cn.bugstack.api.dto.GoodsMarketResponseDTO;
import cn.bugstack.domain.activity.model.entity.TrialBalanceEntity;
import cn.bugstack.domain.activity.model.entity.UserGroupBuyOrderDetailEntity;
import cn.bugstack.domain.activity.model.valobj.TeamStatisticVO;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Component
public class GroupBuyMarketConfigResponseAssembler {

    public GoodsMarketResponseDTO toResponse(TrialBalanceEntity trialBalanceEntity,
                                             List<UserGroupBuyOrderDetailEntity> userGroupBuyOrderDetailEntities,
                                             TeamStatisticVO teamStatisticVO) {
        GoodsMarketResponseDTO.Goods goods = GoodsMarketResponseDTO.Goods.builder()
                .goodsId(trialBalanceEntity.getGoodsId())
                .originalPrice(trialBalanceEntity.getOriginalPrice())
                .deductionPrice(trialBalanceEntity.getDeductionPrice())
                .payPrice(trialBalanceEntity.getPayPrice())
                .build();

        List<GoodsMarketResponseDTO.Team> teams = new ArrayList<>();
        if (null != userGroupBuyOrderDetailEntities && !userGroupBuyOrderDetailEntities.isEmpty()) {
            for (UserGroupBuyOrderDetailEntity userGroupBuyOrderDetailEntity : userGroupBuyOrderDetailEntities) {
                teams.add(GoodsMarketResponseDTO.Team.builder()
                        .userId(userGroupBuyOrderDetailEntity.getUserId())
                        .teamId(userGroupBuyOrderDetailEntity.getTeamId())
                        .activityId(userGroupBuyOrderDetailEntity.getActivityId())
                        .targetCount(userGroupBuyOrderDetailEntity.getTargetCount())
                        .completeCount(userGroupBuyOrderDetailEntity.getCompleteCount())
                        .lockCount(userGroupBuyOrderDetailEntity.getLockCount())
                        .validStartTime(userGroupBuyOrderDetailEntity.getValidStartTime())
                        .validEndTime(userGroupBuyOrderDetailEntity.getValidEndTime())
                        .validTimeCountdown(GoodsMarketResponseDTO.Team.differenceDateTime2Str(new Date(), userGroupBuyOrderDetailEntity.getValidEndTime()))
                        .outTradeNo(userGroupBuyOrderDetailEntity.getOutTradeNo())
                        .build());
            }
        }

        GoodsMarketResponseDTO.TeamStatistic teamStatistic = GoodsMarketResponseDTO.TeamStatistic.builder()
                .allTeamCount(teamStatisticVO.getAllTeamCount())
                .allTeamCompleteCount(teamStatisticVO.getAllTeamCompleteCount())
                .allTeamUserCount(teamStatisticVO.getAllTeamUserCount())
                .build();

        return GoodsMarketResponseDTO.builder()
                .activityId(trialBalanceEntity.getGroupBuyActivityDiscountVO().getActivityId())
                .goods(goods)
                .teamList(teams)
                .teamStatistic(teamStatistic)
                .build();
    }

}
