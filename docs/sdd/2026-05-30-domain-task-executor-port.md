# 2026-05-30 领域异步执行端口治理

## 背景

上一轮已经通过 `DomainPurityTest` 防止 domain 层重新引入 Spring 注解，但继续扫描发现两个服务的 domain 层仍直接依赖 `ThreadPoolExecutor`。这不是容器依赖，但属于具体执行器实现细节，会让领域服务感知线程池配置、拒绝策略和基础设施调度方式。

本轮目标是在不改变业务行为的前提下，把具体线程池从 domain 层移到 app 层配置。

## 设计

- 在营销服务 domain 新增 `cn.bugstack.domain.shared.adapter.port.IDomainTaskExecutor`。
- 在商城服务 domain 新增同名端口。
- 领域服务只依赖 `IDomainTaskExecutor#execute(Runnable)`。
- app 层 `ThreadPoolConfig` 通过 `threadPoolExecutor::execute` 适配真实线程池。
- 拼团首页试算、拼团结算通知、拼团退单通知、商城支付回调后的异步营销结算都保持原异步行为。
- `DomainPurityTest` 和 `scripts/check-domain-purity.ps1` 增加 `ThreadPoolExecutor` 检查，防止具体线程池回流到 domain。

## 影响范围

- 营销服务：
  - 首页试算 `MarketNode` / `MarketNode2CompletableFuture`。
  - 拼团结算 `TradeSettlementOrderService`。
  - 拼团退单策略 `AbstractRefundOrderStrategy` 及三个具体策略。
- 商城服务：
  - 支付回调后的拼团/秒杀营销异步结算。

## 验收

```powershell
$env:JAVA_HOME='C:\Program Files\Eclipse Adoptium\jdk-8.0.492.9-hotspot'
$env:Path="$env:JAVA_HOME\bin;E:\java\qsyy-ecommerce-platform\.tools\apache-maven-3.8.8\bin;$env:Path"

cd E:\java\qsyy-ecommerce-platform\group-buy-market-master
mvn -q -DskipTests compile
mvn -pl group-buy-market-app -am -DskipTests=false -DfailIfNoTests=false "-Dtest=cn.bugstack.test.architecture.DomainPurityTest,cn.bugstack.test.domain.shared.OrderStateMachineTest" test

cd E:\java\qsyy-ecommerce-platform\s-pay-mall-ddd-market-master
mvn -q -DskipTests compile

cd E:\java\qsyy-ecommerce-platform
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\check-domain-purity.ps1
```

## 面试表达

> 我不仅去掉了 domain 里的 Spring 注解，也继续把 `ThreadPoolExecutor` 这种具体执行器从领域层抽走。领域层只依赖 `IDomainTaskExecutor` 端口，app 层把真实线程池适配进去。这样领域模型不关心线程池参数和拒绝策略，后续想换成本地线程池、MQ 异步、任务调度器都不需要改领域代码。
