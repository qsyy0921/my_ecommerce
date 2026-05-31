package cn.bugstack.domain.seckill.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Date;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SeckillOrderCreateMessageEntity {

    public static final String SCHEMA_VERSION = "1.0";
    public static final String EVENT_TYPE = "SECKILL_ORDER_CREATE";

    private String schemaVersion;
    private String eventType;
    private String messageId;
    private String routeKey;
    private Long activityId;
    private String userId;
    private String outTradeNo;
    private String orderId;
    private String source;
    private String channel;
    private String goodsId;
    private String traceId;
    private String occurredAt;

    private String activityName;
    private String goodsName;
    private BigDecimal originalPrice;
    private BigDecimal seckillPrice;
    private Integer status;
    private Integer stockBucket;
    private Integer stockBefore;
    private Integer stockAfter;
    private Date createTime;

    public static SeckillOrderCreateMessageEntity fromOrder(SeckillOrderEntity seckillOrderEntity) {
        String messageId = messageId(seckillOrderEntity.getActivityId(), seckillOrderEntity.getUserId(), seckillOrderEntity.getOutTradeNo());
        return SeckillOrderCreateMessageEntity.builder()
                .schemaVersion(SCHEMA_VERSION)
                .eventType(EVENT_TYPE)
                .messageId(messageId)
                .routeKey(messageId)
                .activityId(seckillOrderEntity.getActivityId())
                .userId(seckillOrderEntity.getUserId())
                .outTradeNo(seckillOrderEntity.getOutTradeNo())
                .orderId(seckillOrderEntity.getOrderId())
                .source(seckillOrderEntity.getSource())
                .channel(seckillOrderEntity.getChannel())
                .goodsId(seckillOrderEntity.getGoodsId())
                .traceId(seckillOrderEntity.getTraceId())
                .occurredAt(OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME))
                .activityName(seckillOrderEntity.getActivityName())
                .goodsName(seckillOrderEntity.getGoodsName())
                .originalPrice(seckillOrderEntity.getOriginalPrice())
                .seckillPrice(seckillOrderEntity.getSeckillPrice())
                .status(seckillOrderEntity.getStatus())
                .stockBucket(seckillOrderEntity.getStockBucket())
                .stockBefore(seckillOrderEntity.getStockBefore())
                .stockAfter(seckillOrderEntity.getStockAfter())
                .createTime(seckillOrderEntity.getCreateTime())
                .build();
    }

    public SeckillOrderEntity toOrderEntity() {
        return SeckillOrderEntity.builder()
                .userId(userId)
                .activityId(activityId)
                .activityName(activityName)
                .goodsId(goodsId)
                .goodsName(goodsName)
                .source(source)
                .channel(channel)
                .orderId(orderId)
                .outTradeNo(outTradeNo)
                .originalPrice(originalPrice)
                .seckillPrice(seckillPrice)
                .status(status)
                .stockBucket(stockBucket)
                .stockBefore(stockBefore)
                .stockAfter(stockAfter)
                .traceId(traceId)
                .sourceMessageId(nonBlank(messageId) ? messageId : messageId(activityId, userId, outTradeNo))
                .createTime(createTime)
                .build();
    }

    public String stableRouteKey() {
        return nonBlank(routeKey) ? routeKey : messageId(activityId, userId, outTradeNo);
    }

    public static String messageId(Long activityId, String userId, String outTradeNo) {
        return String.valueOf(activityId) + ":" + String.valueOf(userId) + ":" + String.valueOf(outTradeNo);
    }

    private static boolean nonBlank(String value) {
        return null != value && value.trim().length() > 0;
    }

}
