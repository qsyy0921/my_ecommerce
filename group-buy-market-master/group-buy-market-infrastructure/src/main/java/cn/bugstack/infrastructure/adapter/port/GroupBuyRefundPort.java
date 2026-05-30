package cn.bugstack.infrastructure.adapter.port;

import cn.bugstack.domain.shared.adapter.port.IOrderStateFlowPort;
import cn.bugstack.domain.shared.model.entity.OrderStateTransitionEntity;
import cn.bugstack.domain.trade.adapter.port.IGroupBuyRefundPort;
import cn.bugstack.domain.trade.adapter.port.IGroupBuyStockFlowPort;
import cn.bugstack.domain.trade.adapter.port.ITradeLockRequestPort;
import cn.bugstack.domain.trade.adapter.port.ITradeNotifyTaskPort;
import cn.bugstack.domain.trade.model.aggregate.GroupBuyRefundAggregate;
import cn.bugstack.domain.trade.model.entity.GroupBuyStockFlowEntity;
import cn.bugstack.domain.trade.model.entity.NotifyTaskEntity;
import cn.bugstack.domain.trade.model.entity.TradeRefundOrderEntity;
import cn.bugstack.domain.trade.model.valobj.GroupBuyProgressVO;
import cn.bugstack.domain.trade.model.valobj.RefundTypeEnumVO;
import cn.bugstack.infrastructure.dao.IGroupBuyOrderDao;
import cn.bugstack.infrastructure.dao.IGroupBuyOrderListDao;
import cn.bugstack.infrastructure.dao.po.GroupBuyOrder;
import cn.bugstack.infrastructure.dao.po.GroupBuyOrderList;
import cn.bugstack.types.enums.GroupBuyOrderEnumVO;
import cn.bugstack.types.enums.ResponseCode;
import cn.bugstack.types.exception.AppException;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;

@Slf4j
@Service
public class GroupBuyRefundPort implements IGroupBuyRefundPort {

    @Resource
    private IGroupBuyOrderDao groupBuyOrderDao;
    @Resource
    private IGroupBuyOrderListDao groupBuyOrderListDao;
    @Resource
    private IOrderStateFlowPort orderStateFlowPort;
    @Resource
    private ITradeNotifyTaskPort tradeNotifyTaskPort;
    @Resource
    private IGroupBuyStockFlowPort groupBuyStockFlowPort;
    @Resource
    private ITradeLockRequestPort tradeLockRequestPort;

    @Value("${spring.rabbitmq.config.producer.topic_team_refund.routing_key}")
    private String topicTeamRefund;

    @Transactional(timeout = 5000)
    @Override
    public NotifyTaskEntity unpaid2Refund(GroupBuyRefundAggregate groupBuyRefundAggregate) {
        TradeRefundOrderEntity tradeRefundOrderEntity = groupBuyRefundAggregate.getTradeRefundOrderEntity();
        GroupBuyProgressVO groupBuyProgress = groupBuyRefundAggregate.getGroupBuyProgress();

        GroupBuyOrderList groupBuyOrderListReq = new GroupBuyOrderList();
        groupBuyOrderListReq.setUserId(tradeRefundOrderEntity.getUserId());
        groupBuyOrderListReq.setOrderId(tradeRefundOrderEntity.getOrderId());

        int updateUnpaid2RefundCount = groupBuyOrderListDao.unpaid2Refund(groupBuyOrderListReq);
        if (1 != updateUnpaid2RefundCount) {
            log.error("逆向流程-unpaid2Refund，更新订单状态(退单)失败 {} {}", tradeRefundOrderEntity.getUserId(), tradeRefundOrderEntity.getOrderId());
            throw new AppException(ResponseCode.UPDATE_ZERO);
        }
        orderStateFlowPort.record(OrderStateTransitionEntity.groupBuyUnpaidOrderClosed(
                tradeRefundOrderEntity.getOutTradeNo(),
                tradeRefundOrderEntity.getOrderId(),
                tradeRefundOrderEntity.getUserId(),
                MDC.get("trace-id")));
        tradeLockRequestPort.removeLockResult(tradeRefundOrderEntity.getUserId(), tradeRefundOrderEntity.getOutTradeNo());

        GroupBuyOrder groupBuyOrderReq = new GroupBuyOrder();
        groupBuyOrderReq.setTeamId(tradeRefundOrderEntity.getTeamId());
        groupBuyOrderReq.setLockCount(groupBuyProgress.getLockCount());

        int updateTeamUnpaid2Refund = groupBuyOrderDao.unpaid2Refund(groupBuyOrderReq);
        if (1 != updateTeamUnpaid2Refund) {
            log.error("逆向流程-unpaid2Refund，更新组队记录(退单)失败 {} {}", tradeRefundOrderEntity.getUserId(), tradeRefundOrderEntity.getOrderId());
            throw new AppException(ResponseCode.UPDATE_ZERO);
        }
        orderStateFlowPort.record(OrderStateTransitionEntity.groupBuyProgressSlotReleased(
                tradeRefundOrderEntity.getTeamId(),
                tradeRefundOrderEntity.getOrderId(),
                tradeRefundOrderEntity.getUserId(),
                MDC.get("trace-id"),
                "unpaid group buy team slot released"));

        NotifyTaskEntity notifyTaskEntity = tradeNotifyTaskPort.createRefundTask(
                tradeRefundOrderEntity,
                RefundTypeEnumVO.UNPAID_UNLOCK,
                topicTeamRefund);
        groupBuyStockFlowPort.record(GroupBuyStockFlowEntity.unpaidRefunded(
                tradeRefundOrderEntity,
                MDC.get("trace-id")));

        return notifyTaskEntity;
    }

