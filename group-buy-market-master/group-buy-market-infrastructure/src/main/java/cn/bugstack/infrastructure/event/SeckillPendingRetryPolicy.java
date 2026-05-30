package cn.bugstack.infrastructure.event;

/**
 * Technical policy for Redis Stream pending retry isolation.
 */
public class SeckillPendingRetryPolicy {

    private static final int DEFAULT_MAX_RETRY = 5;

    public boolean shouldIsolate(long retryCount, Integer configuredMaxRetry) {
        return retryCount >= maxRetry(configuredMaxRetry);
    }

    public int maxRetry(Integer configuredMaxRetry) {
        return Math.max(1, null == configuredMaxRetry ? DEFAULT_MAX_RETRY : configuredMaxRetry);
    }

}
