package cn.bugstack.test.infrastructure.reconcile;

import cn.bugstack.domain.order.model.entity.ReconcileOperationLogEntity;
import cn.bugstack.infrastructure.adapter.support.ReconcileOperationLogSupport;
import cn.bugstack.infrastructure.dao.IReconcileOperationLogDao;
import cn.bugstack.infrastructure.dao.po.ReconcileOperationLog;
import org.junit.Assert;
import org.junit.Test;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

public class ReconcileOperationLogSupportUnitTest {

    @Test
    public void recordShouldFallbackOperatorAndTrimLargePayloads() {
        Fixture fixture = new Fixture();
        fixture.apply();

        fixture.support.record(" ", "REPLAY", "case_001", repeat("r", 1200), repeat("s", 700));

        Assert.assertEquals("unknown", fixture.dao.insertedLog.getOperator());
        Assert.assertEquals("REPLAY", fixture.dao.insertedLog.getOperationType());
        Assert.assertEquals("case_001", fixture.dao.insertedLog.getBizId());
        Assert.assertEquals(1024, fixture.dao.insertedLog.getRequestBody().length());
        Assert.assertEquals(512, fixture.dao.insertedLog.getResult().length());
    }

    @Test
    public void queryLogListShouldMapPoToEntity() {
        Fixture fixture = new Fixture();
        Date now = new Date();
        fixture.dao.logs = Arrays.asList(ReconcileOperationLog.builder()
                .id(1L)
                .operator("admin")
                .operationType("CONFIRM")
                .bizId("case_001")
                .requestBody("{}")
                .result("ok")
                .createTime(now)
                .build());
        fixture.apply();

        List<ReconcileOperationLogEntity> result = fixture.support.queryLogList("case_001", 0L, 10);

        Assert.assertEquals(1, result.size());
        Assert.assertEquals(Long.valueOf(1L), result.get(0).getId());
        Assert.assertEquals("admin", result.get(0).getOperator());
        Assert.assertEquals("CONFIRM", result.get(0).getOperationType());
        Assert.assertEquals("case_001", result.get(0).getBizId());
        Assert.assertEquals("{}", result.get(0).getRequestBody());
        Assert.assertEquals("ok", result.get(0).getResult());
        Assert.assertEquals(now, result.get(0).getCreateTime());
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
        private final ReconcileOperationLogSupport support = new ReconcileOperationLogSupport();
        private final FakeReconcileOperationLogDao dao = new FakeReconcileOperationLogDao();

        private void apply() {
            setField(support, "reconcileOperationLogDao", dao);
        }
    }

    private static class FakeReconcileOperationLogDao implements IReconcileOperationLogDao {
        private ReconcileOperationLog insertedLog;
        private List<ReconcileOperationLog> logs;

        @Override
        public int insert(ReconcileOperationLog operationLog) {
            insertedLog = operationLog;
            return 1;
        }

        @Override
        public List<ReconcileOperationLog> queryLogList(String bizId, Long lastId, Integer pageSize) {
            return logs;
        }
    }

}
