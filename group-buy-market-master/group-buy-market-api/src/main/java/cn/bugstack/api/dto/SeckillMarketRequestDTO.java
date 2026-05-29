package cn.bugstack.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Seckill market config query request.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SeckillMarketRequestDTO {

    private String userId;
    private String source;
    private String channel;
    private String goodsId;
    private Long activityId;

}
