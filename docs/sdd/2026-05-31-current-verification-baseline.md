# 当前目标 Prompt 与验证基线

## 背景

本项目已经从课程代码逐步治理成 DDD + SDD 约束下的交易营销系统。后续继续开发时，最容易出问题的不是“还缺一个类”，而是：

- 忘记先读当前代码和 SDD 现态。
- 继续机械拆分类，把系统拆散但没有解决真实风险。
- 只记录亮点，不维护 Done List、TODO List 和所有未完成任务清单。
- 只跑局部命令，导致编译、架构守护、领域单测和文档检查证据分散。

本文件作为后续继续工作的可复制目标 Prompt 和当前验证基线入口。

## 可复制目标 Prompt

```text
你是一个务实的高级后端工程师，正在维护 E:\java\group_buy_market 这个拼团 + 秒杀 + 支付商城项目。

目标：
在 DDD 架构和 SDD 开发流程下继续治理项目。每次开始前必须先检查当前工作区、阅读 docs/sdd 的现态入口，不允许只凭历史记忆判断。每轮只处理一个真实问题，不做没有收益的机械拆分。

开发约束：
1. 先写清规格和边界，再改代码。
2. domain 层只保留领域模型、领域服务、领域端口和状态机，不能直接依赖 Spring、MyBatis、Redis、RabbitMQ、HTTP Client、DAO 或 PO。
3. 基础设施细节进入 adapter/support，业务语义进入端口，跨服务一致性依靠幂等、补偿、消息可靠性和对账闭环。
4. 秒杀高并发链路可以使用 Redis 资格预扣和削峰，但必须诚实说明 Redis Stream 不是大促终局方案，真正生产化需要 RocketMQ/Kafka/Pulsar 级别消息系统和真实多机压测证明。
5. 不要声称项目已经完美；必须维护当前边界和剩余问题。

每轮必须维护三类清单：
1. Done List：本轮完成了什么、影响了哪些文件、验证命令是什么、提交号是什么。
2. TODO List：下一轮最值得做的一件事，写清原因、范围和验收标准。
3. 所有未完成任务清单：保留所有尚未完成、暂不处理或受环境阻塞的问题，写清状态、类型、优先级、不完成的影响、当前为什么还没做、后续触发条件。

每轮必做流程：
1. git status -sb，确认工作区状态。
2. 阅读 docs/sdd/2026-05-31-current-top-risk-map-and-open-items.md。
3. 如果只是文档治理，至少执行 git diff --check。
4. 如果改了 Java 代码，按 docs/sdd/2026-05-31-current-verification-baseline.md 执行对应编译、架构守护和领域测试。
5. 更新 docs/sdd/README.md、docs/sdd/tasks.md、docs/sdd/ddd-sdd-todo-list.md。
6. 如果架构口径、短板或面试表达变化，同步 docs/interview-baguwen.md。
7. 提交并推送到 GitHub，提交说明写清楚改了什么、为什么改、如何验证。
```

## 当前权威入口

- 当前风险和三类清单：`docs/sdd/2026-05-31-current-top-risk-map-and-open-items.md`
- 当前 SDD 任务状态：`docs/sdd/tasks.md`
- DDD + SDD 治理约束：`docs/sdd/ddd-sdd-todo-list.md`
- 面试口径：`docs/interview-baguwen.md`

## Done List

- [x] 建立当前目标 Prompt。
  - 结果：明确后续 AI/人工继续开发时必须先读现态、按 DDD + SDD 约束工作、维护三类清单。
  - 验收：本文件包含可直接复制的目标 Prompt。

- [x] 建立当前验证基线。
  - 结果：把文档检查、营销服务编译、商城服务编译、领域架构守护、拼团测试、秒杀测试、商城对账测试收敛到同一入口。
  - 验收：本文件列出当前推荐命令和触发条件。

- [x] 明确验证分级。
  - 结果：文档变更、领域边界变更、拼团链路变更、秒杀链路变更、商城对账/支付变更分别对应不同验证集合。
  - 验收：后续不再只写“已验证”，必须说明跑了哪一级命令。

## TODO List

- [ ] P0：审计秒杀订单消息端口接入专业 MQ 的最小可切换边界。
  - 原因：当前前 5 风险中，秒杀生产化消息链路仍排第一；Redis Stream 适合本机演示，但不能包装成大促终局方案。
  - 范围：`docs/sdd`、秒杀消息端口和现有 MQ evolution 文档。
  - 验收：明确是否需要本机接入 RocketMQ/Kafka adapter，还是继续只保留方案边界；不得把本机环境包装成生产容量证明。

## 所有未完成任务清单

