package cn.bugstack.test.domain.trade;

import cn.bugstack.domain.activity.adapter.port.ICrowdTagPort;
import cn.bugstack.domain.activity.model.entity.MarketProductEntity;
import cn.bugstack.domain.activity.model.entity.TrialBalanceEntity;
import cn.bugstack.domain.activity.model.valobj.GroupBuyActivityDiscountVO;
import cn.bugstack.domain.activity.model.valobj.SkuVO;
import cn.bugstack.domain.activity.service.trial.factory.DefaultActivityStrategyFactory;
import cn.bugstack.domain.activity.service.trial.node.EndNode;
import cn.bugstack.domain.activity.service.trial.node.TagNode;
import cn.bugstack.domain.trade.adapter.port.IGroupBuyOrderPort;
import cn.bugstack.domain.trade.adapter.port.IGroupBuyQueryPort;
import cn.bugstack.domain.trade.adapter.port.IGroupBuyTeamStockPort;
import cn.bugstack.domain.trade.adapter.port.ITradeLockRequestPort;
import cn.bugstack.domain.trade.model.aggregate.GroupBuyOrderAggregate;
import cn.bugstack.domain.trade.model.entity.GroupBuyActivityEntity;
import cn.bugstack.domain.trade.model.entity.GroupBuyTeamEntity;
import cn.bugstack.domain.trade.model.entity.MarketPayOrderEntity;
import cn.bugstack.domain.trade.model.entity.PayActivityEntity;
import cn.bugstack.domain.trade.model.entity.PayDiscountEntity;
import cn.bugstack.domain.trade.model.entity.TradeLockRuleCommandEntity;
import cn.bugstack.domain.trade.model.entity.TradeLockRuleFilterBackEntity;
import cn.bugstack.domain.trade.model.entity.UserEntity;
import cn.bugstack.domain.trade.model.valobj.GroupBuyProgressVO;
import cn.bugstack.domain.trade.model.valobj.TradeOrderStatusEnumVO;
import cn.bugstack.domain.trade.service.lock.TradeLockOrderService;
import cn.bugstack.domain.trade.service.lock.factory.TradeLockRuleFilterFactory;
import cn.bugstack.domain.trade.service.lock.filter.ActivityUsabilityRuleFilter;
import cn.bugstack.domain.trade.service.lock.filter.TeamStockOccupyRuleFilter;
import cn.bugstack.domain.trade.service.lock.filter.UserTakeLimitRuleFilter;
import cn.bugstack.types.enums.ActivityStatusEnumVO;
import cn.bugstack.types.enums.GroupBuyOrderEnumVO;
import cn.bugstack.types.enums.ResponseCode;
import cn.bugstack.types.exception.AppException;
import cn.bugstack.wrench.design.framework.link.model2.chain.BusinessLinkedList;
import org.junit.Assert;
import org.junit.Test;

import java.math.BigDecimal;
import java.util.Date;

public class TradeLockOrderServiceUnitTest {

    private static final Long ACTIVITY_ID = 100001L;
    private static final String USER_ID = "user_001";
    private static final String TEAM_ID = "team_001";
    private static final String OUT_TRADE_NO = "trade_001";

    @Test
    public void duplicateLockRequestShouldReturnCachedResultWithoutCreatingOrderAgain() throws Exception {
        Fixture fixture = new Fixture();
        fixture.lockRequestPort.acquireLock = false;
        fixture.lockRequestPort.cachedResult = marketPayOrder("order_cached", TEAM_ID);

        MarketPayOrderEntity result = fixture.service.lockMarketPayOrder(user(), payActivity(TEAM_ID), payDiscount(OUT_TRADE_NO));

        Assert.assertEquals("order_cached", result.getOrderId());
        Assert.assertEquals(0, fixture.orderPort.lockCalls);
        Assert.assertEquals(0, fixture.lockRequestPort.releaseCalls);
        Assert.assertEquals(0, fixture.stockPort.occupyCalls);
    }

    @Test
    public void duplicateLockRequestWithoutCachedOrPersistedResultShouldTimeoutAsDuplicateSubmit() {
        Fixture fixture = new Fixture();
        fixture.lockRequestPort.acquireLock = false;

        assertAppException(ResponseCode.E0010, new ThrowingRunnable() {
            @Override
            public void run() throws Exception {
                fixture.service.lockMarketPayOrder(user(), payActivity(TEAM_ID), payDiscount(OUT_TRADE_NO));
            }
        });

        Assert.assertEquals(5, fixture.lockRequestPort.queryLockResultCalls);
        Assert.assertEquals(5, fixture.queryPort.queryMarketPayOrderCalls);
        Assert.assertEquals(0, fixture.orderPort.lockCalls);
        Assert.assertEquals(0, fixture.stockPort.occupyCalls);
        Assert.assertEquals(0, fixture.lockRequestPort.releaseCalls);
    }

