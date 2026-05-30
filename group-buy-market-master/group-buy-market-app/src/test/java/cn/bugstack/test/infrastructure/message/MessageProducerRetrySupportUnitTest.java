package cn.bugstack.test.infrastructure.message;

import cn.bugstack.infrastructure.adapter.support.MessageProducerRetrySupport;
import cn.bugstack.infrastructure.dao.IMqMessageRecordDao;
import cn.bugstack.infrastructure.dao.po.MqMessageRecord;
import cn.bugstack.infrastructure.event.EventPublisher;
import org.junit.Assert;
import org.junit.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class MessageProducerRetrySupportUnitTest {

    @Test
    public void retryProducerFailedMessagesShouldPublishAndMarkSuccess() {
        Fixture fixture = new Fixture();
        fixture.dao.records.add(MqMessageRecord.builder()
                .messageId("msg_001")
                .exchangeName("market.exchange")
                .queueName("routing:market.settlement")
                .messageBody("{\"orderId\":\"001\"}")
                .build());
        fixture.apply();

        int count = fixture.support.retryProducerFailedMessages(10);

        Assert.assertEquals(1, count);
        Assert.assertEquals(Arrays.asList("msg_001"), fixture.dao.processingIds);
        Assert.assertEquals(Arrays.asList("msg_001"), fixture.dao.successIds);
        Assert.assertEquals(1, fixture.publisher.publishRecords.size());
        Assert.assertEquals("market.exchange|market.settlement|{\"orderId\":\"001\"}", fixture.publisher.publishRecords.get(0));
    }

    @Test
    public void retryProducerFailedMessagesShouldSkipInvalidRecords() {
        Fixture fixture = new Fixture();
        fixture.dao.records.add(MqMessageRecord.builder()
                .messageId("msg_001")
                .exchangeName("")
                .queueName("routing:market.settlement")
                .messageBody("{\"orderId\":\"001\"}")
                .build());
        fixture.dao.records.add(MqMessageRecord.builder()
                .messageId("msg_002")
                .exchangeName("market.exchange")
                .queueName("")
                .messageBody("{\"orderId\":\"002\"}")
                .build());
        fixture.dao.records.add(MqMessageRecord.builder()
                .messageId("msg_003")
                .exchangeName("market.exchange")
                .queueName("routing:market.settlement")
                .messageBody("")
                .build());
        fixture.apply();

        int count = fixture.support.retryProducerFailedMessages(10);

        Assert.assertEquals(0, count);
        Assert.assertTrue(fixture.dao.processingIds.isEmpty());
        Assert.assertTrue(fixture.dao.successIds.isEmpty());
        Assert.assertTrue(fixture.publisher.publishRecords.isEmpty());
    }

    @Test
    public void retryProducerFailedMessagesShouldMarkFailureWhenPublishFails() {
        Fixture fixture = new Fixture();
        fixture.publisher.failMessage = repeat("x", 600);
        fixture.dao.records.add(MqMessageRecord.builder()
                .messageId("msg_001")
                .exchangeName("market.exchange")
                .queueName("market.settlement")
                .messageBody("{\"orderId\":\"001\"}")
                .build());
        fixture.apply();

        int count = fixture.support.retryProducerFailedMessages(10);

        Assert.assertEquals(0, count);
        Assert.assertEquals(Arrays.asList("msg_001"), fixture.dao.processingIds);
        Assert.assertTrue(fixture.dao.successIds.isEmpty());
        Assert.assertEquals("msg_001", fixture.dao.failedRecord.getMessageId());
        Assert.assertTrue(fixture.dao.failedRecord.getErrorMessage().startsWith("producer retry failed: "));
        Assert.assertTrue(fixture.dao.failedRecord.getErrorMessage().length() <= 512);
    }

    private static String repeat(String value, int times) {
        StringBuilder builder = new StringBuilder(value.length() * times);
        for (int i = 0; i < times; i++) {
            builder.append(value);
        }
        return builder.toString();
    }

    private static void setField(Object target, String fieldName, Object value) {
        try {
            Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static class Fixture {
        private final MessageProducerRetrySupport support = new MessageProducerRetrySupport();
        private final FakeMqMessageRecordDao dao = new FakeMqMessageRecordDao();
        private final FakeEventPublisher publisher = new FakeEventPublisher();

        private void apply() {
            setField(support, "mqMessageRecordDao", dao);
            setField(support, "eventPublisher", publisher);
        }
    }

    private static class FakeEventPublisher extends EventPublisher {
        private final List<String> publishRecords = new ArrayList<>();
        private String failMessage;

        @Override
        public void publishToExchange(String exchange, String routingKey, String message) {
            publishRecords.add(exchange + "|" + routingKey + "|" + message);
            if (null != failMessage) {
                throw new IllegalStateException(failMessage);
            }
        }
    }

    private static class FakeMqMessageRecordDao implements IMqMessageRecordDao {
        private final List<MqMessageRecord> records = new ArrayList<>();
        private final List<String> processingIds = new ArrayList<>();
        private final List<String> successIds = new ArrayList<>();
        private MqMessageRecord failedRecord;

        @Override
        public void insert(MqMessageRecord mqMessageRecord) {
        }

        @Override
        public MqMessageRecord queryByMessageId(String messageId) {
            return null;
        }

        @Override
        public int updateProcessing(String messageId) {
            processingIds.add(messageId);
            return 1;
        }

        @Override
        public int updateSuccess(String messageId) {
            successIds.add(messageId);
            return 1;
        }

        @Override
        public int updateFail(MqMessageRecord mqMessageRecord) {
            failedRecord = mqMessageRecord;
            return 1;
        }

        @Override
        public List<MqMessageRecord> queryFailedMessageList(Integer limit) {
            return new ArrayList<>();
        }

        @Override
        public List<MqMessageRecord> queryProducerFailedMessageList(Integer limit) {
            return records;
        }
    }

}
