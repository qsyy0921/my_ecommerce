package cn.bugstack.infrastructure.adapter.port;

import cn.bugstack.domain.shared.adapter.port.IOrderStateFlowPort;
import cn.bugstack.domain.shared.model.entity.OrderStateTransitionEntity;
import cn.bugstack.domain.trade.adapter.port.IGroupBuySettlementPort;
import cn.bugstack.domain.trade.adapter.port.ITradeLockRequestPort;
import cn.bugstack.domain.trade.adapter.port.ITradeNotifyTaskCreatePort;
import cn.bugstack.domain.trade.model.aggregate.GroupBuyTeamSettlementAggregate;
import cn.bugstack.domain.trade.model.entity.GroupBuyTeamEntity;
import cn.bugstack.domain.trade.model.entity.NotifyTaskEntity;
import cn.bugstack.domain.trade.model.entity.TradePaySuccessEntity;
import cn.bugstack.domain.trade.model.entity.UserEntity;
import cn.bugstack.domain.trade.model.valobj.NotifyConfigVO;
import cn.bugstack.infrastructure.dao.IGroupBuyOrderDao;
import cn.bugstack.infrastructure.dao.IGroupBuyOrderListDao;
import cn.bugstack.infrastructure.dao.po.GroupBuyOrderList;
import cn.bugstack.types.enums.ResponseCode;
import cn.bugstack.types.exception.AppException;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.List;

@Service
public class GroupBuySettlementPort implements IGroupBuySettlementPort {

    @Resource
    private IGroupBuyOrderDao groupBuyOrderDao;
    @Resource
    private IGroupBuyOrderListDao groupBuyOrderListDao;
    @Resource
    private IOrderStateFlowPort orderStateFlowPort;
    @Resource
    private ITradeNotifyTaskCreatePort tradeNotifyTaskCreatePort;
    @Resource
    private ITradeLockRequestPort tradeLockRequestPort;

    @Transactional(timeout = 5000)
    @Override
    public NotifyTaskEntity settlementMarketPayOrder(GroupBuyTeamSettlementAggregate groupBuyTeamSettlementAggregate) {
        UserEntity userEntity = groupBuyTeamSettlementAggregate.getUserEntity();
        GroupBuyTeamEntity groupBuyTeamEntity = groupBuyTeamSettlementAggregate.getGroupBuyTeamEntity();
        NotifyConfigVO notifyConfigVO = groupBuyTeamEntity.getNotifyConfigVO();
        TradePaySuccessEntity tradePaySuccessEntity = groupBuyTeamSettlementAggregate.getTradePaySuccessEntity();

        GroupBuyOrderList groupBuyOrderListReq = new GroupBuyOrderList();
        groupBuyOrderListReq.setUserId(userEntity.getUserId());
        groupBuyOrderListReq.setOutTradeNo(tradePaySuccessEntity.getOutTradeNo());
        groupBuyOrderListReq.setOutTradeTime(tradePaySuccessEntity.getOutTradeTime());

        int updateOrderListStatusCount = groupBuyOrderListDao.updateOrderStatus2COMPLETE(groupBuyOrderListReq);
        if (1 != updateOrderListStatusCount) {
            throw new AppException(ResponseCode.UPDATE_ZERO);
        }
        orderStateFlowPort.record(OrderStateTransitionEntity.groupBuyOrderPaid(
                tradePaySuccessEntity.getOutTradeNo(),
                null,
                userEntity.getUserId(),
                MDC.get("trace-id")));
        tradeLockRequestPort.removeLockResult(userEntity.getUserId(), tradePaySuccessEntity.getOutTradeNo());

        int updateAddCount = groupBuyOrderDao.updateAddCompleteCount(groupBuyTeamEntity.getTeamId());
        if (1 != updateAddCount) {
            throw new AppException(ResponseCode.UPDATE_ZERO);
        }

        int updateOrderStatusCount = groupBuyOrderDao.updateOrderStatus2COMPLETE(groupBuyTeamEntity.getTeamId());
        if (1 == updateOrderStatusCount) {
            orderStateFlowPort.record(OrderStateTransitionEntity.groupBuyTeamFormed(
                    groupBuyTeamEntity.getTeamId(),
                    userEntity.getUserId(),
                    MDC.get("trace-id")));

            List<String> outTradeNoList = groupBuyOrderListDao.queryGroupBuyCompleteOrderOutTradeNoListByTeamId(groupBuyTeamEntity.getTeamId());
            return tradeNotifyTaskCreatePort.createSettlementTask(
                    groupBuyTeamEntity.getActivityId(),
                    groupBuyTeamEntity.getTeamId(),
                    notifyConfigVO,
                    outTradeNoList);
        }

        return null;
    }

}
