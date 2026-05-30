package cn.bugstack.test.architecture;

import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Architecture guard: domain modules must not depend on Spring/container annotations.
 */
public class DomainPurityTest {

    private static final List<Pattern> FORBIDDEN_PATTERNS = Arrays.asList(
            Pattern.compile("org\\.springframework"),
            Pattern.compile("javax\\.annotation\\.Resource"),
            Pattern.compile("@Service"),
            Pattern.compile("@Component"),
            Pattern.compile("@Resource"),
            Pattern.compile("@Autowired"),
            Pattern.compile("@Value"),
            Pattern.compile("ThreadPoolExecutor")
    );

    @Test
    public void domainPackagesShouldStaySpringFree() throws Exception {
        Path workspaceRoot = findWorkspaceRoot();
        List<Path> domainPaths = Arrays.asList(
                workspaceRoot.resolve("group-buy-market-master/group-buy-market-domain/src/main/java/cn/bugstack/domain"),
                workspaceRoot.resolve("s-pay-mall-ddd-market-master/s-pay-mall-ddd-domain/src/main/java/cn/bugstack/domain")
        );

        List<String> violations = new ArrayList<>();
        for (Path domainPath : domainPaths) {
            collectViolations(domainPath, violations);
        }

        Assert.assertTrue("Domain layer must not import Spring/container annotations:\n" + joinLines(violations), violations.isEmpty());
    }

    @Test
    public void genericTradeRepositoryShouldStayDeleted() throws Exception {
        Path workspaceRoot = findWorkspaceRoot();
        Path tradeRepositoryPort = workspaceRoot.resolve("group-buy-market-master/group-buy-market-domain/src/main/java/cn/bugstack/domain/trade/adapter/repository/ITradeRepository.java");
        Path tradeRepositoryAdapter = workspaceRoot.resolve("group-buy-market-master/group-buy-market-infrastructure/src/main/java/cn/bugstack/infrastructure/adapter/repository/TradeRepository.java");

        Assert.assertFalse("Generic ITradeRepository should stay deleted; use dedicated trade ports instead.", Files.exists(tradeRepositoryPort));
        Assert.assertFalse("Generic TradeRepository adapter should stay deleted; use dedicated infrastructure ports instead.", Files.exists(tradeRepositoryAdapter));
    }

    @Test
    public void genericTradePortShouldStaySplitIntoNotificationPort() throws Exception {
        Path workspaceRoot = findWorkspaceRoot();
        Path tradePort = workspaceRoot.resolve("group-buy-market-master/group-buy-market-domain/src/main/java/cn/bugstack/domain/trade/adapter/port/ITradePort.java");
        Path tradePortAdapter = workspaceRoot.resolve("group-buy-market-master/group-buy-market-infrastructure/src/main/java/cn/bugstack/infrastructure/adapter/port/TradePort.java");

        Assert.assertFalse("Generic ITradePort should stay deleted; use ITradeNotificationPort instead.", Files.exists(tradePort));
        Assert.assertFalse("Generic TradePort adapter should stay deleted; use TradeNotificationPort instead.", Files.exists(tradePortAdapter));

        List<Path> requiredFiles = Arrays.asList(
                workspaceRoot.resolve("group-buy-market-master/group-buy-market-domain/src/main/java/cn/bugstack/domain/trade/adapter/port/ITradeNotificationPort.java"),
                workspaceRoot.resolve("group-buy-market-master/group-buy-market-infrastructure/src/main/java/cn/bugstack/infrastructure/adapter/port/TradeNotificationPort.java"),
                workspaceRoot.resolve("group-buy-market-master/group-buy-market-infrastructure/src/main/java/cn/bugstack/infrastructure/adapter/support/TradeNotificationLockSupport.java"),
                workspaceRoot.resolve("group-buy-market-master/group-buy-market-infrastructure/src/main/java/cn/bugstack/infrastructure/adapter/support/TradeNotificationChannelDispatcher.java")
        );
        for (Path requiredFile : requiredFiles) {
            Assert.assertTrue("Missing trade notification split file: " + requiredFile.getFileName(), Files.exists(requiredFile));
        }

        List<String> violations = new ArrayList<>();
        Path notificationPortAdapter = workspaceRoot.resolve("group-buy-market-master/group-buy-market-infrastructure/src/main/java/cn/bugstack/infrastructure/adapter/port/TradeNotificationPort.java");
        Path taskService = workspaceRoot.resolve("group-buy-market-master/group-buy-market-domain/src/main/java/cn/bugstack/domain/trade/service/task/TradeTaskService.java");
        Path config = workspaceRoot.resolve("group-buy-market-master/group-buy-market-app/src/main/java/cn/bugstack/config/DomainServiceConfig.java");
        assertSourceDoesNotContain(notificationPortAdapter, violations, Arrays.asList(
                "IRedisService",
                "RLock",
                "GroupBuyNotifyService",
                "EventPublisher",
                "NotifyTypeEnumVO.HTTP",
                "NotifyTypeEnumVO.MQ",
                "StringUtils",
                "publisher.publish",
                "groupBuyNotifyService"
        ));
        assertSourceDoesNotContain(taskService, violations, Arrays.asList("ITradePort"));
        assertSourceDoesNotContain(config, violations, Arrays.asList("ITradePort"));

        Assert.assertTrue("Trade notification port must keep lock and channel details delegated: " + violations, violations.isEmpty());
    }

    @Test
    public void tradeNotifyTaskPortShouldStaySplitByCreateAndExecution() throws Exception {
        Path workspaceRoot = findWorkspaceRoot();
        Path oldPort = workspaceRoot.resolve("group-buy-market-master/group-buy-market-domain/src/main/java/cn/bugstack/domain/trade/adapter/port/ITradeNotifyTaskPort.java");
        Path oldAdapter = workspaceRoot.resolve("group-buy-market-master/group-buy-market-infrastructure/src/main/java/cn/bugstack/infrastructure/adapter/port/TradeNotifyTaskPort.java");

        Assert.assertFalse("Generic ITradeNotifyTaskPort should stay deleted; split notify-task create and execution ports.", Files.exists(oldPort));
        Assert.assertFalse("Generic TradeNotifyTaskPort adapter should stay deleted; split notify-task create and execution adapters.", Files.exists(oldAdapter));

        List<Path> requiredFiles = Arrays.asList(
                workspaceRoot.resolve("group-buy-market-master/group-buy-market-domain/src/main/java/cn/bugstack/domain/trade/adapter/port/ITradeNotifyTaskCreatePort.java"),
                workspaceRoot.resolve("group-buy-market-master/group-buy-market-domain/src/main/java/cn/bugstack/domain/trade/adapter/port/ITradeNotifyTaskExecutionPort.java"),
                workspaceRoot.resolve("group-buy-market-master/group-buy-market-infrastructure/src/main/java/cn/bugstack/infrastructure/adapter/port/TradeNotifyTaskCreatePort.java"),
                workspaceRoot.resolve("group-buy-market-master/group-buy-market-infrastructure/src/main/java/cn/bugstack/infrastructure/adapter/port/TradeNotifyTaskExecutionPort.java"),
                workspaceRoot.resolve("group-buy-market-master/group-buy-market-infrastructure/src/main/java/cn/bugstack/infrastructure/adapter/support/TradeNotifyTaskFactory.java"),
                workspaceRoot.resolve("group-buy-market-master/group-buy-market-infrastructure/src/main/java/cn/bugstack/infrastructure/adapter/support/TradeNotifyTaskMapper.java")
        );
        for (Path requiredFile : requiredFiles) {
            Assert.assertTrue("Missing trade notify task split file: " + requiredFile.getFileName(), Files.exists(requiredFile));
        }

        List<String> violations = new ArrayList<>();
        Path createPort = workspaceRoot.resolve("group-buy-market-master/group-buy-market-domain/src/main/java/cn/bugstack/domain/trade/adapter/port/ITradeNotifyTaskCreatePort.java");
        Path executionPort = workspaceRoot.resolve("group-buy-market-master/group-buy-market-domain/src/main/java/cn/bugstack/domain/trade/adapter/port/ITradeNotifyTaskExecutionPort.java");
        Path createAdapter = workspaceRoot.resolve("group-buy-market-master/group-buy-market-infrastructure/src/main/java/cn/bugstack/infrastructure/adapter/port/TradeNotifyTaskCreatePort.java");
        Path executionAdapter = workspaceRoot.resolve("group-buy-market-master/group-buy-market-infrastructure/src/main/java/cn/bugstack/infrastructure/adapter/port/TradeNotifyTaskExecutionPort.java");
        Path taskService = workspaceRoot.resolve("group-buy-market-master/group-buy-market-domain/src/main/java/cn/bugstack/domain/trade/service/task/TradeTaskService.java");
        Path settlementAdapter = workspaceRoot.resolve("group-buy-market-master/group-buy-market-infrastructure/src/main/java/cn/bugstack/infrastructure/adapter/port/GroupBuySettlementPort.java");
        Path refundSupport = workspaceRoot.resolve("group-buy-market-master/group-buy-market-infrastructure/src/main/java/cn/bugstack/infrastructure/adapter/support/GroupBuyRefundSupport.java");

        assertSourceDoesNotContain(createPort, violations, Arrays.asList(
                "queryUnExecutedNotifyTaskList",
                "updateNotifyTaskStatus"
        ));
        assertSourceDoesNotContain(executionPort, violations, Arrays.asList(
                "createSettlementTask",
                "createRefundTask",
                "TradeRefundOrderEntity",
                "NotifyConfigVO"
        ));
        assertSourceDoesNotContain(createAdapter, violations, Arrays.asList(
                "JSON.toJSONString",
                "new HashMap",
                "TaskNotifyCategoryEnumVO",
                "NotifyTypeEnumVO",
                "NotifyTask.builder",
                "NotifyTaskEntity.builder",
                "switch (refundTypeEnumVO)"
        ));
        assertSourceDoesNotContain(executionAdapter, violations, Arrays.asList(
                "createSettlementTask",
                "createRefundTask",
                "JSON.toJSONString",
                "TaskNotifyCategoryEnumVO"
        ));
        assertSourceDoesNotContain(taskService, violations, Arrays.asList(
                "ITradeNotifyTaskPort",
                "ITradeNotifyTaskCreatePort"
        ));
        assertSourceDoesNotContain(settlementAdapter, violations, Arrays.asList(
                "ITradeNotifyTaskPort",
                "ITradeNotifyTaskExecutionPort"
        ));
        assertSourceDoesNotContain(refundSupport, violations, Arrays.asList(
                "ITradeNotifyTaskPort",
                "ITradeNotifyTaskExecutionPort"
        ));

        Assert.assertTrue("Trade notify task ports must stay split by create and execution responsibilities: " + violations, violations.isEmpty());
    }

