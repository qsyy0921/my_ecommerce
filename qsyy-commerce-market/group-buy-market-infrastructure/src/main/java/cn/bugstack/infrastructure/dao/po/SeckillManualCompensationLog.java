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
public class SeckillManualCompensationLog {

    private Long id;
    private String operator;
    private String operationType;
    private String messageIds;
    private Integer requestLimit;
    private String manualStreamKey;
    private Integer resultCount;
    private Integer success;
    private String errorMessage;
    private Date createTime;

}
