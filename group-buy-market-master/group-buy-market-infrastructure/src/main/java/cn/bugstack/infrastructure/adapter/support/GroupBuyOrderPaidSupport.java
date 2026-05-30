package cn.bugstack.infrastructure.adapter.support;

import cn.bugstack.domain.shared.adapter.port.IOrderStateFlowPort;
import cn.bugstack.domain.shared.model.entity.OrderStateTransitionEntity;
import cn.bugstack.domain.trade.adapter.port.ITradeLockRequestPort;
import cn.bugstack.domain.trade.model.entity.TradePaySuccessEntity;
import cn.bugstack.domain.trade.model.entity.UserEntity;
import cn.bugstack.infrastructure.dao.IGroupBuyOrderListDao;
import cn.bugstack.infrastructure.dao.po.GroupBuyOrderList;
import cn.bugstack.types.enums.ResponseCode;
import cn.bugstack.types.exception.AppException;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

@Component
public class GroupBuyOrderPaidSupport {

    @Resource
    private IGroupBuyOrderListDao groupBuyOrderListDao;
    @Resource
    private IOrderStateFlowPort orderStateFlowPort;
    @Resource
    private ITradeLockRequestPort tradeLockRequestPort;

    public void markPaid(UserEntity userEntity, TradePaySuccessEntity tradePaySuccessEntity) {
        GroupBuyOrderList groupBuyOrderListReq = new GroupBuyOrderList();
        groupBuyOrderListReq.setUserId(userEntity.getUserId());
        groupBuyOrderListReq.setOutTradeNo(tradePaySuccessEntity.getOutTradeNo());
        groupBuyOrderListReq.setOutTradeTime(tradePaySuccessEntity.getOutTradeTime());

        int updated = groupBuyOrderListDao.updateOrderStatus2COMPLETE(groupBuyOrderListReq);
        if (1 != updated) {
            throw new AppException(ResponseCode.UPDATE_ZERO);
        }
        orderStateFlowPort.record(OrderStateTransitionEntity.groupBuyOrderPaid(
                tradePaySuccessEntity.getOutTradeNo(),
                null,
                userEntity.getUserId(),
                MDC.get("trace-id")));
        tradeLockRequestPort.removeLockResult(userEntity.getUserId(), tradePaySuccessEntity.getOutTradeNo());
    }

}
