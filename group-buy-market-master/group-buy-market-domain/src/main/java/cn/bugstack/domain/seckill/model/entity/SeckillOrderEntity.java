package cn.bugstack.domain.seckill.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Date;

/**
 * Seckill locked order.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SeckillOrderEntity {

    public static final String RESULT_PROCESSING = "PROCESSING";
    public static final String RESULT_SUCCESS = "SUCCESS";
    public static final String RESULT_FAIL = "FAIL";
    public static final String RESULT_SOLD_OUT = "SOLD_OUT";
    public static final String RESULT_DUPLICATE = "DUPLICATE";
    public static final String RESULT_NOT_FOUND = "NOT_FOUND";

    private String userId;
    private Long activityId;
    private String activityName;
    private String goodsId;
    private String goodsName;
    private String source;
    private String channel;
    private String orderId;
    private String outTradeNo;
    private BigDecimal originalPrice;
    private BigDecimal seckillPrice;
    private Integer status;
    private Integer stockBucket;
    private Integer stockBefore;
    private Integer stockAfter;
    private String traceId;
    private String sourceMessageId;
    private String resultStatus;
    private String message;
    private Date createTime;

}