    @Transactional(timeout = 5000)
    @Override
    public NotifyTaskEntity paid2Refund(GroupBuyRefundAggregate groupBuyRefundAggregate) {
        TradeRefundOrderEntity tradeRefundOrderEntity = groupBuyRefundAggregate.getTradeRefundOrderEntity();
        GroupBuyProgressVO groupBuyProgress = groupBuyRefundAggregate.getGroupBuyProgress();

        GroupBuyOrderList groupBuyOrderListReq = new GroupBuyOrderList();
        groupBuyOrderListReq.setUserId(tradeRefundOrderEntity.getUserId());
        groupBuyOrderListReq.setOrderId(tradeRefundOrderEntity.getOrderId());

        int updatePaid2RefundCount = groupBuyOrderListDao.paid2Refund(groupBuyOrderListReq);
        if (1 != updatePaid2RefundCount) {
            log.error("逆向流程-paid2Refund，更新订单状态(退单)失败 {} {}", tradeRefundOrderEntity.getUserId(), tradeRefundOrderEntity.getOrderId());
            throw new AppException(ResponseCode.UPDATE_ZERO);
        }
        orderStateFlowPort.record(OrderStateTransitionEntity.groupBuyPaidOrderRefunded(
                tradeRefundOrderEntity.getOutTradeNo(),
                tradeRefundOrderEntity.getOrderId(),
                tradeRefundOrderEntity.getUserId(),
                MDC.get("trace-id"),
                "paid unformed group buy order refunded"));
        tradeLockRequestPort.removeLockResult(tradeRefundOrderEntity.getUserId(), tradeRefundOrderEntity.getOutTradeNo());

        GroupBuyOrder groupBuyOrderReq = new GroupBuyOrder();
        groupBuyOrderReq.setTeamId(tradeRefundOrderEntity.getTeamId());
        groupBuyOrderReq.setLockCount(groupBuyProgress.getLockCount());
        groupBuyOrderReq.setCompleteCount(groupBuyProgress.getCompleteCount());

        int updateTeamPaid2Refund = groupBuyOrderDao.paid2Refund(groupBuyOrderReq);
        if (1 != updateTeamPaid2Refund) {
            log.error("逆向流程-paid2Refund，更新组队记录(退单)失败 {} {}", tradeRefundOrderEntity.getUserId(), tradeRefundOrderEntity.getOrderId());
            throw new AppException(ResponseCode.UPDATE_ZERO);
        }
        orderStateFlowPort.record(OrderStateTransitionEntity.groupBuyProgressSlotReleased(
                tradeRefundOrderEntity.getTeamId(),
                tradeRefundOrderEntity.getOrderId(),
                tradeRefundOrderEntity.getUserId(),
                MDC.get("trace-id"),
                "paid unformed group buy team slot released"));

        NotifyTaskEntity notifyTaskEntity = tradeNotifyTaskPort.createRefundTask(
                tradeRefundOrderEntity,
                RefundTypeEnumVO.PAID_UNFORMED,
                topicTeamRefund);
        groupBuyStockFlowPort.record(GroupBuyStockFlowEntity.paidUnformedRefunded(
                tradeRefundOrderEntity,
                MDC.get("trace-id")));

        return notifyTaskEntity;
    }

