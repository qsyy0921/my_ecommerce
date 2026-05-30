package cn.bugstack.infrastructure.adapter.support;

import cn.bugstack.domain.shared.adapter.port.IOrderStateFlowPort;
import cn.bugstack.domain.trade.adapter.port.IGroupBuyStockFlowPort;
import cn.bugstack.domain.trade.adapter.port.ITradeLockRequestPort;
import cn.bugstack.domain.trade.adapter.port.ITradeNotifyTaskPort;
import cn.bugstack.domain.trade.model.entity.GroupBuyStockFlowEntity;
import cn.bugstack.domain.trade.model.entity.NotifyTaskEntity;
import cn.bugstack.domain.trade.model.entity.TradeRefundOrderEntity;
import cn.bugstack.domain.trade.model.valobj.GroupBuyProgressVO;
import cn.bugstack.domain.trade.model.valobj.RefundTypeEnumVO;
import cn.bugstack.infrastructure.dao.po.GroupBuyOrder;
import cn.bugstack.infrastructure.dao.po.GroupBuyOrderList;
import cn.bugstack.types.enums.ResponseCode;
import cn.bugstack.types.exception.AppException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

@Slf4j
@Component
public class GroupBuyRefundSupport {

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

    public GroupBuyOrderList orderListRequest(TradeRefundOrderEntity tradeRefundOrderEntity) {
        GroupBuyOrderList groupBuyOrderListReq = new GroupBuyOrderList();
        groupBuyOrderListReq.setUserId(tradeRefundOrderEntity.getUserId());
        groupBuyOrderListReq.setOrderId(tradeRefundOrderEntity.getOrderId());
        return groupBuyOrderListReq;
    }

    public GroupBuyOrder teamRequest(TradeRefundOrderEntity tradeRefundOrderEntity, GroupBuyProgressVO groupBuyProgress) {
        GroupBuyOrder groupBuyOrderReq = new GroupBuyOrder();
        groupBuyOrderReq.setTeamId(tradeRefundOrderEntity.getTeamId());
        groupBuyOrderReq.setLockCount(groupBuyProgress.getLockCount());
        groupBuyOrderReq.setCompleteCount(groupBuyProgress.getCompleteCount());
        return groupBuyOrderReq;
    }

    public void assertUpdated(String action, int updated, TradeRefundOrderEntity tradeRefundOrderEntity) {
        if (1 == updated) {
            return;
        }
        log.error("逆向流程-{}，更新状态失败 {} {}", action, tradeRefundOrderEntity.getUserId(), tradeRefundOrderEntity.getOrderId());
        throw new AppException(ResponseCode.UPDATE_ZERO);
    }

    public void removeLockResult(TradeRefundOrderEntity tradeRefundOrderEntity) {
        tradeLockRequestPort.removeLockResult(tradeRefundOrderEntity.getUserId(), tradeRefundOrderEntity.getOutTradeNo());
    }

    public NotifyTaskEntity createRefundTask(TradeRefundOrderEntity tradeRefundOrderEntity, RefundTypeEnumVO refundType) {
        return tradeNotifyTaskPort.createRefundTask(tradeRefundOrderEntity, refundType, topicTeamRefund);
    }

    public void recordStockFlow(GroupBuyStockFlowEntity groupBuyStockFlowEntity) {
        groupBuyStockFlowPort.record(groupBuyStockFlowEntity);
    }

    public IOrderStateFlowPort orderStateFlowPort() {
        return orderStateFlowPort;
    }

}
