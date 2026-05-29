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
public class ThirdPartyBill {

    private Long id;
    private String billNo;
    private String orderId;
    private String channel;
    private String channelTradeNo;
    private String billType;
    private BigDecimal amount;
    private String billStatus;
    private Date billTime;
    private String rawLine;
    private Date createTime;
    private Date updateTime;

}
