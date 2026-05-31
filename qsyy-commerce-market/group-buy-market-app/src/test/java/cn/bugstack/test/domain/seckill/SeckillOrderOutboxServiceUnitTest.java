package cn.bugstack.test.domain.seckill;

import cn.bugstack.domain.seckill.adapter.port.ISeckillOrderOutboxPort;
import cn.bugstack.domain.seckill.model.entity.SeckillOrderCreateMessageEntity;
import cn.bugstack.domain.seckill.model.entity.SeckillOrderOutboxEntity;
import cn.bugstack.domain.seckill.service.SeckillOrderOutboxService;
import org.junit.Assert;
import org.junit.Test;

import java.util.Collections;
import java.util.List;

public class SeckillOrderOutboxServiceUnitTest {

    @Test
    public void queryMessagesShouldClampLimitAndKeepStatus() {
        FakeOutboxPort port = new FakeOutboxPort();
        SeckillOrderOutboxService service = new SeckillOrderOutboxService(port);

        List<SeckillOrderOutboxEntity> result = service.queryMessages(SeckillOrderOutboxEntity.STATUS_FAILED, 500);

        Assert.assertEquals(1, result.size());
        Assert.assertEquals(Integer.valueOf(SeckillOrderOutboxEntity.STATUS_FAILED), port.queryStatus);
        Assert.assertEquals(100, port.queryLimit);
    }

    @Test
    public void invalidStatusShouldNotHitPort() {
        FakeOutboxPort port = new FakeOutboxPort();
        SeckillOrderOutboxService service = new SeckillOrderOutboxService(port);

        List<SeckillOrderOutboxEntity> result = service.queryMessages(99, 20);
        int count = service.countMessages(99);

        Assert.assertTrue(result.isEmpty());
        Assert.assertEquals(0, count);
        Assert.assertNull(port.queryStatus);
        Assert.assertNull(port.countStatus);
    }

    @Test
    public void countMessagesShouldDelegateKnownStatus() {
        FakeOutboxPort port = new FakeOutboxPort();
        SeckillOrderOutboxService service = new SeckillOrderOutboxService(port);

        int count = service.countMessages(SeckillOrderOutboxEntity.STATUS_DEAD);

        Assert.assertEquals(7, count);
        Assert.assertEquals(Integer.valueOf(SeckillOrderOutboxEntity.STATUS_DEAD), port.countStatus);
    }

    private static class FakeOutboxPort implements ISeckillOrderOutboxPort {
        private Integer queryStatus;
        private Integer countStatus;
        private int queryLimit;

        @Override
        public boolean recordInit(SeckillOrderCreateMessageEntity message, String topic, String messageBody) {
            return true;
        }

        @Override
        public void markSent(String messageId) {
        }

        @Override
        public void markFailed(String messageId, String errorMessage) {
        }

        @Override
        public int retryDueMessages(int limit) {
            return 0;
        }

        @Override
        public int retryManualMessages(int limit) {
            return 0;
        }

        @Override
        public List<SeckillOrderOutboxEntity> queryMessages(Integer status, int limit) {
            queryStatus = status;
            queryLimit = limit;
            return Collections.singletonList(SeckillOrderOutboxEntity.builder()
                    .messageId("msg_001")
                    .status(status)
                    .build());
        }

        @Override
        public int countMessages(Integer status) {
            countStatus = status;
            return 7;
        }
    }

}
