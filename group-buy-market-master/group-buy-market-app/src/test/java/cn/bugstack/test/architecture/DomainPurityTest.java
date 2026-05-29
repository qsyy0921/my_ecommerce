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
    public void tradeRepositoryShouldNotExposeInfrastructureSidePorts() throws Exception {
        Path workspaceRoot = findWorkspaceRoot();
        Path tradeRepository = workspaceRoot.resolve("group-buy-market-master/group-buy-market-domain/src/main/java/cn/bugstack/domain/trade/adapter/repository/ITradeRepository.java");
        String source = new String(Files.readAllBytes(tradeRepository), StandardCharsets.UTF_8);

        List<String> forbiddenMethods = Arrays.asList(
                "queryUnExecutedNotifyTaskList",
                "updateNotifyTaskStatusSuccess",
                "updateNotifyTaskStatusError",
                "updateNotifyTaskStatusRetry",
                "occupyTeamStock",
                "recoveryTeamStock",
                "releaseUserTeamOccupy",
                "refund2AddRecovery",
                "queryLockMarketPayOrderEntityByOutTradeNo",
                "tryAcquireLockRequest",
                "releaseLockRequest",
                "cacheLockResult"
        );

        List<String> violations = new ArrayList<>();
        for (String method : forbiddenMethods) {
            if (source.contains(method)) {
                violations.add(method);
            }
        }

        Assert.assertTrue("ITradeRepository must keep notify task, team stock and lock request operations behind dedicated ports: " + violations, violations.isEmpty());
    }

    @Test
    public void seckillRepositoryShouldNotExposeMaintenanceJobMethods() throws Exception {
        Path workspaceRoot = findWorkspaceRoot();
        Path seckillRepository = workspaceRoot.resolve("group-buy-market-master/group-buy-market-domain/src/main/java/cn/bugstack/domain/seckill/adapter/repository/ISeckillRepository.java");
        String source = new String(Files.readAllBytes(seckillRepository), StandardCharsets.UTF_8);

        List<String> forbiddenMethods = Arrays.asList(
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

        Assert.assertTrue("ISeckillRepository must keep maintenance job operations behind ISeckillMaintenancePort: " + violations, violations.isEmpty());
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