    @Test
    public void unavailableActivityShouldRejectBeforeOrderPersist() {
        Fixture fixture = new Fixture();
        fixture.queryPort.activity = activity(ActivityStatusEnumVO.OVERDUE);

        assertAppException(ResponseCode.E0101, new ThrowingRunnable() {
            @Override
            public void run() throws Exception {
                fixture.service.lockMarketPayOrder(user(), payActivity(TEAM_ID), payDiscount(OUT_TRADE_NO));
            }
        });

        Assert.assertEquals(0, fixture.orderPort.lockCalls);
        Assert.assertEquals(0, fixture.stockPort.occupyCalls);
        Assert.assertEquals(1, fixture.lockRequestPort.releaseCalls);
    }

    @Test
    public void userTakeLimitShouldRejectBeforeTeamStockOccupy() {
        Fixture fixture = new Fixture();
        fixture.queryPort.orderCount = 3;

        assertAppException(ResponseCode.E0103, new ThrowingRunnable() {
            @Override
            public void run() throws Exception {
                fixture.service.lockMarketPayOrder(user(), payActivity(TEAM_ID), payDiscount(OUT_TRADE_NO));
            }
        });

        Assert.assertEquals(0, fixture.orderPort.lockCalls);
        Assert.assertEquals(0, fixture.stockPort.occupyCalls);
        Assert.assertEquals(1, fixture.lockRequestPort.releaseCalls);
    }

    @Test
    public void fullTeamShouldRejectBeforeRedisOccupy() {
        Fixture fixture = new Fixture();
        fixture.queryPort.team = team(3, 3, GroupBuyOrderEnumVO.PROGRESS);

        assertAppException(ResponseCode.E0107, new ThrowingRunnable() {
            @Override
            public void run() throws Exception {
                fixture.service.lockMarketPayOrder(user(), payActivity(TEAM_ID), payDiscount(OUT_TRADE_NO));
            }
        });

        Assert.assertEquals(0, fixture.orderPort.lockCalls);
        Assert.assertEquals(0, fixture.stockPort.occupyCalls);
        Assert.assertEquals(1, fixture.lockRequestPort.releaseCalls);
    }

    @Test
    public void redisOccupyFailureShouldRejectAndReleaseRequestLock() {
        Fixture fixture = new Fixture();
        fixture.stockPort.nextOccupyResult = -1;

        assertAppException(ResponseCode.E0008, new ThrowingRunnable() {
            @Override
            public void run() throws Exception {
                fixture.service.lockMarketPayOrder(user(), payActivity(TEAM_ID), payDiscount(OUT_TRADE_NO));
            }
        });

        Assert.assertEquals(1, fixture.stockPort.occupyCalls);
        Assert.assertEquals(0, fixture.orderPort.lockCalls);
        Assert.assertEquals(1, fixture.lockRequestPort.releaseCalls);
        Assert.assertEquals(expectedTeamStockKey(), fixture.stockPort.lastTeamStockKey);
    }

    @Test
    public void databaseUniqueIndexConflictShouldRollbackTeamStockOccupy() {
        Fixture fixture = new Fixture();
        fixture.orderPort.exception = new AppException(ResponseCode.INDEX_EXCEPTION);

        assertAppException(ResponseCode.INDEX_EXCEPTION, new ThrowingRunnable() {
            @Override
            public void run() throws Exception {
                fixture.service.lockMarketPayOrder(user(), payActivity(TEAM_ID), payDiscount(OUT_TRADE_NO));
            }
        });

        Assert.assertEquals(1, fixture.stockPort.occupyCalls);
        Assert.assertEquals(1, fixture.orderPort.lockCalls);
        Assert.assertEquals(1, fixture.stockPort.recoveryCalls);
        Assert.assertEquals(1, fixture.stockPort.releaseUserOccupyCalls);
        Assert.assertEquals(expectedRecoveryKey(), fixture.stockPort.lastRecoveryKey);
        Assert.assertEquals(expectedUserTeamOccupyKey(), fixture.stockPort.lastReleaseUserTeamOccupyKey);
        Assert.assertEquals(1, fixture.lockRequestPort.releaseCalls);
    }

