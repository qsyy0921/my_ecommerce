# MQ 发布器支撑组件拆分

## 背景

`EventPublisher` 是 RabbitMQ 生产者适配器。它当前同时承担：

- RabbitTemplate 发送、mandatory、confirm callback 和 returns callback。
- 业务消息 ID 生成。
- 发送失败时写入 `mq_message_record` 失败台账。
- 失败台账 PO 构建、重复键处理和错误信息截断。

发送器继续直接持有 DAO/PO，会让消息发送链路和失败补偿表结构耦合。后续接 RocketMQ/Kafka outbox 或调整 messageId 规则时，容易影响 RabbitMQ 发布主链路。

## 目标

- `EventPublisher` 保留 RabbitMQ 发布职责。
- 消息 ID 生成拆成独立组件。
- 生产者发送失败台账拆成独立组件。
- 保持 `publish`、`publishToExchange`、`publishWithoutConfirm` 对外方法不变。
- 增加架构测试，防止 DAO/PO、MessageDigest 和失败记录构建回流到 `EventPublisher`。

## 设计

### MqMessageIdGenerator

负责根据 `exchange + routingKey + message` 生成稳定 SHA-256 messageId。该规则同时被发布器和失败台账兜底复用。

### MqProducerFailureRecorder

负责：

- 查询发送失败台账是否已存在。
- 新失败写入 `mq_message_record`，queueName 使用 `routing:{routingKey}` 保留重投路由。
- 已存在失败记录更新错误摘要。
- 捕获重复键和台账记录异常，避免二次异常吞掉原始发送异常。

### EventPublisher

负责：

- 初始化 RabbitTemplate mandatory、confirm callback 和 returns callback。
- 发布普通消息和不等待 confirm 的秒杀削峰消息。
- 发送失败时调用 `MqProducerFailureRecorder` 记录台账。

## 验收

- JDK 1.8 编译通过。
- `DomainPurityTest` 通过。
- 新增 `MqProducerFailureRecorderUnitTest` 覆盖：
  - 新失败记录写入失败台账。
  - 已存在失败记录只更新错误。
  - 未传 messageId 时用生成器兜底。
- 架构测试防止 `EventPublisher` 重新依赖 `IMqMessageRecordDao`、`MqMessageRecord`、`DuplicateKeyException`、`MessageDigest` 和失败记录构建细节。

## 面试表达

> MQ 生产者发送失败不是直接打印日志就结束，而是写入失败台账，由补偿任务后续重投。我把 RabbitMQ 发布、消息 ID 生成、失败台账记录拆开，发布器只关注 broker 交互，失败记录组件关注本地补偿表。这样后续替换专业 MQ 或引入 outbox 时，核心发送入口不用被表结构和补偿策略污染。
