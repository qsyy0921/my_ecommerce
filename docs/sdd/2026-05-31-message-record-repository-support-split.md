# MQ 记录仓储支撑组件拆分

## 背景

`MessageRecordRepository` 是 MQ 消费幂等和生产者失败重试的基础设施适配器。当前类同时承担：

- `MessageRecordEntity` 与 `MqMessageRecord` 的双向映射。
- 消费幂等记录查询、插入和状态更新。
- 生产者发送失败消息扫描和重投。
- routing key 解析、错误信息截断和重投失败状态更新。

这些逻辑都属于基础设施层，但不是同一种职责。继续堆在仓储门面里，后续扩展 RocketMQ/Kafka producer outbox、DLQ 重放或消息 schema 版本时，容易让 `MessageRecordRepository` 重新变成消息可靠性大类。

## 目标

- 不改领域接口 `IMessageRecordRepository`，保持应用层和领域层调用契约稳定。
- `MessageRecordRepository` 只保留幂等记录读写门面和状态更新委托。
- PO/Entity 映射收敛到独立 mapper。
- 生产者失败重投收敛到独立 support，隔离 `EventPublisher`、routing key 解析和失败截断。
- 增加架构测试，防止重投技术细节回流到仓储门面。

## 设计

### MessageRecordMapper

负责：

- `MqMessageRecord -> MessageRecordEntity`
- `MessageRecordEntity -> MqMessageRecord`
- `List<MqMessageRecord> -> List<MessageRecordEntity>`
- 构造失败更新 PO

### MessageProducerRetrySupport

负责：

- 扫描生产者发送失败记录。
- 校验 exchange、routing key、message body。
- 将 `routing:{routingKey}` 还原成真实 routing key。
- 调用 `EventPublisher#publishToExchange` 重投。
- 重投成功更新成功状态，失败更新失败状态和错误摘要。

### MessageRecordRepository

保留：

- 根据 messageId 查询。
- 插入幂等记录。
- 更新 processing/success/fail。
- 查询失败消息列表。
- 委托生产者失败重试。

## 验收

- JDK 1.8 编译通过。
- `DomainPurityTest` 通过。
- 新增 `MessageProducerRetrySupportUnitTest` 覆盖：
  - 生产者失败消息成功重投。
  - 缺少 exchange/routing/body 时跳过。
  - 重投异常时写失败状态并截断错误信息。
- `DomainPurityTest` 防止 `MessageRecordRepository` 重新持有 `EventPublisher`、routing key 解析和错误截断细节。

## 面试表达

> MQ 幂等表不是简单插入一行记录，我把它拆成了三个层次：领域服务负责消费幂等语义，仓储门面负责记录读写，基础设施 support 负责生产者失败消息重投。这样后续从 RabbitMQ 扩展到 RocketMQ/Kafka outbox 时，消息重投和路由规则可以替换，不会污染领域服务和幂等记录仓储。