| 任务名称 | 当前状态 | 所属类型 | 优先级 | 不完成的影响 | 当前为什么还没做 | 后续触发条件 |
| --- | --- | --- | --- | --- | --- | --- |
| 秒杀专业 MQ 演进落地 | 暂不处理 | 生产边界 / 代码风险 | P0 | Redis Stream 容量和堆积能力不能包装成大促终局方案 | 缺真实 MQ 集群和多机压测环境 | 明确引入 RocketMQ/Kafka/Pulsar 或有生产化演练目标 |
| 真实多实例容量验证 | 已阻塞 | 生产边界 | P0 | 本机 QPS 不能证明生产容量 | 只有当前单机环境 | 有独立 Linux 压测机、多服务实例和独立中间件节点 |
| 拼团锁单等待策略重构 | 暂不处理 | 代码风险 | P1 | domain service 继续保留 `Thread.sleep` 技术等待 | 等待超时测试已补齐，但当前没有功能故障 | 压测暴露 RT 抖动，或继续增强锁单幂等策略 |
| Redis 通用接口拆分 | 暂不处理 | 代码风险 / 基础设施边界 | P0 | 公共 Redis 总线继续扩大 | 新增能力准入规则已补齐；直接拆改动面大，现有业务端口暂时守住边界 | 新增 Redis 能力或公共接口继续膨胀 |
| 对账后台权限、审批和 SLA | 未开始 | 业务边界 | P1 | 对账中心只能算最小闭环 | 属于新产品范围 | 明确建设运营后台 |
| 完整售后体系 | 未开始 | 业务边界 | P1 | 不能包装成完整电商售后 | 会引入部分退款、拒绝退款、履约后退款等新模型 | 明确建设售后子系统 |
| 完整支付中台能力 | 未开始 | 业务边界 | P2 | `mock/alipay` 适合演示，不等于支付中台 | 当前项目目标是交易营销，不是支付平台 | 接入更多支付渠道或账单文件 |
| `DomainPurityTest` 结构拆分 | 暂不处理 | 测试缺口 / 文档治理 | P2 | 架构守护可能继续变成大型文本快照测试 | 规则分层已评估，当前测试仍能有效挡回归，直接拆分收益不高 | 新增大量同类守护规则，或该测试继续显著膨胀 |
| 八股文档细粒度短板同步 | 暂不处理 | 面试口径 | P1 | 面试材料可能落后于 SDD 现态 | 当前前 5 风险口径已同步，本轮验证基线不改变业务架构口径 | 每次新增风险排序或主链路结论 |

## 验证分级

### L0 文档变更

适用范围：只修改 `docs`、`README`、清单、面试材料，不改 Java 代码。

推荐使用脚本：

```powershell
cd E:\java\group_buy_market
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\verify-current-baseline.ps1 -ProfileName docs-only
```

等价命令：

```powershell
cd E:\java\group_buy_market
git diff --check
rg -n "Done List|TODO List|所有未完成任务清单|current-verification-baseline" docs\sdd
```

### L1 领域边界或架构守护变更

适用范围：修改 domain、领域端口、架构测试、SDD 架构约束。

推荐使用脚本：

```powershell
cd E:\java\group_buy_market
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\verify-current-baseline.ps1 -ProfileName market-domain
```

等价命令：

```powershell
cd E:\java\group_buy_market
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\check-domain-purity.ps1
& "E:\java\group_buy_market\.tools\apache-maven-3.8.8\bin\mvn.cmd" -f "E:\java\group_buy_market\group-buy-market-master\pom.xml" -q -pl group-buy-market-app -am -DskipTests=false -DfailIfNoTests=false "-Dtest=cn.bugstack.test.architecture.DomainPurityTest,cn.bugstack.test.domain.shared.OrderStateMachineTest" test
```

### L2 营销服务编译

适用范围：修改营销服务任意 Java 生产代码、配置或 mapper。

推荐使用脚本：

```powershell
cd E:\java\group_buy_market
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\verify-current-baseline.ps1 -ProfileName market-compile
```

等价命令：

```powershell
cd E:\java\group_buy_market
& "E:\java\group_buy_market\.tools\apache-maven-3.8.8\bin\mvn.cmd" -f "E:\java\group_buy_market\group-buy-market-master\pom.xml" -q -pl group-buy-market-app -am -DskipTests compile
```

### L3 商城服务编译

适用范围：修改商城服务任意 Java 生产代码、配置或 mapper。

推荐使用脚本：

```powershell
cd E:\java\group_buy_market
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\verify-current-baseline.ps1 -ProfileName mall-compile
```

等价命令：

```powershell
cd E:\java\group_buy_market
& "E:\java\group_buy_market\.tools\apache-maven-3.8.8\bin\mvn.cmd" -f "E:\java\group_buy_market\s-pay-mall-ddd-market-master\pom.xml" -q -pl s-pay-mall-ddd-app -am -DskipTests compile
```

### L4 拼团链路变更

适用范围：拼团试算、锁单、结算、退单、队伍库存、锁单幂等。

推荐使用脚本：

```powershell
cd E:\java\group_buy_market
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\verify-current-baseline.ps1 -ProfileName group-buy
```

等价命令：