    @Test
    public void genericActivityRepositoryShouldStaySplitIntoSemanticPorts() throws Exception {
        Path workspaceRoot = findWorkspaceRoot();
        Path activityRepositoryPort = workspaceRoot.resolve("group-buy-market-master/group-buy-market-domain/src/main/java/cn/bugstack/domain/activity/adapter/repository/IActivityRepository.java");
        Path activityRepositoryAdapter = workspaceRoot.resolve("group-buy-market-master/group-buy-market-infrastructure/src/main/java/cn/bugstack/infrastructure/adapter/repository/ActivityRepository.java");

        Assert.assertFalse("Generic IActivityRepository should stay deleted; use activity semantic ports instead.", Files.exists(activityRepositoryPort));
        Assert.assertFalse("Generic ActivityRepository adapter should stay deleted; use dedicated infrastructure ports instead.", Files.exists(activityRepositoryAdapter));

        List<Path> requiredPorts = Arrays.asList(
                workspaceRoot.resolve("group-buy-market-master/group-buy-market-domain/src/main/java/cn/bugstack/domain/activity/adapter/port/IActivityTrialQueryPort.java"),
                workspaceRoot.resolve("group-buy-market-master/group-buy-market-domain/src/main/java/cn/bugstack/domain/activity/adapter/port/ICrowdTagPort.java"),
                workspaceRoot.resolve("group-buy-market-master/group-buy-market-domain/src/main/java/cn/bugstack/domain/activity/adapter/port/IActivitySwitchPort.java"),
                workspaceRoot.resolve("group-buy-market-master/group-buy-market-domain/src/main/java/cn/bugstack/domain/activity/adapter/port/IGroupBuyDisplayPort.java")
        );
        for (Path requiredPort : requiredPorts) {
            Assert.assertTrue("Missing activity semantic port: " + requiredPort.getFileName(), Files.exists(requiredPort));
        }

        List<Path> requiredAdapters = Arrays.asList(
                workspaceRoot.resolve("group-buy-market-master/group-buy-market-infrastructure/src/main/java/cn/bugstack/infrastructure/adapter/port/ActivityTrialQueryPort.java"),
                workspaceRoot.resolve("group-buy-market-master/group-buy-market-infrastructure/src/main/java/cn/bugstack/infrastructure/adapter/port/CrowdTagPort.java"),
                workspaceRoot.resolve("group-buy-market-master/group-buy-market-infrastructure/src/main/java/cn/bugstack/infrastructure/adapter/port/ActivitySwitchPort.java"),
                workspaceRoot.resolve("group-buy-market-master/group-buy-market-infrastructure/src/main/java/cn/bugstack/infrastructure/adapter/port/GroupBuyDisplayPort.java")
        );
        for (Path requiredAdapter : requiredAdapters) {
            Assert.assertTrue("Missing activity semantic adapter: " + requiredAdapter.getFileName(), Files.exists(requiredAdapter));
        }
    }

    @Test
    public void activitySemanticPortsShouldNotLeakOtherResponsibilities() throws Exception {
        Path workspaceRoot = findWorkspaceRoot();
        Path trialQueryPort = workspaceRoot.resolve("group-buy-market-master/group-buy-market-domain/src/main/java/cn/bugstack/domain/activity/adapter/port/IActivityTrialQueryPort.java");
        Path crowdTagPort = workspaceRoot.resolve("group-buy-market-master/group-buy-market-domain/src/main/java/cn/bugstack/domain/activity/adapter/port/ICrowdTagPort.java");
        Path switchPort = workspaceRoot.resolve("group-buy-market-master/group-buy-market-domain/src/main/java/cn/bugstack/domain/activity/adapter/port/IActivitySwitchPort.java");
        Path displayPort = workspaceRoot.resolve("group-buy-market-master/group-buy-market-domain/src/main/java/cn/bugstack/domain/activity/adapter/port/IGroupBuyDisplayPort.java");
        Path config = workspaceRoot.resolve("group-buy-market-master/group-buy-market-app/src/main/java/cn/bugstack/config/ActivityDomainConfig.java");

        List<String> violations = new ArrayList<>();
        assertSourceDoesNotContain(trialQueryPort, violations, Arrays.asList(
                "isTagCrowdRange",
                "downgradeSwitch",
                "cutRange",
                "queryInProgressUserGroupBuyOrderDetailList",
                "queryTeamStatisticByActivityId"
        ));
        assertSourceDoesNotContain(crowdTagPort, violations, Arrays.asList(
                "queryGroupBuyActivityDiscountVO",
                "querySkuByGoodsId",
                "downgradeSwitch",
                "queryInProgressUserGroupBuyOrderDetailList"
        ));
        assertSourceDoesNotContain(switchPort, violations, Arrays.asList(
                "queryGroupBuyActivityDiscountVO",
                "querySkuByGoodsId",
                "isTagCrowdRange",
                "queryInProgressUserGroupBuyOrderDetailList"
        ));
        assertSourceDoesNotContain(displayPort, violations, Arrays.asList(
                "queryGroupBuyActivityDiscountVO",
                "querySkuByGoodsId",
                "isTagCrowdRange",
                "downgradeSwitch",
                "cutRange"
        ));
        assertSourceDoesNotContain(config, violations, Arrays.asList(
                "IActivityRepository"
        ));

        Assert.assertTrue("Activity semantic ports must stay responsibility-specific: " + violations, violations.isEmpty());
    }

    @Test
    public void groupBuyQueryPortShouldNotExposeWriteOrCompensationOperations() throws Exception {
        Path workspaceRoot = findWorkspaceRoot();
        Path queryPort = workspaceRoot.resolve("group-buy-market-master/group-buy-market-domain/src/main/java/cn/bugstack/domain/trade/adapter/port/IGroupBuyQueryPort.java");
        String source = new String(Files.readAllBytes(queryPort), StandardCharsets.UTF_8);

        List<String> forbiddenMethods = Arrays.asList(
                "lockMarketPayOrder",
                "settlementMarketPayOrder",
                "unpaid2Refund",
                "paid2Refund",
                "paidTeam2Refund",
                "queryTimeoutUnpaidOrderList",
                "queryUnExecutedNotifyTaskList",
                "updateNotifyTaskStatus",
                "occupyTeamStock",
                "tryAcquireLockRequest",
                "isSCBlackIntercept"
        );

        List<String> violations = new ArrayList<>();
        for (String method : forbiddenMethods) {
            if (source.contains(method)) {
                violations.add(method);
            }
        }

        Assert.assertTrue("IGroupBuyQueryPort must only expose group-buy read-model queries: " + violations, violations.isEmpty());
    }

    @Test
    public void groupBuyOrderPortAdapterShouldStayTransactionalFacade() throws Exception {
        Path workspaceRoot = findWorkspaceRoot();
        Path orderAdapter = workspaceRoot.resolve("group-buy-market-master/group-buy-market-infrastructure/src/main/java/cn/bugstack/infrastructure/adapter/port/GroupBuyOrderPort.java");
        String source = new String(Files.readAllBytes(orderAdapter), StandardCharsets.UTF_8);

        List<String> forbiddenSnippets = Arrays.asList(
                "IGroupBuyOrderDao",
                "IGroupBuyOrderListDao",
                "IOrderStateFlowPort",
                "IGroupBuyStockFlowPort",
                "GroupBuyOrder.builder",
                "GroupBuyOrderList.builder",
                "RandomStringUtils",
                "Calendar",
                "DuplicateKeyException",
                "GroupBuyStockFlowEntity.orderLocked",
                "OrderStateTransitionEntity.groupBuyTeamOpened",
                "OrderStateTransitionEntity.groupBuyOrderLocked"
        );

        List<String> violations = new ArrayList<>();
        for (String snippet : forbiddenSnippets) {
            if (source.contains(snippet)) {
                violations.add(snippet);
            }
        }

        List<Path> requiredSupportFiles = Arrays.asList(
                workspaceRoot.resolve("group-buy-market-master/group-buy-market-infrastructure/src/main/java/cn/bugstack/infrastructure/adapter/support/GroupBuyTeamLockSupport.java"),
                workspaceRoot.resolve("group-buy-market-master/group-buy-market-infrastructure/src/main/java/cn/bugstack/infrastructure/adapter/support/GroupBuyOrderListCreateSupport.java")
        );
        for (Path supportFile : requiredSupportFiles) {
            if (!Files.exists(supportFile)) {
                violations.add("missing support:" + supportFile.getFileName());
            }
        }

        Assert.assertTrue("GroupBuyOrderPort must stay a transactional facade and delegate team/order-list write details: " + violations, violations.isEmpty());
    }

    @Test
    public void groupBuySettlementPortShouldDelegateOrderAndTeamDetails() throws Exception {
        Path workspaceRoot = findWorkspaceRoot();
        Path settlementAdapter = workspaceRoot.resolve("group-buy-market-master/group-buy-market-infrastructure/src/main/java/cn/bugstack/infrastructure/adapter/port/GroupBuySettlementPort.java");
        String source = new String(Files.readAllBytes(settlementAdapter), StandardCharsets.UTF_8);

        List<String> forbiddenSnippets = Arrays.asList(
                "IGroupBuyOrderDao",
                "IGroupBuyOrderListDao",
                "IOrderStateFlowPort",
                "ITradeNotifyTaskCreatePort",
                "ITradeLockRequestPort",
                "GroupBuyOrderList",
                "OrderStateTransitionEntity",
                "MDC",
                "ResponseCode",
                "updateOrderStatus2COMPLETE",
                "updateAddCompleteCount",
                "queryGroupBuyCompleteOrderOutTradeNoListByTeamId",
                "removeLockResult"
        );

        List<String> violations = new ArrayList<>();
        for (String snippet : forbiddenSnippets) {
            if (source.contains(snippet)) {
                violations.add(snippet);
            }
        }

        List<Path> requiredSupportFiles = Arrays.asList(
                workspaceRoot.resolve("group-buy-market-master/group-buy-market-infrastructure/src/main/java/cn/bugstack/infrastructure/adapter/support/GroupBuyOrderPaidSupport.java"),
                workspaceRoot.resolve("group-buy-market-master/group-buy-market-infrastructure/src/main/java/cn/bugstack/infrastructure/adapter/support/GroupBuyTeamFormationSupport.java")
        );
        for (Path supportFile : requiredSupportFiles) {
            if (!Files.exists(supportFile)) {
                violations.add("missing support:" + supportFile.getFileName());
            }
        }

        Assert.assertTrue("GroupBuySettlementPort must delegate order-paid and team-formation details: " + violations, violations.isEmpty());
    }

