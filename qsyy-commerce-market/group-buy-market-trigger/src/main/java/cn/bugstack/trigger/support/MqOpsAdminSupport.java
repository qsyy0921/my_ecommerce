package cn.bugstack.trigger.support;

import cn.bugstack.api.response.Response;
import cn.bugstack.types.enums.ResponseCode;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Admin guard for MQ operations endpoints.
 */
@Component
public class MqOpsAdminSupport {

    @Value("${app.seckill.admin-token:local-admin-token}")
    private String adminToken;

    public boolean authorized(String token) {
        return StringUtils.isNotBlank(token) && token.equals(adminToken);
    }

    public String operator(String operator) {
        return StringUtils.defaultIfBlank(operator, "local-admin");
    }

    public <T> Response<T> denied() {
        return Response.<T>builder()
                .code(ResponseCode.ILLEGAL_PARAMETER.getCode())
                .info("invalid admin token")
                .build();
    }

}
