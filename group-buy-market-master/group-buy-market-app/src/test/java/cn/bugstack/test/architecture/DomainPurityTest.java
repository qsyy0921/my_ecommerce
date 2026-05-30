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