    @Test
    public void groupBuyRefundPortAdapterShouldStayFacadeOnly() throws Exception {
        Path workspaceRoot = findWorkspaceRoot();
        Path refundAdapter = workspaceRoot.resolve("group-buy-market-master/group-buy-market-infrastructure/src/main/java/cn/bugstack/infrastructure/adapter/port/GroupBuyRefundPort.java");
        String source = new String(Files.readAllBytes(refundAdapter), StandardCharsets.UTF_8);

        List<String> forbiddenSnippets = Arrays.asList(
                "IGroupBuyOrderDao",
                "IGroupBuyOrderListDao",
                "IOrderStateFlowPort",
                "ITradeNotifyTaskPort",
                "IGroupBuyStockFlowPort",
                "ITradeLockRequestPort",
                "@Transactional",
                "GroupBuyOrderList",
                "GroupBuyOrderEnumVO"
        );

        List<String> violations = new ArrayList<>();
        for (String snippet : forbiddenSnippets) {
            if (source.contains(snippet)) {
                violations.add(snippet);
            }
        }

        List<Path> requiredProcessors = Arrays.asList(
                workspaceRoot.resolve("group-buy-market-master/group-buy-market-infrastructure/src/main/java/cn/bugstack/infrastructure/adapter/support/GroupBuyUnpaidRefundProcessor.java"),
                workspaceRoot.resolve("group-buy-market-master/group-buy-market-infrastructure/src/main/java/cn/bugstack/infrastructure/adapter/support/GroupBuyPaidUnformedRefundProcessor.java"),
                workspaceRoot.resolve("group-buy-market-master/group-buy-market-infrastructure/src/main/java/cn/bugstack/infrastructure/adapter/support/GroupBuyPaidFormedRefundProcessor.java")
        );
        for (Path processor : requiredProcessors) {
            if (!Files.exists(processor)) {
                violations.add("missing processor:" + processor.getFileName());
            }
        }

        Assert.assertTrue("GroupBuyRefundPort adapter must stay a facade and delegate scenario details to processors: " + violations, violations.isEmpty());
    }

    @Test
    public void genericSeckillRepositoryShouldStayDeleted() throws Exception {
        Path workspaceRoot = findWorkspaceRoot();
        Path seckillRepositoryPort = workspaceRoot.resolve("group-buy-market-master/group-buy-market-domain/src/main/java/cn/bugstack/domain/seckill/adapter/repository/ISeckillRepository.java");
        Path seckillRepositoryAdapter = workspaceRoot.resolve("group-buy-market-master/group-buy-market-infrastructure/src/main/java/cn/bugstack/infrastructure/adapter/repository/SeckillRepository.java");

        Assert.assertFalse("Generic ISeckillRepository should stay deleted; use dedicated seckill ports instead.", Files.exists(seckillRepositoryPort));
        Assert.assertFalse("Generic SeckillRepository adapter should stay deleted; use dedicated infrastructure ports instead.", Files.exists(seckillRepositoryAdapter));
    }

    @Test
    public void seckillQueryPortShouldNotExposeStockLockOrCommandOperations() throws Exception {
        Path workspaceRoot = findWorkspaceRoot();
        Path queryPort = workspaceRoot.resolve("group-buy-market-master/group-buy-market-domain/src/main/java/cn/bugstack/domain/seckill/adapter/port/ISeckillQueryPort.java");
        String source = new String(Files.readAllBytes(queryPort), StandardCharsets.UTF_8);

        List<String> forbiddenMethods = Arrays.asList(
                "queryAvailableStock",
                "lockSeckillOrder",
                "createSeckillOrder",
                "createSeckillOrders",
                "settlementSeckillOrder",
                "refundSeckillOrder",
                "syncSeckillActivityStock",
                "releaseTimeoutUnpaidOrders",
                "prewarmUpcomingActivities"
        );

        List<String> violations = new ArrayList<>();
        for (String method : forbiddenMethods) {
            if (source.contains(method)) {
                violations.add(method);
            }
        }

        Assert.assertTrue("ISeckillQueryPort must only expose seckill read-model queries: " + violations, violations.isEmpty());
    }

    @Test
    public void seckillQueryAdapterShouldDelegateCacheMappingAndShardDetails() throws Exception {
        Path workspaceRoot = findWorkspaceRoot();
        Path queryAdapter = workspaceRoot.resolve("group-buy-market-master/group-buy-market-infrastructure/src/main/java/cn/bugstack/infrastructure/adapter/port/SeckillQueryPort.java");
        String source = new String(Files.readAllBytes(queryAdapter), StandardCharsets.UTF_8);

        List<String> forbiddenSnippets = Arrays.asList(
                "@Value",
                "ISeckillActivityDao",
                "ISeckillOrderDao",
                "ISkuDao",
                "ISeckillResultCachePort",
                "SeckillOrderShardRouter",
                "ConcurrentHashMap",
                "ActivityCacheEntry",
                "activityCacheKey",
                "querySeckillOrderByOutTradeNoFromTable",
                "SeckillActivity.builder",
                "Sku",
                "cache(existsOrder",
                "RESULT_NOT_FOUND"
        );

        List<String> violations = new ArrayList<>();
        for (String snippet : forbiddenSnippets) {
            if (source.contains(snippet)) {
                violations.add(snippet);
            }
        }

        List<Path> requiredSupportFiles = Arrays.asList(
                workspaceRoot.resolve("group-buy-market-master/group-buy-market-infrastructure/src/main/java/cn/bugstack/infrastructure/adapter/support/SeckillActivityQuerySupport.java"),
                workspaceRoot.resolve("group-buy-market-master/group-buy-market-infrastructure/src/main/java/cn/bugstack/infrastructure/adapter/support/SeckillResultQuerySupport.java"),
                workspaceRoot.resolve("group-buy-market-master/group-buy-market-infrastructure/src/main/java/cn/bugstack/infrastructure/adapter/support/SeckillOrderTableGateway.java")
        );
        for (Path supportFile : requiredSupportFiles) {
            if (!Files.exists(supportFile)) {
                violations.add("missing support:" + supportFile.getFileName());
            }
        }

        Assert.assertTrue("SeckillQueryPort adapter must delegate cache, mapping, result backfill and shard details: " + violations, violations.isEmpty());
    }

    @Test
    public void seckillMaintenancePortShouldDelegateScenarioDetails() throws Exception {
        Path workspaceRoot = findWorkspaceRoot();
        Path maintenancePort = workspaceRoot.resolve("group-buy-market-master/group-buy-market-infrastructure/src/main/java/cn/bugstack/infrastructure/adapter/port/SeckillMaintenancePort.java");
        String source = new String(Files.readAllBytes(maintenancePort), StandardCharsets.UTF_8);

        List<String> forbiddenSnippets = Arrays.asList(
                "ISeckillActivityDao",
                "ISeckillOrderDao",
                "IOrderStateFlowPort",
                "ISeckillStockFlowPort",
                "ISeckillStockReservationPort",
                "ISeckillQueryPort",
                "ISeckillStockAvailabilityPort",
                "SeckillOrderShardRouter",
                "SeckillSoldOutCache",
                "countActiveOrdersFromTable",
                "queryTimeoutUnpaidOrdersFromTable",
                "closeTimeoutUnpaidOrder",
                "SeckillStockFlowEntity.rollback",
                "OrderStateTransitionEntity.seckillTimeoutClosed",
                "queryPrewarmActivities",
                "for (int shardIndex"
        );

        List<String> violations = new ArrayList<>();
        for (String snippet : forbiddenSnippets) {
            if (source.contains(snippet)) {
                violations.add(snippet);
            }
        }

        List<Path> requiredSupportFiles = Arrays.asList(
                workspaceRoot.resolve("group-buy-market-master/group-buy-market-infrastructure/src/main/java/cn/bugstack/infrastructure/adapter/support/SeckillActivityStockSyncSupport.java"),
                workspaceRoot.resolve("group-buy-market-master/group-buy-market-infrastructure/src/main/java/cn/bugstack/infrastructure/adapter/support/SeckillTimeoutUnpaidReleaseSupport.java"),
                workspaceRoot.resolve("group-buy-market-master/group-buy-market-infrastructure/src/main/java/cn/bugstack/infrastructure/adapter/support/SeckillActivityPrewarmSupport.java")
        );
        for (Path supportFile : requiredSupportFiles) {
            if (!Files.exists(supportFile)) {
                violations.add("missing support:" + supportFile.getFileName());
            }
        }

        Assert.assertTrue("SeckillMaintenancePort must stay a facade and delegate maintenance scenario details: " + violations, violations.isEmpty());
    }

