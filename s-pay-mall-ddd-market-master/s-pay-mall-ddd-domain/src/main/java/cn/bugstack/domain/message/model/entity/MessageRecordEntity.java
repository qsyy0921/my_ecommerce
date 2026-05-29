package cn.bugstack.domain.message.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * MQ consume record.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class MessageRecordEntity {

    public static final int STATUS_PROCESSING = 0;
    public static final int STATUS_SUCCESS = 1;
    public static final int STATUS_FAIL = 2;

    private String messageId;
    private String exchangeName;
    private String queueName;
    private String messageBody;
    private Integer status;
    private Integer retryCount;
    private String errorMessage;

    public boolean consumed() {
        return Integer.valueOf(STATUS_SUCCESS).equals(status);
    }

}
