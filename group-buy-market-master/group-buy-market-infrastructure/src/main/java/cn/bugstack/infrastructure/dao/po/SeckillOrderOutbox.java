package cn.bugstack.infrastructure.dao.po;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * Seckill order reliable outbox record.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SeckillOrderOutbox {

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

}