    @Test
    public void seckillOrderCommandPortShouldStaySplitByLifecycle() throws Exception {
        Path workspaceRoot = findWorkspaceRoot();
        Path commandPort = workspaceRoot.resolve("group-buy-market-master/group-buy-market-domain/src/main/java/cn/bugstack/domain/seckill/adapter/port/ISeckillOrderCommandPort.java");
        Path commandAdapter = workspaceRoot.resolve("group-buy-market-master/group-buy-market-infrastructure/src/main/java/cn/bugstack/infrastructure/adapter/port/SeckillOrderCommandPort.java");
        Assert.assertFalse("Generic ISeckillOrderCommandPort should stay deleted; use create, settlement and refund ports instead.", Files.exists(commandPort));
        Assert.assertFalse("Generic SeckillOrderCommandPort adapter should stay deleted; use create, settlement and refund adapters instead.", Files.exists(commandAdapter));

        Path createPort = workspaceRoot.resolve("group-buy-market-master/group-buy-market-domain/src/main/java/cn/bugstack/domain/seckill/adapter/port/ISeckillOrderCreatePort.java");
        Path settlementPort = workspaceRoot.resolve("group-buy-market-master/group-buy-market-domain/src/main/java/cn/bugstack/domain/seckill/adapter/port/ISeckillSettlementPort.java");
        Path refundPort = workspaceRoot.resolve("group-buy-market-master/group-buy-market-domain/src/main/java/cn/bugstack/domain/seckill/adapter/port/ISeckillRefundPort.java");

        String createSource = new String(Files.readAllBytes(createPort), StandardCharsets.UTF_8);
        String settlementSource = new String(Files.readAllBytes(settlementPort), StandardCharsets.UTF_8);
        String refundSource = new String(Files.readAllBytes(refundPort), StandardCharsets.UTF_8);

        List<String> violations = new ArrayList<>();
        if (createSource.contains("settlementSeckillOrder") || createSource.contains("refundSeckillOrder")) {
            violations.add("ISeckillOrderCreatePort exposes settlement/refund");
        }
        if (settlementSource.contains("createSeckillOrder") || settlementSource.contains("refundSeckillOrder")) {
            violations.add("ISeckillSettlementPort exposes create/refund");
        }
        if (refundSource.contains("createSeckillOrder") || refundSource.contains("settlementSeckillOrder")) {
            violations.add("ISeckillRefundPort exposes create/settlement");
        }

        Assert.assertTrue("Seckill order lifecycle ports must stay split by command responsibility: " + violations, violations.isEmpty());
    }

    @Test
    public void seckillOrderCreatePortShouldDelegateSingleAndBatchDetails() throws Exception {
        Path workspaceRoot = findWorkspaceRoot();
        Path createAdapter = workspaceRoot.resolve("group-buy-market-master/group-buy-market-infrastructure/src/main/java/cn/bugstack/infrastructure/adapter/port/SeckillOrderCreatePort.java");
        String source = new String(Files.readAllBytes(createAdapter), StandardCharsets.UTF_8);

        List<String> forbiddenSnippets = Arrays.asList(
                "IOrderStateFlowPort",
                "ISeckillStockFlowPort",
                "ISeckillResultCachePort",
                "SeckillOrderTableGateway",
                "SeckillStockReleaseSupport",
                "SeckillStreamMetrics",
                "SeckillFaultInjector",
                "DuplicateKeyException",
                "SeckillStockFlowEntity",
                "OrderStateTransitionEntity",
                "insertIgnoreBatch",
                "recordBatchInsert",
                "rollbackReservation",
                "new ArrayList"
        );

        List<String> violations = new ArrayList<>();
        for (String snippet : forbiddenSnippets) {
            if (source.contains(snippet)) {
                violations.add(snippet);
            }
        }

        List<Path> requiredSupportFiles = Arrays.asList(
                workspaceRoot.resolve("group-buy-market-master/group-buy-market-infrastructure/src/main/java/cn/bugstack/infrastructure/adapter/support/SeckillSingleOrderCreateSupport.java"),
                workspaceRoot.resolve("group-buy-market-master/group-buy-market-infrastructure/src/main/java/cn/bugstack/infrastructure/adapter/support/SeckillBatchOrderCreateSupport.java")
        );
        for (Path supportFile : requiredSupportFiles) {
            if (!Files.exists(supportFile)) {
                violations.add("missing support:" + supportFile.getFileName());
            }
        }

        Assert.assertTrue("SeckillOrderCreatePort must stay a transactional facade and delegate single/batch creation details: " + violations, violations.isEmpty());
    }

    @Test
    public void seckillRefundPortShouldDelegateStateSpecificDetails() throws Exception {
        Path workspaceRoot = findWorkspaceRoot();
        Path refundAdapter = workspaceRoot.resolve("group-buy-market-master/group-buy-market-infrastructure/src/main/java/cn/bugstack/infrastructure/adapter/port/SeckillRefundPort.java");
        String source = new String(Files.readAllBytes(refundAdapter), StandardCharsets.UTF_8);

        List<String> forbiddenSnippets = Arrays.asList(
                "IOrderStateFlowPort",
                "SeckillStockReleaseSupport",
                "SeckillStockFlowEntity",
                "OrderStateTransitionEntity",
                "MDC",
                "closeUnpaid",
                "refundPaid",
                "releaseByOrder"
        );

        List<String> violations = new ArrayList<>();
        for (String snippet : forbiddenSnippets) {
            if (source.contains(snippet)) {
                violations.add(snippet);
            }
        }

        List<Path> requiredSupportFiles = Arrays.asList(
                workspaceRoot.resolve("group-buy-market-master/group-buy-market-infrastructure/src/main/java/cn/bugstack/infrastructure/adapter/support/SeckillUnpaidCancelSupport.java"),
                workspaceRoot.resolve("group-buy-market-master/group-buy-market-infrastructure/src/main/java/cn/bugstack/infrastructure/adapter/support/SeckillPaidRefundSupport.java")
        );
        for (Path supportFile : requiredSupportFiles) {
            if (!Files.exists(supportFile)) {
                violations.add("missing support:" + supportFile.getFileName());
            }
        }

        Assert.assertTrue("SeckillRefundPort must delegate state-specific refund details: " + violations, violations.isEmpty());
    }

    @Test
    public void seckillLockAndAvailabilityAdaptersShouldNotContainOrderLifecycleCommands() throws Exception {
        Path workspaceRoot = findWorkspaceRoot();
        List<Path> adapterPaths = Arrays.asList(
                workspaceRoot.resolve("group-buy-market-master/group-buy-market-infrastructure/src/main/java/cn/bugstack/infrastructure/adapter/port/SeckillOrderLockPort.java"),
                workspaceRoot.resolve("group-buy-market-master/group-buy-market-infrastructure/src/main/java/cn/bugstack/infrastructure/adapter/port/SeckillStockAvailabilityPort.java")
        );

        List<String> forbiddenSnippets = Arrays.asList(
                "IRedisService",
                "redisService",
                "ISeckillStockFlowDao",
                "SeckillStockFlow.builder",
                "SECKILL_RESULT_KEY",
                "SECKILL_STOCK_KEY",
                "SECKILL_USER_LOCK_KEY",
                "reserveSeckillQualification",
                "redisService.getValue(resultKey",
                "redisService.setValue(resultKey",
                "orderShardCount",
                "orderTablePrefix",
                "private String orderTableName",
                "private boolean useOrderSharding",
                "private String stockBucketKey",
                "private String userLockKey",
                "private int bucketOf",
                "insertIgnoreBatch",
                "insertIgnoreShardBatch",
                "paySuccessOrder",
                "refundPaidOrder",
                "closeUnpaidOrder",
                "SeckillOrderStatusEnumVO",
                "DuplicateKeyException",
                "SeckillFaultInjector",
                "SeckillStreamMetrics"
        );

        List<String> violations = new ArrayList<>();
        for (Path adapterPath : adapterPaths) {
            String source = new String(Files.readAllBytes(adapterPath), StandardCharsets.UTF_8);
            for (String snippet : forbiddenSnippets) {
                if (source.contains(snippet)) {
                    violations.add(adapterPath.getFileName() + ":" + snippet);
                }
            }
        }

        Assert.assertTrue("Seckill lock and stock availability adapters must not own order lifecycle command details: " + violations, violations.isEmpty());
    }

    @Test
    public void seckillStockReservationPortShouldDelegateRedisAndBucketDetails() throws Exception {
        Path workspaceRoot = findWorkspaceRoot();
        Path reservationPort = workspaceRoot.resolve("group-buy-market-master/group-buy-market-infrastructure/src/main/java/cn/bugstack/infrastructure/adapter/port/SeckillStockReservationPort.java");
        String source = new String(Files.readAllBytes(reservationPort), StandardCharsets.UTF_8);

        List<String> forbiddenSnippets = Arrays.asList(
                "IRedisService",
                "ISeckillResultCachePort",
                "SeckillFaultInjector",
                "SeckillStockKeyBuilder",
                "SeckillStockBucketRouter",
                "SeckillStockInitializationCache",
                "JSON.toJSONString",
                "RLock",
                "reserveSeckillQualification",
                "setAtomicLong",
                "getAtomicLong",
                "redisService.incr",
                "redisService.remove",
                "for (int i = 0",
                "SECKILL_STOCK_KEY",
                "SECKILL_USER_LOCK_KEY",
                "SECKILL_STOCK_INIT_LOCK_KEY",
                "ConcurrentHashMap",
                "CRC32",
                "StandardCharsets",
                "stockBucketCount",
                "stockInitCacheTtlMillis",
                "private String stockBucketKey",
                "private String userLockKey",
                "private int bucketOf",
                "private int bucketCount",
                "private boolean isStockInitializedRecently",
                "private void markStockInitialized"
        );

        List<String> violations = new ArrayList<>();
        for (String snippet : forbiddenSnippets) {
            if (source.contains(snippet)) {
                violations.add(snippet);
            }
        }

        List<Path> requiredSupportFiles = Arrays.asList(
                workspaceRoot.resolve("group-buy-market-master/group-buy-market-infrastructure/src/main/java/cn/bugstack/infrastructure/adapter/support/SeckillStockKeyBuilder.java"),
                workspaceRoot.resolve("group-buy-market-master/group-buy-market-infrastructure/src/main/java/cn/bugstack/infrastructure/adapter/support/SeckillStockBucketRouter.java"),
                workspaceRoot.resolve("group-buy-market-master/group-buy-market-infrastructure/src/main/java/cn/bugstack/infrastructure/adapter/support/SeckillStockInitializationCache.java"),
                workspaceRoot.resolve("group-buy-market-master/group-buy-market-infrastructure/src/main/java/cn/bugstack/infrastructure/adapter/support/SeckillStockBucketInventorySupport.java"),
                workspaceRoot.resolve("group-buy-market-master/group-buy-market-infrastructure/src/main/java/cn/bugstack/infrastructure/adapter/support/SeckillQualificationReservationSupport.java")
        );
        for (Path supportFile : requiredSupportFiles) {
            if (!Files.exists(supportFile)) {
                violations.add("missing support:" + supportFile.getFileName());
            }
        }

        Assert.assertTrue("Seckill stock reservation adapter must delegate Redis, Lua, bucket and init-cache details: " + violations, violations.isEmpty());
    }

