package cn.bugstack.infrastructure.dao.po;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SeckillStockFlow {

    private Long id;
    private String flowNo;
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
    private Date createTime;

}
