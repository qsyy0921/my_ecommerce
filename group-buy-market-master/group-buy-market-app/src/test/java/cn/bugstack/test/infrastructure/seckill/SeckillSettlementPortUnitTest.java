package cn.bugstack.test.infrastructure.seckill;

import cn.bugstack.domain.seckill.adapter.port.ISeckillResultCachePort;
import cn.bugstack.domain.seckill.model.entity.SeckillOrderEntity;
import cn.bugstack.domain.seckill.model.valobj.SeckillOrderStatusEnumVO;
import cn.bugstack.domain.shared.adapter.port.IOrderStateFlowPort;
import cn.bugstack.domain.shared.model.entity.OrderStateTransitionEntity;
import cn.bugstack.infrastructure.adapter.port.SeckillSettlementPort;
import cn.bugstack.infrastructure.adapter.support.SeckillOrderTableGateway;
import cn.bugstack.infrastructure.adapter.support.SeckillPaidSettlementSupport;
import cn.bugstack.infrastructure.dao.po.SeckillOrder;
import cn.bugstack.types.enums.ResponseCode;
import cn.bugstack.types.exception.AppException;
import org.junit.Assert;
import org.junit.Test;

import java.lang.reflect.Field;
import java.math.BigDecimal;

public class SeckillSettlementPortUnitTest {

    private static final Long ACTIVITY_ID = 200001L;
    private static final String USER_ID = "user_001";
    private static final String OUT_TRADE_NO = "seckill_trade_001";

    @Test
    public void createOrderShouldSettleToCompleteAndCacheResult() {
        Fixture fixture = new Fixture();
        fixture.gateway.currentOrder = order(SeckillOrderStatusEnumVO.CREATE);

        SeckillOrderEntity result = fixture.port.settlementSeckillOrder(USER_ID, OUT_TRADE_NO);

        Assert.assertEquals(SeckillOrderStatusEnumVO.COMPLETE.getCode(), result.getStatus());
        Assert.assertEquals(1, fixture.gateway.paySuccessCalls);
        Assert.assertEquals(1, fixture.stateFlowPort.recordCalls);
        Assert.assertEquals(1, fixture.resultCachePort.cacheCalls);
        Assert.assertEquals(SeckillOrderEntity.RESULT_SUCCESS, fixture.resultCachePort.lastResultStatus);
    }

    @Test
    public void completeOrderShouldReturnIdempotentlyWithoutUpdatingAgain() {
        Fixture fixture = new Fixture();
        fixture.gateway.currentOrder = order(SeckillOrderStatusEnumVO.COMPLETE);

        SeckillOrderEntity result = fixture.port.settlementSeckillOrder(USER_ID, OUT_TRADE_NO);

        Assert.assertEquals(SeckillOrderStatusEnumVO.COMPLETE.getCode(), result.getStatus());
        Assert.assertEquals(0, fixture.gateway.paySuccessCalls);
        Assert.assertEquals(0, fixture.stateFlowPort.recordCalls);
        Assert.assertEquals("seckill order already paid", fixture.resultCachePort.lastMessage);
    }

    @Test
    public void invalidOrderStatusShouldRejectSettlement() {
        Fixture fixture = new Fixture();
        fixture.gateway.currentOrder = order(SeckillOrderStatusEnumVO.REFUND);

        try {
            fixture.port.settlementSeckillOrder(USER_ID, OUT_TRADE_NO);
            Assert.fail("Expected AppException E0207");
        } catch (AppException e) {
            Assert.assertEquals(ResponseCode.E0207.getCode(), e.getCode());
        }
    }

    private static SeckillOrder order(SeckillOrderStatusEnumVO status) {
        return SeckillOrder.builder()
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
                .status(status.getCode())
                .build();
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
        private final SeckillSettlementPort port = new SeckillSettlementPort();
        private final SeckillPaidSettlementSupport paidSettlementSupport = new SeckillPaidSettlementSupport();
        private final FakeOrderTableGateway gateway = new FakeOrderTableGateway();
        private final FakeResultCachePort resultCachePort = new FakeResultCachePort();
        private final FakeStateFlowPort stateFlowPort = new FakeStateFlowPort();

        private Fixture() {
            setField(port, "seckillResultCachePort", resultCachePort);
            setField(port, "seckillOrderTableGateway", gateway);
            setField(port, "seckillPaidSettlementSupport", paidSettlementSupport);
            setField(paidSettlementSupport, "orderStateFlowPort", stateFlowPort);
            setField(paidSettlementSupport, "seckillOrderTableGateway", gateway);
        }
    }

    private static class FakeOrderTableGateway extends SeckillOrderTableGateway {
        private SeckillOrder currentOrder;
        private int paySuccessCalls;

        @Override
        public SeckillOrder queryByOutTradeNo(String userId, String outTradeNo) {
            return currentOrder;
        }

        @Override
        public int paySuccess(String userId, String outTradeNo, String orderId) {
            paySuccessCalls++;
            currentOrder.setStatus(SeckillOrderStatusEnumVO.COMPLETE.getCode());
            return 1;
        }
    }

    private static class FakeResultCachePort implements ISeckillResultCachePort {
        private int cacheCalls;
        private String lastResultStatus;
        private String lastMessage;

        @Override
        public SeckillOrderEntity query(Long activityId, String userId, String outTradeNo) {
            return null;
        }

        @Override
        public void cache(SeckillOrderEntity seckillOrderEntity, String resultStatus, String message) {
            cacheCalls++;
            lastResultStatus = resultStatus;
            lastMessage = message;
        }

        @Override
        public void remove(Long activityId, String userId, String outTradeNo) {
        }

        @Override
        public String resultKey(Long activityId, String userId, String outTradeNo) {
            return activityId + ":" + userId + ":" + outTradeNo;
        }
    }

    private static class FakeStateFlowPort implements IOrderStateFlowPort {
        private int recordCalls;

        @Override
        public void record(OrderStateTransitionEntity orderStateTransitionEntity) {
            recordCalls++;
        }
    }

}
