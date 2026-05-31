package cn.bugstack.trigger.support;

import lombok.Data;

import java.util.List;

@Data
public class ReconcileBatchReplayRequest {

    private List<String> caseNoList;
    private String operator;

}
