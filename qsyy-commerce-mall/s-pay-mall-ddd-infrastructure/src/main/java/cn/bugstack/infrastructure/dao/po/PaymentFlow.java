package cn.bugstack.infrastructure.dao.po;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Date;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PaymentFlow {

    private Long id;
    private String flowNo;
    private String orderId;
    private String userId;
    private String payChannel;
    private String channelTradeNo;
    private BigDecimal payAmount;
    private String payStatus;
    private Date payTime;
    private String rawMessage;
    private Date createTime;
    private Date updateTime;

}
