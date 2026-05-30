# 对账差错处理闭环记录

## 背景

对账中心已经可以扫描商城订单、支付流水、退款流水、三方账单和 MQ 失败台账，并生成差错单。但原有处理模型还偏粗：差错单状态是裸数字，人工操作只有通用 `handle`，操作日志只能写不能查，忽略后的差错单仍可能被定时扫描再次刷新。

本轮目标是在本机条件下把“差错单处理闭环”做完整：人工确认、重放、忽略、关闭、备注和审计都可操作、可查询、可回归验证。

## 规格

- 差错单状态必须领域化，不在业务代码中散落裸数字。
- 终态差错单包括已确认、已忽略、已关闭，定时扫描不能把这些终态差错单重新刷成待处理。
- 自动重放只能对待处理差错单执行，避免已经忽略或关闭的差错单再次触发副作用。
- 后台必须支持单条确认、忽略、关闭、备注、重放和操作日志查询。
- 所有人工操作写入 `reconcile_operation_log`，可按差错单号查询。

## 设计

### 状态模型

新增 `ReconcileCaseStatusVO`：

- `OPEN(0)`：待处理。
- `CONFIRMED(1)`：已确认处理。
- `IGNORED(2)`：已忽略。
- `CLOSED(3)`：已关闭。

`reconcile_case` 的 upsert 逻辑保留终态：当 `case_status in (1, 2, 3)` 时，不覆盖状态、不增加重试次数、不刷新更新时间。

### 服务能力

`IOrderReconcileService` 新增：

- `confirmReconcileCase`
- `ignoreReconcileCase`
- `closeReconcileCase`
- `remarkReconcileCase`
- `queryReconcileOperationLogList`

`OrderReconcileService#replayReconcileCase` 先查询差错单，只有 `OPEN` 才允许执行重放。

### 后台入口

`ReconcileCaseController` 新增：

- `/api/v1/reconcile/confirm`
- `/api/v1/reconcile/ignore`
- `/api/v1/reconcile/close`
- `/api/v1/reconcile/remark`
- `/api/v1/reconcile/operation_logs`

前端 `reconcile-admin.html` 支持确认、忽略、关闭、备注、日志查询和批量关闭。

## 验收

- 商城服务 JDK 1.8 编译通过。
- `DomainPurityTest` 增加对账闭环架构守护，检查显式接口、状态枚举、终态保护和重放前置查询。
- domain purity 继续通过。

## 边界

本机已完成接口、表模型和页面闭环。生产级后台还需要接入统一登录、权限审批、SLA 报表、操作人身份不可伪造和正式告警通知路由。
