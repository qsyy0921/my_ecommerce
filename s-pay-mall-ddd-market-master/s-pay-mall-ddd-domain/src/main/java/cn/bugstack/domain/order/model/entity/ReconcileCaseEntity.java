package cn.bugstack.domain.order.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ReconcileCaseEntity {

    private Long id;
    private String caseNo;
    private String bizType;
    private String bizId;
    private String userId;
    private String caseType;
    private Integer caseStatus;
    private String severity;
    private String sourceStatus;
    private String targetStatus;
    private String summary;
    private String detail;
    private Integer retryCount;
    private Date createTime;
    private Date updateTime;
    private Date handledTime;
    private String handler;
    private String handleNote;

}
