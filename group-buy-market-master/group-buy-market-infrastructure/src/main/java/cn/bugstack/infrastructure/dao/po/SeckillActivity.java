package cn.bugstack.infrastructure.dao.po;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Date;

/**
 * Seckill activity PO.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SeckillActivity {

    private Long id;
    private Long activityId;
    private String activityName;
    private String source;
    private String channel;
    private String goodsId;
    private BigDecimal seckillPrice;
    private Integer totalCount;
    private Integer availableCount;
    private Integer lockCount;
    private Integer takeLimitCount;
    private Integer status;
    private Date startTime;
    private Date endTime;
    private Date createTime;
    private Date updateTime;

}
