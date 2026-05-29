package cn.bugstack.trigger.job;

import com.alibaba.fastjson.JSON;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.lang.management.ManagementFactory;
import java.net.InetAddress;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Records scheduled job execution and provides a MySQL based lock for mall jobs.
 */
@Slf4j
@Component
public class JobExecutionRecorder {

    private static final int STATUS_RUNNING = 0;
    private static final int STATUS_SUCCESS = 1;
    private static final int STATUS_FAIL = 2;
    private static final int STATUS_SKIPPED = 3;

    @Resource
    private JdbcTemplate jdbcTemplate;

    public Execution start(String jobName, int lockSeconds) {
        String ownerId = ownerId();
        String executionId = jobName + ":" + System.currentTimeMillis() + ":" + UUID.randomUUID().toString().replace("-", "");
        long startMillis = System.currentTimeMillis();
        boolean lockAcquired = acquireLock(jobName, ownerId, lockSeconds);
        Execution execution = new Execution(executionId, jobName, ownerId, lockAcquired, startMillis);
        insertExecution(execution, lockAcquired ? STATUS_RUNNING : STATUS_SKIPPED, null);
        logStructured("start", execution, lockAcquired ? STATUS_RUNNING : STATUS_SKIPPED, 0, 0, null, null);
        if (!lockAcquired) {
            complete(execution, STATUS_SKIPPED, 0, 0, "lock skipped", null);
        }
        return execution;
    }

    public void success(Execution execution, int successCount, int failCount, String resultMessage) {
        complete(execution, STATUS_SUCCESS, successCount, failCount, resultMessage, null);
        releaseLock(execution);
    }

    public void fail(Execution execution, Throwable error) {
        String message = null == error ? null : error.getClass().getSimpleName() + ": " + error.getMessage();
        complete(execution, STATUS_FAIL, 0, 1, null, message);
        releaseLock(execution);
    }

    private boolean acquireLock(String jobName, String ownerId, int lockSeconds) {
        try {
            int inserted = jdbcTemplate.update(
                    "insert ignore into job_lock(job_name, owner_id, lock_until, create_time, update_time) " +
                            "values(?, ?, date_add(now(), interval ? second), now(), now())",
                    jobName, ownerId, lockSeconds);
            if (inserted > 0) {
                return true;
            }
            int updated = jdbcTemplate.update(
                    "update job_lock set owner_id = ?, lock_until = date_add(now(), interval ? second), update_time = now() " +
                            "where job_name = ? and lock_until <= now()",
                    ownerId, lockSeconds, jobName);
            return updated > 0;
        } catch (DataAccessException e) {
            log.warn("job lock unavailable, run without db lock jobName:{}", jobName, e);
            return true;
        }
    }

    private void insertExecution(Execution execution, int status, String errorMessage) {
        try {
            jdbcTemplate.update(
                    "insert into job_execution_record(execution_id, job_name, owner_id, status, lock_acquired, start_time, trace_id, error_message, create_time, update_time) " +
                            "values(?, ?, ?, ?, ?, now(), ?, ?, now(), now())",
                    execution.getExecutionId(), execution.getJobName(), execution.getOwnerId(), status,
                    execution.isLockAcquired() ? 1 : 0, MDC.get("trace-id"), truncate(errorMessage, 2048));
        } catch (DataAccessException e) {
            log.warn("job execution insert failed jobName:{}", execution.getJobName(), e);
        }
    }

    private void complete(Execution execution, int status, int successCount, int failCount, String resultMessage, String errorMessage) {
        try {
            jdbcTemplate.update(
                    "update job_execution_record set status = ?, end_time = now(), duration_ms = ?, success_count = ?, fail_count = ?, " +
                            "result_message = ?, error_message = ?, update_time = now() where execution_id = ?",
                    status, System.currentTimeMillis() - execution.getStartMillis(), successCount, failCount,
                    truncate(resultMessage, 1024), truncate(errorMessage, 2048), execution.getExecutionId());
        } catch (DataAccessException e) {
            log.warn("job execution update failed jobName:{}", execution.getJobName(), e);
        }
        logStructured("complete", execution, status, successCount, failCount, resultMessage, errorMessage);
    }

    private void releaseLock(Execution execution) {
        // Keep lock_until unchanged so other instances skip the same schedule window.
        // The next run can acquire the lock after the lease expires.
    }

    private String ownerId() {
        try {
            return InetAddress.getLocalHost().getHostName() + ":" + ManagementFactory.getRuntimeMXBean().getName();
        } catch (Exception e) {
            return ManagementFactory.getRuntimeMXBean().getName();
        }
    }

    private String truncate(String value, int maxLength) {
        if (null == value || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private void logStructured(String event, Execution execution, int status, int successCount, int failCount,
                               String resultMessage, String errorMessage) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("event", "job_execution");
        fields.put("phase", event);
        fields.put("executionId", execution.getExecutionId());
        fields.put("jobName", execution.getJobName());
        fields.put("ownerId", execution.getOwnerId());
        fields.put("status", status);
        fields.put("lockAcquired", execution.isLockAcquired());
        fields.put("durationMs", System.currentTimeMillis() - execution.getStartMillis());
        fields.put("successCount", successCount);
        fields.put("failCount", failCount);
        fields.put("traceId", MDC.get("trace-id"));
        fields.put("resultMessage", truncate(resultMessage, 1024));
        fields.put("errorMessage", truncate(errorMessage, 2048));
        log.info(JSON.toJSONString(fields));
    }

    @Getter
    public static class Execution {
        private final String executionId;
        private final String jobName;
        private final String ownerId;
        private final boolean lockAcquired;
        private final long startMillis;

        private Execution(String executionId, String jobName, String ownerId, boolean lockAcquired, long startMillis) {
            this.executionId = executionId;
            this.jobName = jobName;
            this.ownerId = ownerId;
            this.lockAcquired = lockAcquired;
            this.startMillis = startMillis;
        }
    }

}
