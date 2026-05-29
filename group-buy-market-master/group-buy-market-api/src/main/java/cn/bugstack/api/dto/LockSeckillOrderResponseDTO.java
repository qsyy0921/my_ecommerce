package cn.bugstack.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Seckill order lock response.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class LockSeckillOrderResponseDTO {

    private String orderId;
    private Long activityId;
    private String goodsId;
    private BigDecimal originalPrice;
    private BigDecimal seckillPrice;
    private Integer status;
    private String resultStatus;
    private String message;

}
