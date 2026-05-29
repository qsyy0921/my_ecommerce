package cn.bugstack.infrastructure.dao.po;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * MQ consume idempotency record.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class MqMessageRecord {

    private Long id;
    private String messageId;
    private String exchangeName;
    private String queueName;
    private String messageBody;
    private Integer status;
    private Integer retryCount;
    private String errorMessage;
    private Date createTime;
    private Date updateTime;

}
