package cn.bugstack.test.infrastructure.seckill;

import cn.bugstack.domain.seckill.adapter.port.ISeckillMetricsPort;
import cn.bugstack.domain.seckill.adapter.port.ISeckillOrderMessagePort;
import cn.bugstack.domain.seckill.adapter.port.ISeckillStockAvailabilityPort;
import cn.bugstack.domain.seckill.adapter.port.ISeckillStockFlowPort;
import cn.bugstack.domain.seckill.adapter.port.ISeckillStockReservationPort;
import cn.bugstack.domain.seckill.model.entity.SeckillOrderEntity;
import cn.bugstack.domain.seckill.model.entity.SeckillStockFlowEntity;
import cn.bugstack.domain.seckill.model.entity.SeckillStockReservationEntity;
import cn.bugstack.domain.seckill.model.valobj.SeckillOrderStatusEnumVO;
import cn.bugstack.infrastructure.adapter.port.SeckillOrderLockPort;
import cn.bugstack.infrastructure.adapter.support.SeckillReservationPublishSupport;
import cn.bugstack.infrastructure.adapter.support.SeckillSoldOutCache;
import cn.bugstack.infrastructure.adapter.support.SeckillStockGuardSupport;
import cn.bugstack.infrastructure.adapter.support.SeckillStockReleaseSupport;
import cn.bugstack.infrastructure.event.SeckillPendingRetryPolicy;
import cn.bugstack.types.enums.ResponseCode;
import cn.bugstack.types.exception.AppException;
import org.junit.Assert;
import org.junit.Test;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class SeckillOrderLockPortUnitTest {

    private static final Long ACTIVITY_ID = 200001L;
    private static final String USER_ID = "user_001";
    private static final String OUT_TRADE_NO = "seckill_trade_001";

    @Test
    public void reserveSuccessShouldPublishOrderCreateMessage() {
        Fixture fixture = new Fixture();
        fixture.reservationPort.nextReservation = SeckillStockReservationEntity.success(2, 10, 9);

        SeckillOrderEntity result = fixture.lockPort.lockSeckillOrder(order());

        Assert.assertEquals(1, fixture.reservationPort.reserveCalls);
        Assert.assertEquals(1, fixture.messagePort.publishCalls);
        Assert.assertEquals(0, fixture.reservationPort.rollbackCalls);
        Assert.assertEquals(ACTIVITY_ID, result.getActivityId());
    }

    @Test
    public void duplicateReservationShouldRejectWithoutPublish() {
        Fixture fixture = new Fixture();
        fixture.reservationPort.nextReservation = SeckillStockReservationEntity.duplicate();

        assertAppException(ResponseCode.E0204, new ThrowingRunnable() {
            @Override
            public void run() {
                fixture.lockPort.lockSeckillOrder(order());
            }
        });

        Assert.assertEquals(1, fixture.reservationPort.reserveCalls);
        Assert.assertEquals(0, fixture.messagePort.publishCalls);
        Assert.assertEquals(1, fixture.metricsPort.duplicateCount);
    }

    @Test
    public void stockNotEnoughShouldMarkSoldOutAndReject() {
        Fixture fixture = new Fixture();
        fixture.reservationPort.nextReservation = SeckillStockReservationEntity.stockNotEnough();
        fixture.stockAvailabilityPort.availableStock = 0;

        assertAppException(ResponseCode.E0203, new ThrowingRunnable() {
            @Override
            public void run() {
                fixture.lockPort.lockSeckillOrder(order());
            }
        });

        Assert.assertTrue(fixture.soldOutCache.isSoldOut(ACTIVITY_ID));
        Assert.assertEquals(1, fixture.metricsPort.stockNotEnoughCount);
        Assert.assertEquals(0, fixture.messagePort.publishCalls);
    }

    @Test
    public void soldOutCacheShouldShortCircuitBeforeReserve() {
        Fixture fixture = new Fixture();
        fixture.soldOutCache.markSoldOut(ACTIVITY_ID);

        assertAppException(ResponseCode.E0203, new ThrowingRunnable() {
            @Override
            public void run() {
                fixture.lockPort.lockSeckillOrder(order());
            }
        });

        Assert.assertEquals(0, fixture.reservationPort.reserveCalls);
        Assert.assertEquals(0, fixture.messagePort.publishCalls);
        Assert.assertEquals(1, fixture.metricsPort.stockNotEnoughCount);
    }

    @Test
    public void publishFailureShouldRollbackReservationAndRecordRollbackFlow() {
        Fixture fixture = new Fixture();
        fixture.reservationPort.nextReservation = SeckillStockReservationEntity.success(3, 8, 7);
        fixture.messagePort.publishResult = false;

        assertAppException(ResponseCode.RATE_LIMITER, new ThrowingRunnable() {
            @Override
            public void run() {
                fixture.lockPort.lockSeckillOrder(order());
            }
        });

        Assert.assertEquals(1, fixture.reservationPort.rollbackCalls);
        Assert.assertTrue(fixture.reservationPort.lastRollbackRemoveResult);
        Assert.assertEquals(1, fixture.stockFlowPort.flows.size());
        Assert.assertEquals(SeckillStockFlowEntity.ROLLBACK, fixture.stockFlowPort.flows.get(0).getChangeType());
        Assert.assertFalse(fixture.soldOutCache.isSoldOut(ACTIVITY_ID));
    }

    @Test
    public void pendingRetryPolicyShouldIsolateAfterConfiguredMaxRetry() {
        SeckillPendingRetryPolicy policy = new SeckillPendingRetryPolicy();

        Assert.assertFalse(policy.shouldIsolate(2, 3));
        Assert.assertTrue(policy.shouldIsolate(3, 3));
        Assert.assertTrue(policy.shouldIsolate(1, 0));
        Assert.assertEquals(5, policy.maxRetry(null));
    }

    @Test
    public void stockReleaseFlowNoShouldBeStableForIdempotentInsertIgnore() {
        SeckillOrderEntity order = order();
        order.setStockBucket(1);
        order.setStockBefore(9);
        order.setStockAfter(10);

        SeckillStockFlowEntity first = SeckillStockFlowEntity.rollback(order, SeckillStockFlowEntity.ROLLBACK_CANCEL, 1, "timeout");
        SeckillStockFlowEntity duplicate = SeckillStockFlowEntity.rollback(order, SeckillStockFlowEntity.ROLLBACK_CANCEL, 1, "retry timeout");
        SeckillStockFlowEntity refund = SeckillStockFlowEntity.rollback(order, SeckillStockFlowEntity.ROLLBACK_REFUND, 1, "refund");

        Assert.assertEquals(first.flowNo(), duplicate.flowNo());
        Assert.assertNotEquals(first.flowNo(), refund.flowNo());
    }

    private static SeckillOrderEntity order() {
        return SeckillOrderEntity.builder()
                .userId(USER_ID)
                .activityId(ACTIVITY_ID)
                .activityName("秒杀活动")
                .goodsId("sku_001")
                .goodsName("秒杀商品")
                .source("s01")
                .channel("c01")
                .orderId("order_001")
                .outTradeNo(OUT_TRADE_NO)
                .originalPrice(new BigDecimal("100.00"))
                .seckillPrice(new BigDecimal("79.00"))
                .status(SeckillOrderStatusEnumVO.CREATE.getCode())
                .build();
    }

    private static void assertAppException(ResponseCode responseCode, ThrowingRunnable runnable) {
        try {
            runnable.run();
            Assert.fail("Expected AppException " + responseCode.getCode());
        } catch (AppException e) {
            Assert.assertEquals(responseCode.getCode(), e.getCode());
        }
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

    private interface ThrowingRunnable {
        void run();
    }

    private static class Fixture {
        private final SeckillOrderLockPort lockPort = new SeckillOrderLockPort();
        private final SeckillReservationPublishSupport reservationPublishSupport = new SeckillReservationPublishSupport();
        private final SeckillStockGuardSupport stockGuardSupport = new SeckillStockGuardSupport();
        private final SeckillStockReleaseSupport stockReleaseSupport = new SeckillStockReleaseSupport();
        private final FakeStockAvailabilityPort stockAvailabilityPort = new FakeStockAvailabilityPort();
        private final FakeStockReservationPort reservationPort = new FakeStockReservationPort();
        private final FakeStockFlowPort stockFlowPort = new FakeStockFlowPort();
        private final FakeOrderMessagePort messagePort = new FakeOrderMessagePort();
        private final SeckillSoldOutCache soldOutCache = new SeckillSoldOutCache();
        private final FakeMetricsPort metricsPort = new FakeMetricsPort();

        private Fixture() {
            setField(soldOutCache, "soldOutCacheTtlMillis", 60_000L);
            setField(lockPort, "stockBucketTryCount", 64);
            setField(lockPort, "seckillReservationPublishSupport", reservationPublishSupport);

            setField(reservationPublishSupport, "seckillStockReservationPort", reservationPort);
            setField(reservationPublishSupport, "seckillOrderMessagePort", messagePort);
            setField(reservationPublishSupport, "seckillMetricsPort", metricsPort);
            setField(reservationPublishSupport, "seckillStockGuardSupport", stockGuardSupport);
            setField(reservationPublishSupport, "seckillStockReleaseSupport", stockReleaseSupport);

            setField(stockGuardSupport, "seckillStockAvailabilityPort", stockAvailabilityPort);
            setField(stockGuardSupport, "seckillStockReservationPort", reservationPort);
            setField(stockGuardSupport, "seckillSoldOutCache", soldOutCache);
            setField(stockGuardSupport, "seckillMetricsPort", metricsPort);

            setField(stockReleaseSupport, "seckillStockFlowPort", stockFlowPort);
            setField(stockReleaseSupport, "seckillStockReservationPort", reservationPort);
            setField(stockReleaseSupport, "seckillSoldOutCache", soldOutCache);
        }
    }

    private static class FakeStockAvailabilityPort implements ISeckillStockAvailabilityPort {
        private Integer availableStock = 100;

        @Override
        public Integer queryAvailableStock(Long activityId) {
            return availableStock;
        }
    }

    private static class FakeStockReservationPort implements ISeckillStockReservationPort {
        private SeckillStockReservationEntity nextReservation = SeckillStockReservationEntity.success(0, 10, 9);
        private int reserveCalls;
        private int rollbackCalls;
        private boolean lastRollbackRemoveResult;

        @Override
        public boolean isStockInitialized(Long activityId) {
            return true;
        }

        @Override
        public boolean tryAcquireInitializationLock(Long activityId, long waitMillis, long leaseMillis) {
            return true;
        }

        @Override
        public void releaseInitializationLock(Long activityId) {
        }

        @Override
        public void initializeStock(Long activityId, int availableCount) {
        }

        @Override
        public int queryStock(Long activityId) {
            return 0;
        }

        @Override
        public SeckillStockReservationEntity reserve(SeckillOrderEntity seckillOrderEntity, Integer stockBucketTryCount) {
            reserveCalls++;
            seckillOrderEntity.setStockBucket(nextReservation.getStockBucket());
            seckillOrderEntity.setStockBefore(nextReservation.getStockBefore());
            seckillOrderEntity.setStockAfter(nextReservation.getStockAfter());
            return nextReservation;
        }

        @Override
        public SeckillOrderEntity rollback(SeckillOrderEntity seckillOrderEntity, boolean removeResult) {
            rollbackCalls++;
            lastRollbackRemoveResult = removeResult;
            return seckillOrderEntity;
        }

        @Override
        public SeckillOrderEntity release(SeckillOrderEntity seckillOrderEntity, int changeCount) {
            return seckillOrderEntity;
        }
    }

    private static class FakeStockFlowPort implements ISeckillStockFlowPort {
        private final List<SeckillStockFlowEntity> flows = new ArrayList<>();

        @Override
        public void record(SeckillStockFlowEntity seckillStockFlowEntity) {
            flows.add(seckillStockFlowEntity);
        }

        @Override
        public void recordBatch(List<SeckillStockFlowEntity> seckillStockFlowEntities) {
            flows.addAll(seckillStockFlowEntities);
        }
    }

    private static class FakeOrderMessagePort implements ISeckillOrderMessagePort {
        private boolean publishResult = true;
        private int publishCalls;

        @Override
        public boolean publishOrderCreate(SeckillOrderEntity seckillOrderEntity) {
            publishCalls++;
            return publishResult;
        }
    }

    private static class FakeMetricsPort implements ISeckillMetricsPort {
        private int stockNotEnoughCount;
        private int duplicateCount;

        @Override
        public void recordLock(long nanos, String outcome) {
        }

        @Override
        public void recordRateLimited() {
        }

        @Override
        public void recordStockNotEnough() {
            stockNotEnoughCount++;
        }

        @Override
        public void recordDuplicate() {
            duplicateCount++;
        }
    }
}
