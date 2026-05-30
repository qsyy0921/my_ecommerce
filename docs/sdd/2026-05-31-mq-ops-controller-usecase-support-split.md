# MQ 运维 Controller 用例支撑拆分

## 背景

`MqOpsController` 是营销服务 MQ 失败消息运维入口。当前它直接承担：

- admin token 配置和认证。
- 操作人兜底。
- 失败消息查询。
- 标记失败消息已处理。
- 重试生产者发送失败消息。
- 直接返回 `MessageRecordEntity` 领域对象。

这类逻辑堆在 HTTP Controller 中，会让运维入口继续膨胀；HTTP API 直接返回 domain entity 也会让领域模型和运维接口契约耦合。

## 目标

- `MqOpsController` 只保留 HTTP 路由和参数接收。
- admin token、操作人解析和拒绝响应进入 `MqOpsAdminSupport`。
- 查询、标记处理和生产者失败重试进入 `MqOpsSupport`。
- `MessageRecordEntity` 到 HTTP DTO 的转换进入 `MqOpsResponseAssembler`。
- API 返回 `MqFailedMessageResponseDTO`，不直接暴露领域对象。

## 验收

- 营销服务 JDK 1.8 编译通过。
- `DomainPurityTest` 通过，防止 `MqOpsController` 重新直接依赖 `IMessageRecordService`、`MessageRecordEntity`、`@Value`、`StringUtils` 和业务编排细节。
- 原接口路径保持不变：
  - `GET /api/v1/gbm/mq/ops/failed_messages`
  - `POST /api/v1/gbm/mq/ops/mark_handled`
  - `POST /api/v1/gbm/mq/ops/retry_producer_failed`

## 面试表达

> MQ 运维入口不是直接在 Controller 里查领域对象。我把 HTTP 路由、管理员认证、MQ 运维用例和响应 DTO 组装拆开。这样后续接统一权限、操作审计或批量补偿时，不会继续污染 Controller，也不会把领域对象变成外部 API 契约。
