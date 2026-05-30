# 商城对账仓储扫描与操作日志支撑拆分

## 背景

`OrderReconcileRepository` 已经拆出差错单工厂、MQ 重放、三方账单 CSV 解析和实体映射，但仍然直接承担：

- 多类对账扫描规则编排：商城订单中间态、MQ 失败、支付流水缺账单、退款流水缺账单、三方账单缺本地流水。
- 差错单批量 upsert 辅助逻辑。
- 对账操作日志写入、操作人兜底、请求/结果截断。
- 对账操作日志查询和 PO/Entity 映射。

这些逻辑都属于基础设施适配层，但不应该继续堆在仓储门面里。后续如果补 SLA、告警升级、批量扫描策略或操作审计报表，仓储会重新膨胀成对账大类。

## 目标

- 不改 `IOrderReconcileRepository` 领域端口契约。
- `OrderReconcileRepository` 保留对账仓储门面、查询、处理、备注、MQ 重放和账单导入委托。
- 对账扫描规则和差错单 upsert 拆到 `ReconcileCaseScanSupport`。
- 操作日志记录、截断和查询拆到 `ReconcileOperationLogSupport`。
- 增加架构守护，防止扫描规则和操作日志细节回流到仓储门面。

## 设计

### ReconcileCaseScanSupport

负责：

- 扫描支付成功但营销未结算。
- 扫描退款中间态超时。
- 扫描待支付超时。
- 扫描 MQ 消费失败。
- 扫描本地支付/退款流水缺三方账单。
- 扫描三方账单缺本地流水。
- 使用 `ReconcileCaseFactory` 构造差错单并 upsert。

### ReconcileOperationLogSupport

负责：

- 写入对账操作日志。
- 操作人为空时兜底为 `unknown`。
- 请求体和结果摘要截断。
- 查询操作日志并转换为领域实体。

## 验收

- 商城服务 JDK 1.8 编译通过。
- `DomainPurityTest` 通过，防止 `OrderReconcileRepository` 重新直接持有 `IPaymentFlowPort`、`IRefundFlowPort`、`IMqMessageRecordDao`、`IReconcileOperationLogDao`、差错扫描 helper 和日志截断细节。
- `scripts/check-domain-purity.ps1` 通过。

## 面试表达

> 对账仓储不是把所有扫描 SQL 和操作日志都堆在一个 Repository 里。我把对账端口门面、差错扫描、操作审计、MQ 重放、账单导入分开：仓储负责适配领域端口，扫描支撑负责生成差错单，日志支撑负责操作审计。这样后续补 SLA 报表、告警升级或批量处理，不会把对账仓储继续做成大类。
