package cn.bugstack.trigger.support;

import lombok.Data;

@Data
public class ReconcileHandleRequest {

    private String caseNo;
    private Integer caseStatus;
    private String handler;
    private String handleNote;

}
