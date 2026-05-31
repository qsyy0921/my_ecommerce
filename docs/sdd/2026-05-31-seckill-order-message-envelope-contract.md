# 秒杀订单创建消息 Envelope 契约

## 背景

上一轮已经确认秒杀锁单主流程通过 `ISeckillOrderMessagePort` 和具体消息中间件解耦，但消息体仍需要稳定契约。否则后续从 Redis Stream / RabbitMQ 演进到 RocketMQ、Kafka 或 Pulsar 时，消费端可能继续依赖 `SeckillOrderEntity` 的内部字段结构，导致 MQ 替换变成业务模型兼容问题。

本轮目标是补齐最小消息 Envelope 和契约测试，不直接接入 RocketMQ adapter。

## 规格

`SeckillOrderCreateMessageEntity` 作为秒杀订单创建消息 Envelope，字段分为两类：

- 消息路由和幂等字段：`schemaVersion`、`eventType`、`messageId`、`routeKey`、`activityId`、`userId`、`outTradeNo`、`orderId`、`source`、`channel`、`goodsId`、`traceId`、`occurredAt`。
- 订单创建负载字段：`activityName`、`goodsName`、`originalPrice`、`seckillPrice`、`status`、`stockBucket`、`stockBefore`、`stockAfter`、`createTime`。

核心约束：

- `schemaVersion = 1.0`。
- `eventType = SECKILL_ORDER_CREATE`。
- `messageId = activityId:userId:outTradeNo`，作为 producer、consumer、outbox、补偿台的统一幂等键。
- `routeKey` 默认与 `messageId` 一致，后续 RocketMQ/Kafka 分区路由复用同一语义。
- `stableMessageKey()`、`stablePartitionKey()`、`stableEventTag()` 已作为专业 MQ adapter 的最小可执行契约。
- Envelope 可转换回 `SeckillOrderEntity`，消费端仍复用现有订单创建端口和批量落库逻辑。
- 兼容旧 JSON：如果历史消息只有订单字段、没有 Envelope 字段，消费端仍可以解析并生成稳定幂等键。

## 实现

- 新增领域消息契约：`group-buy-market-domain/src/main/java/cn/bugstack/domain/seckill/model/entity/SeckillOrderCreateMessageEntity.java`
- 修改消息发布适配器：`SeckillOrderMessagePort` 发布 Envelope JSON，不再直接发布裸 `SeckillOrderEntity`。
- 修改 Redis Stream worker：`SeckillOrderCreateBufferWorker` 解析 Envelope 后转成订单实体。
- 修改 RabbitMQ listener：`SeckillOrderCreateListener` 用 Envelope `messageId` 作为消费幂等键，异常路径回退到 AMQP message id。
- 新增契约测试：`SeckillOrderCreateMessageContractTest`
- 更新验证脚本：`scripts/verify-current-baseline.ps1` 的 `seckill` profile 纳入 Envelope 契约测试。

## 验证

```powershell
cd E:\java\group_buy_market
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\verify-current-baseline.ps1 -ProfileName seckill
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\verify-current-baseline.ps1 -ProfileName market-domain
```

验证结果：

- 当前 `seckill` profile：25 tests passed。
- `market-domain` profile：54 tests passed，domain purity check passed。

## Done List

- [x] 定义秒杀订单创建消息 Envelope。
  - 文件：`SeckillOrderCreateMessageEntity.java`
  - 验收：schema 版本、event type、messageId、routeKey 和订单字段映射稳定。

- [x] 发布端改为发布 Envelope JSON。
  - 文件：`SeckillOrderMessagePort.java`
  - 验收：RabbitMQ、Redis Stream、Redis Queue、本地队列复用同一 Envelope。

- [x] 消费端兼容 Envelope 和历史裸订单 JSON。
  - 文件：`SeckillOrderCreateBufferWorker.java`、`SeckillOrderCreateListener.java`
  - 验收：消费幂等键优先使用 Envelope `messageId`，异常兜底仍能回退。

- [x] 补齐消息契约测试。
  - 文件：`SeckillOrderCreateMessageContractTest.java`
  - 验收：覆盖 schema、routeKey、JSON round trip、旧消息兼容。

- [x] 后续已补齐 Outbox 代码闭环。
  - 文件：`docs/sdd/2026-05-31-seckill-order-outbox-code-closure.md`
  - 验收：当前 `seckill` profile 通过 25 个测试。

- [x] 后续已补专业 MQ adapter key/tag/partition key 契约测试。
  - 文件：`docs/sdd/2026-05-31-seckill-professional-mq-adapter-contract.md`
  - 验收：`SeckillOrderCreateMessageContractTest` 覆盖 message key、partition key 和 event tag。

## TODO List

- [x] P0：评估并决定是否实现 RocketMQ adapter 最小 profile。
  - 原因：Envelope、Outbox 和专业 MQ key/tag/partition key 契约已完成，但还没有 RocketMQ/Kafka/Pulsar adapter，也没有 consumer group、DLQ、lag 和堆积恢复验证。
  - 结论：本轮决策暂不实现，见 `2026-05-31-seckill-rocketmq-adapter-profile-decision.md`。
  - 验收：明确 adapter 触发条件，避免把单机 profile 包装成生产容量证明。

- [ ] P1：补秒杀 Outbox 查询、状态台账和告警。
  - 原因：Outbox 已有重试和人工重放，但运维侧还不能直接查询 INIT/FAILED/DEAD 明细。
  - 范围：运维查询接口、响应 DTO、Micrometer 指标、Prometheus 告警规则。
  - 验收：能查询 Outbox 状态明细，指标和告警能发现失败积压。

## 所有未完成任务清单

| 任务名称 | 当前状态 | 所属类型 | 优先级 | 不完成的影响 | 当前为什么还没做 | 后续触发条件 |
| --- | --- | --- | --- | --- | --- | --- |
| RocketMQ/Kafka/Pulsar adapter 实现 | 暂不处理 | 生产边界 / 代码风险 | P0 | Redis Stream 仍不能包装成大促终局方案 | 本轮决策认为单机 adapter 不能证明生产能力，且当前端口/契约已足够支撑后续切换 | 有独立 MQ 环境，或明确接受本机 profile 只做演示 |
| 秒杀 Outbox 查询、状态台账和告警 | 未开始 | 业务边界 / 运维边界 | P1 | 目前有自动重试和手动重放，但没有专门查询接口、pending/dead 指标和告警展示 INIT/FAILED/DEAD 明细 | Outbox 投递闭环已优先完成，查询台账属于下一层运维体验 | 明确要完善补偿后台、运维页面或 Outbox 告警 |
| 真实多实例容量验证 | 已阻塞 | 生产边界 | P0 | 本机 QPS 不能证明生产容量 | 只有当前单机环境 | 有独立 Linux 压测机、多服务实例和独立中间件节点 |

## 面试口径

可以说：

> 秒杀订单创建消息已经从裸 `SeckillOrderEntity` JSON 升级成稳定 Envelope，里面包含 schemaVersion、eventType、messageId、routeKey、traceId 和订单创建负载，并且已经固化专业 MQ 需要的 message key、event tag 和 partition key。这样后续从 Redis Stream 切到 RocketMQ/Kafka 时，锁单主流程和消费端不需要依赖领域实体的内部序列化结构。但我不会说专业 MQ 已经完成，因为还缺 RocketMQ/Kafka adapter、真实多机压测和堆积恢复验证。
