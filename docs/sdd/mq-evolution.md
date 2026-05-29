# 秒杀消息队列演进方案

## 当前方案

当前秒杀入口使用 Redis Lua 抢资格，抢到后写 Redis Stream 分片队列，再由 Consumer Group 批量落库。

适用范围：

- 项目规模中等。
- Redis 已经承担库存预扣，链路短。
- 需要 pending-list、XAUTOCLAIM、人工补偿 Stream 做可靠消费。

主要风险：

- Redis 同时承担库存、结果缓存、用户防重和消息队列，热点活动下会资源争抢。
- Stream 缺少 Kafka/RocketMQ 那样成熟的副本、分区治理、堆积治理和跨机房能力。
- 大流量下 Redis 持久化和内存压力需要单独评估。

## 选型结论

生产演进目标选择 **RocketMQ**，Redis Stream 保留为本地压测和课程项目可运行方案，RabbitMQ 保留为拼团成团、退单这类跨服务业务通知。

选择 RocketMQ 的原因：

- 秒杀订单创建是交易型消息，RocketMQ 的重试、DLQ、消费组、顺序/分区路由和事务消息能力更贴近订单流。
- 相比 RabbitMQ，RocketMQ 更适合高吞吐订单创建、堆积恢复和按 key 路由到队列。
- 相比 Kafka，RocketMQ 在交易消息、延迟消息、重试语义和 Java/Spring 交易系统表达上更直接。
- Redis 应该收敛为库存资格预扣、用户防重和短期结果缓存，不继续承担长期订单消息队列职责。

最终职责拆分：

```text
Redis：库存预扣 / 用户防重 / 短期结果缓存
RocketMQ：秒杀订单创建消息、延迟关闭、失败重试、DLQ
RabbitMQ：拼团成团通知、退单通知等现有业务通知
MySQL Outbox：RocketMQ 投递失败兜底和人工重放
```

## 演进目标

秒杀入口保持：

```text
限流 -> 活动缓存 -> Redis Lua 抢资格 -> 快速返回 PROCESSING
```

订单创建消息从 Redis Stream 演进为专业 MQ：

```text
Redis Lua 抢资格 -> Outbox/事务消息 -> RocketMQ -> 批量落库 -> 结果缓存
```

## 推荐路线

第一阶段：保留 Redis Stream。

- 使用 Stream 分片。
- 使用 pending-list 和人工补偿 Stream。
- 使用消费幂等和库存流水。
- 适合当前单机/小规模多实例演示。

第二阶段：引入可靠 Outbox。

- 入口抢资格后写本地 outbox 或 Redis Stream。
- 后台投递专业 MQ。
- 投递失败由 outbox 状态重试。
- 入口线程不等待 broker confirm。

第三阶段：替换为 RocketMQ。

- 按 `activityId + userId + outTradeNo` 路由到分区。
- 消费端按分区批量落库。
- 保留唯一索引、库存流水、结果缓存和补偿台。
- 使用 MQ 自带堆积、重试、DLQ、消费组和分区扩容能力。
- 本地已提供 `docs/dev-ops/docker-compose-rocketmq.yml`，用于后续把 `seckill_order_outbox` 投递到 RocketMQ。

## 面试说法

当前项目本地仍使用 Redis Stream，是因为单机演示环境下它能覆盖可靠削峰和 pending 补偿。生产大促场景下，我会把 Redis 的职责收敛到库存资格预扣，把订单创建消息迁移到 RocketMQ，并通过 outbox、消费幂等、库存流水和补偿台保证最终一致。RabbitMQ 不下线，继续负责拼团成团和退单这类业务通知。
