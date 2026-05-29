package cn.bugstack.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * Seckill payment settlement request.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SettlementSeckillOrderRequestDTO {

    private String userId;
    private String outTradeNo;
    private String source;
    private String channel;
    private Date outTradeTime;

}
