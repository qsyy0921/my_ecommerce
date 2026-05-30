package cn.bugstack.infrastructure.adapter.support;

import cn.bugstack.domain.shared.adapter.port.IOrderStateFlowPort;
import cn.bugstack.domain.shared.model.entity.OrderStateTransitionEntity;
import cn.bugstack.domain.trade.adapter.port.ITradeNotifyTaskCreatePort;
import cn.bugstack.domain.trade.model.entity.GroupBuyTeamEntity;
import cn.bugstack.domain.trade.model.entity.NotifyTaskEntity;
import cn.bugstack.domain.trade.model.entity.UserEntity;
import cn.bugstack.domain.trade.model.valobj.NotifyConfigVO;
import cn.bugstack.infrastructure.dao.IGroupBuyOrderDao;
import cn.bugstack.infrastructure.dao.IGroupBuyOrderListDao;
import cn.bugstack.types.enums.ResponseCode;
import cn.bugstack.types.exception.AppException;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.List;

@Component
public class GroupBuyTeamFormationSupport {

    @Resource
    private IGroupBuyOrderDao groupBuyOrderDao;
    @Resource
    private IGroupBuyOrderListDao groupBuyOrderListDao;
    @Resource
    private IOrderStateFlowPort orderStateFlowPort;
    @Resource
    private ITradeNotifyTaskCreatePort tradeNotifyTaskCreatePort;

    public NotifyTaskEntity settleTeam(UserEntity userEntity, GroupBuyTeamEntity groupBuyTeamEntity) {
        int updateAddCount = groupBuyOrderDao.updateAddCompleteCount(groupBuyTeamEntity.getTeamId());
        if (1 != updateAddCount) {
            throw new AppException(ResponseCode.UPDATE_ZERO);
        }

        int updateOrderStatusCount = groupBuyOrderDao.updateOrderStatus2COMPLETE(groupBuyTeamEntity.getTeamId());
        if (1 != updateOrderStatusCount) {
            return null;
        }

        orderStateFlowPort.record(OrderStateTransitionEntity.groupBuyTeamFormed(
                groupBuyTeamEntity.getTeamId(),
                userEntity.getUserId(),
                MDC.get("trace-id")));

        NotifyConfigVO notifyConfigVO = groupBuyTeamEntity.getNotifyConfigVO();
        List<String> outTradeNoList = groupBuyOrderListDao.queryGroupBuyCompleteOrderOutTradeNoListByTeamId(groupBuyTeamEntity.getTeamId());
        return tradeNotifyTaskCreatePort.createSettlementTask(
                groupBuyTeamEntity.getActivityId(),
                groupBuyTeamEntity.getTeamId(),
                notifyConfigVO,
                outTradeNoList);
    }

}
