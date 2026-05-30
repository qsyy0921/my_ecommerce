package cn.bugstack.domain.order.model.entity;

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
public class RefundFlowEntity {

    private Long id;
    private String flowNo;
    private String orderId;
    private String userId;
    private String refundChannel;
    private String channelRefundNo;
    private BigDecimal refundAmount;
    private String refundStatus;
    private String refundReason;
    private Date refundTime;
    private String rawMessage;
    private Date createTime;
    private Date updateTime;

}
