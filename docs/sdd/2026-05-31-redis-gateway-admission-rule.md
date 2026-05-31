# Redis 通用接口新增能力准入规则

## 背景

当前 `IRedisService` / `RedissonService` 仍然是项目里最典型的基础设施总线风险。

它现在同时包含：

- Redis/Redisson 原语能力，如 bucket、queue、lock、semaphore、map、set、bitset。
- 少量业务语义原子脚本，如 `reserveSeckillStock`、`reserveSeckillQualification`、`reserveTeamStock`。
- 默认 hash 路由工具方法，如 `getIndexFromUserId`。

这些能力在早期快速落地秒杀和拼团队伍库存时有效，但长期看会形成一个风险：

- 后续新需求容易继续往 `IRedisService` 堆方法，而不是先设计更窄的业务语义端口。

本轮不拆生产代码，只制定新增能力准入规则。

## 准入原则

### 1. `IRedisService` 不能继续作为业务能力入口

以后新增 Redis 能力时，不允许直接把业务动作加到 `IRedisService`。

禁止新增类似命名：

- `reserveXxx`
- `lockXxxOrder`
- `occupyXxx`
- `releaseXxx`
- `syncXxxStock`
- `cacheXxxResult`
- `queryXxxBusinessState`

这些名称说明方法已经在表达业务语义，应先进入业务端口或支撑组件。

### 2. 新业务必须先定义领域端口或基础设施 adapter 支撑

如果新增能力属于业务规则，例如库存、限流、资格、防重、补偿、排队，应优先定义：

- domain port：表达业务语义。
- infrastructure adapter：适配 Redis、Lua、Stream、Lock 等具体技术。
- support component：拆分复杂 Redis key、Lua、序列化、指标、故障注入等细节。

示例：

- 秒杀库存预扣：走 `ISeckillStockReservationPort`。
- 秒杀结果缓存：走 `ISeckillResultCachePort`。
- 拼团队伍库存：走 `IGroupBuyTeamStockPort`。
- 拼团锁单请求幂等：走 `ITradeLockRequestPort`。

### 3. `IRedisService` 只允许承载少量技术原语

后续如果确实要新增 `IRedisService` 方法，只能是 Redis 技术原语或 Redisson 对象访问。

允许类型：

- 通用 key/value。
- 通用 lock。
- 通用 queue/stream 基础访问。
- 通用 map/set/bitset。
- 通用 Lua 执行器，如果未来抽象出来。

不允许类型：

- 带业务名词的方法。
- 组合多个业务 key 的方法。
- 写业务状态流水的方法。
- 写业务补偿逻辑的方法。
- 直接包含秒杀、拼团、支付、退款、对账语义的方法。

### 4. 现有业务方法作为历史例外，不作为新增先例

当前已有：

- `reserveSeckillStock`
- `reserveSeckillQualification`
- `reserveTeamStock`

这三个方法属于历史遗留和当前兼容边界。

本轮不直接拆它们，原因是：

- 现有核心业务端口已经包住了直接调用面。
- 直接拆会影响多个基础设施 adapter 和单元测试。
- 当前更高收益的动作是先阻止继续扩张。

但后续如果继续治理 Redis gateway，这三个方法应优先迁移到更窄的 script gateway 或对应业务 support。

## 新增 Redis 能力评审清单

每次要新增 Redis 能力时，先回答这 6 个问题：

1. 这个方法名里是否包含秒杀、拼团、订单、支付、退款、对账、库存、资格、补偿等业务词。
2. 这个方法是否组合了多个 Redis key。
3. 这个方法是否需要 Lua 原子脚本。
4. 这个方法是否会影响业务状态或库存数量。
5. 这个方法是否需要指标、日志、故障注入或补偿。
6. 这个方法是否只有一个业务场景使用。

如果任意一项为“是”，优先不要加到 `IRedisService`，而应新建或复用业务语义端口。

## 守护策略

本轮暂不新增架构测试。

原因：

- `DomainPurityTest` 已经是大型字符串规则清单，继续往里堆 Redis 方法名禁止规则会加重维护负担。
- 当前最需要的是团队规则和 SDD 入口，而不是立即增加新的脆弱文本断言。

后续触发自动化守护的条件：

- `IRedisService` 新增了业务语义方法。
- `RedissonService` 新增了组合业务 key 的 Lua 方法。
- 新代码绕过已有业务端口，直接依赖 `IRedisService` 实现业务规则。

如果触发，再考虑新增一个小而专门的测试，而不是继续扩大 `DomainPurityTest`。

## 验收

- 本轮只新增准入规则文档，不修改生产代码。
- `README.md`、`tasks.md`、`ddd-sdd-todo-list.md` 和当前风险地图同步记录。
- Done/TODO/Open Items 更新：Redis 准入规则从 TODO 移到 Done，Redis 通用接口拆分仍保留为暂不处理。

## 面试表述

如果面试官问“你怎么避免 Redis 工具类继续变大”，可以这样说：

> 我没有直接大拆 `IRedisService`，因为现有业务端口已经隔离了大部分调用面，贸然拆会影响面很大。我先制定了 Redis 通用接口准入规则：新增 Redis 能力如果带业务语义、组合多个 key、需要 Lua、影响库存或状态，就不能直接加到 `IRedisService`，必须先进入业务语义端口或基础设施支撑组件。已有的几个业务方法作为历史例外，后续有生产化重构时再迁移到更窄的 script gateway。
