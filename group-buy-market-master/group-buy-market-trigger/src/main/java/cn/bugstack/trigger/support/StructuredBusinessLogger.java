package cn.bugstack.trigger.support;

import com.alibaba.fastjson.JSON;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Emits machine-readable business events for core transaction paths.
 */
@Slf4j
@Component
public class StructuredBusinessLogger {

    private static final String TRACE_ID = "trace-id";

    public Map<String, Object> fields(Object... keyValues) {
        Map<String, Object> fields = new LinkedHashMap<>();
        if (null == keyValues) {
            return fields;
        }
        for (int i = 0; i + 1 < keyValues.length; i += 2) {
            fields.put(String.valueOf(keyValues[i]), keyValues[i + 1]);
        }
        return fields;
    }

    public void info(String event, String phase, Map<String, Object> fields) {
        log("INFO", event, phase, fields, null);
    }

    public void warn(String event, String phase, Map<String, Object> fields) {
        log("WARN", event, phase, fields, null);
    }

    public void error(String event, String phase, Map<String, Object> fields, Throwable error) {
        log("ERROR", event, phase, fields, error);
    }

    private void log(String level, String event, String phase, Map<String, Object> fields, Throwable error) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("event", event);
        body.put("phase", phase);
        body.put("service", "group-buy-market");
        body.put("traceId", MDC.get(TRACE_ID));
        body.put("timestamp", System.currentTimeMillis());
        if (null != fields) {
            body.putAll(fields);
        }
        if (null != error) {
            body.put("errorType", error.getClass().getSimpleName());
            body.put("errorMessage", truncate(error.getMessage(), 512));
        }
        String message = JSON.toJSONString(body);
        if ("ERROR".equals(level)) {
            log.error(message, error);
        } else if ("WARN".equals(level)) {
            log.warn(message);
        } else {
            log.info(message);
        }
    }

    private String truncate(String value, int maxLength) {
        if (null == value || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

}
