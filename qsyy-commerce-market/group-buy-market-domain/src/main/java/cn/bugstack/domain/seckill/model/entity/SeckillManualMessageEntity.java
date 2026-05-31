package cn.bugstack.domain.seckill.model.entity;

/**
 * Manual compensation message isolated from the seckill async order stream.
 */
public class SeckillManualMessageEntity {

    private final String id;
    private final String body;
    private final String originalStreamKey;
    private final String originalMessageId;
    private final Long retryCount;
    private final String error;

    public SeckillManualMessageEntity(String id, String body, String originalStreamKey, String originalMessageId, Long retryCount, String error) {
        this.id = id;
        this.body = body;
        this.originalStreamKey = originalStreamKey;
        this.originalMessageId = originalMessageId;
        this.retryCount = retryCount;
        this.error = error;
    }

    public String getId() {
        return id;
    }

    public String getBody() {
        return body;
    }

    public String getOriginalStreamKey() {
        return originalStreamKey;
    }

    public String getOriginalMessageId() {
        return originalMessageId;
    }

    public Long getRetryCount() {
        return retryCount;
    }

    public String getError() {
        return error;
    }

}
