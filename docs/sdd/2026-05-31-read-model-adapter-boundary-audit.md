# 读模型适配器边界审计

## 背景

连续拆分订单创建、结算、退款、锁单等写模型后，剩余较大的基础设施适配器主要集中在读模型和技术适配能力：

- `GroupBuyQueryPort`：拼团交易读模型，查询订单、队伍、活动和用户参与次数。
- `GroupBuyDisplayPort`：首页展示读模型，查询用户可参与队伍、随机队伍和统计。
- `ActivityTrialQueryPort`：首页试算所需活动、折扣、SKU 和商城 SKU 活动映射。
- `SeckillRateLimitPort`：秒杀入口 Redis 限流。
- `TradeLockRequestPort`：拼团锁单请求幂等锁和锁单结果缓存。
- `GroupBuyTeamStockPort`：拼团队伍库存 Redis 预扣和恢复。

这些类有一定行数，但并不等于需要拆。DDD 治理的目标不是把每个类拆小，而是把写模型状态流转、补偿、消息和技术设施细节隔离在合适的位置。

## 审计结论

### 本轮不拆

- `GroupBuyQueryPort` 当前只做读查询和 PO 到领域读模型的映射，没有状态更新、补偿、消息投递或分布式锁逻辑。
- `GroupBuyDisplayPort` 当前只做展示聚合查询、队伍 ID 汇总和 DTO/Entity 组装，随机队伍截取属于展示读模型策略，不属于领域状态变更。
- `ActivityTrialQueryPort` 当前只做试算所需基础数据查询，缓存回源来自基础设施 `AbstractRepository`，未混入交易写职责。
- `SeckillRateLimitPort` 是独立技术适配器，Redis Lua 限流细节属于该端口自身职责，不需要拆成领域端口。
- `TradeLockRequestPort` 是拼团请求锁和结果缓存的 Redis 适配器，已经是独立端口，不再继续拆分。
- `GroupBuyTeamStockPort` 是拼团队伍库存 Redis 适配器，承担预扣、恢复和用户占位释放，当前仍在同一库存语义内。

### 后续触发拆分的条件

只有出现以下变化时再拆：

- 读模型适配器开始写 DB、发 MQ、写补偿台账或记录状态流水。
- 读模型里出现跨多个业务场景的 if/else 编排，而不是单纯查询和映射。
- Redis 技术适配器同时承担两个以上不同业务语义，例如把拼团请求锁和队伍库存预扣混到一个类。
- 同一映射逻辑在多个适配器重复出现，并且字段已经开始不一致。

## 架构守护

新增架构测试，要求以下读模型适配器保持只读和无补偿语义：

- `GroupBuyQueryPort`
- `GroupBuyDisplayPort`
- `ActivityTrialQueryPort`

禁止它们直接出现写操作、消息发送、补偿任务、状态流水、Redis 锁和交易命令语义。

## 验收

- JDK 1.8 下营销 app 编译通过。
- `DomainPurityTest` 通过。
- 本轮只记录审计结论和增加边界测试，不做生产代码拆分。

## 实现记录

- 新增 `readModelAdaptersShouldStayReadOnlyAndCompensationFree` 架构测试。
- 更新 `docs/sdd/ddd-sdd-todo-list.md` 和 `docs/sdd/tasks.md`，记录读模型适配器本轮不拆的边界。
- 生产代码不变，避免对查询适配器做无收益拆分。

## 验证结果

```powershell
cd E:\java\group_buy_market\group-buy-market-master
$env:JAVA_HOME='C:\Program Files\Eclipse Adoptium\jdk-8.0.492.9-hotspot'
$env:Path="$env:JAVA_HOME\bin;E:\java\group_buy_market\.tools\apache-maven-3.8.8\bin;$env:Path"
..\.tools\apache-maven-3.8.8\bin\mvn.cmd -q -pl group-buy-market-app -am -DskipTests compile
..\.tools\apache-maven-3.8.8\bin\mvn.cmd -q -pl group-buy-market-app -am -DskipTests=false -DfailIfNoTests=false "-Dtest=DomainPurityTest" test
```

- 编译：通过。
- `DomainPurityTest`：37 个测试通过。

## 面试表述

我没有为了行数机械拆读模型适配器。DDD 重点不是“小类崇拜”，而是写模型状态变化、消息、补偿和技术设施边界清晰。像拼团首页展示、交易读模型、试算基础数据查询，本质上就是查询聚合和映射，只要不混入写操作和补偿逻辑，就保持现状并用架构测试守住边界。
