# 秒杀订单 Outbox 代码闭环

## 背景

秒杀订单创建消息已经升级为稳定 Envelope，但此前 `seckill_order_outbox` 只有 SQL 表结构，没有代码闭环。这样后续切 RocketMQ/Kafka 时，入口仍缺少“DB 已接收消息、后台投递补偿”的可靠边界。

本轮目标是补齐本机可验证的最小 Outbox 闭环，不直接接入 RocketMQ/Kafka 集群。

## 设计原则

- 锁单入口先写 Outbox，再尝试即时投递。
- Outbox 写入成功后，即时投递失败也返回成功，由后台任务继续补投，避免 Redis 资格回滚后又被重试消息创建订单。
- Outbox 写入失败才返回失败，让锁单链路按原逻辑回滚 Redis 资格。
- 定时任务只重试 `INIT/FAILED` 且到达 `next_retry_time` 的记录。
- 人工重放接口可以重放 `INIT/FAILED/DEAD` 记录，支持失败隔离后的人工恢复。
- 消费端继续使用 Envelope `messageId` 做幂等，重复投递不会重复落单。

## 实现

- 新增领域端口和服务：
  - `ISeckillOrderOutboxPort`
  - `ISeckillOrderOutboxService`
  - `SeckillOrderOutboxService`
  - `SeckillOrderOutboxEntity`
- 新增基础设施：
  - `ISeckillOrderOutboxDao`
  - `SeckillOrderOutbox`
  - `seckill_order_outbox_mapper.xml`
  - `SeckillOrderOutboxPort`
  - `SeckillOrderOutboxMapper`
  - `SeckillOrderMessagePublisherSupport`
  - `SeckillOrderOutboxPublishSupport`
  - `SeckillOrderOutboxRetrySupport`
- 新增补偿入口：
  - `SeckillOrderOutboxRetryJob`
  - `MqOpsController#retry_seckill_order_outbox`
  - `MqOpsSupport#retrySeckillOrderOutbox`
- 更新配置：
  - `application-dev.yml`
  - `application-prod.yml`
- 更新 SQL 状态说明：
  - `0-init`
  - `1-sent`
  - `2-failed`
  - `3-dead`

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

- [x] 补齐 Outbox 领域端口、实体和服务。
  - 文件：`ISeckillOrderOutboxPort.java`、`SeckillOrderOutboxEntity.java`、`ISeckillOrderOutboxService.java`、`SeckillOrderOutboxService.java`
  - 验证：`market-domain` profile 通过。

- [x] 补齐 Outbox DAO、PO、Mapper 和基础设施适配。
  - 文件：`ISeckillOrderOutboxDao.java`、`SeckillOrderOutbox.java`、`seckill_order_outbox_mapper.xml`、`SeckillOrderOutboxPort.java`、`SeckillOrderOutboxMapper.java`
  - 验证：`seckill` profile 编译并运行通过。

- [x] 发布端改成 Outbox 优先。
  - 文件：`SeckillOrderMessagePort.java`、`SeckillOrderOutboxPublishSupport.java`、`SeckillOrderMessagePublisherSupport.java`
  - 验收：Outbox 写入失败才返回失败；Outbox 写入成功后即时投递失败由后台补偿。

- [x] 补齐自动重试和人工重放入口。
  - 文件：`SeckillOrderOutboxRetrySupport.java`、`SeckillOrderOutboxRetryJob.java`、`MqOpsController.java`、`MqOpsSupport.java`
  - 验收：定时任务重试到期记录；管理接口可手动重放 `INIT/FAILED/DEAD`。

- [x] 补齐 Outbox 重试单元测试。
  - 文件：`SeckillOrderOutboxRetrySupportUnitTest.java`
  - 验收：覆盖投递成功标记 sent、达到最大重试转 dead、人工重放 dead 记录。

## TODO List

- [ ] P0：补专业 MQ adapter 的 producer/consumer 契约设计和测试。
  - 原因：Outbox 已能保证本机可靠投递边界，但还没有 RocketMQ/Kafka/Pulsar adapter，也没有 consumer group、DLQ、lag 和堆积恢复验证。
  - 范围：`docs/sdd`、消息 adapter 边界、consumer 幂等契约测试；暂不直接声称生产容量完成。
  - 验收：明确 adapter 切换条件、topic/tag/key/routeKey 规则、consumer 幂等重放契约和回滚路径。

## 所有未完成任务清单

| 任务名称 | 当前状态 | 所属类型 | 优先级 | 不完成的影响 | 当前为什么还没做 | 后续触发条件 |
| --- | --- | --- | --- | --- | --- | --- |
| RocketMQ/Kafka/Pulsar adapter 实现 | 暂不处理 | 生产边界 / 代码风险 | P0 | Redis Stream 仍不能包装成大促终局方案 | 后续已完成专业 MQ 契约并决策单机暂不实现 adapter | 有独立 MQ 环境，或明确接受本机 profile 只做演示 |
| 秒杀 Outbox 查询、状态台账和告警 | 未开始 | 业务边界 / 运维边界 | P1 | 目前有自动重试和手动重放，但没有专门查询接口、pending/dead 指标和告警展示 INIT/FAILED/DEAD 明细 | 本轮优先完成 RocketMQ adapter profile 决策，尚未进入运维台账实现 | 明确要完善补偿后台、运维页面或 Outbox 告警 |
| 真实多实例容量验证 | 已阻塞 | 生产边界 | P0 | 本机 QPS 不能证明生产容量 | 只有当前单机环境 | 有独立 Linux 压测机、多服务实例和独立中间件节点 |

## 面试口径

可以说：

> 秒杀下单消息现在是“Redis 资格预扣 + Envelope + Outbox + 异步队列”的结构。入口抢到资格后先写 `seckill_order_outbox`，Outbox 写成功就认为消息已被系统可靠接收；即时投递 Redis Stream/RabbitMQ 失败时，不回滚资格，而是由定时任务和人工重放接口继续补投。这样避免了“资格回滚但消息后来又创建订单”的不一致。当前仍不能说专业 MQ 完成，因为 RocketMQ/Kafka adapter、真实 broker 堆积恢复和多机容量验证还没落地。
