package cn.bugstack.domain.seckill.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * Seckill order creation reliable outbox record.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SeckillOrderOutboxEntity {

    public static final int STATUS_INIT = 0;
    public static final int STATUS_SENT = 1;
    public static final int STATUS_FAILED = 2;
    public static final int STATUS_DEAD = 3;

    private Long id;
    private String messageId;
    private String routeKey;
    private String topic;
    private String messageBody;
    private Integer status;
    private Integer retryCount;
    private Date nextRetryTime;
    private String errorMessage;
    private String traceId;
    private Date createTime;
    private Date updateTime;

    public static SeckillOrderOutboxEntity init(SeckillOrderCreateMessageEntity message, String topic, String messageBody) {
        return SeckillOrderOutboxEntity.builder()
                .messageId(message.getMessageId())
                .routeKey(message.stableRouteKey())
                .topic(topic)
                .messageBody(messageBody)
                .status(STATUS_INIT)
                .retryCount(0)
                .traceId(message.getTraceId())
                .build();
    }

    public static int failedStatus(int retryCountAfterFailure, int maxRetry) {
        return retryCountAfterFailure >= maxRetry ? STATUS_DEAD : STATUS_FAILED;
    }

}
