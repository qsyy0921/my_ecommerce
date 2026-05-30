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
    public void afterSaleLifecycleShouldCoverApplyPartialRejectAndFulfilledRefund() {
        Assert.assertTrue(OrderStateMachine.canTransit(
                OrderStateMachine.BIZ_SECKILL_ORDER,
                OrderStateMachine.STATE_COMPLETE,
                OrderStateMachine.EVENT_REFUND_APPLY,
                OrderStateMachine.STATE_REFUNDING));

        Assert.assertTrue(OrderStateMachine.canTransit(
                OrderStateMachine.BIZ_SECKILL_ORDER,
                OrderStateMachine.STATE_REFUNDING,
                OrderStateMachine.EVENT_REFUND_PARTIAL_SUCCESS,
                OrderStateMachine.STATE_PARTIAL_REFUND));

        Assert.assertTrue(OrderStateMachine.canTransit(
                OrderStateMachine.BIZ_SECKILL_ORDER,
                OrderStateMachine.STATE_REFUNDING,
                OrderStateMachine.EVENT_REFUND_REJECT,
                OrderStateMachine.STATE_REFUND_REJECTED));

        Assert.assertTrue(OrderStateMachine.canTransit(
                OrderStateMachine.BIZ_SECKILL_ORDER,
                OrderStateMachine.STATE_COMPLETE,
                OrderStateMachine.EVENT_FULFILL,
                OrderStateMachine.STATE_FULFILLED));

        Assert.assertTrue(OrderStateMachine.canTransit(
                OrderStateMachine.BIZ_SECKILL_ORDER,
                OrderStateMachine.STATE_FULFILLED,
                OrderStateMachine.EVENT_REFUND_APPLY,
                OrderStateMachine.STATE_REFUNDING));

        Assert.assertTrue(OrderStateMachine.canTransit(
                OrderStateMachine.BIZ_SECKILL_ORDER,
                OrderStateMachine.STATE_PARTIAL_REFUND,
                OrderStateMachine.EVENT_REFUND_SUCCESS,
                OrderStateMachine.STATE_REFUND));

        Assert.assertTrue(OrderStateMachine.canTransit(
                OrderStateMachine.BIZ_GROUP_BUY_ORDER_LIST,
                OrderStateMachine.STATE_FULFILLED,
                OrderStateMachine.EVENT_REFUND_APPLY,
                OrderStateMachine.STATE_REFUNDING));
    }

    @Test
    public void afterSaleLifecycleShouldRejectInvalidAndDuplicateRefundTransitions() {
        Assert.assertFalse(OrderStateMachine.canTransit(
                OrderStateMachine.BIZ_SECKILL_ORDER,
                OrderStateMachine.STATE_CREATE,
                OrderStateMachine.EVENT_REFUND_APPLY,
                OrderStateMachine.STATE_REFUNDING));

        Assert.assertFalse(OrderStateMachine.canTransit(
                OrderStateMachine.BIZ_SECKILL_ORDER,
                OrderStateMachine.STATE_CLOSE,
                OrderStateMachine.EVENT_REFUND_APPLY,
                OrderStateMachine.STATE_REFUNDING));

        Assert.assertFalse(OrderStateMachine.canTransit(
                OrderStateMachine.BIZ_SECKILL_ORDER,
                OrderStateMachine.STATE_REFUND,
                OrderStateMachine.EVENT_REFUND_SUCCESS,
                OrderStateMachine.STATE_REFUND));

        Assert.assertFalse(OrderStateMachine.canTransit(
                OrderStateMachine.BIZ_SECKILL_ORDER,
                OrderStateMachine.STATE_REFUND_REJECTED,
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

    @Test
    public void afterSaleTransitionEntityShouldBuildAuditableFlowNo() {
        OrderStateTransitionEntity applyEntity = OrderStateTransitionEntity.seckillRefundApplied(
                "trade-2001", "order-2001", OrderStateMachine.STATE_FULFILLED,
                "user-2001", "trace-2001", "fulfilled refund apply");

        Assert.assertEquals(OrderStateMachine.STATE_FULFILLED, applyEntity.getFromStatus());
        Assert.assertEquals(OrderStateMachine.STATE_REFUNDING, applyEntity.getToStatus());
        Assert.assertEquals(OrderStateMachine.EVENT_REFUND_APPLY, applyEntity.getEvent());
        Assert.assertEquals("SECKILL_ORDER:trade-2001:order-2001:REFUND_APPLY:REFUNDING", applyEntity.flowNo());

        OrderStateTransitionEntity partialEntity = OrderStateTransitionEntity.groupBuyPartialRefunded(
                "trade-2002", "order-2002", "user-2002", "trace-2002", "partial refund success");

        Assert.assertEquals(OrderStateMachine.BIZ_GROUP_BUY_ORDER_LIST, partialEntity.getBizType());
        Assert.assertEquals(OrderStateMachine.STATE_REFUNDING, partialEntity.getFromStatus());
        Assert.assertEquals(OrderStateMachine.STATE_PARTIAL_REFUND, partialEntity.getToStatus());
        Assert.assertEquals(OrderStateMachine.EVENT_REFUND_PARTIAL_SUCCESS, partialEntity.getEvent());
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
