package cn.bugstack.api.dto;

import lombok.Data;

import java.util.Date;

@Data
public class ReconcileOperationLogResponseDTO {

    private Long id;
    private String operator;
    private String operationType;
    private String bizId;
    private String requestBody;
    private String result;
    private Date createTime;

}
