# 秒杀服务本地技术决策边界审计

## 背景

上一轮已经补齐 `seckill_order_outbox` 的查询台账、状态指标和告警，秒杀消息链路的主要缺口从“是否有 Outbox 闭环”收敛到专业 MQ adapter 和真实容量证明。本轮继续按当前目标 Prompt 审计 `SeckillService`，重点确认领域服务里是否还残留容易被误讲成生产能力的本地技术决策。

## 审计结论

`SeckillService` 里有两个最明确的残留点：

1. 本地 `ConcurrentHashMap<Long, Semaphore>` 活动级并发闸门。
2. `RandomStringUtils.randomNumeric(12)` 订单号生成。

本轮判断：订单号生成是真实代码风险，应该从领域服务移出；本地 `Semaphore` 先保留为单机入口闸门，但文档和面试口径必须明确它不是全局限流。

## 本轮实现

- 新增领域端口 `ISeckillOrderIdPort`。
- 新增基础设施实现 `SeckillOrderIdPort`。
- `SeckillService` 改为依赖端口获取订单号，不再直接依赖 `RandomStringUtils`。
- 订单号仍保持 12 位数字，兼容当前 `seckill_order.order_id varchar(12)` 表结构。
- 新增 `SeckillOrderIdPortUnitTest`，覆盖 12 位数字格式和单 JVM 内批量生成不重复。
- `seckill` 验证 profile 已纳入订单号生成测试。

## 保留边界

当前 12 位订单号实现解决的是“领域层不直接生成随机 ID”和“单 JVM 随机碰撞风险”两个问题，不等于完整分布式全局 ID。

真正生产化建议：

- 把 `order_id` 从 `varchar(12)` 扩到 18 到 20 位。
- 使用 Snowflake/Leaf/UidGenerator 或由订单中心统一发号。
- 多实例部署时引入 workerId 或发号服务，避免不同 JVM 时间戳相同导致冲突。

本地 `Semaphore` 仍然只是单机并发闸门，不是全局限流。当前全局削峰能力主要靠 Redis 限流、Redis 资格预扣和消息缓冲；如果后续锁单 RT 抖动或多实例入口拥塞成为真实问题，再把它独立成入口并发策略或下沉到应用层。

## Done List

- [x] 抽离秒杀订单号生成端口。
  - 文件：`ISeckillOrderIdPort.java`、`SeckillOrderIdPort.java`、`SeckillService.java`、`DomainServiceConfig.java`
  - 验证：`SeckillOrderIdPortUnitTest`
  - 结果：领域服务不再直接依赖随机数工具。

- [x] 把订单号生成测试纳入 `seckill` 验证 profile。
  - 文件：`scripts/verify-current-baseline.ps1`
  - 验证：后续统一执行 `-ProfileName seckill`。

## TODO List

- [ ] P1：审计本地 `Semaphore` 是否需要独立为入口并发策略。
  - 原因：订单号技术细节已经移出，剩余最容易被误讲成生产能力的是单机 `Semaphore`。
  - 范围：`SeckillService`、秒杀 HTTP 入口、Redis 限流端口、文档口径。
  - 验收：明确保留、迁移或删除的判断；只有压测或代码证据证明它造成问题时才改代码。

## 所有未完成任务清单

| 任务名称 | 当前状态 | 所属类型 | 优先级 | 不完成的影响 | 当前为什么还没做 | 后续触发条件 |
| --- | --- | --- | --- | --- | --- | --- |
| 本地 `Semaphore` 入口闸门治理 | 暂不处理 | 代码风险 / 面试口径 | P1 | 可能被误讲成全局限流；高并发下也可能带来单机 RT 抖动 | 当前没有证据显示它造成功能问题，Redis 限流和资格预扣才是主要削峰能力 | 锁单压测出现 RT 抖动，或准备做多实例入口治理 |
| 分布式全局订单号 | 暂不处理 | 生产边界 / 数据模型 | P1 | 多实例下 12 位本地时间序列仍不能等同全局发号服务 | 当前表结构是 `varchar(12)`，直接引入 Snowflake 会扩大 SQL 和兼容改动 | 明确升级订单号模型，或引入订单中心/发号服务 |
| 真实多实例容量验证 | 已阻塞 | 生产边界 | P0 | 本机 QPS 不能证明生产容量 | 只有当前单机环境 | 有独立 Linux 压测机、多服务实例和独立中间件节点 |

## 面试口径

可以说：秒杀订单号生成已经从领域服务抽成端口，当前本机实现保持 12 位数字兼容旧表，并通过单 JVM 唯一性测试。

不能说：已经完成分布式全局 ID 或生产级发号中心。生产环境应扩展字段长度并接入 Snowflake/Leaf/订单中心。
