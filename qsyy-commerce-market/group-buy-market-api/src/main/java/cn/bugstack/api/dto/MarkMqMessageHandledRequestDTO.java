package cn.bugstack.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request for marking a failed MQ message handled.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class MarkMqMessageHandledRequestDTO {

    private String messageId;

}
