package cn.bugstack.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * Seckill manual compensation operation log response.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SeckillManualCompensationLogResponseDTO {

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
