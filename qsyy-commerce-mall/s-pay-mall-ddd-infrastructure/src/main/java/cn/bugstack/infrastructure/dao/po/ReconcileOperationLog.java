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
public class ReconcileOperationLog {

    private Long id;
    private String operator;
    private String operationType;
    private String bizId;
    private String requestBody;
    private String result;
    private Date createTime;

}