    @Test
    public void seckillOrderCreateBufferShouldDelegateStreamRoutingAndMapping() throws Exception {
        Path workspaceRoot = findWorkspaceRoot();
        Path buffer = workspaceRoot.resolve("group-buy-market-master/group-buy-market-infrastructure/src/main/java/cn/bugstack/infrastructure/event/SeckillOrderCreateBuffer.java");
        String source = new String(Files.readAllBytes(buffer), StandardCharsets.UTF_8);

        List<String> forbiddenSnippets = Arrays.asList(
                "CRC32",
                "StandardCharsets",
                "StreamAddArgs",
                "TrimStrategy",
                "RScript",
                "JSON.toJSONString",
                "JSON.parseObject",
                "JSONObject",
                "STREAM_FIELD_BODY",
                "STREAM_METRICS_LUA",
                "private String streamKey",
                "private Integer streamShardCount",
                "private int shardOf",
                "private int shardCount",
                "private String retryKey",
                "private long parseLong",
                "public static class BufferMessage",
                "private SeckillManualMessageEntity toDeadMessage",
                "private StreamMessageId parseStreamMessageId",
                "implements ISeckillManualCompensationPort",
                "queryManualMessages",
                "replayManualMessages",
                "manualStreamKey",
                "deadStreamKey",
                "IRedisService",
                "RedissonClient",
                "RStream",
                "AutoClaimResult",
                "StreamReadGroupArgs",
                "StreamCreateGroupArgs",
                "StringCodec",
                "RedisException",
                "BlockingQueue",
                "ArrayBlockingQueue",
                "ConcurrentHashMap",
                "AtomicInteger",
                "SeckillPendingRetryPolicy",
                "claimPending",
                "readNeverDelivered",
                "createGroupIfAbsent",
                "nextShardCursor",
                "pendingMaxRetry",
                "pendingIdleMillis"
        );

        List<String> violations = new ArrayList<>();
        for (String snippet : forbiddenSnippets) {
            if (source.contains(snippet)) {
                violations.add(snippet);
            }
        }

        List<Path> requiredSupportFiles = Arrays.asList(
                workspaceRoot.resolve("group-buy-market-master/group-buy-market-infrastructure/src/main/java/cn/bugstack/infrastructure/event/SeckillOrderBufferMessage.java"),
                workspaceRoot.resolve("group-buy-market-master/group-buy-market-infrastructure/src/main/java/cn/bugstack/infrastructure/event/SeckillStreamShardRouter.java"),
                workspaceRoot.resolve("group-buy-market-master/group-buy-market-infrastructure/src/main/java/cn/bugstack/infrastructure/event/SeckillStreamMessageMapper.java"),
                workspaceRoot.resolve("group-buy-market-master/group-buy-market-infrastructure/src/main/java/cn/bugstack/infrastructure/event/SeckillStreamMetricsSampler.java"),
                workspaceRoot.resolve("group-buy-market-master/group-buy-market-infrastructure/src/main/java/cn/bugstack/infrastructure/event/SeckillManualCompensationStream.java"),
                workspaceRoot.resolve("group-buy-market-master/group-buy-market-infrastructure/src/main/java/cn/bugstack/infrastructure/adapter/port/SeckillManualCompensationPort.java"),
                workspaceRoot.resolve("group-buy-market-master/group-buy-market-infrastructure/src/main/java/cn/bugstack/infrastructure/event/SeckillLocalOrderCreateBuffer.java"),
                workspaceRoot.resolve("group-buy-market-master/group-buy-market-infrastructure/src/main/java/cn/bugstack/infrastructure/event/SeckillRedisQueueOrderCreateBuffer.java"),
                workspaceRoot.resolve("group-buy-market-master/group-buy-market-infrastructure/src/main/java/cn/bugstack/infrastructure/event/SeckillRedisStreamOrderCreateBuffer.java")
        );
        for (Path supportFile : requiredSupportFiles) {
            if (!Files.exists(supportFile)) {
                violations.add("missing support:" + supportFile.getFileName());
            }
        }

        Assert.assertTrue("Seckill order create buffer must delegate stream routing, retry key and message mapping details: " + violations, violations.isEmpty());
    }

    @Test
    public void seckillRedisStreamBufferShouldDelegateLifecycleDetails() throws Exception {
        Path workspaceRoot = findWorkspaceRoot();
        Path buffer = workspaceRoot.resolve("group-buy-market-master/group-buy-market-infrastructure/src/main/java/cn/bugstack/infrastructure/event/SeckillRedisStreamOrderCreateBuffer.java");
        String source = new String(Files.readAllBytes(buffer), StandardCharsets.UTF_8);

        List<String> forbiddenSnippets = Arrays.asList(
                "RedissonClient",
                "RStream",
                "AutoClaimResult",
                "StreamReadGroupArgs",
                "StreamCreateGroupArgs",
                "RedisException",
                "StringCodec",
                "ConcurrentHashMap",
                "AtomicInteger",
                "SeckillPendingRetryPolicy",
                "SeckillManualCompensationStream",
                "claimPending",
                "readNeverDelivered",
                "createGroupIfAbsent",
                "nextShardCursor",
                "removeDeadMessages",
                "removeIsolatedMessages",
                "pendingMaxRetry",
                "pendingIdleMillis"
        );

        List<String> violations = new ArrayList<>();
        for (String snippet : forbiddenSnippets) {
            if (source.contains(snippet)) {
                violations.add(snippet);
            }
        }

        List<Path> requiredSupportFiles = Arrays.asList(
                workspaceRoot.resolve("group-buy-market-master/group-buy-market-infrastructure/src/main/java/cn/bugstack/infrastructure/event/SeckillRedisStreamRegistry.java"),
                workspaceRoot.resolve("group-buy-market-master/group-buy-market-infrastructure/src/main/java/cn/bugstack/infrastructure/event/SeckillRedisStreamPublisher.java"),
                workspaceRoot.resolve("group-buy-market-master/group-buy-market-infrastructure/src/main/java/cn/bugstack/infrastructure/event/SeckillRedisStreamReader.java"),
                workspaceRoot.resolve("group-buy-market-master/group-buy-market-infrastructure/src/main/java/cn/bugstack/infrastructure/event/SeckillRedisStreamAcknowledger.java"),
                workspaceRoot.resolve("group-buy-market-master/group-buy-market-infrastructure/src/main/java/cn/bugstack/infrastructure/event/SeckillRedisStreamFailureIsolator.java")
        );
        for (Path supportFile : requiredSupportFiles) {
            if (!Files.exists(supportFile)) {
                violations.add("missing support:" + supportFile.getFileName());
            }
        }

        Assert.assertTrue("Seckill Redis Stream buffer must delegate stream registry, reader, ack and failure isolation details: " + violations, violations.isEmpty());
    }

    @Test
    public void seckillOrderLockPortShouldNotOwnMessageMiddlewareRouting() throws Exception {
        Path workspaceRoot = findWorkspaceRoot();
        Path lockPort = workspaceRoot.resolve("group-buy-market-master/group-buy-market-infrastructure/src/main/java/cn/bugstack/infrastructure/adapter/port/SeckillOrderLockPort.java");
        String source = new String(Files.readAllBytes(lockPort), StandardCharsets.UTF_8);

        List<String> forbiddenSnippets = Arrays.asList(
                "EventPublisher",
                "SeckillOrderCreateBuffer",
                "topicSeckillOrderCreate",
                "publishWithoutConfirm",
                "JSON.toJSONString",
                "useMq()",
                ".offer("
        );

        List<String> violations = new ArrayList<>();
        for (String snippet : forbiddenSnippets) {
            if (source.contains(snippet)) {
                violations.add(snippet);
            }
        }

        Assert.assertTrue("Seckill order lock adapter must delegate message middleware routing to ISeckillOrderMessagePort: " + violations, violations.isEmpty());
    }

    @Test
    public void seckillOpsControllerShouldDependOnManualCompensationPortsOnly() throws Exception {
        Path workspaceRoot = findWorkspaceRoot();
        Path controller = workspaceRoot.resolve("group-buy-market-master/group-buy-market-trigger/src/main/java/cn/bugstack/trigger/http/SeckillOpsController.java");
        String source = new String(Files.readAllBytes(controller), StandardCharsets.UTF_8);

        List<String> forbiddenSnippets = Arrays.asList(
                "cn.bugstack.infrastructure",
                "SeckillOrderCreateBuffer",
                "RStream",
                "Redisson",
                "IRedisService",
                "StreamMessageId"
        );

        List<String> violations = new ArrayList<>();
        for (String snippet : forbiddenSnippets) {
            if (source.contains(snippet)) {
                violations.add(snippet);
            }
        }

        Assert.assertTrue("Seckill ops controller must operate through manual compensation domain ports only: " + violations, violations.isEmpty());
    }

    @Test
    public void mallOrderRepositoryShouldNotOwnReconcileOrFlowDetails() throws Exception {
        Path workspaceRoot = findWorkspaceRoot();
        Path orderRepository = workspaceRoot.resolve("s-pay-mall-ddd-market-master/s-pay-mall-ddd-infrastructure/src/main/java/cn/bugstack/infrastructure/adapter/repository/OrderRepository.java");
        String source = new String(Files.readAllBytes(orderRepository), StandardCharsets.UTF_8);

        List<String> forbiddenSnippets = Arrays.asList(
                "IOrderReconcileRepository",
                "IPaymentFlowDao",
                "IRefundFlowDao",
                "PaymentFlow",
                "RefundFlow",
                "PaySuccessMessageEvent",
                "EventPublisher",
                "BaseEvent",
                "JSON.toJSONString",
                "eventPublisher.publish",
                "IReconcileCaseDao",
                "IReconcileOperationLogDao",
                "IThirdPartyBillDao",
                "IMqMessageRecordDao",
                "insertPaymentFlow",
                "insertRefundFlow",
                "scanReconcileCases",
                "replayMqFailure",
                "OrderEntity.builder",
                "PayOrder.builder",
                "new PayOrder(",
                "ProductEntity",
                "OrderStatusVO",
                "BigDecimal",
                "Collectors.toList"
        );

        List<String> violations = new ArrayList<>();
        for (String snippet : forbiddenSnippets) {
            if (source.contains(snippet)) {
                violations.add(snippet);
            }
        }

        List<Path> requiredFiles = Arrays.asList(
                workspaceRoot.resolve("s-pay-mall-ddd-market-master/s-pay-mall-ddd-domain/src/main/java/cn/bugstack/domain/order/adapter/port/IOrderPaySuccessMessagePort.java"),
                workspaceRoot.resolve("s-pay-mall-ddd-market-master/s-pay-mall-ddd-infrastructure/src/main/java/cn/bugstack/infrastructure/adapter/port/OrderPaySuccessMessagePort.java"),
                workspaceRoot.resolve("s-pay-mall-ddd-market-master/s-pay-mall-ddd-infrastructure/src/main/java/cn/bugstack/infrastructure/adapter/support/PayOrderEntityMapper.java")
        );
        for (Path requiredFile : requiredFiles) {
            if (!Files.exists(requiredFile)) {
                violations.add("missing mall order repository support file: " + requiredFile.getFileName());
            }
        }

        Assert.assertTrue("Mall OrderRepository must only own order persistence and delegate flows/events to dedicated ports: " + violations, violations.isEmpty());
    }

