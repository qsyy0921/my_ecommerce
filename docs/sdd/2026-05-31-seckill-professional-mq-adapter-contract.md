# 秒杀专业 MQ Adapter 契约设计和测试

## 背景

当前前 5 风险的第一项已经从“Outbox 是否存在”收敛为“专业 MQ adapter、Outbox 运维台账和真实容量证明”。本轮只处理其中最高价值的一部分：先把 RocketMQ/Kafka/Pulsar 这类专业 MQ adapter 需要遵守的 producer/consumer 契约固化下来，并补可执行测试。

本轮不引入 RocketMQ/Kafka/Pulsar 客户端依赖，也不改变当前 Redis Stream / RabbitMQ / Outbox 运行链路。

原因是：本机单机环境即使接入一个 MQ 客户端，也只能证明代码能启动，不能证明生产吞吐、分区副本、堆积恢复和 broker 故障恢复。当前更有价值的是先把消息 key、tag、partition key 和消费幂等规则固定住，避免后续 adapter 直接依赖领域实体内部 JSON。

## 当前代码证据

- `SeckillOrderCreateMessageEntity` 已是订单创建消息 Envelope。
- `ISeckillOrderMessagePort` 保持业务语义，只表达“发布秒杀订单创建消息”。
- `SeckillOrderMessagePort` 负责把 `SeckillOrderEntity` 转成 Envelope JSON。
- `SeckillOrderOutboxPublishSupport` 已实现“先写 Outbox，再即时投递，失败由重试任务补偿”。
- `SeckillOrderCreateListener` 和 `SeckillOrderCreateBufferWorker` 都把 Envelope 转回订单实体，再复用 `ISeckillService` 创建订单。

## Producer 契约

专业 MQ producer adapter 必须遵守以下契约：

| MQ 概念 | 当前契约字段 | 约束 |
| --- | --- | --- |
| message body | Envelope JSON | 必须是 `SeckillOrderCreateMessageEntity` 序列化结果 |
| schema | `schemaVersion` | 当前固定为 `1.0` |
| event tag | `stableEventTag()` | 默认 `SECKILL_ORDER_CREATE`，用于 RocketMQ tag 或 Kafka header |
| message key | `stableMessageKey()` | 默认 `activityId:userId:outTradeNo`，用于 producer、consumer、Outbox 和补偿幂等 |
| partition key | `stablePartitionKey()` | 默认等于 `routeKey`，用于 RocketMQ message key、Kafka partition key 或 Pulsar ordering key |
| trace | `traceId` | 写入 MQ header 或 message property |
| accepted 语义 | Outbox 写入成功 | Outbox 写入成功后，即时投递失败也返回 accepted，由重试任务补偿 |

producer adapter 不允许：

- 重新生成随机 message id。
- 直接序列化 `SeckillOrderEntity` 裸订单 JSON。
- 绕过 `seckill_order_outbox` 自行发送。
- 把 RocketMQ/Kafka/Pulsar 客户端对象暴露给 domain。

## Consumer 契约

专业 MQ consumer adapter 必须遵守以下契约：

1. 解析 Envelope JSON，兼容历史裸订单 JSON。
2. 消费幂等键优先使用 `stableMessageKey()`，并落到 `sourceMessageId`。
3. 只调用 `ISeckillService#createSeckillOrder(...)` 或批量创建端口，不直接写 DAO。
4. 订单创建成功后再 ACK / commit offset。
5. 可重试异常进入 broker retry；超过阈值进入 DLQ 或人工补偿队列。
6. 重放同一消息必须依赖 `messageId/sourceMessageId`、DB 唯一索引和消费幂等表保证不重复落单。

## 本轮实现

- `SeckillOrderCreateMessageEntity` 新增：
  - `stableMessageKey()`：专业 MQ message key 和消费幂等键。
  - `stablePartitionKey()`：专业 MQ 分区/顺序路由键。
  - `stableEventTag()`：专业 MQ event tag。
