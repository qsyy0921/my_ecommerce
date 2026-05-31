package cn.bugstack.infrastructure.dao.po;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class OrderStateFlow {

    private Long id;
    private String flowNo;
    private String bizType;
    private String bizId;
    private String subBizId;
    private String fromStatus;
    private String toStatus;
    private String event;
    private String operatorId;
    private String traceId;
    private String sourceMessageId;
    private String message;
    private Date createTime;

}