    @Test
    public void mallProductPortShouldStaySplitFromMarketTradePorts() throws Exception {
        Path workspaceRoot = findWorkspaceRoot();
        Path productPort = workspaceRoot.resolve("s-pay-mall-ddd-market-master/s-pay-mall-ddd-domain/src/main/java/cn/bugstack/domain/order/adapter/port/IProductPort.java");
        Assert.assertFalse("Generic IProductPort should stay deleted; split product query from market trade ports.", Files.exists(productPort));

        List<Path> requiredPorts = Arrays.asList(
                workspaceRoot.resolve("s-pay-mall-ddd-market-master/s-pay-mall-ddd-domain/src/main/java/cn/bugstack/domain/order/adapter/port/IProductQueryPort.java"),
                workspaceRoot.resolve("s-pay-mall-ddd-market-master/s-pay-mall-ddd-domain/src/main/java/cn/bugstack/domain/order/adapter/port/IMarketOrderLockPort.java"),
                workspaceRoot.resolve("s-pay-mall-ddd-market-master/s-pay-mall-ddd-domain/src/main/java/cn/bugstack/domain/order/adapter/port/IMarketSettlementPort.java"),
                workspaceRoot.resolve("s-pay-mall-ddd-market-master/s-pay-mall-ddd-domain/src/main/java/cn/bugstack/domain/order/adapter/port/IMarketRefundPort.java")
        );
        for (Path requiredPort : requiredPorts) {
            Assert.assertTrue("Missing mall product/market semantic port: " + requiredPort.getFileName(), Files.exists(requiredPort));
        }

        List<Path> requiredAdapters = Arrays.asList(
                workspaceRoot.resolve("s-pay-mall-ddd-market-master/s-pay-mall-ddd-infrastructure/src/main/java/cn/bugstack/infrastructure/adapter/port/ProductPort.java"),
                workspaceRoot.resolve("s-pay-mall-ddd-market-master/s-pay-mall-ddd-infrastructure/src/main/java/cn/bugstack/infrastructure/adapter/port/MarketOrderLockPort.java"),
                workspaceRoot.resolve("s-pay-mall-ddd-market-master/s-pay-mall-ddd-infrastructure/src/main/java/cn/bugstack/infrastructure/adapter/port/MarketSettlementPort.java"),
                workspaceRoot.resolve("s-pay-mall-ddd-market-master/s-pay-mall-ddd-infrastructure/src/main/java/cn/bugstack/infrastructure/adapter/port/MarketRefundPort.java")
        );
        for (Path requiredAdapter : requiredAdapters) {
            Assert.assertTrue("Missing mall product/market semantic adapter: " + requiredAdapter.getFileName(), Files.exists(requiredAdapter));
        }

        List<String> violations = new ArrayList<>();
        Path productAdapter = workspaceRoot.resolve("s-pay-mall-ddd-market-master/s-pay-mall-ddd-infrastructure/src/main/java/cn/bugstack/infrastructure/adapter/port/ProductPort.java");
        assertSourceDoesNotContain(productAdapter, violations, Arrays.asList(
                "lockGroupBuyMarketPayOrder",
                "lockSeckillPayOrder",
                "settlementGroupBuyMarketPayOrder",
                "settlementSeckillPayOrder",
                "refundGroupBuyMarketPayOrder",
                "refundSeckillPayOrder",
                "IGroupBuyMarketService",
                "LockMarketPayOrderRequestDTO",
                "SettlementMarketPayOrderRequestDTO",
                "RefundMarketPayOrderRequestDTO"
        ));

        Path domainConfig = workspaceRoot.resolve("s-pay-mall-ddd-market-master/s-pay-mall-ddd-app/src/main/java/cn/bugstack/config/DomainServiceConfig.java");
        assertSourceDoesNotContain(domainConfig, violations, Arrays.asList(
                "IProductPort"
        ));

        Assert.assertTrue("Mall ProductPort must stay product-query only, with market operations in dedicated ports: " + violations, violations.isEmpty());
    }

    @Test
    public void mallOrderReconcileRepositoryShouldDelegateCaseReplayCsvAndMappingDetails() throws Exception {
        Path workspaceRoot = findWorkspaceRoot();
        Path reconcileRepository = workspaceRoot.resolve("s-pay-mall-ddd-market-master/s-pay-mall-ddd-infrastructure/src/main/java/cn/bugstack/infrastructure/adapter/repository/OrderReconcileRepository.java");
        String source = new String(Files.readAllBytes(reconcileRepository), StandardCharsets.UTF_8);

        List<String> forbiddenSnippets = Arrays.asList(
                "EventPublisher",
                "publishToExchange",
                "resolveRoutingKey",
                "new BigDecimal",
                "SimpleDateFormat",
                "ParseException",
                "parseBillTime",
                "line.split",
                "ThirdPartyBill.builder",
                "OrderEntity.builder",
                "ReconcileCase.builder",
                "ReconcileCaseEntity.builder",
                "ReconcileOperationLogEntity.builder"
        );

        List<String> violations = new ArrayList<>();
        for (String snippet : forbiddenSnippets) {
            if (source.contains(snippet)) {
                violations.add(snippet);
            }
        }

        List<Path> requiredSupportFiles = Arrays.asList(
                workspaceRoot.resolve("s-pay-mall-ddd-market-master/s-pay-mall-ddd-infrastructure/src/main/java/cn/bugstack/infrastructure/adapter/support/ReconcileCaseFactory.java"),
                workspaceRoot.resolve("s-pay-mall-ddd-market-master/s-pay-mall-ddd-infrastructure/src/main/java/cn/bugstack/infrastructure/adapter/support/MqFailureReplaySupport.java"),
                workspaceRoot.resolve("s-pay-mall-ddd-market-master/s-pay-mall-ddd-infrastructure/src/main/java/cn/bugstack/infrastructure/adapter/support/ThirdPartyBillCsvParser.java"),
                workspaceRoot.resolve("s-pay-mall-ddd-market-master/s-pay-mall-ddd-infrastructure/src/main/java/cn/bugstack/infrastructure/adapter/support/OrderReconcileEntityMapper.java")
        );
        for (Path supportFile : requiredSupportFiles) {
            if (!Files.exists(supportFile)) {
                violations.add("missing support:" + supportFile.getFileName());
            }
        }

        Assert.assertTrue("Mall OrderReconcileRepository must delegate case construction, MQ replay, CSV parsing and entity mapping details: " + violations, violations.isEmpty());
    }

    @Test
    public void mallReconcileCaseShouldKeepExplicitClosedLoopOperations() throws Exception {
        Path workspaceRoot = findWorkspaceRoot();
        Path controller = workspaceRoot.resolve("s-pay-mall-ddd-market-master/s-pay-mall-ddd-trigger/src/main/java/cn/bugstack/trigger/http/ReconcileCaseController.java");
        Path service = workspaceRoot.resolve("s-pay-mall-ddd-market-master/s-pay-mall-ddd-domain/src/main/java/cn/bugstack/domain/order/service/OrderReconcileService.java");
        Path mapper = workspaceRoot.resolve("s-pay-mall-ddd-market-master/s-pay-mall-ddd-app/src/main/resources/mybatis/mapper/reconcile_case_mapper.xml");

        String controllerSource = new String(Files.readAllBytes(controller), StandardCharsets.UTF_8);
        String serviceSource = new String(Files.readAllBytes(service), StandardCharsets.UTF_8);
        String mapperSource = new String(Files.readAllBytes(mapper), StandardCharsets.UTF_8);

        List<String> requiredSnippets = Arrays.asList(
                "value = \"confirm\"",
                "value = \"ignore\"",
                "value = \"close\"",
                "value = \"remark\"",
                "value = \"operation_logs\"",
                "ReconcileCaseStatusVO",
                "queryReconcileCase(caseNo)",
                "case_status in (1, 2, 3)",
                "and case_status = 0"
        );

        List<String> violations = new ArrayList<>();
        for (String snippet : requiredSnippets) {
            if (!controllerSource.contains(snippet) && !serviceSource.contains(snippet) && !mapperSource.contains(snippet)) {
                violations.add(snippet);
            }
        }

        Assert.assertTrue("Mall reconcile case closed-loop operations must stay explicit and terminal-safe: " + violations, violations.isEmpty());
    }

