# 商城 MQ 发布与记录支撑组件拆分

## 背景

营销服务的 MQ 发布器和消息记录仓储已经完成支撑组件拆分，但商城服务仍保留相同的旧结构：

- `EventPublisher` 同时承担 RabbitMQ 发布、消息 ID 生成、发送失败台账写入、DAO/PO 构建和错误截断。
- `MessageRecordRepository` 同时承担消费幂等记录读写、PO/Entity 映射、生产者失败消息重投、routing key 解析和错误截断。

商城服务是支付成功消息、订单状态变更和对账重放的入口，MQ 可靠性代码如果继续混在大类里，后续接 RocketMQ/Kafka outbox 或扩展对账补偿时会变成新的腐化点。

## 目标

- 不改商城领域接口和业务调用契约。
- `EventPublisher` 只保留 RabbitMQ 发布、confirm 和 returns callback。
- `MessageRecordRepository` 只保留 MQ 幂等记录读写和状态更新门面。
- 消息 ID 生成、生产者失败台账记录、生产者失败重投、PO/Entity 映射拆到独立支撑组件。
- 与营销服务 MQ 可靠性结构保持一致，降低两个服务后续维护成本。

## 设计

### 发布器支撑

- `MqMessageIdGenerator`：根据 `exchange + routingKey + message` 生成稳定 SHA-256 messageId。
- `MqProducerFailureRecorder`：负责写入或更新 `mq_message_record` 生产者失败台账。

### 记录仓储支撑

- `MessageRecordMapper`：负责 `MessageRecordEntity` 和 `MqMessageRecord` 映射。
- `MessageProducerRetrySupport`：负责扫描生产者失败消息，解析 `routing:{routingKey}`，调用发布器重投，并更新成功或失败状态。

## 验收

- 商城 JDK 1.8 编译通过。
- `DomainPurityTest` 通过，防止商城 MQ 发布器和记录仓储回流 DAO/PO、MessageDigest、routing key 解析和重投细节。
- 新增商城侧 `MessageProducerRetrySupportUnitTest` 和 `MqProducerFailureRecorderUnitTest`，覆盖重投成功、非法记录跳过、重投失败回写、新失败插入、已存在失败更新和缺失 messageId 兜底。

## 面试表达

> 商城侧 MQ 可靠性和营销侧保持同构：发布器只负责 RabbitMQ broker 交互，失败台账和重投逻辑由独立支撑组件负责，领域服务只看到消息幂等和重试语义。这样支付成功消息、对账重放和后续专业 MQ 演进不会把 DAO、PO、routing key 规则反向污染到业务层。
