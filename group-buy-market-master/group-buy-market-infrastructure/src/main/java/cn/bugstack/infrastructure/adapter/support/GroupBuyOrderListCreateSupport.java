package cn.bugstack.infrastructure.adapter.support;

import cn.bugstack.domain.shared.adapter.port.IOrderStateFlowPort;
import cn.bugstack.domain.shared.model.entity.OrderStateTransitionEntity;
import cn.bugstack.domain.trade.adapter.port.IGroupBuyStockFlowPort;
import cn.bugstack.domain.trade.model.entity.GroupBuyStockFlowEntity;
import cn.bugstack.domain.trade.model.entity.PayActivityEntity;
import cn.bugstack.domain.trade.model.entity.PayDiscountEntity;
import cn.bugstack.domain.trade.model.entity.UserEntity;
import cn.bugstack.domain.trade.model.valobj.TradeOrderStatusEnumVO;
import cn.bugstack.infrastructure.dao.IGroupBuyOrderListDao;
import cn.bugstack.infrastructure.dao.po.GroupBuyOrderList;
import cn.bugstack.types.common.Constants;
import cn.bugstack.types.enums.ResponseCode;
import cn.bugstack.types.exception.AppException;
import org.apache.commons.lang3.RandomStringUtils;
import org.slf4j.MDC;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.Calendar;
import java.util.Date;

@Component
public class GroupBuyOrderListCreateSupport {

    @Resource
    private IGroupBuyOrderListDao groupBuyOrderListDao;
    @Resource
    private IOrderStateFlowPort orderStateFlowPort;
    @Resource
    private IGroupBuyStockFlowPort groupBuyStockFlowPort;

    public String createOrderList(String teamId,
                                  UserEntity userEntity,
                                  PayActivityEntity payActivityEntity,
                                  PayDiscountEntity payDiscountEntity,
                                  Integer userTakeOrderCount) {
        String orderId = RandomStringUtils.randomNumeric(12);
        GroupBuyOrderList groupBuyOrderListReq = buildOrderList(teamId, orderId, userEntity, payActivityEntity, payDiscountEntity, userTakeOrderCount);
        try {
            groupBuyOrderListDao.insert(groupBuyOrderListReq);
            recordLockedFlows(teamId, orderId, userEntity, payActivityEntity, payDiscountEntity);
            return orderId;
        } catch (DuplicateKeyException e) {
            throw new AppException(ResponseCode.INDEX_EXCEPTION);
        }
    }

    private GroupBuyOrderList buildOrderList(String teamId,
                                             String orderId,
                                             UserEntity userEntity,
                                             PayActivityEntity payActivityEntity,
                                             PayDiscountEntity payDiscountEntity,
                                             Integer userTakeOrderCount) {
        Date currentDate = new Date();
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(currentDate);
        calendar.add(Calendar.MINUTE, payActivityEntity.getValidTime());

        return GroupBuyOrderList.builder()
                .userId(userEntity.getUserId())
                .teamId(teamId)
                .orderId(orderId)
                .activityId(payActivityEntity.getActivityId())
                .startTime(currentDate)
                .endTime(calendar.getTime())
                .goodsId(payDiscountEntity.getGoodsId())
                .source(payDiscountEntity.getSource())
                .channel(payDiscountEntity.getChannel())
                .originalPrice(payDiscountEntity.getOriginalPrice())
                .deductionPrice(payDiscountEntity.getDeductionPrice())
                .payPrice(payDiscountEntity.getPayPrice())
                .status(TradeOrderStatusEnumVO.CREATE.getCode())
                .outTradeNo(payDiscountEntity.getOutTradeNo())
                .bizId(payActivityEntity.getActivityId() + Constants.UNDERLINE + userEntity.getUserId() + Constants.UNDERLINE + (userTakeOrderCount + 1))
                .build();
    }

    private void recordLockedFlows(String teamId,
                                   String orderId,
                                   UserEntity userEntity,
                                   PayActivityEntity payActivityEntity,
                                   PayDiscountEntity payDiscountEntity) {
        orderStateFlowPort.record(OrderStateTransitionEntity.groupBuyOrderLocked(
                payDiscountEntity.getOutTradeNo(),
                orderId,
                userEntity.getUserId(),
                MDC.get("trace-id")));
        groupBuyStockFlowPort.record(GroupBuyStockFlowEntity.orderLocked(
                userEntity.getUserId(),
                payActivityEntity.getActivityId(),
                teamId,
                orderId,
                payDiscountEntity.getOutTradeNo(),
                MDC.get("trace-id")));
    }

}
