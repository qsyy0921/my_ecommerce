# 2026-05-30 商城对账后台 HTTP 用例支撑拆分 SDD 记录

## 背景

`ReconcileCaseController` 已经拆出管理员鉴权、操作人解析、操作审计、查询 DTO 映射等支撑组件，但 Controller 仍然直接编排扫描、查询、处理、确认、忽略、关闭、备注、批量处理、重放、批量重放、账单导入和告警 webhook。对账后台入口继续承载这些用例流程，会让 HTTP 层和对账应用能力耦合，后续补权限审批、SLA、批量重放或操作审计报表时容易重新变成大类。

## 目标

- Controller 只保留 HTTP 路由和请求体类型。
- 对账扫描/查询、差错处理、重放、账单导入、告警 webhook 拆到独立 usecase support。
- 请求体从 Controller 内部静态类移动为独立 trigger request 类。
- 架构测试防止对账服务、管理员支撑、查询支撑、JSON 序列化、审计和批量循环重新回流到 Controller。

## 拆分设计

- `ReconcileCaseQueryEndpointSupport`：扫描、差错单列表、操作日志查询。
- `ReconcileCaseOperationSupport`：处理、确认、忽略、关闭、备注、批量处理。
- `ReconcileCaseReplaySupport`：单条/批量重放。
- `ReconcileBillImportSupport`：三方账单导入。
- `ReconcileAlertWebhookSupport`：Alertmanager webhook 接收。
- `ReconcileHandleRequest`、`ReconcileBatchHandleRequest`、`ReconcileReplayRequest`、`ReconcileBatchReplayRequest`：HTTP 请求体。

## 验收

- `ReconcileCaseController` 不再直接依赖 `IOrderReconcileService`、`ReconcileAdminSupport`、`ReconcileQuerySupport` 和 `JSON.toJSONString`。
- `ReconcileCaseController` 不再出现审计、批量循环、账单导入和重放编排细节。
- JDK 1.8 编译、domain purity、`DomainPurityTest` 和对账契约测试通过。
