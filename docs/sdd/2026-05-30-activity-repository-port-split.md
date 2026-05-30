# SDD: 活动通用仓储端口拆分

## 背景

`IActivityRepository` 同时暴露首页试算、商品查询、人群标签、动态配置开关、拼团队伍展示和统计查询。领域服务、试算节点、折扣策略只需要其中一小部分能力，但被迫依赖一个过宽的通用仓储端口。

这会带来两个问题：

- 首页试算节点可能误用拼团队伍展示查询，导致领域编排变宽。
- 基础设施 `ActivityRepository` 同时持有活动、折扣、SKU、渠道活动、Redis BitSet、DCC、拼团订单 DAO，后续修改任一能力都会影响整个活动上下文。

## 目标

按 DDD 语义拆成小端口：

- `IActivityTrialQueryPort`：首页试算所需的活动、渠道商品活动、SKU 查询。
- `ICrowdTagPort`：人群标签判断。
- `IActivitySwitchPort`：DCC 降级和切量判断。
- `IGroupBuyDisplayPort`：首页可参与队伍列表和拼团统计查询。

基础设施侧分别实现对应适配器，删除通用 `IActivityRepository` 和 `ActivityRepository`，避免通用仓储回流。

## 设计约束

- domain 仍只依赖端口，不感知 Redis、DCC、DAO、PO。
- 折扣计算服务只依赖 `ICrowdTagPort`。
- 试算 `MarketNode` 只依赖 `IActivityTrialQueryPort`。
- `SwitchNode` 只依赖 `IActivitySwitchPort`。
- 首页队伍展示服务只依赖 `IGroupBuyDisplayPort`。
- `DomainPurityTest` 增加守护，防止通用活动仓储重新出现。

## 验收

- `IActivityRepository.java` 和 `ActivityRepository.java` 不存在。
- `ActivityDomainConfig` 不再注入 `IActivityRepository`。
- `DomainPurityTest` 能验证活动端口职责拆分。
- 营销服务 JDK 1.8 编译通过。
- 相关领域单元测试通过。