```powershell
cd E:\java\group_buy_market
& "E:\java\group_buy_market\.tools\apache-maven-3.8.8\bin\mvn.cmd" -f "E:\java\group_buy_market\group-buy-market-master\pom.xml" -q -pl group-buy-market-app -am -DskipTests=false -DfailIfNoTests=false "-Dtest=cn.bugstack.test.domain.trade.TradeLockOrderServiceUnitTest,cn.bugstack.test.domain.trade.TradeRefundOrderServiceUnitTest" test
```

### L5 秒杀链路变更

适用范围：秒杀限流、库存预扣、锁单、异步落库、结算、退款、Stream 缓冲。

推荐使用脚本：

```powershell
cd E:\java\group_buy_market
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\verify-current-baseline.ps1 -ProfileName seckill
```

等价命令：

```powershell
cd E:\java\group_buy_market
& "E:\java\group_buy_market\.tools\apache-maven-3.8.8\bin\mvn.cmd" -f "E:\java\group_buy_market\group-buy-market-master\pom.xml" -q -pl group-buy-market-app -am -DskipTests=false -DfailIfNoTests=false "-Dtest=cn.bugstack.test.infrastructure.seckill.SeckillOrderLockPortUnitTest,cn.bugstack.test.infrastructure.seckill.SeckillStockAvailabilityPortUnitTest,cn.bugstack.test.infrastructure.seckill.SeckillRateLimitPortUnitTest,cn.bugstack.test.infrastructure.seckill.SeckillSettlementPortUnitTest" test
```

### L6 商城支付和对账变更

适用范围：支付回调、支付流水、退款流水、商城订单状态、对账差错、MQ 重放。

推荐使用脚本：

```powershell
cd E:\java\group_buy_market
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\verify-current-baseline.ps1 -ProfileName mall-reconcile
```

等价命令：

```powershell
cd E:\java\group_buy_market
& "E:\java\group_buy_market\.tools\apache-maven-3.8.8\bin\mvn.cmd" -f "E:\java\group_buy_market\s-pay-mall-ddd-market-master\pom.xml" -q -pl s-pay-mall-ddd-app -am -DskipTests=false -DfailIfNoTests=false "-Dtest=cn.bugstack.test.domain.OrderReconcileServiceReplayContractTest,cn.bugstack.test.domain.OrderServiceTest,cn.bugstack.test.infrastructure.message.MessageProducerRetrySupportUnitTest,cn.bugstack.test.infrastructure.message.MqProducerFailureRecorderUnitTest,cn.bugstack.test.infrastructure.reconcile.ReconcileOperationLogSupportUnitTest" test
```

### L7 发布前本地全量验证

适用范围：跨服务代码变更、公共端口变更、状态机变更、消息可靠性变更。

推荐使用脚本：

```powershell
cd E:\java\group_buy_market
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\verify-current-baseline.ps1 -ProfileName full-local
```

等价命令：

```powershell
cd E:\java\group_buy_market
git diff --check
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\check-domain-purity.ps1
& "E:\java\group_buy_market\.tools\apache-maven-3.8.8\bin\mvn.cmd" -f "E:\java\group_buy_market\group-buy-market-master\pom.xml" -q -pl group-buy-market-app -am -DskipTests compile
& "E:\java\group_buy_market\.tools\apache-maven-3.8.8\bin\mvn.cmd" -f "E:\java\group_buy_market\s-pay-mall-ddd-market-master\pom.xml" -q -pl s-pay-mall-ddd-app -am -DskipTests compile
& "E:\java\group_buy_market\.tools\apache-maven-3.8.8\bin\mvn.cmd" -f "E:\java\group_buy_market\group-buy-market-master\pom.xml" -q -pl group-buy-market-app -am -DskipTests=false -DfailIfNoTests=false "-Dtest=cn.bugstack.test.architecture.DomainPurityTest,cn.bugstack.test.domain.shared.OrderStateMachineTest,cn.bugstack.test.domain.trade.TradeLockOrderServiceUnitTest,cn.bugstack.test.domain.trade.TradeRefundOrderServiceUnitTest,cn.bugstack.test.infrastructure.seckill.SeckillOrderLockPortUnitTest" test
& "E:\java\group_buy_market\.tools\apache-maven-3.8.8\bin\mvn.cmd" -f "E:\java\group_buy_market\s-pay-mall-ddd-market-master\pom.xml" -q -pl s-pay-mall-ddd-app -am -DskipTests=false -DfailIfNoTests=false "-Dtest=cn.bugstack.test.domain.OrderReconcileServiceReplayContractTest" test
```

## 本轮判断

本轮没有改变业务架构口径，不需要同步 `interview-baguwen.md`。后续如果新增 MQ 选型、状态机、对账/售后能力，才需要同步面试文档。

当前权威清单仍以 `2026-05-31-current-top-risk-map-and-open-items.md` 为准；本文件提供可复制 Prompt 和验证命令基线。
