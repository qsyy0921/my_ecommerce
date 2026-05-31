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
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;

@Component
public class GroupBuyPaidUnformedRefundProcessor {

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

        int updatedOrder = groupBuyOrderListDao.paid2Refund(groupBuyRefundSupport.orderListRequest(tradeRefundOrderEntity));
        groupBuyRefundSupport.assertUpdated("paid2Refund，更新订单状态(退单)", updatedOrder, tradeRefundOrderEntity);
        groupBuyRefundSupport.orderStateFlowPort().record(OrderStateTransitionEntity.groupBuyPaidOrderRefunded(
                tradeRefundOrderEntity.getOutTradeNo(),
                tradeRefundOrderEntity.getOrderId(),
                tradeRefundOrderEntity.getUserId(),
                MDC.get("trace-id"),
                "paid unformed group buy order refunded"));
        groupBuyRefundSupport.removeLockResult(tradeRefundOrderEntity);

        GroupBuyOrder groupBuyOrderReq = groupBuyRefundSupport.teamRequest(tradeRefundOrderEntity, groupBuyProgress);
        int updatedTeam = groupBuyOrderDao.paid2Refund(groupBuyOrderReq);
        groupBuyRefundSupport.assertUpdated("paid2Refund，更新组队记录(退单)", updatedTeam, tradeRefundOrderEntity);
        groupBuyRefundSupport.orderStateFlowPort().record(OrderStateTransitionEntity.groupBuyProgressSlotReleased(
                tradeRefundOrderEntity.getTeamId(),
                tradeRefundOrderEntity.getOrderId(),
                tradeRefundOrderEntity.getUserId(),
                MDC.get("trace-id"),
                "paid unformed group buy team slot released"));

        NotifyTaskEntity notifyTaskEntity = groupBuyRefundSupport.createRefundTask(tradeRefundOrderEntity, RefundTypeEnumVO.PAID_UNFORMED);
        groupBuyRefundSupport.recordStockFlow(GroupBuyStockFlowEntity.paidUnformedRefunded(
                tradeRefundOrderEntity,
                MDC.get("trace-id")));

        return notifyTaskEntity;
    }

}
