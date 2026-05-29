package cn.bugstack.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Seckill refund response.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class RefundSeckillOrderResponseDTO {

    private String userId;
    private Long activityId;
    private String orderId;
    private String outTradeNo;
    private Integer status;
    private Boolean stockReleased;

}
