package cn.bugstack.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Date;

/**
 * Seckill market config response.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SeckillMarketResponseDTO {

    private Long activityId;
    private String activityName;
    private String goodsId;
    private String goodsName;
    private BigDecimal originalPrice;
    private BigDecimal seckillPrice;
    private Integer totalCount;
    private Integer availableCount;
    private Integer lockCount;
    private Integer status;
    private Date startTime;
    private Date endTime;

}
