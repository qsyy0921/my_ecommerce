package cn.bugstack.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * Seckill order outbox record response for operations endpoints.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SeckillOrderOutboxResponseDTO {

    private Long id;
    private String messageId;
    private String routeKey;
    private String topic;
    private String messageBody;
    private Integer status;
    private String statusName;
    private Integer retryCount;
    private Date nextRetryTime;
    private String errorMessage;
    private String traceId;
    private Date createTime;
    private Date updateTime;

}
