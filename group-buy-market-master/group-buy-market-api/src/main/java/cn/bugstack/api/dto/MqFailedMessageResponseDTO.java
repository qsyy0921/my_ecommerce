package cn.bugstack.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Failed MQ message response for operations endpoints.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class MqFailedMessageResponseDTO {

    private String messageId;
    private String exchangeName;
    private String queueName;
    private String messageBody;
    private Integer status;
    private Integer retryCount;
    private String errorMessage;

}
