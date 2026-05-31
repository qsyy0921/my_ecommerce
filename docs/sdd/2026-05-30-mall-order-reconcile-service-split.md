# 2026-05-30 商城订单服务与对账服务拆分

## 背景

商城 `OrderService` 已经承担下单、支付回调、退款、营销结算补偿、对账扫描、差错单查询、人工处理、重放和三方账单导入。虽然功能可用，但职责过宽，继续扩展会让订单服务变成大类。

本轮目标是在不改变接口行为的前提下，把对账和差错处理职责从订单服务中拆出来。

## 设计

- 新增 `IOrderReconcileService`。
- 新增 `OrderReconcileService`，负责：
  - 支付成功但营销未结算的订单补偿。
  - 差错单扫描。
  - 差错单查询、处理、重放。
  - 对账操作审计。
  - 三方账单 CSV 导入。
- `IOrderService` 回归订单主链路：
  - 下单。
  - 支付状态变更。
  - 订单关闭。
  - 订单列表。
  - 退款。
- 新增 `IOrderReconcileRepository`，把差错单扫描、查询、处理、MQ 重放和三方账单导入从 `IOrderRepository` 中拆出。
- `IOrderRepository` 只保留订单主链路所需的订单查询、支付状态、关单、营销结算状态和退款方法。
- `ReconcileCaseController`、`OrderReconciliationJob`、`ReconcileCaseScanJob` 改注入 `IOrderReconcileService`。
- 对账重放仍可调用 `IOrderService.refundPayOrder(...)` 完成真实退款动作，避免复制退款逻辑。

## 额外修复

旧的 `replayReconcileCase("MARKET_SETTLEMENT_TIMEOUT")` 只调用拼团营销结算。本轮拆分时顺手补齐 `marketType=SECKILL` 的重放路由：秒杀订单走 `settlementSeckillPayOrder`，并把商城订单推进到营销结算完成。

## 验收

```powershell
$env:JAVA_HOME='C:\Program Files\Eclipse Adoptium\jdk-8.0.492.9-hotspot'
$env:Path="$env:JAVA_HOME\bin;E:\java\qsyy-ecommerce-platform\.tools\apache-maven-3.8.8\bin;$env:Path"

cd E:\java\qsyy-ecommerce-platform\qsyy-commerce-mall
mvn -q -DskipTests compile

cd E:\java\qsyy-ecommerce-platform
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\check-domain-purity.ps1
```

## 面试表达

> 商城订单服务早期为了快速闭环，把对账和差错重放也放在 `OrderService` 里。后续我把它拆成 `OrderReconcileService`，让订单服务只负责下单、支付、退款主链路，对账服务负责扫描、差错单、重放和三方账单导入。这样后续继续加对账日报、人工审批、SLA 统计，不会污染订单主流程。

## 追加治理

服务拆分后继续把仓储端口也拆开：

- `IOrderRepository`：订单主链路仓储端口。
- `IOrderReconcileRepository`：对账差错仓储端口。
- `OrderRepository` 作为基础设施适配器同时实现两个端口，但 domain 服务只依赖自己需要的端口。

这样做的目的不是增加类数量，而是让订单主链路在接口层看不到对账台账、三方账单和 MQ 重放方法。
