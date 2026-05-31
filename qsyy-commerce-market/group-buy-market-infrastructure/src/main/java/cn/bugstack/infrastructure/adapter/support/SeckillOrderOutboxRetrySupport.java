package cn.bugstack.infrastructure.adapter.support;

import cn.bugstack.domain.seckill.model.entity.SeckillOrderOutboxEntity;
import cn.bugstack.infrastructure.dao.ISeckillOrderOutboxDao;
import cn.bugstack.infrastructure.dao.po.SeckillOrderOutbox;
import cn.bugstack.infrastructure.event.SeckillOrderOutboxMetrics;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.List;

/**
 * Retries reliable outbox records whose immediate queue publish failed.
 */
@Component
public class SeckillOrderOutboxRetrySupport {

    @Value("${app.seckill.order-outbox.max-retry:10}")
    private Integer maxRetry;
    @Value("${app.seckill.order-outbox.retry-delay-seconds:30}")
    private Integer retryDelaySeconds;

    @Resource
    private ISeckillOrderOutboxDao seckillOrderOutboxDao;
    @Resource
    private SeckillOrderMessagePublisherSupport messagePublisherSupport;
    @Autowired(required = false)
    private SeckillOrderOutboxMetrics seckillOrderOutboxMetrics;

    public int retryDueMessages(int limit) {
        long startNanos = System.nanoTime();
        try {
            List<SeckillOrderOutbox> records = seckillOrderOutboxDao.queryDueMessageList(limit);
            int count = retryRecords(records);
            recordRetry("auto", "success", startNanos);
            return count;
        } catch (RuntimeException e) {
            recordRetry("auto", "failed", startNanos);
            throw e;
        }
    }

    public int retryManualMessages(int limit) {
        long startNanos = System.nanoTime();
        try {
            List<SeckillOrderOutbox> records = seckillOrderOutboxDao.queryManualRetryMessageList(limit);
            int count = retryRecords(records);
            recordRetry("manual", "success", startNanos);
            return count;
        } catch (RuntimeException e) {
            recordRetry("manual", "failed", startNanos);
            throw e;
        }
    }

    private int retryRecords(List<SeckillOrderOutbox> records) {
        if (null == records || records.isEmpty()) {
            return 0;
        }
        int successCount = 0;
        for (SeckillOrderOutbox record : records) {
            if (retryOne(record)) {
                successCount++;
            }
        }
        return successCount;
    }

    private boolean retryOne(SeckillOrderOutbox record) {
        try {
            if (messagePublisherSupport.publish(record.getMessageBody(), record.getRouteKey())) {
                seckillOrderOutboxDao.markSent(record.getMessageId());
                return true;
            }
            markFailed(record.getMessageId(), "publish returned false");
            return false;
        } catch (Exception e) {
            markFailed(record.getMessageId(), "outbox retry failed: " + e.getMessage());
            return false;
        }
    }

    public void markFailed(String messageId, String errorMessage) {
        seckillOrderOutboxDao.markFailed(
                messageId,
                StringUtils.left(errorMessage, 512),
                Math.max(1, maxRetry),
                Math.max(1, retryDelaySeconds));
    }

    public int deadStatusAfterFailure(int retryCountBeforeFailure) {
        return SeckillOrderOutboxEntity.failedStatus(retryCountBeforeFailure + 1, Math.max(1, maxRetry));
    }

    private void recordRetry(String mode, String outcome, long startNanos) {
        if (null != seckillOrderOutboxMetrics) {
            seckillOrderOutboxMetrics.recordRetry(mode, outcome, System.nanoTime() - startNanos);
        }
    }

}
