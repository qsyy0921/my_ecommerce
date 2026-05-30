package cn.bugstack.test.infrastructure.message;

import cn.bugstack.infrastructure.dao.IMqMessageRecordDao;
import cn.bugstack.infrastructure.dao.po.MqMessageRecord;
import cn.bugstack.infrastructure.event.support.MqMessageIdGenerator;
import cn.bugstack.infrastructure.event.support.MqProducerFailureRecorder;
import org.junit.Assert;
import org.junit.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

public class MqProducerFailureRecorderUnitTest {

    @Test
    public void recordPublishFailureShouldInsertNewFailureRecord() {
        Fixture fixture = new Fixture();
        fixture.apply();

        fixture.recorder.recordPublishFailure("mall.exchange", "mall.pay.success", "{\"orderId\":\"001\"}", "msg_001", "timeout");

        Assert.assertNotNull(fixture.dao.insertedRecord);
        Assert.assertEquals("msg_001", fixture.dao.insertedRecord.getMessageId());
        Assert.assertEquals("mall.exchange", fixture.dao.insertedRecord.getExchangeName());
        Assert.assertEquals("routing:mall.pay.success", fixture.dao.insertedRecord.getQueueName());
        Assert.assertEquals(Integer.valueOf(2), fixture.dao.insertedRecord.getStatus());
        Assert.assertEquals(Integer.valueOf(0), fixture.dao.insertedRecord.getRetryCount());
        Assert.assertTrue(fixture.dao.insertedRecord.getErrorMessage().startsWith("producer publish failed: timeout"));
        Assert.assertNull(fixture.dao.updatedRecord);
    }

    @Test
    public void recordPublishFailureShouldUpdateExistingFailureRecord() {
        Fixture fixture = new Fixture();
        fixture.dao.existingRecord = MqMessageRecord.builder().messageId("msg_001").build();
        fixture.apply();

        fixture.recorder.recordPublishFailure("mall.exchange", "mall.pay.success", "{\"orderId\":\"001\"}", "msg_001", "nack");

        Assert.assertNull(fixture.dao.insertedRecord);
        Assert.assertNotNull(fixture.dao.updatedRecord);
        Assert.assertEquals("msg_001", fixture.dao.updatedRecord.getMessageId());
        Assert.assertTrue(fixture.dao.updatedRecord.getErrorMessage().startsWith("producer publish failed: nack"));
    }

    @Test
    public void recordPublishFailureShouldGenerateMessageIdWhenMissing() {
        Fixture fixture = new Fixture();
        String longError = repeat("x", 600);
        fixture.apply();

        fixture.recorder.recordPublishFailure("mall.exchange", "mall.pay.success", "{\"orderId\":\"001\"}", null, longError);

        String expectedId = fixture.generator.build("mall.exchange", "mall.pay.success", "{\"orderId\":\"001\"}");
        Assert.assertEquals(expectedId, fixture.dao.insertedRecord.getMessageId());
        Assert.assertTrue(fixture.dao.insertedRecord.getErrorMessage().length() <= 512);
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
        private final MqProducerFailureRecorder recorder = new MqProducerFailureRecorder();
        private final FakeMqMessageRecordDao dao = new FakeMqMessageRecordDao();
        private final MqMessageIdGenerator generator = new MqMessageIdGenerator();

        private void apply() {
            setField(recorder, "mqMessageRecordDao", dao);
            setField(recorder, "mqMessageIdGenerator", generator);
        }
    }

    private static class FakeMqMessageRecordDao implements IMqMessageRecordDao {
        private MqMessageRecord existingRecord;
        private MqMessageRecord insertedRecord;
        private MqMessageRecord updatedRecord;

        @Override
        public void insert(MqMessageRecord mqMessageRecord) {
            insertedRecord = mqMessageRecord;
        }

        @Override
        public MqMessageRecord queryByMessageId(String messageId) {
            if (null != existingRecord && existingRecord.getMessageId().equals(messageId)) {
                return existingRecord;
            }
            return null;
        }

        @Override
        public int updateProcessing(String messageId) {
            return 0;
        }

        @Override
        public int updateSuccess(String messageId) {
            return 0;
        }

        @Override
        public int updateFail(MqMessageRecord mqMessageRecord) {
            updatedRecord = mqMessageRecord;
            return 1;
        }

        @Override
        public List<MqMessageRecord> queryFailedMessageList() {
            return new ArrayList<>();
        }

        @Override
        public List<MqMessageRecord> queryProducerFailedMessageList(Integer limit) {
            return new ArrayList<>();
        }

        @Override
        public int countFailedMessages() {
            return 0;
        }
    }

}
