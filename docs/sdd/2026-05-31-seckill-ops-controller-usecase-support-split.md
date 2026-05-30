# 秒杀补偿台 Controller 用例支撑拆分

## 背景

`SeckillOpsController` 是秒杀人工补偿台的 HTTP 入口。它当前直接承担：

- admin token 校验和操作人兜底。
- 查询人工补偿 Stream 消息。
- 重放人工补偿消息。
- 查询补偿操作日志。
- 审计日志构建和失败吞吐。
- JSON 序列化请求快照。
- 直接返回 `SeckillManualMessageEntity` 和 `SeckillManualCompensationLogEntity` 领域对象。

这些能力都堆在 HTTP Controller 中，会让补偿台入口变成新的大类。更重要的是，HTTP API 直接暴露 domain entity，会让领域模型和外部接口契约耦合。

## 目标

- Controller 只保留路由、请求参数接收和支撑组件委托。
- admin token、操作人解析和拒绝响应进入 `SeckillOpsAdminSupport`。
- 查询、重放、日志查询和审计写入进入 `SeckillManualCompensationOpsSupport`。
- domain entity 到 HTTP response DTO 的转换进入 `SeckillManualCompensationResponseAssembler`。
- API 返回 `SeckillManualMessageResponseDTO` 和 `SeckillManualCompensationLogResponseDTO`，不直接返回领域对象。

## 验收

- JDK 1.8 编译通过。
- `DomainPurityTest` 通过，防止 `SeckillOpsController` 重新直接依赖人工补偿领域端口、domain entity、`@Value`、`StringUtils`、FastJSON 和审计细节。
- 原接口路径保持不变：
  - `GET /api/v1/gbm/seckill/ops/manual_messages`
  - `POST /api/v1/gbm/seckill/ops/replay_manual`
  - `GET /api/v1/gbm/seckill/ops/manual_logs`

## 面试表达

> 补偿台不是简单开几个 Controller 接口。我把 HTTP 入口、管理员认证、补偿用例、审计记录和响应 DTO 分开，Controller 不直接暴露领域对象。这样后续接权限系统、审批流、批量重放 SLA 或前端后台页面时，不会把运营能力继续堆在 Controller 里。
