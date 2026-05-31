package cn.bugstack.infrastructure.dao.po;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Date;

/**
 * Seckill order PO.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SeckillOrder {

    private Long id;
    private String userId;
    private Long activityId;
    private String activityName;
    private String goodsId;
    private String goodsName;
    private String source;
    private String channel;
    private String orderId;
    private String outTradeNo;
    private BigDecimal originalPrice;
    private BigDecimal seckillPrice;
    private Integer status;
    private Date createTime;
    private Date updateTime;

}
