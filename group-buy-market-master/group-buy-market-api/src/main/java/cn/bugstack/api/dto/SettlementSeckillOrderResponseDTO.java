package cn.bugstack.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Seckill payment settlement response.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SettlementSeckillOrderResponseDTO {

    private String userId;
    private Long activityId;
    private String orderId;
    private String outTradeNo;
    private Integer status;

}
