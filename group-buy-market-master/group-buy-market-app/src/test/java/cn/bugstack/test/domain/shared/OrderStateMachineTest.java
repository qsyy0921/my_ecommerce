package cn.bugstack.test.domain.shared;

import cn.bugstack.domain.shared.model.entity.OrderStateTransitionEntity;
import cn.bugstack.domain.shared.statemachine.OrderStateMachine;
import org.junit.Assert;
import org.junit.Test;

public class OrderStateMachineTest {

    @Test
    public void seckillOrderLifecycleShouldAllowOnlyLegalTransitions() {
        Assert.assertTrue(OrderStateMachine.canTransit(
                OrderStateMachine.BIZ_SECKILL_ORDER,
                OrderStateMachine.STATE_PROCESSING,
                OrderStateMachine.EVENT_ASYNC_ORDER_CREATED,
                OrderStateMachine.STATE_CREATE));

        Assert.assertTrue(OrderStateMachine.canTransit(
                OrderStateMachine.BIZ_SECKILL_ORDER,
                OrderStateMachine.STATE_CREATE,
                OrderStateMachine.EVENT_PAY_SUCCESS,
                OrderStateMachine.STATE_COMPLETE));

        Assert.assertTrue(OrderStateMachine.canTransit(
                OrderStateMachine.BIZ_SECKILL_ORDER,
                OrderStateMachine.STATE_COMPLETE,
                OrderStateMachine.EVENT_REFUND_SUCCESS,
                OrderStateMachine.STATE_REFUND));

        Assert.assertFalse(OrderStateMachine.canTransit(
                OrderStateMachine.BIZ_SECKILL_ORDER,
                OrderStateMachine.STATE_CREATE,
                OrderStateMachine.EVENT_REFUND_SUCCESS,
                OrderStateMachine.STATE_REFUND));
    }

    @Test
    public void groupBuyOrderAndTeamLifecycleShouldAllowSettlementAndRefundTransitions() {
        Assert.assertTrue(OrderStateMachine.canTransit(
                OrderStateMachine.BIZ_GROUP_BUY_ORDER_LIST,
                OrderStateMachine.STATE_INIT,
                OrderStateMachine.EVENT_LOCK,
                OrderStateMachine.STATE_CREATE));

        Assert.assertTrue(OrderStateMachine.canTransit(
                OrderStateMachine.BIZ_GROUP_BUY_ORDER_LIST,
                OrderStateMachine.STATE_COMPLETE,
                OrderStateMachine.EVENT_REFUND_SUCCESS,
                OrderStateMachine.STATE_CLOSE));

        Assert.assertTrue(OrderStateMachine.canTransit(
                OrderStateMachine.BIZ_GROUP_BUY_TEAM,
                OrderStateMachine.STATE_COMPLETE,
                OrderStateMachine.EVENT_TEAM_REFUND_PARTIAL,
                OrderStateMachine.STATE_COMPLETE_FAIL));

        Assert.assertFalse(OrderStateMachine.canTransit(
                OrderStateMachine.BIZ_GROUP_BUY_TEAM,
                OrderStateMachine.STATE_FAIL,
                OrderStateMachine.EVENT_TEAM_FORMED,
                OrderStateMachine.STATE_COMPLETE));
    }

    @Test
    public void transitionEntityShouldBuildAuditableFlowNo() {
        OrderStateTransitionEntity entity = OrderStateTransitionEntity.seckillOrderRefunded(
                "trade-1001", "order-1001", "user-1001", "trace-1001", "refund success");

        Assert.assertEquals(OrderStateMachine.BIZ_SECKILL_ORDER, entity.getBizType());
        Assert.assertEquals(OrderStateMachine.STATE_COMPLETE, entity.getFromStatus());
        Assert.assertEquals(OrderStateMachine.STATE_REFUND, entity.getToStatus());
        Assert.assertEquals(OrderStateMachine.EVENT_REFUND_SUCCESS, entity.getEvent());
        Assert.assertEquals("SECKILL_ORDER:trade-1001:order-1001:REFUND_SUCCESS:REFUND", entity.flowNo());
    }

    @Test(expected = IllegalStateException.class)
    public void illegalTransitionEntityShouldBeRejected() {
        OrderStateTransitionEntity.of(
                OrderStateMachine.BIZ_SECKILL_ORDER,
                "trade-1002",
                "order-1002",
                OrderStateMachine.STATE_CREATE,
                OrderStateMachine.STATE_REFUND,
                OrderStateMachine.EVENT_REFUND_SUCCESS,
                "user-1002",
                "trace-1002",
                null,
                "refund before pay");
    }

}
