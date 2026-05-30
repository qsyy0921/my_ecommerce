package cn.bugstack.domain.shared.model.entity;

import cn.bugstack.domain.shared.statemachine.OrderStateMachine;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Domain description of a legal order state transition.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class OrderStateTransitionEntity {

    private String bizType;
    private String bizId;
    private String subBizId;
    private String fromStatus;
    private String toStatus;
    private String event;
    private String operatorId;
    private String traceId;
    private String sourceMessageId;
    private String message;

    public static OrderStateTransitionEntity of(String bizType, String bizId, String subBizId,
                                                String fromStatus, String toStatus, String event,
                                                String operatorId, String traceId,
                                                String sourceMessageId, String message) {
        OrderStateMachine.check(bizType, fromStatus, event, toStatus);
        return OrderStateTransitionEntity.builder()
                .bizType(bizType)
                .bizId(bizId)
                .subBizId(subBizId)
                .fromStatus(fromStatus)
                .toStatus(toStatus)
                .event(event)
                .operatorId(operatorId)
                .traceId(traceId)
                .sourceMessageId(sourceMessageId)
                .message(message)
                .build();
    }

    public String flowNo() {
        return bizType + ":" + bizId + ":" + String.valueOf(subBizId) + ":" + event + ":" + toStatus;
    }

    public static OrderStateTransitionEntity seckillOrderCreated(String outTradeNo, String orderId,
                                                                 String operatorId, String traceId,
                                                                 String sourceMessageId) {
        return of(OrderStateMachine.BIZ_SECKILL_ORDER, outTradeNo, orderId,
                OrderStateMachine.STATE_PROCESSING, OrderStateMachine.STATE_CREATE,
                OrderStateMachine.EVENT_ASYNC_ORDER_CREATED, operatorId, traceId,
                sourceMessageId, "seckill async order created");
    }

    public static OrderStateTransitionEntity seckillTimeoutClosed(String outTradeNo, String orderId,
                                                                  String operatorId, String traceId) {
        return of(OrderStateMachine.BIZ_SECKILL_ORDER, outTradeNo, orderId,
                OrderStateMachine.STATE_CREATE, OrderStateMachine.STATE_CLOSE,
                OrderStateMachine.EVENT_TIMEOUT_RELEASE, operatorId, traceId,
                null, "timeout unpaid released");
    }

    public static OrderStateTransitionEntity seckillUnpaidCanceled(String outTradeNo, String orderId,
                                                                   String operatorId, String traceId,
                                                                   String message) {
        return of(OrderStateMachine.BIZ_SECKILL_ORDER, outTradeNo, orderId,
                OrderStateMachine.STATE_CREATE, OrderStateMachine.STATE_CLOSE,
                OrderStateMachine.EVENT_TIMEOUT_RELEASE, operatorId, traceId,
                null, message);
    }

    public static OrderStateTransitionEntity seckillOrderPaid(String outTradeNo, String orderId,
                                                              String operatorId, String traceId) {
        return of(OrderStateMachine.BIZ_SECKILL_ORDER, outTradeNo, orderId,
                OrderStateMachine.STATE_CREATE, OrderStateMachine.STATE_COMPLETE,
                OrderStateMachine.EVENT_PAY_SUCCESS, operatorId, traceId,
                null, "seckill order paid");
    }

    public static OrderStateTransitionEntity seckillOrderRefunded(String outTradeNo, String orderId,
                                                                  String operatorId, String traceId,
                                                                  String message) {
        return of(OrderStateMachine.BIZ_SECKILL_ORDER, outTradeNo, orderId,
                OrderStateMachine.STATE_COMPLETE, OrderStateMachine.STATE_REFUND,
                OrderStateMachine.EVENT_REFUND_SUCCESS, operatorId, traceId,
                null, message);
    }

    public static OrderStateTransitionEntity seckillOrderFulfilled(String outTradeNo, String orderId,
                                                                   String operatorId, String traceId) {
        return of(OrderStateMachine.BIZ_SECKILL_ORDER, outTradeNo, orderId,
                OrderStateMachine.STATE_COMPLETE, OrderStateMachine.STATE_FULFILLED,
                OrderStateMachine.EVENT_FULFILL, operatorId, traceId,
                null, "seckill order fulfilled");
    }

    public static OrderStateTransitionEntity seckillRefundApplied(String outTradeNo, String orderId,
                                                                  String fromStatus, String operatorId,
                                                                  String traceId, String message) {
        return of(OrderStateMachine.BIZ_SECKILL_ORDER, outTradeNo, orderId,
                fromStatus, OrderStateMachine.STATE_REFUNDING,
                OrderStateMachine.EVENT_REFUND_APPLY, operatorId, traceId,
                null, message);
    }

    public static OrderStateTransitionEntity seckillPartialRefunded(String outTradeNo, String orderId,
                                                                    String operatorId, String traceId,
                                                                    String message) {
        return of(OrderStateMachine.BIZ_SECKILL_ORDER, outTradeNo, orderId,
                OrderStateMachine.STATE_REFUNDING, OrderStateMachine.STATE_PARTIAL_REFUND,
                OrderStateMachine.EVENT_REFUND_PARTIAL_SUCCESS, operatorId, traceId,
                null, message);
    }

    public static OrderStateTransitionEntity seckillRefundRejected(String outTradeNo, String orderId,
                                                                   String operatorId, String traceId,
                                                                   String message) {
        return of(OrderStateMachine.BIZ_SECKILL_ORDER, outTradeNo, orderId,
                OrderStateMachine.STATE_REFUNDING, OrderStateMachine.STATE_REFUND_REJECTED,
                OrderStateMachine.EVENT_REFUND_REJECT, operatorId, traceId,
                null, message);
    }

    public static OrderStateTransitionEntity groupBuyTeamOpened(String teamId, String operatorId, String traceId) {
        return of(OrderStateMachine.BIZ_GROUP_BUY_TEAM, teamId, null,
                OrderStateMachine.STATE_INIT, OrderStateMachine.STATE_PROGRESS,
                OrderStateMachine.EVENT_OPEN_TEAM, operatorId, traceId,
                null, "group buy team opened");
    }

    public static OrderStateTransitionEntity groupBuyOrderLocked(String outTradeNo, String orderId,
                                                                 String operatorId, String traceId) {
        return of(OrderStateMachine.BIZ_GROUP_BUY_ORDER_LIST, outTradeNo, orderId,
                OrderStateMachine.STATE_INIT, OrderStateMachine.STATE_CREATE,
                OrderStateMachine.EVENT_LOCK, operatorId, traceId,
                null, "group buy order locked");
    }

    public static OrderStateTransitionEntity groupBuyOrderPaid(String outTradeNo, String orderId,
                                                               String operatorId, String traceId) {
        return of(OrderStateMachine.BIZ_GROUP_BUY_ORDER_LIST, outTradeNo, orderId,
                OrderStateMachine.STATE_CREATE, OrderStateMachine.STATE_COMPLETE,
                OrderStateMachine.EVENT_PAY_SUCCESS, operatorId, traceId,
                null, "group buy order paid");
    }

    public static OrderStateTransitionEntity groupBuyTeamFormed(String teamId, String operatorId, String traceId) {
        return of(OrderStateMachine.BIZ_GROUP_BUY_TEAM, teamId, null,
                OrderStateMachine.STATE_PROGRESS, OrderStateMachine.STATE_COMPLETE,
                OrderStateMachine.EVENT_TEAM_FORMED, operatorId, traceId,
                null, "group buy team formed");
    }

    public static OrderStateTransitionEntity groupBuyUnpaidOrderClosed(String outTradeNo, String orderId,
                                                                       String operatorId, String traceId) {
        return of(OrderStateMachine.BIZ_GROUP_BUY_ORDER_LIST, outTradeNo, orderId,
                OrderStateMachine.STATE_CREATE, OrderStateMachine.STATE_CLOSE,
                OrderStateMachine.EVENT_TIMEOUT_RELEASE, operatorId, traceId,
                null, "unpaid group buy order closed");
    }

    public static OrderStateTransitionEntity groupBuyProgressSlotReleased(String teamId, String orderId,
                                                                          String operatorId, String traceId,
                                                                          String message) {
        return of(OrderStateMachine.BIZ_GROUP_BUY_TEAM, teamId, orderId,
                OrderStateMachine.STATE_PROGRESS, OrderStateMachine.STATE_PROGRESS,
                OrderStateMachine.EVENT_REFUND_SUCCESS, operatorId, traceId,
                null, message);
    }

    public static OrderStateTransitionEntity groupBuyPaidOrderRefunded(String outTradeNo, String orderId,
                                                                       String operatorId, String traceId,
                                                                       String message) {
        return of(OrderStateMachine.BIZ_GROUP_BUY_ORDER_LIST, outTradeNo, orderId,
                OrderStateMachine.STATE_COMPLETE, OrderStateMachine.STATE_CLOSE,
                OrderStateMachine.EVENT_REFUND_SUCCESS, operatorId, traceId,
                null, message);
    }

    public static OrderStateTransitionEntity groupBuyOrderFulfilled(String outTradeNo, String orderId,
                                                                    String operatorId, String traceId) {
        return of(OrderStateMachine.BIZ_GROUP_BUY_ORDER_LIST, outTradeNo, orderId,
                OrderStateMachine.STATE_COMPLETE, OrderStateMachine.STATE_FULFILLED,
                OrderStateMachine.EVENT_FULFILL, operatorId, traceId,
                null, "group buy order fulfilled");
    }

    public static OrderStateTransitionEntity groupBuyRefundApplied(String outTradeNo, String orderId,
                                                                   String fromStatus, String operatorId,
                                                                   String traceId, String message) {
        return of(OrderStateMachine.BIZ_GROUP_BUY_ORDER_LIST, outTradeNo, orderId,
                fromStatus, OrderStateMachine.STATE_REFUNDING,
                OrderStateMachine.EVENT_REFUND_APPLY, operatorId, traceId,
                null, message);
    }

    public static OrderStateTransitionEntity groupBuyPartialRefunded(String outTradeNo, String orderId,
                                                                     String operatorId, String traceId,
                                                                     String message) {
        return of(OrderStateMachine.BIZ_GROUP_BUY_ORDER_LIST, outTradeNo, orderId,
                OrderStateMachine.STATE_REFUNDING, OrderStateMachine.STATE_PARTIAL_REFUND,
                OrderStateMachine.EVENT_REFUND_PARTIAL_SUCCESS, operatorId, traceId,
                null, message);
    }

    public static OrderStateTransitionEntity groupBuyRefundRejected(String outTradeNo, String orderId,
                                                                    String operatorId, String traceId,
                                                                    String message) {
        return of(OrderStateMachine.BIZ_GROUP_BUY_ORDER_LIST, outTradeNo, orderId,
                OrderStateMachine.STATE_REFUNDING, OrderStateMachine.STATE_REFUND_REJECTED,
                OrderStateMachine.EVENT_REFUND_REJECT, operatorId, traceId,
                null, message);
    }

    public static OrderStateTransitionEntity groupBuyTeamPartialRefund(String teamId, String orderId,
                                                                       String operatorId, String traceId) {
        return of(OrderStateMachine.BIZ_GROUP_BUY_TEAM, teamId, orderId,
                OrderStateMachine.STATE_COMPLETE, OrderStateMachine.STATE_COMPLETE_FAIL,
                OrderStateMachine.EVENT_TEAM_REFUND_PARTIAL, operatorId, traceId,
                null, "formed team partial refund");
    }

    public static OrderStateTransitionEntity groupBuyTeamAllRefunded(String teamId, String orderId,
                                                                     String operatorId, String traceId) {
        return of(OrderStateMachine.BIZ_GROUP_BUY_TEAM, teamId, orderId,
                OrderStateMachine.STATE_COMPLETE, OrderStateMachine.STATE_FAIL,
                OrderStateMachine.EVENT_TEAM_REFUND_ALL, operatorId, traceId,
                null, "formed team all refunded");
    }

}
