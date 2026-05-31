package cn.bugstack.domain.seckill.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Date;

/**
 * Seckill activity aggregate root.
 */
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SeckillActivityEntity {

    private Long activityId;
    private String activityName;
    private String source;
    private String channel;
    private String goodsId;
    private String goodsName;
    private BigDecimal originalPrice;
    private BigDecimal seckillPrice;
    private Integer totalCount;
    private Integer availableCount;
    private Integer lockCount;
    private Integer takeLimitCount;
    private Integer status;
    private Date startTime;
    private Date endTime;

    public boolean enabled(Date currentTime) {
        return Integer.valueOf(1).equals(status)
                && null != startTime
                && null != endTime
                && !currentTime.before(startTime)
                && !currentTime.after(endTime);
    }

}