    @Test
    public void openNewTeamShouldSkipTeamStockOccupyAndCacheLockResult() throws Exception {
        Fixture fixture = new Fixture();

        MarketPayOrderEntity result = fixture.service.lockMarketPayOrder(user(), payActivity(null), payDiscount(OUT_TRADE_NO));

        Assert.assertEquals("order_created", result.getOrderId());
        Assert.assertEquals(1, fixture.orderPort.lockCalls);
        Assert.assertEquals(0, fixture.stockPort.occupyCalls);
        Assert.assertEquals(1, fixture.lockRequestPort.cacheCalls);
        Assert.assertEquals(1, fixture.lockRequestPort.releaseCalls);
        Assert.assertEquals(Integer.valueOf(0), fixture.orderPort.lastAggregate.getUserTakeOrderCount());
    }

    @Test
    public void joinExistingTeamShouldOccupyTeamStockAndPersistAggregate() throws Exception {
        Fixture fixture = new Fixture();

        MarketPayOrderEntity result = fixture.service.lockMarketPayOrder(user(), payActivity(TEAM_ID), payDiscount(OUT_TRADE_NO));

        Assert.assertEquals("order_created", result.getOrderId());
        Assert.assertEquals(1, fixture.stockPort.occupyCalls);
        Assert.assertEquals(1, fixture.orderPort.lockCalls);
        Assert.assertEquals(expectedTeamStockKey(), fixture.stockPort.lastTeamStockKey);
        Assert.assertEquals(expectedRecoveryKey(), fixture.stockPort.lastRecoveryKey);
        Assert.assertEquals(expectedUserTeamOccupyKey(), fixture.stockPort.lastUserTeamOccupyKey);
        Assert.assertEquals(OUT_TRADE_NO, fixture.stockPort.lastOutTradeNo);
        Assert.assertEquals(Integer.valueOf(3), fixture.stockPort.lastTarget);
        Assert.assertEquals(Integer.valueOf(30), fixture.stockPort.lastValidTime);
        Assert.assertEquals(1, fixture.lockRequestPort.cacheCalls);
        Assert.assertEquals(1, fixture.lockRequestPort.releaseCalls);
    }

