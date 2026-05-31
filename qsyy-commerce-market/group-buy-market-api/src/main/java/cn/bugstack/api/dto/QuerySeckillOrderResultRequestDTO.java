package cn.bugstack.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Seckill order result query request.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class QuerySeckillOrderResultRequestDTO {

    private String userId;
    private Long activityId;
    private String outTradeNo;

}
