package cn.bugstack.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Seckill manual compensation message response.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SeckillManualMessageResponseDTO {

    private String id;
    private String body;
    private String originalStreamKey;
    private String originalMessageId;
    private Long retryCount;
    private String error;

}