    @Test
    public void tagMismatchShouldDisableTrialResultBeforeLockFlow() throws Exception {
        FakeCrowdTagPort crowdTagPort = new FakeCrowdTagPort();
        TestableTagNode tagNode = new TestableTagNode(crowdTagPort, new EndNode());
        DefaultActivityStrategyFactory.DynamicContext context = DefaultActivityStrategyFactory.DynamicContext.builder()
                .groupBuyActivityDiscountVO(GroupBuyActivityDiscountVO.builder()
                        .activityId(ACTIVITY_ID)
                        .activityName("测试拼团")
                        .goodsId("sku_001")
                        .target(3)
                        .validTime(30)
                        .startTime(new Date(System.currentTimeMillis() - 60_000L))
                        .endTime(new Date(System.currentTimeMillis() + 3_600_000L))
                        .tagId("vip_only")
                        .tagScope("1,2")
                        .build())
                .skuVO(SkuVO.builder()
                        .goodsId("sku_001")
                        .goodsName("测试商品")
                        .originalPrice(new BigDecimal("100.00"))
                        .build())
                .deductionPrice(new BigDecimal("20.00"))
                .payPrice(new BigDecimal("80.00"))
                .build();

        TrialBalanceEntity result = tagNode.applyDirect(MarketProductEntity.builder()
                .activityId(ACTIVITY_ID)
                .userId(USER_ID)
                .goodsId("sku_001")
                .source("s01")
                .channel("c01")
                .build(), context);

        Assert.assertFalse(result.getIsVisible());
        Assert.assertFalse(result.getIsEnable());
        Assert.assertEquals("vip_only", crowdTagPort.lastTagId);
        Assert.assertEquals(USER_ID, crowdTagPort.lastUserId);
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

    private static UserEntity user() {
        return UserEntity.builder()
                .userId(USER_ID)
                .build();
    }

    private static PayActivityEntity payActivity(String teamId) {
        return PayActivityEntity.builder()
                .teamId(teamId)
                .activityId(ACTIVITY_ID)
                .activityName("测试拼团")
                .startTime(new Date(System.currentTimeMillis() - 60_000L))
                .endTime(new Date(System.currentTimeMillis() + 3_600_000L))
                .validTime(30)
                .targetCount(3)
                .build();
    }

    private static PayDiscountEntity payDiscount(String outTradeNo) {
        return PayDiscountEntity.builder()
                .source("s01")
                .channel("c01")
                .goodsId("sku_001")
                .goodsName("测试商品")
                .originalPrice(new BigDecimal("100.00"))
                .deductionPrice(new BigDecimal("20.00"))
                .payPrice(new BigDecimal("80.00"))
                .outTradeNo(outTradeNo)
                .build();
    }

    private static GroupBuyActivityEntity activity(ActivityStatusEnumVO status) {
        return GroupBuyActivityEntity.builder()
                .activityId(ACTIVITY_ID)
                .activityName("测试拼团")
                .takeLimitCount(3)
                .target(3)
                .validTime(30)
                .status(status)
                .startTime(new Date(System.currentTimeMillis() - 60_000L))
                .endTime(new Date(System.currentTimeMillis() + 3_600_000L))
                .build();
    }

    private static GroupBuyTeamEntity team(int lockCount, int targetCount, GroupBuyOrderEnumVO status) {
        return GroupBuyTeamEntity.builder()
                .teamId(TEAM_ID)
                .activityId(ACTIVITY_ID)
                .targetCount(targetCount)
                .completeCount(0)
                .lockCount(lockCount)
                .status(status)
                .validStartTime(new Date(System.currentTimeMillis() - 60_000L))
                .validEndTime(new Date(System.currentTimeMillis() + 3_600_000L))
                .build();
    }

    private static MarketPayOrderEntity marketPayOrder(String orderId, String teamId) {
        return MarketPayOrderEntity.builder()
                .teamId(teamId)
                .orderId(orderId)
                .originalPrice(new BigDecimal("100.00"))
                .deductionPrice(new BigDecimal("20.00"))
                .payPrice(new BigDecimal("80.00"))
                .tradeOrderStatusEnumVO(TradeOrderStatusEnumVO.CREATE)
                .build();
    }

    private static String expectedTeamStockKey() {
        return TradeLockRuleFilterFactory.generateTeamStockKey(ACTIVITY_ID, TEAM_ID);
    }

    private static String expectedRecoveryKey() {
        return TradeLockRuleFilterFactory.generateRecoveryTeamStockKey(ACTIVITY_ID, TEAM_ID);
    }

    private static String expectedUserTeamOccupyKey() {
        return TradeLockRuleFilterFactory.generateUserTeamOccupyKey(ACTIVITY_ID, TEAM_ID, USER_ID);
    }

    private interface ThrowingRunnable {
        void run() throws Exception;
    }

    private static class Fixture {
        private final FakeGroupBuyQueryPort queryPort = new FakeGroupBuyQueryPort();
        private final FakeGroupBuyOrderPort orderPort = new FakeGroupBuyOrderPort();
        private final FakeGroupBuyTeamStockPort stockPort = new FakeGroupBuyTeamStockPort();
        private final FakeTradeLockRequestPort lockRequestPort = new FakeTradeLockRequestPort();
        private final TradeLockOrderService service;

        private Fixture() {
            TradeLockRuleFilterFactory factory = new TradeLockRuleFilterFactory();
            BusinessLinkedList<TradeLockRuleCommandEntity, TradeLockRuleFilterFactory.DynamicContext, TradeLockRuleFilterBackEntity> tradeRuleFilter =
                    factory.tradeRuleFilter(
                            new ActivityUsabilityRuleFilter(queryPort),
                            new UserTakeLimitRuleFilter(queryPort),
                            new TeamStockOccupyRuleFilter(queryPort, stockPort));

            this.service = new TradeLockOrderService(queryPort, orderPort, stockPort, lockRequestPort, tradeRuleFilter);
        }
    }

    private static class FakeGroupBuyQueryPort implements IGroupBuyQueryPort {
        private MarketPayOrderEntity existingOrder;
        private int queryMarketPayOrderCalls;
        private GroupBuyActivityEntity activity = TradeLockOrderServiceUnitTest.activity(ActivityStatusEnumVO.EFFECTIVE);
        private Integer orderCount = 0;
        private GroupBuyTeamEntity team = TradeLockOrderServiceUnitTest.team(1, 3, GroupBuyOrderEnumVO.PROGRESS);

        @Override
        public MarketPayOrderEntity queryMarketPayOrderEntityByOutTradeNo(String userId, String outTradeNo) {
            queryMarketPayOrderCalls++;
            return existingOrder;
        }

        @Override
        public GroupBuyProgressVO queryGroupBuyProgress(String teamId) {
            return null;
        }

        @Override
        public GroupBuyActivityEntity queryGroupBuyActivityEntityByActivityId(Long activityId) {
            return activity;
        }

        @Override
        public Integer queryOrderCountByActivityId(Long activityId, String userId) {
            return orderCount;
        }

        @Override
        public GroupBuyTeamEntity queryGroupBuyTeamByTeamId(String teamId) {
            return team;
        }
    }

    private static class FakeGroupBuyOrderPort implements IGroupBuyOrderPort {
        private int lockCalls;
        private GroupBuyOrderAggregate lastAggregate;
        private RuntimeException exception;

        @Override
        public MarketPayOrderEntity lockMarketPayOrder(GroupBuyOrderAggregate groupBuyOrderAggregate) {
            lockCalls++;
            lastAggregate = groupBuyOrderAggregate;
            if (null != exception) {
                throw exception;
            }
            String teamId = groupBuyOrderAggregate.getPayActivityEntity().getTeamId();
            return marketPayOrder("order_created", teamId);
        }
    }

    private static class FakeGroupBuyTeamStockPort implements IGroupBuyTeamStockPort {
        private long nextOccupyResult = 1;
        private int occupyCalls;
        private int recoveryCalls;
        private int releaseUserOccupyCalls;
        private String lastTeamStockKey;
        private String lastRecoveryKey;
        private String lastUserTeamOccupyKey;
        private String lastOutTradeNo;
        private Integer lastTarget;
        private Integer lastValidTime;
        private String lastReleaseUserTeamOccupyKey;

        @Override
        public long occupyTeamStock(String teamStockKey, String recoveryTeamStockKey, String userTeamOccupyKey, String outTradeNo, Integer target, Integer validTime) {
            occupyCalls++;
            lastTeamStockKey = teamStockKey;
            lastRecoveryKey = recoveryTeamStockKey;
            lastUserTeamOccupyKey = userTeamOccupyKey;
            lastOutTradeNo = outTradeNo;
            lastTarget = target;
            lastValidTime = validTime;
            return nextOccupyResult;
        }

        @Override
        public void recoveryTeamStock(String recoveryTeamStockKey, Integer validTime) {
            recoveryCalls++;
            lastRecoveryKey = recoveryTeamStockKey;
            lastValidTime = validTime;
        }

        @Override
        public void releaseUserTeamOccupy(String userTeamOccupyKey) {
            releaseUserOccupyCalls++;
            lastReleaseUserTeamOccupyKey = userTeamOccupyKey;
        }

        @Override
        public void refund2AddRecovery(String recoveryTeamStockKey, String orderId) {
        }
    }

    private static class FakeTradeLockRequestPort implements ITradeLockRequestPort {
        private boolean acquireLock = true;
        private MarketPayOrderEntity cachedResult;
        private int queryLockResultCalls;
        private int releaseCalls;
        private int cacheCalls;

        @Override
        public MarketPayOrderEntity queryLockResult(String userId, String outTradeNo) {
            queryLockResultCalls++;
            return cachedResult;
        }

        @Override
        public boolean tryAcquireLockRequest(String userId, String outTradeNo, Integer validTime) {
            return acquireLock;
        }

        @Override
        public void releaseLockRequest(String userId, String outTradeNo) {
            releaseCalls++;
        }

        @Override
        public void cacheLockResult(String userId, String outTradeNo, MarketPayOrderEntity marketPayOrderEntity, Integer validTime) {
            cacheCalls++;
            cachedResult = marketPayOrderEntity;
        }

        @Override
        public void removeLockResult(String userId, String outTradeNo) {
            cachedResult = null;
        }
    }

    private static class TestableTagNode extends TagNode {
        private TestableTagNode(ICrowdTagPort crowdTagPort, EndNode endNode) {
            super(crowdTagPort, endNode);
        }

        private TrialBalanceEntity applyDirect(MarketProductEntity requestParameter, DefaultActivityStrategyFactory.DynamicContext dynamicContext) throws Exception {
            return doApply(requestParameter, dynamicContext);
        }
    }

    private static class FakeCrowdTagPort implements ICrowdTagPort {
        private String lastTagId;
        private String lastUserId;

        @Override
        public boolean isTagCrowdRange(String tagId, String userId) {
            lastTagId = tagId;
            lastUserId = userId;
            return false;
        }
    }
}