    @Test
    public void mallReconcileControllerShouldDelegateAdminAuthOperatorAndAuditDetails() throws Exception {
        Path workspaceRoot = findWorkspaceRoot();
        Path controller = workspaceRoot.resolve("s-pay-mall-ddd-market-master/s-pay-mall-ddd-trigger/src/main/java/cn/bugstack/trigger/http/ReconcileCaseController.java");
        String source = new String(Files.readAllBytes(controller), StandardCharsets.UTF_8);

        List<String> forbiddenSnippets = Arrays.asList(
                "@Value",
                "adminToken",
                "recordReconcileOperation",
                "local-admin",
                "substring(0, Math.min",
                "private boolean authorized",
                "private <T> Response<T> noLogin",
                "private String resolveOperator",
                "private void audit",
                "IOrderReconcileService",
                "ReconcileAdminSupport",
                "ReconcileQuerySupport",
                "JSON.toJSONString",
                "orderReconcileService.",
                "adminSupport.",
                "querySupport.",
                "for (String caseNo",
                "handleReconcileCase",
                "replayReconcileCase",
                "importThirdPartyBillCsv"
        );

        List<String> violations = new ArrayList<>();
        for (String snippet : forbiddenSnippets) {
            if (source.contains(snippet)) {
                violations.add(snippet);
            }
        }

        List<Path> requiredSupportFiles = Arrays.asList(
                workspaceRoot.resolve("s-pay-mall-ddd-market-master/s-pay-mall-ddd-trigger/src/main/java/cn/bugstack/trigger/support/ReconcileAdminSupport.java"),
                workspaceRoot.resolve("s-pay-mall-ddd-market-master/s-pay-mall-ddd-trigger/src/main/java/cn/bugstack/trigger/support/ReconcileCaseQueryEndpointSupport.java"),
                workspaceRoot.resolve("s-pay-mall-ddd-market-master/s-pay-mall-ddd-trigger/src/main/java/cn/bugstack/trigger/support/ReconcileCaseOperationSupport.java"),
                workspaceRoot.resolve("s-pay-mall-ddd-market-master/s-pay-mall-ddd-trigger/src/main/java/cn/bugstack/trigger/support/ReconcileCaseReplaySupport.java"),
                workspaceRoot.resolve("s-pay-mall-ddd-market-master/s-pay-mall-ddd-trigger/src/main/java/cn/bugstack/trigger/support/ReconcileBillImportSupport.java"),
                workspaceRoot.resolve("s-pay-mall-ddd-market-master/s-pay-mall-ddd-trigger/src/main/java/cn/bugstack/trigger/support/ReconcileAlertWebhookSupport.java")
        );
        for (Path supportFile : requiredSupportFiles) {
            if (!Files.exists(supportFile)) {
                violations.add("missing support:" + supportFile.getFileName());
            }
        }

        Assert.assertTrue("Mall ReconcileCaseController must delegate admin auth, operator resolution, audit, request preview and reconcile usecase details: " + violations, violations.isEmpty());
    }

    @Test
    public void mallReconcileControllerShouldReturnApiDtosInsteadOfDomainEntities() throws Exception {
        Path workspaceRoot = findWorkspaceRoot();
        Path controller = workspaceRoot.resolve("s-pay-mall-ddd-market-master/s-pay-mall-ddd-trigger/src/main/java/cn/bugstack/trigger/http/ReconcileCaseController.java");
        String source = new String(Files.readAllBytes(controller), StandardCharsets.UTF_8);

        List<String> forbiddenSnippets = Arrays.asList(
                "import cn.bugstack.domain.order.model.entity.ReconcileCaseEntity",
                "import cn.bugstack.domain.order.model.entity.ReconcileOperationLogEntity",
                "Response<List<ReconcileCaseEntity>>",
                "Response<List<ReconcileOperationLogEntity>>",
                "List<ReconcileCaseEntity>",
                "List<ReconcileOperationLogEntity>"
        );

        List<String> violations = new ArrayList<>();
        for (String snippet : forbiddenSnippets) {
            if (source.contains(snippet)) {
                violations.add(snippet);
            }
        }

        List<Path> requiredFiles = Arrays.asList(
                workspaceRoot.resolve("s-pay-mall-ddd-market-master/s-pay-mall-ddd-api/src/main/java/cn/bugstack/api/dto/ReconcileCaseResponseDTO.java"),
                workspaceRoot.resolve("s-pay-mall-ddd-market-master/s-pay-mall-ddd-api/src/main/java/cn/bugstack/api/dto/ReconcileOperationLogResponseDTO.java"),
                workspaceRoot.resolve("s-pay-mall-ddd-market-master/s-pay-mall-ddd-trigger/src/main/java/cn/bugstack/trigger/support/ReconcileResponseAssembler.java"),
                workspaceRoot.resolve("s-pay-mall-ddd-market-master/s-pay-mall-ddd-trigger/src/main/java/cn/bugstack/trigger/support/ReconcileQuerySupport.java"),
                workspaceRoot.resolve("s-pay-mall-ddd-market-master/s-pay-mall-ddd-trigger/src/main/java/cn/bugstack/trigger/support/ReconcileCaseQueryEndpointSupport.java")
        );
        for (Path requiredFile : requiredFiles) {
            if (!Files.exists(requiredFile)) {
                violations.add("missing file:" + requiredFile.getFileName());
            }
        }

        Assert.assertTrue("Mall ReconcileCaseController query APIs must return API DTOs and keep domain entities out of HTTP response contracts: " + violations, violations.isEmpty());
    }

    @Test
    public void mallAliPayControllerShouldDelegatePaymentChannelAndDtoMappingDetails() throws Exception {
        Path workspaceRoot = findWorkspaceRoot();
        Path controller = workspaceRoot.resolve("s-pay-mall-ddd-market-master/s-pay-mall-ddd-trigger/src/main/java/cn/bugstack/trigger/http/AliPayController.java");
        String source = new String(Files.readAllBytes(controller), StandardCharsets.UTF_8);

        List<String> forbiddenSnippets = Arrays.asList(
                "@Value",
                "AlipayClient",
                "AlipaySignature",
                "AlipayTradeQueryModel",
                "AlipayTradeQueryRequest",
                "PaymentCallbackMetrics",
                "JSONObject",
                "SimpleDateFormat",
                "new HashMap",
                "getParameterMap",
                "Collectors.toList",
                "new QueryOrderListResponseDTO.OrderInfo",
                "IOrderService",
                "PayOrderEntity",
                "ShopCartEntity",
                "OrderEntity",
                "MarketTypeVO",
                "StructuredBusinessLogger",
                "Constants.ResponseCode",
                "JSON.toJSONString",
                "orderService.",
                "businessLogger.",
                "Response.<RefundOrderResponseDTO>builder()",
                "new RefundOrderResponseDTO()",
                "System.currentTimeMillis()"
        );

        List<String> violations = new ArrayList<>();
        for (String snippet : forbiddenSnippets) {
            if (source.contains(snippet)) {
                violations.add(snippet);
            }
        }

        List<Path> requiredSupportFiles = Arrays.asList(
                workspaceRoot.resolve("s-pay-mall-ddd-market-master/s-pay-mall-ddd-trigger/src/main/java/cn/bugstack/trigger/support/AlipayNotifySupport.java"),
                workspaceRoot.resolve("s-pay-mall-ddd-market-master/s-pay-mall-ddd-trigger/src/main/java/cn/bugstack/trigger/support/ActivePayNotifySupport.java"),
                workspaceRoot.resolve("s-pay-mall-ddd-market-master/s-pay-mall-ddd-trigger/src/main/java/cn/bugstack/trigger/support/OrderListResponseAssembler.java"),
                workspaceRoot.resolve("s-pay-mall-ddd-market-master/s-pay-mall-ddd-trigger/src/main/java/cn/bugstack/trigger/support/MallPayOrderCreateSupport.java"),
                workspaceRoot.resolve("s-pay-mall-ddd-market-master/s-pay-mall-ddd-trigger/src/main/java/cn/bugstack/trigger/support/MallGroupBuyNotifySupport.java"),
                workspaceRoot.resolve("s-pay-mall-ddd-market-master/s-pay-mall-ddd-trigger/src/main/java/cn/bugstack/trigger/support/MallOrderQuerySupport.java"),
                workspaceRoot.resolve("s-pay-mall-ddd-market-master/s-pay-mall-ddd-trigger/src/main/java/cn/bugstack/trigger/support/MallRefundOrderSupport.java")
        );
        for (Path supportFile : requiredSupportFiles) {
            if (!Files.exists(supportFile)) {
                violations.add("missing support:" + supportFile.getFileName());
            }
        }

        Assert.assertTrue("Mall AliPayController must delegate payment channel SDK, callback parsing and order-list DTO mapping details: " + violations, violations.isEmpty());
    }

    @Test
    public void mallOrderServiceShouldDelegatePaySuccessAndRefundUsecases() throws Exception {
        Path workspaceRoot = findWorkspaceRoot();
        Path orderService = workspaceRoot.resolve("s-pay-mall-ddd-market-master/s-pay-mall-ddd-domain/src/main/java/cn/bugstack/domain/order/service/OrderService.java");
        String source = new String(Files.readAllBytes(orderService), StandardCharsets.UTF_8);

        List<String> forbiddenSnippets = Arrays.asList(
                "IMarketSettlementPort",
                "IMarketRefundPort",
                "IPaymentFlowPort",
                "IRefundFlowPort",
                "IOrderPaySuccessMessagePort",
                "IDomainTaskExecutor",
                "asyncSettlement",
                "recordPaySuccess",
                "recordRefund",
                "refundGroupBuyMarketPayOrder",
                "refundSeckillPayOrder",
                "publishAll"
        );

        List<String> violations = new ArrayList<>();
        for (String snippet : forbiddenSnippets) {
            if (source.contains(snippet)) {
                violations.add(snippet);
            }
        }

        List<Path> requiredFiles = Arrays.asList(
                workspaceRoot.resolve("s-pay-mall-ddd-market-master/s-pay-mall-ddd-domain/src/main/java/cn/bugstack/domain/order/service/processor/OrderPaySuccessProcessor.java"),
                workspaceRoot.resolve("s-pay-mall-ddd-market-master/s-pay-mall-ddd-domain/src/main/java/cn/bugstack/domain/order/service/processor/OrderRefundProcessor.java")
        );
        for (Path requiredFile : requiredFiles) {
            if (!Files.exists(requiredFile)) {
                violations.add("missing processor:" + requiredFile.getFileName());
            }
        }

        Assert.assertTrue("Mall OrderService must delegate payment-success and refund usecase details to processors: " + violations, violations.isEmpty());
    }

