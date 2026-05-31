package cn.bugstack.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Seckill order lock request.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class LockSeckillOrderRequestDTO {

    private String userId;
    private String source;
    private String channel;
    private String goodsId;
    private Long activityId;
    private String outTradeNo;

}
