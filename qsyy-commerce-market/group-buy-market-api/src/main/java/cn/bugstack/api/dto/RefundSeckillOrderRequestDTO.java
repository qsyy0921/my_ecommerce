package cn.bugstack.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Seckill refund request.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class RefundSeckillOrderRequestDTO {

    private String userId;
    private String outTradeNo;
    private String source;
    private String channel;
    private String refundReason;

}
