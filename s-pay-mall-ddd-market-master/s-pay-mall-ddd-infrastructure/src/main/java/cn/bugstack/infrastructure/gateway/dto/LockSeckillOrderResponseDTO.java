package cn.bugstack.infrastructure.gateway.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

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

}
