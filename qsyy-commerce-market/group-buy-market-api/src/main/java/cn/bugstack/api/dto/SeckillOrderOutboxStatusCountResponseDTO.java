package cn.bugstack.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Seckill order outbox status count response for operations endpoints.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SeckillOrderOutboxStatusCountResponseDTO {

    private Integer status;
    private String statusName;
    private Integer count;

}