- `SeckillOrderCreateMessageContractTest` 新增专业 MQ 契约测试：
  - 验证 message key 稳定。
  - 验证 partition key 稳定。
  - 验证 event tag 稳定。
  - 验证历史裸订单 JSON 也能重建专业 MQ 所需 key/tag。

## Done List

- [x] 固化专业 MQ producer 的 key/tag/partition key 契约。
  - 文件：`SeckillOrderCreateMessageEntity.java`
  - 验证：`SeckillOrderCreateMessageContractTest`
  - 提交：`40e67db`

- [x] 补专业 MQ adapter 消息契约测试。
  - 文件：`SeckillOrderCreateMessageContractTest.java`
  - 验证：`powershell -NoProfile -ExecutionPolicy Bypass -File scripts\verify-current-baseline.ps1 -ProfileName seckill`
  - 提交：`40e67db`

- [x] 记录专业 MQ producer/consumer 设计边界。
  - 文件：`docs/sdd/2026-05-31-seckill-professional-mq-adapter-contract.md`
  - 验证：`powershell -NoProfile -ExecutionPolicy Bypass -File scripts\verify-current-baseline.ps1 -ProfileName docs-only`
  - 提交：`40e67db`

## TODO List

- [x] P0：评估是否在本机实现 RocketMQ adapter 的最小 profile。
  - 原因：契约已固化，但真正的专业 MQ producer/consumer adapter 仍未实现。
  - 结论：后续文档已决策本机暂不实现，见 `2026-05-31-seckill-rocketmq-adapter-profile-decision.md`。
  - 验收：明确 adapter 触发条件，避免把单机 profile 包装成生产容量证明。

- [x] P1：补秒杀 Outbox 查询、状态台账和告警。
  - 原因：Outbox 有重试闭环，但运维侧还不能直接查询 INIT/FAILED/DEAD 明细。
  - 范围：运维查询接口、响应 DTO、Micrometer 指标、Prometheus 告警规则。
  - 验收：能查询 Outbox 状态明细，指标和告警能发现失败积压。

## 所有未完成任务清单

| 任务名称 | 当前状态 | 所属类型 | 优先级 | 不完成的影响 | 当前为什么还没做 | 后续触发条件 |
| --- | --- | --- | --- | --- | --- | --- |
| RocketMQ/Kafka/Pulsar adapter 实现 | 暂不处理 | 生产边界 / 代码风险 | P0 | Redis Stream 仍不能包装成大促终局方案 | 后续文档已决策单机 adapter 不能证明生产能力，当前端口/契约已足够支撑后续切换 | 有独立 MQ 环境，或明确接受本机 profile 只做演示 |
| 专业 MQ 真实容量证明 | 已阻塞 | 生产边界 | P0 | 无法证明生产 QPS、堆积恢复和 broker 故障恢复 | 当前只有单机环境 | 有独立 Linux 压测机、多服务实例和独立 MQ 集群 |
| Outbox 查询台账和告警 | 已完成 | 运维边界 | P1 | 已能查询状态明细和数量，并通过指标/告警发现积压与 DEAD 增长 | 已在 `2026-05-31-seckill-outbox-ops-observability.md` 闭环 | 后续只在建设完整运维后台时扩展页面和批量处理 |
| 八股文档细粒度短板同步 | 进行中 | 面试口径 | P1 | 容易把“契约已固化”误讲成“专业 MQ 已完成” | 需要每轮同步 | 每次新增 MQ adapter 或变更消息链路 |

## 面试口径

可以这样说：

> 我没有直接把 RocketMQ 依赖堆进项目里，而是先做专业 MQ 契约。现在秒杀订单消息有稳定 Envelope，message key、event tag 和 partition key 都有可执行测试；producer 后续必须用这个 key 做投递和 Outbox 幂等，consumer 必须用同一个 key 做消费幂等和重放。当前还不能说专业 MQ 已落地，因为 RocketMQ/Kafka/Pulsar adapter、consumer group 堆积恢复和真实多机压测还没有完成。
