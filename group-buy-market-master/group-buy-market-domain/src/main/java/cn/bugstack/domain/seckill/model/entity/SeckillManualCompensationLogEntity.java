package cn.bugstack.domain.seckill.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SeckillManualCompensationLogEntity {

    public static final String OPERATION_QUERY = "QUERY_MANUAL";
    public static final String OPERATION_REPLAY = "REPLAY_MANUAL";

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
