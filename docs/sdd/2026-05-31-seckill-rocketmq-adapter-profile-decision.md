# 秒杀 RocketMQ Adapter 最小 Profile 决策

## 背景

上一轮已经把秒杀订单创建消息补成稳定 Envelope，并固化了专业 MQ 需要的 `stableMessageKey()`、`stablePartitionKey()`、`stableEventTag()`。当前 TODO 的 P0 是评估是否在本机实现 RocketMQ adapter 最小 profile。

本轮目标不是“为了看起来更大厂而接一个客户端”，而是判断这个改动是否能真实降低当前风险。

## 当前证据

- 代码没有 RocketMQ/Kafka/Pulsar 客户端依赖。
- 当前消息主链路已经通过 `ISeckillOrderMessagePort` 隔离具体中间件。
- 当前可用实现包括 RabbitMQ、Redis Stream、Redis Queue、本地队列和 Outbox 重试。
- `docs/dev-ops/docker-compose-rocketmq.yml` 只有 namesrv + broker 最小编排，缺少持久化、控制台、监控、告警和压测脚本绑定。
- 单机 Windows + Docker Desktop 环境无法证明 broker 堆积恢复、多副本、网络抖动、consumer group rebalance 和生产容量。

## 决策

本轮不实现 RocketMQ adapter 最小 profile。

原因：

1. 当前已经具备“后续可切换”的端口、Envelope、Outbox 和 key/tag/partition key 契约；再接一个未验证客户端不会显著提升业务正确性。
2. 本机单 broker profile 只能证明 SDK 能发送和消费，不能证明生产化消息能力。
3. 引入 RocketMQ adapter 会同时打开 producer send result、consumer group、retry、DLQ、lag、offset commit、线程池、监控和 profile 配置问题；如果没有真实压测和故障演练，容易把半成品包装成生产能力。
4. 当前更有价值的本机可闭环缺口是 Outbox 查询、状态台账和告警，它能直接提升补偿可观测性。

因此，RocketMQ/Kafka/Pulsar adapter 保留为生产演进项；只有满足触发条件时再实现。

## 触发条件

后续满足任一条件时，可以重新进入 adapter 实现：

- 有独立 Linux 环境、专业 MQ 集群和独立压测机。
- 明确要做本机 profile 演示，并接受“只能证明可切换，不证明生产容量”的边界。
- Outbox 查询台账、告警、补偿台账已经完成，需要继续推进消息系统演进。

## 后续实现边界

如果后续实现 RocketMQ adapter，必须遵守：

- 不修改锁单主流程。
- 不让 domain 依赖 RocketMQ/Kafka/Pulsar SDK。
- producer 必须复用 `stableMessageKey()`、`stablePartitionKey()`、`stableEventTag()`。
- producer 发送必须接入 Outbox 状态机。
- consumer 必须复用现有订单创建端口，不直接写 DAO。
- consumer 幂等必须复用 `messageId/sourceMessageId`。
- 必须同步补 broker lag、retry、DLQ、消费耗时和批量落库耗时指标。

## Done List

- [x] 评估是否在本机实现 RocketMQ adapter 最小 profile。
  - 文件：`docs/sdd/2026-05-31-seckill-rocketmq-adapter-profile-decision.md`
  - 验证：`powershell -NoProfile -ExecutionPolicy Bypass -File scripts\verify-current-baseline.ps1 -ProfileName docs-only`
  - 提交：`77761c0`

- [x] 明确本轮不引入 RocketMQ/Kafka/Pulsar 客户端依赖。
  - 文件：`docs/sdd/2026-05-31-seckill-rocketmq-adapter-profile-decision.md`
  - 验证：`rg "rocketmq" group-buy-market-master -n`
  - 提交：`77761c0`

## TODO List

- [ ] P1：补秒杀 Outbox 查询、状态台账和告警。
  - 原因：Outbox 已能重试和人工重放，但运维侧还不能直接查询 INIT/FAILED/DEAD 明细，也没有专门积压和 DEAD 增长告警。
  - 范围：运维查询接口、响应 DTO、Micrometer 指标、Prometheus 告警规则。
  - 验收：能查询 Outbox 状态明细，指标和告警能发现失败积压。

## 所有未完成任务清单

| 任务名称 | 当前状态 | 所属类型 | 优先级 | 不完成的影响 | 当前为什么还没做 | 后续触发条件 |
| --- | --- | --- | --- | --- | --- | --- |
| RocketMQ/Kafka/Pulsar adapter 实现 | 暂不处理 | 生产边界 / 代码风险 | P0 | Redis Stream 仍不能包装成大促终局方案 | 本轮决策认为单机 adapter 不能证明生产能力，且当前端口/契约已足够支撑后续切换 | 有独立 MQ 环境，或明确接受本机 profile 只做演示 |
| 真实多实例容量验证 | 已阻塞 | 生产边界 | P0 | 无法证明生产 QPS、堆积恢复和 broker 故障恢复 | 当前只有单机环境 | 有独立 Linux 压测机、多服务实例和独立 MQ 集群 |
| Outbox 查询台账和告警 | 未开始 | 运维边界 | P1 | 失败消息可重试但不够容易观察 | 本轮先做 RocketMQ adapter 决策 | 开始完善补偿后台或监控告警 |
| 八股文档细粒度短板同步 | 进行中 | 面试口径 | P1 | 容易把“不接本机 RocketMQ”误讲成“消息系统没设计” | 需要每轮同步 | 每次新增 MQ adapter 或变更消息链路 |

## 面试口径

可以这样说：

> 我没有在本机硬接一个 RocketMQ adapter，因为单 broker 只能证明 SDK 能跑，不能证明生产容量。当前项目已经具备端口隔离、稳定 Envelope、Outbox 和专业 MQ key/tag/partition key 契约。下一步如果有真实环境，会按这个契约接 RocketMQ/Kafka/Pulsar；在当前单机条件下，我更优先补 Outbox 查询台账和告警，因为它能真实提升补偿可观测性。
