package cn.bugstack.trigger.support;

import cn.bugstack.api.response.Response;
import cn.bugstack.domain.order.service.IOrderReconcileService;
import cn.bugstack.types.common.Constants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

@Slf4j
@Component
public class ReconcileAdminSupport {

    @Value("${reconcile.admin-token:local-admin-token}")
    private String adminToken;

    @Resource
    private IOrderReconcileService orderReconcileService;

    public boolean authorized(String token) {
        return null != token && token.equals(adminToken);
    }

    public <T> Response<T> noLogin() {
        return Response.<T>builder()
                .code(Constants.ResponseCode.NO_LOGIN.getCode())
                .info(Constants.ResponseCode.NO_LOGIN.getInfo())
                .build();
    }

    public String resolveOperator(String headerOperator, String requestOperator) {
        if (null != headerOperator && !headerOperator.trim().isEmpty()) {
            return headerOperator.trim();
        }
        if (null != requestOperator && !requestOperator.trim().isEmpty()) {
            return requestOperator.trim();
        }
        return "local-admin";
    }

    public void audit(String operator, String operationType, String bizId, String requestBody, String result) {
        try {
            orderReconcileService.recordReconcileOperation(resolveOperator(operator, null), operationType, bizId, requestBody, result);
        } catch (Exception e) {
            log.warn("record reconcile operation failed operationType:{} bizId:{}", operationType, bizId, e);
        }
    }

    public String preview(String text, int maxLength) {
        if (null == text) {
            return null;
        }
        return text.substring(0, Math.min(text.length(), maxLength));
    }

}
