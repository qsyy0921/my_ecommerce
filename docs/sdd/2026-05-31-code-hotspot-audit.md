# 代码热点审计

## 背景

在完成一轮 DDD 边界治理后，项目已经不再存在明显的 domain 层 Spring 污染和通用大 Repository 问题。下一步更有价值的是识别“仍然容易继续膨胀”的代码热点，而不是继续机械拆分类。

本轮只做代码热点审计，不做业务代码改造。

## 审计对象

- `group-buy-market-trigger/.../GroupBuyLockOrderSupport`
- `group-buy-market-trigger/.../SeckillLockOrderSupport`
- `group-buy-market-infrastructure/.../redis/IRedisService`
- `group-buy-market-infrastructure/.../redis/RedissonService`
- `s-pay-mall-ddd-domain/.../AbstractOrderService`
- `group-buy-market-domain/.../seckill/SeckillService`

## 发现

### 1. `GroupBuyLockOrderSupport` 仍然是典型的入口编排热点

当前职责包括：

- HTTP 参数合法性分支。
- 通知类型解析。
- 幂等订单查询。
- 队伍满员判断。
- 首页试算前置校验。
- 调用领域锁单。
- 结构化日志和响应封装。

结论：

- 它还在 trigger/support 层，没有污染 domain。
- 但它已经是一个“高频变更入口类”，以后新增风控、灰度、补充幂等来源或多端差异时，最容易继续膨胀。

建议：

- 当前先不拆。
- 触发条件设为：当该类继续增加新业务分支，或与秒杀入口出现明显重复流程时，再抽 application 级用例对象。

### 2. `SeckillLockOrderSupport` 仍然持有较多非协议型逻辑

当前除了基础参数校验外，还承担：

- DB 幂等查询。
- Redis/缓存结果幂等查询。
- 限流端口调用。
- 指标打点。
- 业务日志。
- 锁单成功后的响应装配。

结论：

- 它不是 Controller 大类问题，而是秒杀入口用例编排仍然集中在一个 support 中。
- 目前可以接受，因为秒杀入口本来就是高并发主链路，不适合拆成过多细粒度对象。

建议：

- 继续靠架构测试守边界。
- 如果后续要引入风控、活动灰度、黑名单、降级策略，应该优先抽成应用用例管道，而不是继续堆在 support 里。

### 3. `IRedisService` / `RedissonService` 仍然是技术能力大接口

问题不在行数，而在职责范围：

- 同时暴露普通 KV、队列、阻塞队列、延迟队列、Map、Set、List、SortedSet、锁、信号量、BitSet、BloomFilter。
- 同时承载秒杀 Lua 资格预扣和拼团队伍库存 Lua 预扣。
- 接口上已经混合了“通用 Redis SDK 包装”和“明确业务语义脚本能力”。

结论：

- 它还没有破坏 DDD 分层，但已经是明显的基础设施总线型接口。
- 后续如果再往里面加更多业务脚本或缓存规则，会把业务语义重新拉回通用技术层。

建议：

- 以后新增 Redis 能力，优先放到业务语义端口或专用 support。
- 不再扩张 `IRedisService` 的业务方法数量，尽量只保留 Redisson/Redis 原语包装和少量通用原子脚本。

### 4. `AbstractOrderService` 仍然保留商城域对营销类型的分支

当前问题：

- `lockPayMarketOrder` 通过 `MarketTypeVO` 分支决定调用拼团或秒杀营销锁单。
- `createOrder` 中根据 `marketType`、`marketDeductionAmount` 决定营销锁单和支付下单路径。
- 失败时仍然使用 `RuntimeException("market lock failed")` 这种较弱的领域表达。

结论：

- 这说明商城域虽然已经把对账、支付成功、退款拆开，但“营销订单锁单策略”仍然在抽象订单服务里集中判断。
- 当前是合理的阶段性实现，因为拼团和秒杀都复用商城订单主链路。

建议：

- 不急着拆成更多服务。
- 如果后续新增更多营销类型，例如预售、砍价、券后立减，可以考虑把营销锁单策略再抽成 `MarketOrderLockStrategy` 一层。

### 5. `SeckillService` 仍然保留少量技术决策

当前热点：

- domain service 内部维护 `ConcurrentHashMap<Long, Semaphore>` 做活动级本地并发闸门。
- domain service 内部直接用 `RandomStringUtils.randomNumeric(12)` 生成订单号。

结论：

- 这不是 Spring/DAO 污染，但属于“领域服务感知本地运行时策略”。
- 如果以后要从本地 JVM 并发闸门切换为更明确的限流/隔离组件，或者切换统一订单号生成器，这里会成为迁移点。

建议：

- 当前不必继续拆。
- 后续若要提升一致性，可以考虑：
  - 把本地活动并发闸门抽成 `ISeckillActivityConcurrencyGuard`。
  - 把订单号生成抽成 `IOrderIdGenerator` 或沿用商城统一号段/雪花号策略。

## 总结

当前代码热点的共同特点是：

- 它们已经不属于“DDD 分层错误”。
- 但它们是未来最容易重新长胖的地方。

因此，下一阶段的策略不应再是继续大拆分，而应是：

1. 用文档明确热点。
2. 用架构测试继续守边界。
3. 只有当新需求真正把某个热点再次推成多职责大类时，再做增量拆分。
