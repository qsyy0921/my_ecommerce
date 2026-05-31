package cn.bugstack.trigger.support;

import lombok.Data;

import java.util.List;

@Data
public class ReconcileBatchHandleRequest {

    private List<String> caseNoList;
    private Integer caseStatus;
    private String handler;
    private String handleNote;

}
