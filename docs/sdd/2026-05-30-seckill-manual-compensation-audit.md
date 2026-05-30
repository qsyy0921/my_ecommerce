# 2026-05-30 秒杀人工补偿审计 SDD 记录

## 背景

秒杀 Redis Stream 链路已经具备 pending-list 接管、失败隔离到人工补偿 Stream、补偿台查询和重放能力。但补偿台重放前后只有应用日志，没有持久化操作台账。

这会带来两个问题：

- 面试表达里可以说“可查询、可重放”，但“可审计”证据不足。
- 本机排查时无法追踪是谁在什么时候查询或重放了哪些人工补偿消息。

## 规格

- 新增 `seckill_manual_compensation_log` 表，记录补偿台查询和重放操作。
- 新增 `ISeckillManualCompensationAuditPort`，让 trigger 通过领域端口写审计，不直接依赖 DAO。
- `SeckillOpsController` 在查询人工补偿消息和重放消息后记录审计日志。
- `SeckillOpsController` 新增 `manual_logs` 接口，支持查看最近操作记录。
- 前端 `seckill-ops.html` 增加补偿操作记录表。
- 架构测试守住 `SeckillOpsController` 只能通过人工补偿领域端口工作，不允许直接依赖 Redis Stream 实现类。

## 接口

```text
GET /api/v1/gbm/seckill/ops/manual_messages
GET /api/v1/gbm/seckill/ops/manual_logs
POST /api/v1/gbm/seckill/ops/replay_manual
```

统一要求：

- Header `x-admin-token`：本机运维口令。
- Header `x-admin-operator`：操作人，默认 `local-admin`。

## 数据模型

```text
seckill_manual_compensation_log
  id
  operator
  operation_type      QUERY_MANUAL / REPLAY_MANUAL
  message_ids         被重放的人工补偿 Stream 消息 ID 快照
  request_limit       查询或批量重放上限
  manual_stream_key   人工补偿 Stream key
  result_count        查询或重放结果数量
  success             1 成功 / 0 失败
  error_message
  create_time
```

SQL 文件：

```text
docs/sql/2026-05-30-seckill-manual-compensation-log.sql
```

## 代码变更

- 新增 `ISeckillManualCompensationAuditPort`。
- 新增 `SeckillManualCompensationLogEntity`。
- 新增 `ISeckillManualCompensationLogDao`、`SeckillManualCompensationLog`、`SeckillManualCompensationAuditPort` 和 MyBatis mapper。
- `SeckillOpsController` 增加补偿操作审计和 `manual_logs` 查询接口。
- `seckill-ops.html` / `seckill-ops.js` 增加操作记录查询和展示。
- `DomainPurityTest` 增加运维 Controller 边界守护。

## 验收标准

- 人工补偿消息仍可查询。
- 单条或批量人工补偿消息仍可重放。
- 查询和重放操作写入 `seckill_manual_compensation_log`。
- 前端补偿台可查看最近操作记录。
- JDK 1.8 编译通过。
- domain purity 脚本通过。
- 架构测试和状态机测试通过。

## 边界

当前补齐的是本机可实现的审计闭环。生产上还需要权限分级、审批流、SLA 统计、操作结果回调、告警升级和值班通知。
