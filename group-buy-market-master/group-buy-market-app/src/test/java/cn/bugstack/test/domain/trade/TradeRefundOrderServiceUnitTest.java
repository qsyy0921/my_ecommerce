package cn.bugstack.test.domain.trade;

import cn.bugstack.domain.activity.model.entity.UserGroupBuyOrderDetailEntity;
import cn.bugstack.domain.shared.adapter.port.IDomainTaskExecutor;
import cn.bugstack.domain.trade.adapter.port.IGroupBuyQueryPort;
import cn.bugstack.domain.trade.adapter.port.IGroupBuyRefundPort;
import cn.bugstack.domain.trade.adapter.port.IGroupBuyTeamStockPort;
import cn.bugstack.domain.trade.adapter.port.IGroupBuyTimeoutOrderPort;
import cn.bugstack.domain.trade.model.aggregate.GroupBuyRefundAggregate;
import cn.bugstack.domain.trade.model.entity.GroupBuyTeamEntity;
import cn.bugstack.domain.trade.model.entity.MarketPayOrderEntity;
import cn.bugstack.domain.trade.model.entity.NotifyTaskEntity;
import cn.bugstack.domain.trade.model.entity.TradeRefundBehaviorEntity;
import cn.bugstack.domain.trade.model.entity.TradeRefundBehaviorEntity.TradeRefundBehaviorEnum;
import cn.bugstack.domain.trade.model.entity.TradeRefundCommandEntity;
import cn.bugstack.domain.trade.model.valobj.GroupBuyProgressVO;
import cn.bugstack.domain.trade.model.valobj.NotifyTypeEnumVO;
import cn.bugstack.domain.trade.model.valobj.RefundTypeEnumVO;
import cn.bugstack.domain.trade.model.valobj.TaskNotifyCategoryEnumVO;
import cn.bugstack.domain.trade.model.valobj.TeamRefundSuccess;
import cn.bugstack.domain.trade.model.valobj.TradeOrderStatusEnumVO;
import cn.bugstack.domain.trade.service.ITradeTaskService;
import cn.bugstack.domain.trade.service.refund.TradeRefundOrderService;
import cn.bugstack.domain.trade.service.refund.business.IRefundOrderStrategy;
import cn.bugstack.domain.trade.service.refund.business.impl.Paid2RefundStrategy;
import cn.bugstack.domain.trade.service.refund.business.impl.PaidTeam2RefundStrategy;
import cn.bugstack.domain.trade.service.refund.business.impl.Unpaid2RefundStrategy;
import cn.bugstack.domain.trade.service.refund.factory.TradeRefundRuleFilterFactory;
import cn.bugstack.domain.trade.service.refund.filter.DataNodeFilter;
import cn.bugstack.domain.trade.service.refund.filter.RefundOrderNodeFilter;
import cn.bugstack.domain.trade.service.refund.filter.UniqueRefundNodeFilter;
import cn.bugstack.domain.trade.service.lock.factory.TradeLockRuleFilterFactory;
import cn.bugstack.types.enums.GroupBuyOrderEnumVO;
import cn.bugstack.types.enums.ResponseCode;
import cn.bugstack.types.exception.AppException;
import cn.bugstack.wrench.design.framework.link.model2.chain.BusinessLinkedList;
import org.junit.Assert;
import org.junit.Test;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TradeRefundOrderServiceUnitTest {

    private static final Long ACTIVITY_ID = 100001L;
    private static final String USER_ID = "user_001";
    private static final String TEAM_ID = "team_001";
    private static final String ORDER_ID = "order_001";
    private static final String OUT_TRADE_NO = "trade_001";

    @Test
    public void unpaidUnformedOrderShouldUseUnpaidRefundStrategy() throws Exception {
        Fixture fixture = new Fixture(TradeOrderStatusEnumVO.CREATE, GroupBuyOrderEnumVO.PROGRESS, 2);

        TradeRefundBehaviorEntity result = fixture.service.refundOrder(command());

        Assert.assertEquals(TradeRefundBehaviorEnum.SUCCESS, result.getTradeRefundBehaviorEnum());
        Assert.assertEquals(1, fixture.refundPort.unpaidCalls);
        Assert.assertEquals(0, fixture.refundPort.paidCalls);
        Assert.assertEquals(0, fixture.refundPort.paidTeamCalls);
        Assert.assertEquals(Integer.valueOf(-1), fixture.refundPort.lastAggregate.getGroupBuyProgress().getLockCount());
        Assert.assertEquals(1, fixture.taskService.execCalls);
    }

    @Test
    public void paidUnformedOrderShouldUsePaidRefundStrategy() throws Exception {
        Fixture fixture = new Fixture(TradeOrderStatusEnumVO.COMPLETE, GroupBuyOrderEnumVO.PROGRESS, 2);

        TradeRefundBehaviorEntity result = fixture.service.refundOrder(command());

        Assert.assertEquals(TradeRefundBehaviorEnum.SUCCESS, result.getTradeRefundBehaviorEnum());
        Assert.assertEquals(0, fixture.refundPort.unpaidCalls);
        Assert.assertEquals(1, fixture.refundPort.paidCalls);
        Assert.assertEquals(0, fixture.refundPort.paidTeamCalls);
        GroupBuyProgressVO progress = fixture.refundPort.lastAggregate.getGroupBuyProgress();
        Assert.assertEquals(Integer.valueOf(-1), progress.getLockCount());
        Assert.assertEquals(Integer.valueOf(-1), progress.getCompleteCount());
        Assert.assertEquals(1, fixture.taskService.execCalls);
    }

    @Test
    public void paidFormedOrderShouldUseTeamRefundStrategyAndMarkTeamCompleteFail() throws Exception {
        Fixture fixture = new Fixture(TradeOrderStatusEnumVO.COMPLETE, GroupBuyOrderEnumVO.COMPLETE, 2);

        TradeRefundBehaviorEntity result = fixture.service.refundOrder(command());

        Assert.assertEquals(TradeRefundBehaviorEnum.SUCCESS, result.getTradeRefundBehaviorEnum());
        Assert.assertEquals(0, fixture.refundPort.unpaidCalls);
        Assert.assertEquals(0, fixture.refundPort.paidCalls);
        Assert.assertEquals(1, fixture.refundPort.paidTeamCalls);
        Assert.assertEquals(GroupBuyOrderEnumVO.COMPLETE_FAIL, fixture.refundPort.lastAggregate.getGroupBuyOrderEnumVO());
        Assert.assertEquals(1, fixture.taskService.execCalls);
    }

    @Test
    public void paidFormedLastOrderShouldMarkTeamFail() throws Exception {
        Fixture fixture = new Fixture(TradeOrderStatusEnumVO.COMPLETE, GroupBuyOrderEnumVO.COMPLETE, 1);

        fixture.service.refundOrder(command());

        Assert.assertEquals(GroupBuyOrderEnumVO.FAIL, fixture.refundPort.lastAggregate.getGroupBuyOrderEnumVO());
    }

    @Test
    public void repeatedRefundShouldReturnRepeatWithoutCallingStrategy() throws Exception {
        Fixture fixture = new Fixture(TradeOrderStatusEnumVO.CLOSE, GroupBuyOrderEnumVO.PROGRESS, 2);

        TradeRefundBehaviorEntity result = fixture.service.refundOrder(command());

        Assert.assertEquals(TradeRefundBehaviorEnum.REPEAT, result.getTradeRefundBehaviorEnum());
        Assert.assertEquals(0, fixture.refundPort.unpaidCalls);
        Assert.assertEquals(0, fixture.refundPort.paidCalls);
        Assert.assertEquals(0, fixture.refundPort.paidTeamCalls);
        Assert.assertEquals(0, fixture.taskService.execCalls);
    }

    @Test
    public void unsupportedRefundStateShouldThrowBusinessException() {
        Fixture fixture = new Fixture(TradeOrderStatusEnumVO.CREATE, GroupBuyOrderEnumVO.FAIL, 0);

        assertAppException(ResponseCode.E0108, new ThrowingRunnable() {
            @Override
            public void run() throws Exception {
                fixture.service.refundOrder(command());
            }
        });

        Assert.assertEquals(0, fixture.refundPort.unpaidCalls);
        Assert.assertEquals(0, fixture.refundPort.paidCalls);
        Assert.assertEquals(0, fixture.refundPort.paidTeamCalls);
    }

    @Test
    public void reverseStockShouldRestoreOnlyUnformedRefundTypes() throws Exception {
        Fixture fixture = new Fixture(TradeOrderStatusEnumVO.COMPLETE, GroupBuyOrderEnumVO.PROGRESS, 2);
        TeamRefundSuccess unpaid = teamRefundSuccess(RefundTypeEnumVO.UNPAID_UNLOCK);
        TeamRefundSuccess paidUnformed = teamRefundSuccess(RefundTypeEnumVO.PAID_UNFORMED);
        TeamRefundSuccess paidFormed = teamRefundSuccess(RefundTypeEnumVO.PAID_FORMED);

        fixture.service.restoreTeamLockStock(unpaid);
        fixture.service.restoreTeamLockStock(paidUnformed);
        fixture.service.restoreTeamLockStock(paidFormed);

        Assert.assertEquals(2, fixture.stockPort.refundRecoveryCalls);
        Assert.assertEquals(TradeLockRuleFilterFactory.generateRecoveryTeamStockKey(ACTIVITY_ID, TEAM_ID), fixture.stockPort.lastRecoveryKey);
        Assert.assertEquals(ORDER_ID, fixture.stockPort.lastRecoveryOrderId);
    }

    private static TradeRefundCommandEntity command() {
        return TradeRefundCommandEntity.builder()
                .userId(USER_ID)
                .outTradeNo(OUT_TRADE_NO)
                .source("s01")
                .channel("c01")
                .build();
    }

    private static TeamRefundSuccess teamRefundSuccess(RefundTypeEnumVO refundType) {
        return TeamRefundSuccess.builder()
                .type(refundType.getCode())
                .userId(USER_ID)
                .activityId(ACTIVITY_ID)
                .teamId(TEAM_ID)
                .orderId(ORDER_ID)
                .outTradeNo(OUT_TRADE_NO)
                .build();
    }

    private static MarketPayOrderEntity marketPayOrder(TradeOrderStatusEnumVO status) {
        return MarketPayOrderEntity.builder()
                .teamId(TEAM_ID)
                .orderId(ORDER_ID)
                .originalPrice(new BigDecimal("100.00"))
                .deductionPrice(new BigDecimal("20.00"))
                .payPrice(new BigDecimal("80.00"))
                .tradeOrderStatusEnumVO(status)
                .build();
    }

    private static GroupBuyTeamEntity team(GroupBuyOrderEnumVO status, int completeCount) {
        return GroupBuyTeamEntity.builder()
                .teamId(TEAM_ID)
                .activityId(ACTIVITY_ID)
                .targetCount(3)
                .completeCount(completeCount)
                .lockCount(completeCount)
                .status(status)
                .build();
    }

    private static void assertAppException(ResponseCode responseCode, ThrowingRunnable runnable) {
        try {
            runnable.run();
            Assert.fail("Expected AppException " + responseCode.getCode());
        } catch (AppException e) {
            Assert.assertEquals(responseCode.getCode(), e.getCode());
        } catch (Exception e) {
            Assert.fail("Expected AppException, but got " + e.getClass().getName());
        }
    }

    private interface ThrowingRunnable {
        void run() throws Exception;
    }

    private static class Fixture {
        private final FakeGroupBuyQueryPort queryPort = new FakeGroupBuyQueryPort();
        private final FakeGroupBuyRefundPort refundPort = new FakeGroupBuyRefundPort();
        private final FakeGroupBuyTeamStockPort stockPort = new FakeGroupBuyTeamStockPort();
        private final FakeTradeTaskService taskService = new FakeTradeTaskService();
        private final TradeRefundOrderService service;

        private Fixture(TradeOrderStatusEnumVO tradeStatus, GroupBuyOrderEnumVO teamStatus, int completeCount) {
            queryPort.marketPayOrder = marketPayOrder(tradeStatus);
            queryPort.team = team(teamStatus, completeCount);

            IDomainTaskExecutor directExecutor = new IDomainTaskExecutor() {
                @Override
                public void execute(Runnable runnable) {
                    runnable.run();
                }
            };
            Map<String, IRefundOrderStrategy> strategyMap = new HashMap<>();
            strategyMap.put(RefundTypeEnumVO.UNPAID_UNLOCK.getStrategy(), new Unpaid2RefundStrategy(refundPort, stockPort, taskService, directExecutor));
            strategyMap.put(RefundTypeEnumVO.PAID_UNFORMED.getStrategy(), new Paid2RefundStrategy(refundPort, stockPort, taskService, directExecutor));
            strategyMap.put(RefundTypeEnumVO.PAID_FORMED.getStrategy(), new PaidTeam2RefundStrategy(queryPort, refundPort, stockPort, taskService, directExecutor));

            TradeRefundRuleFilterFactory factory = new TradeRefundRuleFilterFactory();
            BusinessLinkedList<TradeRefundCommandEntity, TradeRefundRuleFilterFactory.DynamicContext, TradeRefundBehaviorEntity> chain =
                    factory.tradeRefundRuleFilter(
                            new DataNodeFilter(queryPort),
                            new UniqueRefundNodeFilter(),
                            new RefundOrderNodeFilter(strategyMap));

            service = new TradeRefundOrderService(new FakeTimeoutOrderPort(), strategyMap, chain);
        }
    }

    private static class FakeGroupBuyQueryPort implements IGroupBuyQueryPort {
        private MarketPayOrderEntity marketPayOrder;
        private GroupBuyTeamEntity team;

        @Override
        public MarketPayOrderEntity queryMarketPayOrderEntityByOutTradeNo(String userId, String outTradeNo) {
            return marketPayOrder;
        }

        @Override
        public cn.bugstack.domain.trade.model.valobj.GroupBuyProgressVO queryGroupBuyProgress(String teamId) {
            return null;
        }

        @Override
        public cn.bugstack.domain.trade.model.entity.GroupBuyActivityEntity queryGroupBuyActivityEntityByActivityId(Long activityId) {
            return null;
        }

        @Override
        public Integer queryOrderCountByActivityId(Long activityId, String userId) {
            return 0;
        }

        @Override
        public GroupBuyTeamEntity queryGroupBuyTeamByTeamId(String teamId) {
            return team;
        }
    }

    private static class FakeGroupBuyRefundPort implements IGroupBuyRefundPort {
        private int unpaidCalls;
        private int paidCalls;
        private int paidTeamCalls;
        private GroupBuyRefundAggregate lastAggregate;

        @Override
        public NotifyTaskEntity unpaid2Refund(GroupBuyRefundAggregate groupBuyRefundAggregate) {
            unpaidCalls++;
            lastAggregate = groupBuyRefundAggregate;
            return notifyTask();
        }

        @Override
        public NotifyTaskEntity paid2Refund(GroupBuyRefundAggregate groupBuyRefundAggregate) {
            paidCalls++;
            lastAggregate = groupBuyRefundAggregate;
            return notifyTask();
        }

        @Override
        public NotifyTaskEntity paidTeam2Refund(GroupBuyRefundAggregate groupBuyRefundAggregate) {
            paidTeamCalls++;
            lastAggregate = groupBuyRefundAggregate;
            return notifyTask();
        }

        private NotifyTaskEntity notifyTask() {
            return NotifyTaskEntity.builder()
                    .teamId(TEAM_ID)
                    .notifyType(NotifyTypeEnumVO.MQ.getCode())
                    .notifyMQ(TaskNotifyCategoryEnumVO.TRADE_PAID2REFUND.getCode())
                    .uuid("notify_001")
                    .build();
        }
    }

    private static class FakeGroupBuyTeamStockPort implements IGroupBuyTeamStockPort {
        private int refundRecoveryCalls;
        private String lastRecoveryKey;
        private String lastRecoveryOrderId;

        @Override
        public long occupyTeamStock(String teamStockKey, String recoveryTeamStockKey, String userTeamOccupyKey, String outTradeNo, Integer target, Integer validTime) {
            return 0;
        }

        @Override
        public void recoveryTeamStock(String recoveryTeamStockKey, Integer validTime) {
        }

        @Override
        public void releaseUserTeamOccupy(String userTeamOccupyKey) {
        }

        @Override
        public void refund2AddRecovery(String recoveryTeamStockKey, String orderId) {
            refundRecoveryCalls++;
            lastRecoveryKey = recoveryTeamStockKey;
            lastRecoveryOrderId = orderId;
        }
    }

    private static class FakeTradeTaskService implements ITradeTaskService {
        private int execCalls;

        @Override
        public Map<String, Integer> execNotifyJob() {
            return Collections.emptyMap();
        }

        @Override
        public Map<String, Integer> execNotifyJob(String teamId) {
            return Collections.emptyMap();
        }

        @Override
        public Map<String, Integer> execNotifyJob(NotifyTaskEntity notifyTaskEntity) {
            execCalls++;
            return Collections.emptyMap();
        }
    }

    private static class FakeTimeoutOrderPort implements IGroupBuyTimeoutOrderPort {
        @Override
        public List<UserGroupBuyOrderDetailEntity> queryTimeoutUnpaidOrderList() {
            return Collections.emptyList();
        }
    }
}
