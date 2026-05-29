package cn.bugstack.domain.trade.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Group-buy stock audit flow expressed in domain language.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class GroupBuyStockFlowEntity {

    private static final String SOURCE = "group_buy";
    private static final String RESERVE = "RESERVE";
    private static final String ROLLBACK_UNPAID = "ROLLBACK_UNPAID";
    private static final String ROLLBACK_PAID_UNFORMED = "ROLLBACK_PAID_UNFORMED";
    private static final String ROLLBACK_PAID_FORMED = "ROLLBACK_PAID_FORMED";

    private String userId;
    private Long activityId;
    private String teamId;
    private String orderId;
    private String outTradeNo;
    private String changeType;
    private Integer changeCount;
    private Integer stockBefore;
    private Integer stockAfter;
    private String bizEvent;
    private String traceId;
    private String sourceMessageId;
    private String source;
    private String message;

    public static GroupBuyStockFlowEntity orderLocked(String userId, Long activityId, String teamId,
                                                      String orderId, String outTradeNo, String traceId) {
        return build(userId, activityId, teamId, orderId, outTradeNo,
                RESERVE, -1, traceId, "group buy order locked");
    }

    public static GroupBuyStockFlowEntity unpaidRefunded(TradeRefundOrderEntity refundOrderEntity, String traceId) {
        return build(refundOrderEntity, ROLLBACK_UNPAID, 1, traceId, "unpaid order refunded");
    }

    public static GroupBuyStockFlowEntity paidUnformedRefunded(TradeRefundOrderEntity refundOrderEntity, String traceId) {
        return build(refundOrderEntity, ROLLBACK_PAID_UNFORMED, 1, traceId, "paid unformed order refunded");
    }

    public static GroupBuyStockFlowEntity paidFormedRefunded(TradeRefundOrderEntity refundOrderEntity, String traceId) {
        return build(refundOrderEntity, ROLLBACK_PAID_FORMED, 1, traceId, "paid formed order refunded");
    }

    public String flowNo() {
        return activityId + "_" + teamId + "_" + orderId + "_" + changeType;
    }

    private static GroupBuyStockFlowEntity build(TradeRefundOrderEntity refundOrderEntity, String changeType,
                                                 Integer changeCount, String traceId, String message) {
        return build(
                refundOrderEntity.getUserId(),
                refundOrderEntity.getActivityId(),
                refundOrderEntity.getTeamId(),
                refundOrderEntity.getOrderId(),
                refundOrderEntity.getOutTradeNo(),
                changeType,
                changeCount,
                traceId,
                message);
    }

    private static GroupBuyStockFlowEntity build(String userId, Long activityId, String teamId,
                                                 String orderId, String outTradeNo, String changeType,
                                                 Integer changeCount, String traceId, String message) {
        return GroupBuyStockFlowEntity.builder()
                .userId(userId)
                .activityId(activityId)
                .teamId(teamId)
                .orderId(orderId)
                .outTradeNo(outTradeNo)
                .changeType(changeType)
                .changeCount(changeCount)
                .bizEvent(changeType)
                .traceId(traceId)
                .source(SOURCE)
                .message(message)
                .build();
    }

}
