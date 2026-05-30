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
    public void seckillStockReservationPortShouldDelegateKeyRoutingAndInitCache() throws Exception {
        Path workspaceRoot = findWorkspaceRoot();
        Path reservationPort = workspaceRoot.resolve("group-buy-market-master/group-buy-market-infrastructure/src/main/java/cn/bugstack/infrastructure/adapter/port/SeckillStockReservationPort.java");
        String source = new String(Files.readAllBytes(reservationPort), StandardCharsets.UTF_8);

        List<String> forbiddenSnippets = Arrays.asList(
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
                workspaceRoot.resolve("group-buy-market-master/group-buy-market-infrastructure/src/main/java/cn/bugstack/infrastructure/adapter/support/SeckillStockInitializationCache.java")
        );
        for (Path supportFile : requiredSupportFiles) {
            if (!Files.exists(supportFile)) {
                violations.add("missing support:" + supportFile.getFileName());
            }
        }

        Assert.assertTrue("Seckill stock reservation adapter must delegate key building, bucket routing and init cache: " + violations, violations.isEmpty());
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
                "private StreamMessageId parseStreamMessageId"
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
                workspaceRoot.resolve("group-buy-market-master/group-buy-market-infrastructure/src/main/java/cn/bugstack/infrastructure/event/SeckillStreamMetricsSampler.java")
        );
        for (Path supportFile : requiredSupportFiles) {
            if (!Files.exists(supportFile)) {
                violations.add("missing support:" + supportFile.getFileName());
            }
        }

        Assert.assertTrue("Seckill order create buffer must delegate stream routing, retry key and message mapping details: " + violations, violations.isEmpty());
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
                "IReconcileCaseDao",
                "IReconcileOperationLogDao",
                "IThirdPartyBillDao",
                "IMqMessageRecordDao",
                "insertPaymentFlow",
                "insertRefundFlow",
                "scanReconcileCases",
                "replayMqFailure"
        );

        List<String> violations = new ArrayList<>();
        for (String snippet : forbiddenSnippets) {
            if (source.contains(snippet)) {
                violations.add(snippet);
            }
        }

        Assert.assertTrue("Mall OrderRepository must only own order persistence and order events: " + violations, violations.isEmpty());
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
                "private void audit"
        );

        List<String> violations = new ArrayList<>();
        for (String snippet : forbiddenSnippets) {
            if (source.contains(snippet)) {
                violations.add(snippet);
            }
        }

        Path supportFile = workspaceRoot.resolve("s-pay-mall-ddd-market-master/s-pay-mall-ddd-trigger/src/main/java/cn/bugstack/trigger/support/ReconcileAdminSupport.java");
        if (!Files.exists(supportFile)) {
            violations.add("missing support:" + supportFile.getFileName());
        }

        Assert.assertTrue("Mall ReconcileCaseController must delegate admin auth, operator resolution, audit and request preview details: " + violations, violations.isEmpty());
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
                workspaceRoot.resolve("s-pay-mall-ddd-market-master/s-pay-mall-ddd-trigger/src/main/java/cn/bugstack/trigger/support/ReconcileQuerySupport.java")
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
                "new QueryOrderListResponseDTO.OrderInfo"
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
                workspaceRoot.resolve("s-pay-mall-ddd-market-master/s-pay-mall-ddd-trigger/src/main/java/cn/bugstack/trigger/support/OrderListResponseAssembler.java")
        );
        for (Path supportFile : requiredSupportFiles) {
            if (!Files.exists(supportFile)) {
                violations.add("missing support:" + supportFile.getFileName());
            }
        }

        Assert.assertTrue("Mall AliPayController must delegate payment channel SDK, callback parsing and order-list DTO mapping details: " + violations, violations.isEmpty());
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
                "private String getClientIp"
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
                workspaceRoot.resolve("group-buy-market-master/group-buy-market-trigger/src/main/java/cn/bugstack/trigger/support/SeckillResponseAssembler.java")
        );
        for (Path supportFile : requiredSupportFiles) {
            if (!Files.exists(supportFile)) {
                violations.add("missing support:" + supportFile.getFileName());
            }
        }

        Assert.assertTrue("SeckillMarketController must delegate request validation, client IP resolution and DTO mapping details: " + violations, violations.isEmpty());
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
                "TradeRefundCommandEntity.builder()"
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
                workspaceRoot.resolve("group-buy-market-master/group-buy-market-trigger/src/main/java/cn/bugstack/trigger/support/GroupBuyTradeResponseAssembler.java")
        );
        for (Path supportFile : requiredSupportFiles) {
            if (!Files.exists(supportFile)) {
                violations.add("missing support:" + supportFile.getFileName());
            }
        }

        Assert.assertTrue("MarketTradeController must delegate request validation, command assembly and response DTO mapping details: " + violations, violations.isEmpty());
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
