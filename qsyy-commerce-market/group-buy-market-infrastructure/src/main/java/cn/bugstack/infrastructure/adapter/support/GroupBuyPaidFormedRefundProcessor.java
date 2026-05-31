package cn.bugstack.infrastructure.adapter.support;

import cn.bugstack.domain.shared.model.entity.OrderStateTransitionEntity;
import cn.bugstack.domain.trade.model.aggregate.GroupBuyRefundAggregate;
import cn.bugstack.domain.trade.model.entity.GroupBuyStockFlowEntity;
import cn.bugstack.domain.trade.model.entity.NotifyTaskEntity;
import cn.bugstack.domain.trade.model.entity.TradeRefundOrderEntity;
import cn.bugstack.domain.trade.model.valobj.GroupBuyProgressVO;
import cn.bugstack.domain.trade.model.valobj.RefundTypeEnumVO;
import cn.bugstack.infrastructure.dao.IGroupBuyOrderDao;
import cn.bugstack.infrastructure.dao.IGroupBuyOrderListDao;
import cn.bugstack.infrastructure.dao.po.GroupBuyOrder;
import cn.bugstack.types.enums.GroupBuyOrderEnumVO;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;

@Component
public class GroupBuyPaidFormedRefundProcessor {

    @Resource
    private IGroupBuyOrderDao groupBuyOrderDao;
    @Resource
    private IGroupBuyOrderListDao groupBuyOrderListDao;
    @Resource
    private GroupBuyRefundSupport groupBuyRefundSupport;

    @Transactional(timeout = 5000)
    public NotifyTaskEntity refund(GroupBuyRefundAggregate groupBuyRefundAggregate) {
        TradeRefundOrderEntity tradeRefundOrderEntity = groupBuyRefundAggregate.getTradeRefundOrderEntity();
        GroupBuyProgressVO groupBuyProgress = groupBuyRefundAggregate.getGroupBuyProgress();
        GroupBuyOrderEnumVO groupBuyOrderEnumVO = groupBuyRefundAggregate.getGroupBuyOrderEnumVO();

        int updatedOrder = groupBuyOrderListDao.paidTeam2Refund(groupBuyRefundSupport.orderListRequest(tradeRefundOrderEntity));
        groupBuyRefundSupport.assertUpdated("paidTeam2Refund，更新订单状态(退单)", updatedOrder, tradeRefundOrderEntity);
        groupBuyRefundSupport.orderStateFlowPort().record(OrderStateTransitionEntity.groupBuyPaidOrderRefunded(
                tradeRefundOrderEntity.getOutTradeNo(),
                tradeRefundOrderEntity.getOrderId(),
                tradeRefundOrderEntity.getUserId(),
                MDC.get("trace-id"),
                "paid formed group buy order refunded"));
        groupBuyRefundSupport.removeLockResult(tradeRefundOrderEntity);

        GroupBuyOrder groupBuyOrderReq = groupBuyRefundSupport.teamRequest(tradeRefundOrderEntity, groupBuyProgress);
        if (GroupBuyOrderEnumVO.COMPLETE_FAIL.equals(groupBuyOrderEnumVO)) {
            int updatedTeam = groupBuyOrderDao.paidTeam2Refund(groupBuyOrderReq);
            groupBuyRefundSupport.assertUpdated("paidTeam2Refund，更新组队记录(退单)", updatedTeam, tradeRefundOrderEntity);
            groupBuyRefundSupport.orderStateFlowPort().record(OrderStateTransitionEntity.groupBuyTeamPartialRefund(
                    tradeRefundOrderEntity.getTeamId(),
                    tradeRefundOrderEntity.getOrderId(),
                    tradeRefundOrderEntity.getUserId(),
                    MDC.get("trace-id")));
        } else if (GroupBuyOrderEnumVO.FAIL.equals(groupBuyOrderEnumVO)) {
            int updatedTeam = groupBuyOrderDao.paidTeam2RefundFail(groupBuyOrderReq);
            groupBuyRefundSupport.assertUpdated("updateTeamPaid2RefundFail，更新组队记录(退单)", updatedTeam, tradeRefundOrderEntity);
            groupBuyRefundSupport.orderStateFlowPort().record(OrderStateTransitionEntity.groupBuyTeamAllRefunded(
                    tradeRefundOrderEntity.getTeamId(),
                    tradeRefundOrderEntity.getOrderId(),
                    tradeRefundOrderEntity.getUserId(),
                    MDC.get("trace-id")));
        }

        NotifyTaskEntity notifyTaskEntity = groupBuyRefundSupport.createRefundTask(tradeRefundOrderEntity, RefundTypeEnumVO.PAID_FORMED);
        groupBuyRefundSupport.recordStockFlow(GroupBuyStockFlowEntity.paidFormedRefunded(
                tradeRefundOrderEntity,
                MDC.get("trace-id")));

        return notifyTaskEntity;
    }

}
