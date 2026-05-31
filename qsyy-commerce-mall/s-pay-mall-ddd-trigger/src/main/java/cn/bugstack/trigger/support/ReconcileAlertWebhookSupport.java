package cn.bugstack.trigger.support;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class ReconcileAlertWebhookSupport {

    public String receive(String body) {
        log.warn("alertmanager webhook received body:{}", body);
        return "success";
    }

}
