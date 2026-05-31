package cn.bugstack.domain.seckill.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Seckill stock audit flow expressed in domain language.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SeckillStockFlowEntity {

    public static final String RESERVE = "RESERVE";
    public static final String ROLLBACK = "ROLLBACK";
    public static final String ROLLBACK_TIMEOUT = "ROLLBACK_TIMEOUT";
    public static final String ROLLBACK_CANCEL = "ROLLBACK_CANCEL";
    public static final String ROLLBACK_REFUND = "ROLLBACK_REFUND";

    private static final String SOURCE = "seckill";

    private String userId;
    private Long activityId;
    private String orderId;
    private String outTradeNo;
    private Integer stockBucket;
    private String changeType;
    private Integer changeCount;
    private Integer stockBefore;
    private Integer stockAfter;
    private String bizEvent;
    private String traceId;
    private String sourceMessageId;
    private String source;
    private String message;

    public static SeckillStockFlowEntity reserved(SeckillOrderEntity seckillOrderEntity, String message) {
        return build(seckillOrderEntity, RESERVE, -1, message);
    }

    public static SeckillStockFlowEntity rollback(SeckillOrderEntity seckillOrderEntity, String changeType, int changeCount, String message) {
        return build(seckillOrderEntity, changeType, changeCount, message);
    }

    public String flowNo() {
        return activityId + ":" + userId + ":" + outTradeNo + ":" + changeType;
    }

    private static SeckillStockFlowEntity build(SeckillOrderEntity seckillOrderEntity, String changeType, int changeCount, String message) {
        return SeckillStockFlowEntity.builder()
                .userId(seckillOrderEntity.getUserId())
                .activityId(seckillOrderEntity.getActivityId())
                .orderId(seckillOrderEntity.getOrderId())
                .outTradeNo(seckillOrderEntity.getOutTradeNo())
                .stockBucket(seckillOrderEntity.getStockBucket())
                .changeType(changeType)
                .changeCount(changeCount)
                .stockBefore(seckillOrderEntity.getStockBefore())
                .stockAfter(seckillOrderEntity.getStockAfter())
                .bizEvent(changeType)
                .traceId(seckillOrderEntity.getTraceId())
                .sourceMessageId(seckillOrderEntity.getSourceMessageId())
                .source(SOURCE)
                .message(message)
                .build();
    }

}
