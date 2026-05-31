package cn.bugstack.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Request for replaying seckill manual compensation messages.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ReplaySeckillManualRequestDTO {

    private List<String> messageIds;
    private Integer limit;

}
