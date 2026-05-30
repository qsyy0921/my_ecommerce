package cn.bugstack.infrastructure.adapter.support;

import cn.bugstack.domain.shared.adapter.port.IOrderStateFlowPort;
import cn.bugstack.domain.shared.model.entity.OrderStateTransitionEntity;
import cn.bugstack.domain.trade.model.entity.PayActivityEntity;
import cn.bugstack.domain.trade.model.entity.PayDiscountEntity;
import cn.bugstack.domain.trade.model.entity.UserEntity;
import cn.bugstack.domain.trade.model.valobj.NotifyConfigVO;
import cn.bugstack.infrastructure.dao.IGroupBuyOrderDao;
import cn.bugstack.infrastructure.dao.po.GroupBuyOrder;
import cn.bugstack.types.enums.ResponseCode;
import cn.bugstack.types.exception.AppException;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

@Component
public class GroupBuyTeamLockSupport {

    @Resource
    private IGroupBuyOrderDao groupBuyOrderDao;
    @Resource
    private IOrderStateFlowPort orderStateFlowPort;

    public String lockTeam(UserEntity userEntity, PayActivityEntity payActivityEntity, PayDiscountEntity payDiscountEntity) {
        String teamId = payActivityEntity.getTeamId();
        if (StringUtils.isBlank(teamId)) {
            return createTeam(userEntity, payActivityEntity, payDiscountEntity);
        }
        addLockCount(teamId);
        return teamId;
    }

    private String createTeam(UserEntity userEntity, PayActivityEntity payActivityEntity, PayDiscountEntity payDiscountEntity) {
        String teamId = RandomStringUtils.randomNumeric(8);
        NotifyConfigVO notifyConfigVO = payDiscountEntity.getNotifyConfigVO();
        GroupBuyOrder groupBuyOrder = GroupBuyOrder.builder()
                .teamId(teamId)
                .activityId(payActivityEntity.getActivityId())
                .source(payDiscountEntity.getSource())
                .channel(payDiscountEntity.getChannel())
                .originalPrice(payDiscountEntity.getOriginalPrice())
                .deductionPrice(payDiscountEntity.getDeductionPrice())
                .payPrice(payDiscountEntity.getPayPrice())
                .targetCount(payActivityEntity.getTargetCount())
                .completeCount(0)
                .lockCount(1)
                .validStartTime(payActivityEntity.getStartTime())
                .validEndTime(payActivityEntity.getEndTime())
                .notifyType(notifyConfigVO.getNotifyType().getCode())
                .notifyUrl(notifyConfigVO.getNotifyUrl())
                .build();

        groupBuyOrderDao.insert(groupBuyOrder);
        orderStateFlowPort.record(OrderStateTransitionEntity.groupBuyTeamOpened(
                teamId,
                userEntity.getUserId(),
                MDC.get("trace-id")));
        return teamId;
    }

    private void addLockCount(String teamId) {
        int updateAddTargetCount = groupBuyOrderDao.updateAddLockCount(teamId);
        if (1 != updateAddTargetCount) {
            throw new AppException(ResponseCode.E0005);
        }
    }

}