    @Test
    public void seckillMarketControllerShouldDelegateValidationClientIpAndDtoMappingDetails() throws Exception {
        Path workspaceRoot = findWorkspaceRoot();
        Path controller = workspaceRoot.resolve("group-buy-market-master/group-buy-market-trigger/src/main/java/cn/bugstack/trigger/http/SeckillMarketController.java");
        String source = new String(Files.readAllBytes(controller), StandardCharsets.UTF_8);

        List<String> forbiddenSnippets = Arrays.asList(
                "StringUtils.isBlank",
                "getHeader(\"X-Forwarded-For\")",
                "getHeader(\"X-Real-IP\")",
                "getRemoteAddr",
                "SeckillMarketResponseDTO.builder()",
                "LockSeckillOrderResponseDTO.builder()",
                "SettlementSeckillOrderResponseDTO.builder()",
                "RefundSeckillOrderResponseDTO.builder()",
                "private LockSeckillOrderResponseDTO buildLockSeckillOrderResponse",
                "private String getClientIp",
                "ISeckillService",
                "ISeckillRateLimitPort",
                "ISeckillMetricsPort",
                "StructuredBusinessLogger",
                "SeckillRequestValidator",
                "SeckillResponseAssembler",
                "SeckillActivityEntity",
                "SeckillOrderEntity",
                "JSON.toJSONString",
                "querySeckillOrderByOutTradeNo",
                "tryAcquire",
                "recordLock",
                "businessLogger."
        );

        List<String> violations = new ArrayList<>();
        for (String snippet : forbiddenSnippets) {
            if (source.contains(snippet)) {
                violations.add(snippet);
            }
        }

        List<Path> requiredSupportFiles = Arrays.asList(
                workspaceRoot.resolve("group-buy-market-master/group-buy-market-trigger/src/main/java/cn/bugstack/trigger/support/SeckillRequestValidator.java"),
                workspaceRoot.resolve("group-buy-market-master/group-buy-market-trigger/src/main/java/cn/bugstack/trigger/support/ClientIpResolver.java"),
                workspaceRoot.resolve("group-buy-market-master/group-buy-market-trigger/src/main/java/cn/bugstack/trigger/support/SeckillResponseAssembler.java"),
                workspaceRoot.resolve("group-buy-market-master/group-buy-market-trigger/src/main/java/cn/bugstack/trigger/support/SeckillMarketConfigQuerySupport.java"),
                workspaceRoot.resolve("group-buy-market-master/group-buy-market-trigger/src/main/java/cn/bugstack/trigger/support/SeckillLockOrderSupport.java"),
                workspaceRoot.resolve("group-buy-market-master/group-buy-market-trigger/src/main/java/cn/bugstack/trigger/support/SeckillOrderResultQuerySupport.java"),
                workspaceRoot.resolve("group-buy-market-master/group-buy-market-trigger/src/main/java/cn/bugstack/trigger/support/SeckillSettlementSupport.java"),
                workspaceRoot.resolve("group-buy-market-master/group-buy-market-trigger/src/main/java/cn/bugstack/trigger/support/SeckillRefundSupport.java")
        );
        for (Path supportFile : requiredSupportFiles) {
            if (!Files.exists(supportFile)) {
                violations.add("missing support:" + supportFile.getFileName());
            }
        }

        Assert.assertTrue("SeckillMarketController must delegate request validation, usecase orchestration, client IP resolution, metrics, logs and DTO mapping details: " + violations, violations.isEmpty());
    }

    @Test
    public void groupBuyMarketTradeControllerShouldDelegateValidationCommandAndDtoMappingDetails() throws Exception {
        Path workspaceRoot = findWorkspaceRoot();
        Path controller = workspaceRoot.resolve("group-buy-market-master/group-buy-market-trigger/src/main/java/cn/bugstack/trigger/http/MarketTradeController.java");
        String source = new String(Files.readAllBytes(controller), StandardCharsets.UTF_8);

        List<String> forbiddenSnippets = Arrays.asList(
                "StringUtils.isBlank",
                "StringUtils.isNotBlank",
                "NotifyTypeEnumVO.valueOf",
                "LockMarketPayOrderResponseDTO.builder()",
                "SettlementMarketPayOrderResponseDTO.builder()",
                "RefundMarketPayOrderResponseDTO.builder()",
                "MarketProductEntity.builder()",
                "UserEntity.builder()",
                "PayActivityEntity.builder()",
                "PayDiscountEntity.builder()",
                "NotifyConfigVO.builder()",
                "TradePaySuccessEntity.builder()",
                "TradeRefundCommandEntity.builder()",
                "IIndexGroupBuyMarketService",
                "ITradeLockOrderService",
                "ITradeSettlementOrderService",
                "ITradeRefundOrderService",
                "StructuredBusinessLogger",
                "GroupBuyTradeRequestValidator",
                "GroupBuyTradeCommandAssembler",
                "GroupBuyTradeResponseAssembler",
                "TrialBalanceEntity",
                "GroupBuyActivityDiscountVO",
                "MarketPayOrderEntity",
                "TradePaySettlementEntity",
                "TradeRefundBehaviorEntity",
                "GroupBuyProgressVO",
                "NotifyTypeEnumVO",
                "JSON.toJSONString",
                "indexMarketTrial",
                "queryNoPayMarketPayOrderByOutTradeNo",
                "queryGroupBuyProgress",
                "tradeOrderService.lockMarketPayOrder",
                "tradeSettlementOrderService.settlementMarketPayOrder",
                "tradeRefundOrderService.refundOrder",
                "businessLogger."
        );

        List<String> violations = new ArrayList<>();
        for (String snippet : forbiddenSnippets) {
            if (source.contains(snippet)) {
                violations.add(snippet);
            }
        }

        List<Path> requiredSupportFiles = Arrays.asList(
                workspaceRoot.resolve("group-buy-market-master/group-buy-market-trigger/src/main/java/cn/bugstack/trigger/support/GroupBuyTradeRequestValidator.java"),
                workspaceRoot.resolve("group-buy-market-master/group-buy-market-trigger/src/main/java/cn/bugstack/trigger/support/GroupBuyTradeCommandAssembler.java"),
                workspaceRoot.resolve("group-buy-market-master/group-buy-market-trigger/src/main/java/cn/bugstack/trigger/support/GroupBuyTradeResponseAssembler.java"),
                workspaceRoot.resolve("group-buy-market-master/group-buy-market-trigger/src/main/java/cn/bugstack/trigger/support/GroupBuyLockOrderSupport.java"),
                workspaceRoot.resolve("group-buy-market-master/group-buy-market-trigger/src/main/java/cn/bugstack/trigger/support/GroupBuySettlementSupport.java"),
                workspaceRoot.resolve("group-buy-market-master/group-buy-market-trigger/src/main/java/cn/bugstack/trigger/support/GroupBuyRefundSupport.java")
        );
        for (Path supportFile : requiredSupportFiles) {
            if (!Files.exists(supportFile)) {
                violations.add("missing support:" + supportFile.getFileName());
            }
        }

        Assert.assertTrue("MarketTradeController must delegate request validation, usecase orchestration, command assembly, logging and response DTO mapping details: " + violations, violations.isEmpty());
    }

    @Test
    public void groupBuyMarketIndexControllerShouldDelegateValidationCommandAndDtoMappingDetails() throws Exception {
        Path workspaceRoot = findWorkspaceRoot();
        Path controller = workspaceRoot.resolve("group-buy-market-master/group-buy-market-trigger/src/main/java/cn/bugstack/trigger/http/MarketIndexController.java");
        String source = new String(Files.readAllBytes(controller), StandardCharsets.UTF_8);

        List<String> forbiddenSnippets = Arrays.asList(
                "StringUtils.isBlank",
                "MarketProductEntity.builder()",
                "GoodsMarketResponseDTO.Goods.builder()",
                "GoodsMarketResponseDTO.Team.builder()",
                "GoodsMarketResponseDTO.TeamStatistic.builder()",
                "GoodsMarketResponseDTO.builder()",
                "differenceDateTime2Str",
                "new ArrayList",
                "new Date()",
                "for (UserGroupBuyOrderDetailEntity"
        );

        List<String> violations = new ArrayList<>();
        for (String snippet : forbiddenSnippets) {
            if (source.contains(snippet)) {
                violations.add(snippet);
            }
        }

        List<Path> requiredSupportFiles = Arrays.asList(
                workspaceRoot.resolve("group-buy-market-master/group-buy-market-trigger/src/main/java/cn/bugstack/trigger/support/GroupBuyMarketConfigRequestValidator.java"),
                workspaceRoot.resolve("group-buy-market-master/group-buy-market-trigger/src/main/java/cn/bugstack/trigger/support/GroupBuyMarketConfigCommandAssembler.java"),
                workspaceRoot.resolve("group-buy-market-master/group-buy-market-trigger/src/main/java/cn/bugstack/trigger/support/GroupBuyMarketConfigResponseAssembler.java")
        );
        for (Path supportFile : requiredSupportFiles) {
            if (!Files.exists(supportFile)) {
                violations.add("missing support:" + supportFile.getFileName());
            }
        }

        Assert.assertTrue("MarketIndexController must delegate request validation, market command assembly and homepage DTO mapping details: " + violations, violations.isEmpty());
    }

    private static void collectViolations(Path domainPath, List<String> violations) throws IOException {
        if (!Files.isDirectory(domainPath)) {
            violations.add("missing domain path: " + domainPath);
            return;
        }

        try (Stream<Path> files = Files.walk(domainPath)) {
            files.filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".java"))
                    .forEach(path -> collectFileViolations(path, violations));
        }
    }

    private static void assertSourceDoesNotContain(Path path, List<String> violations, List<String> forbiddenSnippets) throws IOException {
        String source = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
        for (String snippet : forbiddenSnippets) {
            if (source.contains(snippet)) {
                violations.add(path.getFileName() + ":" + snippet);
            }
        }
    }

    private static void collectFileViolations(Path path, List<String> violations) {
        try {
            List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i);
                for (Pattern pattern : FORBIDDEN_PATTERNS) {
                    if (pattern.matcher(line).find()) {
                        violations.add(path + ":" + (i + 1) + ": " + line.trim());
                    }
                }
            }
        } catch (IOException e) {
            violations.add(path + ": " + e.getMessage());
        }
    }

    private static Path findWorkspaceRoot() {
        Path current = Paths.get("").toAbsolutePath();
        for (Path path = current; path != null; path = path.getParent()) {
            if (Files.isDirectory(path.resolve("group-buy-market-master/group-buy-market-domain"))
                    && Files.isDirectory(path.resolve("s-pay-mall-ddd-market-master/s-pay-mall-ddd-domain"))) {
                return path;
            }
            Path parent = path.getParent();
            if (Files.isDirectory(path.resolve("group-buy-market-domain"))
                    && null != parent
                    && Files.isDirectory(parent.resolve("s-pay-mall-ddd-market-master/s-pay-mall-ddd-domain"))) {
                return parent;
            }
        }
        throw new IllegalStateException("Cannot locate group_buy_market workspace from " + current);
    }

    private static String joinLines(List<String> lines) {
        StringBuilder builder = new StringBuilder();
        for (String line : lines) {
            builder.append(line).append(System.lineSeparator());
        }
        return builder.toString();
    }

}
