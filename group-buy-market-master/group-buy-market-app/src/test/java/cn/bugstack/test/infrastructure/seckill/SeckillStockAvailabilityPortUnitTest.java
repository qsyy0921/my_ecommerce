package cn.bugstack.test.infrastructure.seckill;

import cn.bugstack.domain.seckill.adapter.port.ISeckillStockReservationPort;
import cn.bugstack.domain.seckill.model.entity.SeckillOrderEntity;
import cn.bugstack.domain.seckill.model.entity.SeckillStockReservationEntity;
import cn.bugstack.infrastructure.adapter.port.SeckillStockAvailabilityPort;
import cn.bugstack.infrastructure.adapter.support.SeckillSoldOutCache;
import cn.bugstack.infrastructure.adapter.support.SeckillStockInitializationSupport;
import cn.bugstack.infrastructure.adapter.support.SeckillStockSnapshotSupport;
import cn.bugstack.infrastructure.dao.ISeckillActivityDao;
import cn.bugstack.infrastructure.dao.po.SeckillActivity;
import cn.bugstack.types.enums.ResponseCode;
import cn.bugstack.types.exception.AppException;
import org.junit.Assert;
import org.junit.Test;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.List;

public class SeckillStockAvailabilityPortUnitTest {

    private static final Long ACTIVITY_ID = 200001L;

    @Test
    public void initializedStockShouldReturnRedisSnapshotWithoutDbQuery() {
        Fixture fixture = new Fixture();
        fixture.reservationPort.initialized = true;
        fixture.reservationPort.stock = 7;

        Integer stock = fixture.port.queryAvailableStock(ACTIVITY_ID);

        Assert.assertEquals(Integer.valueOf(7), stock);
        Assert.assertEquals(0, fixture.activityDao.queryCalls);
        Assert.assertFalse(fixture.soldOutCache.isSoldOut(ACTIVITY_ID));
    }

    @Test
    public void zeroInitializedStockShouldMarkSoldOut() {
        Fixture fixture = new Fixture();
        fixture.reservationPort.initialized = true;
        fixture.reservationPort.stock = 0;

        Integer stock = fixture.port.queryAvailableStock(ACTIVITY_ID);

        Assert.assertEquals(Integer.valueOf(0), stock);
        Assert.assertTrue(fixture.soldOutCache.isSoldOut(ACTIVITY_ID));
    }

    @Test
    public void uninitializedStockShouldLoadFromDbAndInitializeRedis() {
        Fixture fixture = new Fixture();
        fixture.reservationPort.initialized = false;
        fixture.activityDao.activity = SeckillActivity.builder()
                .activityId(ACTIVITY_ID)
                .availableCount(9)
                .build();

        Integer stock = fixture.port.queryAvailableStock(ACTIVITY_ID);

        Assert.assertEquals(Integer.valueOf(9), stock);
        Assert.assertEquals(1, fixture.activityDao.queryCalls);
        Assert.assertEquals(1, fixture.reservationPort.initializeCalls);
        Assert.assertEquals(1, fixture.reservationPort.releaseLockCalls);
        Assert.assertFalse(fixture.soldOutCache.isSoldOut(ACTIVITY_ID));
    }

    @Test
    public void missingActivityShouldRejectInitialization() {
        Fixture fixture = new Fixture();
        fixture.reservationPort.initialized = false;
        fixture.activityDao.activity = null;

        try {
            fixture.port.queryAvailableStock(ACTIVITY_ID);
            Assert.fail("Expected AppException E0201");
        } catch (AppException e) {
            Assert.assertEquals(ResponseCode.E0201.getCode(), e.getCode());
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

    private static class Fixture {
        private final SeckillStockAvailabilityPort port = new SeckillStockAvailabilityPort();
        private final SeckillStockSnapshotSupport snapshotSupport = new SeckillStockSnapshotSupport();
        private final SeckillStockInitializationSupport initializationSupport = new SeckillStockInitializationSupport();
        private final FakeStockReservationPort reservationPort = new FakeStockReservationPort();
        private final FakeSeckillActivityDao activityDao = new FakeSeckillActivityDao();
        private final SeckillSoldOutCache soldOutCache = new SeckillSoldOutCache();

        private Fixture() {
            setField(soldOutCache, "soldOutCacheTtlMillis", 60_000L);
            setField(port, "stockInitLockWaitMillis", 200L);
            setField(port, "seckillStockSnapshotSupport", snapshotSupport);
            setField(port, "seckillStockInitializationSupport", initializationSupport);
            setField(snapshotSupport, "seckillStockReservationPort", reservationPort);
            setField(snapshotSupport, "seckillSoldOutCache", soldOutCache);
            setField(initializationSupport, "seckillActivityDao", activityDao);
            setField(initializationSupport, "seckillStockReservationPort", reservationPort);
            setField(initializationSupport, "seckillStockSnapshotSupport", snapshotSupport);
        }
    }

    private static class FakeStockReservationPort implements ISeckillStockReservationPort {
        private boolean initialized = true;
        private int stock = 100;
        private int initializeCalls;
        private int releaseLockCalls;

        @Override
        public boolean isStockInitialized(Long activityId) {
            return initialized;
        }

        @Override
        public boolean tryAcquireInitializationLock(Long activityId, long waitMillis, long leaseMillis) {
            return true;
        }

        @Override
        public void releaseInitializationLock(Long activityId) {
            releaseLockCalls++;
        }

        @Override
        public void initializeStock(Long activityId, int availableCount) {
            initializeCalls++;
            initialized = true;
            stock = availableCount;
        }

        @Override
        public int queryStock(Long activityId) {
            return stock;
        }

        @Override
        public SeckillStockReservationEntity reserve(SeckillOrderEntity seckillOrderEntity, Integer stockBucketTryCount) {
            return SeckillStockReservationEntity.success(0, stock, stock - 1);
        }

        @Override
        public SeckillOrderEntity rollback(SeckillOrderEntity seckillOrderEntity, boolean removeResult) {
            return seckillOrderEntity;
        }

        @Override
        public SeckillOrderEntity release(SeckillOrderEntity seckillOrderEntity, int changeCount) {
            return seckillOrderEntity;
        }
    }

    private static class FakeSeckillActivityDao implements ISeckillActivityDao {
        private SeckillActivity activity;
        private int queryCalls;

        @Override
        public SeckillActivity querySeckillActivity(SeckillActivity seckillActivity) {
            queryCalls++;
            return activity;
        }

        @Override
        public int updateOccupyStock(Long activityId) {
            return 0;
        }

        @Override
        public int updateReleaseStock(Long activityId) {
            return 0;
        }

        @Override
        public List<Long> queryStockSyncActivityIds() {
            return Collections.emptyList();
        }

        @Override
        public int syncStockByOrderCount(Long activityId) {
            return 0;
        }

        @Override
        public int syncStockByActiveCount(Long activityId, Integer activeCount) {
            return 0;
        }

        @Override
        public List<SeckillActivity> queryPrewarmActivities(Integer beforeMinutes, Integer limit) {
            return Collections.emptyList();
        }
    }

}
