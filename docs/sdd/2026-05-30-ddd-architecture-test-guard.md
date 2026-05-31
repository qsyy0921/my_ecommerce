# 2026-05-30 DDD 架构测试护栏

## 背景

项目在拼团、秒杀、支付、补偿、对账和观测能力持续叠加后，最大的本地工程风险已经从“功能缺失”转为“边界腐化”。如果后续为了快速实现需求，把 Spring 注解、DAO、PO、Redis/RabbitMQ 细节重新塞回 domain 层，DDD 分层会很快失效。

本轮不新增业务能力，目标是把 DDD 约束落成可执行测试。

## 设计

- 新增 `DomainPurityTest`，扫描营销服务和商城服务的 `domain` 包。
- 禁止 domain 包出现 `org.springframework`、`javax.annotation.Resource`、`@Service`、`@Component`、`@Resource`、`@Autowired`、`@Value`。
- 新增 `OrderStateMachineTest`，覆盖秒杀订单、拼团订单、拼团队伍的合法/非法状态迁移。
- 状态迁移实体必须先经过 `OrderStateMachine.check(...)`，非法迁移直接失败，防止 Repository 或补偿任务绕过状态机。

## 验收

```powershell
$env:JAVA_HOME='C:\Program Files\Eclipse Adoptium\jdk-8.0.492.9-hotspot'
$env:Path="$env:JAVA_HOME\bin;E:\java\qsyy-ecommerce-platform\.tools\apache-maven-3.8.8\bin;$env:Path"
cd E:\java\qsyy-ecommerce-platform\group-buy-market-master
mvn -pl group-buy-market-app -am -DskipTests=false -DfailIfNoTests=false "-Dtest=cn.bugstack.test.architecture.DomainPurityTest,cn.bugstack.test.domain.shared.OrderStateMachineTest" test
```

## 面试表达

> 我没有只靠口头说 DDD，而是把分层约束做成了可执行测试。`DomainPurityTest` 会扫描商城和营销两个 domain 包，发现 Spring 注解或容器依赖就失败；`OrderStateMachineTest` 会验证秒杀和拼团核心状态机，防止后续补偿、退款、异步落库绕过合法状态迁移。
