package cn.bugstack.test.infrastructure.seckill;

import cn.bugstack.domain.seckill.model.entity.SeckillOrderOutboxEntity;
import cn.bugstack.infrastructure.adapter.support.SeckillOrderMessagePublisherSupport;
import cn.bugstack.infrastructure.adapter.support.SeckillOrderOutboxRetrySupport;
import cn.bugstack.infrastructure.dao.ISeckillOrderOutboxDao;
import cn.bugstack.infrastructure.dao.po.SeckillOrderOutbox;
import org.junit.Assert;
import org.junit.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class SeckillOrderOutboxRetrySupportUnitTest {

    @Test
    public void retryDueMessagesShouldMarkSentWhenPublishSucceeds() throws Exception {
        SeckillOrderOutboxRetrySupport support = new SeckillOrderOutboxRetrySupport();
        FakeOutboxDao dao = new FakeOutboxDao();
        dao.records.add(outbox("msg_001", 0));
        FakePublisher publisher = new FakePublisher(true);
        setField(support, "seckillOrderOutboxDao", dao);
        setField(support, "messagePublisherSupport", publisher);
        setField(support, "maxRetry", 3);
        setField(support, "retryDelaySeconds", 30);

        int count = support.retryDueMessages(10);

        Assert.assertEquals(1, count);
        Assert.assertEquals("msg_001", dao.sentMessageId);
        Assert.assertEquals(1, publisher.publishCount);
    }

    @Test
    public void retryDueMessagesShouldMarkDeadWhenMaxRetryReached() throws Exception {
        SeckillOrderOutboxRetrySupport support = new SeckillOrderOutboxRetrySupport();
        FakeOutboxDao dao = new FakeOutboxDao();
        dao.records.add(outbox("msg_002", 1));
        FakePublisher publisher = new FakePublisher(false);
        setField(support, "seckillOrderOutboxDao", dao);
        setField(support, "messagePublisherSupport", publisher);
        setField(support, "maxRetry", 2);
        setField(support, "retryDelaySeconds", 30);

        int count = support.retryDueMessages(10);

        Assert.assertEquals(0, count);
        Assert.assertEquals("msg_002", dao.failedMessageId);
        Assert.assertEquals(Integer.valueOf(SeckillOrderOutboxEntity.STATUS_DEAD), dao.failedStatus);
        Assert.assertTrue(dao.failedErrorMessage.contains("publish returned false"));
    }

    @Test
    public void retryManualMessagesShouldReplayDeadOutboxRecords() throws Exception {
        SeckillOrderOutboxRetrySupport support = new SeckillOrderOutboxRetrySupport();
        FakeOutboxDao dao = new FakeOutboxDao();
        SeckillOrderOutbox deadRecord = outbox("msg_003", 10);
        deadRecord.setStatus(SeckillOrderOutboxEntity.STATUS_DEAD);
        dao.records.add(deadRecord);
        FakePublisher publisher = new FakePublisher(true);
        setField(support, "seckillOrderOutboxDao", dao);
        setField(support, "messagePublisherSupport", publisher);
        setField(support, "maxRetry", 2);
        setField(support, "retryDelaySeconds", 30);

        int count = support.retryManualMessages(10);

        Assert.assertEquals(1, count);
        Assert.assertEquals("msg_003", dao.sentMessageId);
        Assert.assertEquals(1, publisher.publishCount);
    }

    private static SeckillOrderOutbox outbox(String messageId, int retryCount) {
        return SeckillOrderOutbox.builder()
                .messageId(messageId)
                .routeKey("route-" + messageId)
                .messageBody("{\"messageId\":\"" + messageId + "\"}")
                .retryCount(retryCount)
                .status(SeckillOrderOutboxEntity.STATUS_FAILED)
                .build();
    }

    private static void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static class FakePublisher extends SeckillOrderMessagePublisherSupport {
        private final boolean success;
        private int publishCount;

        private FakePublisher(boolean success) {
            this.success = success;
        }

        @Override
        public boolean publish(String messageBody, String routeKey) {
            publishCount++;
            return success;
        }
    }

    private static class FakeOutboxDao implements ISeckillOrderOutboxDao {
        private final List<SeckillOrderOutbox> records = new ArrayList<>();
        private String sentMessageId;
        private String failedMessageId;
        private String failedErrorMessage;
        private Integer failedStatus;

        @Override
        public int insertIgnore(SeckillOrderOutbox seckillOrderOutbox) {
            records.add(seckillOrderOutbox);
            return 1;
        }

        @Override
        public SeckillOrderOutbox queryByMessageId(String messageId) {
            return null;
        }

        @Override
        public int markSent(String messageId) {
            sentMessageId = messageId;
            return 1;
        }

        @Override
        public int markFailed(String messageId, String errorMessage, Integer maxRetry, Integer retryDelaySeconds) {
            failedMessageId = messageId;
            failedErrorMessage = errorMessage;
            int retryAfterFailure = records.get(0).getRetryCount() + 1;
            failedStatus = SeckillOrderOutboxEntity.failedStatus(retryAfterFailure, maxRetry);
            return 1;
        }

        @Override
        public List<SeckillOrderOutbox> queryDueMessageList(Integer limit) {
            return Collections.unmodifiableList(records);
        }

        @Override
        public List<SeckillOrderOutbox> queryManualRetryMessageList(Integer limit) {
            return Collections.unmodifiableList(records);
        }
    }

}