    @Transactional(timeout = 5000)
    @Override
    public NotifyTaskEntity paidTeam2Refund(GroupBuyRefundAggregate groupBuyRefundAggregate) {
        TradeRefundOrderEntity tradeRefundOrderEntity = groupBuyRefundAggregate.getTradeRefundOrderEntity();
        GroupBuyProgressVO groupBuyProgress = groupBuyRefundAggregate.getGroupBuyProgress();
        GroupBuyOrderEnumVO groupBuyOrderEnumVO = groupBuyRefundAggregate.getGroupBuyOrderEnumVO();

        GroupBuyOrderList groupBuyOrderListReq = new GroupBuyOrderList();
        groupBuyOrderListReq.setUserId(tradeRefundOrderEntity.getUserId());
        groupBuyOrderListReq.setOrderId(tradeRefundOrderEntity.getOrderId());

        int updatePaid2RefundCount = groupBuyOrderListDao.paidTeam2Refund(groupBuyOrderListReq);
        if (1 != updatePaid2RefundCount) {
            log.error("逆向流程-paidTeam2Refund，更新订单状态(退单)失败 {} {}", tradeRefundOrderEntity.getUserId(), tradeRefundOrderEntity.getOrderId());
            throw new AppException(ResponseCode.UPDATE_ZERO);
        }
        orderStateFlowPort.record(OrderStateTransitionEntity.groupBuyPaidOrderRefunded(
                tradeRefundOrderEntity.getOutTradeNo(),
                tradeRefundOrderEntity.getOrderId(),
                tradeRefundOrderEntity.getUserId(),
                MDC.get("trace-id"),
                "paid formed group buy order refunded"));
        tradeLockRequestPort.removeLockResult(tradeRefundOrderEntity.getUserId(), tradeRefundOrderEntity.getOutTradeNo());

        GroupBuyOrder groupBuyOrderReq = new GroupBuyOrder();
        groupBuyOrderReq.setTeamId(tradeRefundOrderEntity.getTeamId());
        groupBuyOrderReq.setLockCount(groupBuyProgress.getLockCount());
        groupBuyOrderReq.setCompleteCount(groupBuyProgress.getCompleteCount());

        if (GroupBuyOrderEnumVO.COMPLETE_FAIL.equals(groupBuyOrderEnumVO)) {
            int updateTeamPaid2Refund = groupBuyOrderDao.paidTeam2Refund(groupBuyOrderReq);
            if (1 != updateTeamPaid2Refund) {
                log.error("逆向流程-paidTeam2Refund，更新组队记录(退单)失败 {} {}", tradeRefundOrderEntity.getUserId(), tradeRefundOrderEntity.getOrderId());
                throw new AppException(ResponseCode.UPDATE_ZERO);
            }
            orderStateFlowPort.record(OrderStateTransitionEntity.groupBuyTeamPartialRefund(
                    tradeRefundOrderEntity.getTeamId(),
                    tradeRefundOrderEntity.getOrderId(),
                    tradeRefundOrderEntity.getUserId(),
                    MDC.get("trace-id")));
        } else if (GroupBuyOrderEnumVO.FAIL.equals(groupBuyOrderEnumVO)) {
            int updateTeamPaid2RefundFail = groupBuyOrderDao.paidTeam2RefundFail(groupBuyOrderReq);
            if (1 != updateTeamPaid2RefundFail) {
                log.error("逆向流程-updateTeamPaid2RefundFail，更新组队记录(退单)失败 {} {}", tradeRefundOrderEntity.getUserId(), tradeRefundOrderEntity.getOrderId());
                throw new AppException(ResponseCode.UPDATE_ZERO);
            }
            orderStateFlowPort.record(OrderStateTransitionEntity.groupBuyTeamAllRefunded(
                    tradeRefundOrderEntity.getTeamId(),
                    tradeRefundOrderEntity.getOrderId(),
                    tradeRefundOrderEntity.getUserId(),
                    MDC.get("trace-id")));
        }

        NotifyTaskEntity notifyTaskEntity = tradeNotifyTaskPort.createRefundTask(
                tradeRefundOrderEntity,
                RefundTypeEnumVO.PAID_FORMED,
                topicTeamRefund);
        groupBuyStockFlowPort.record(GroupBuyStockFlowEntity.paidFormedRefunded(
                tradeRefundOrderEntity,
                MDC.get("trace-id")));

        return notifyTaskEntity;
    }

}
