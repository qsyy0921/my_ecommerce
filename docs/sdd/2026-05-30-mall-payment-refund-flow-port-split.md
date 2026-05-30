# 商城支付/退款流水端口拆分记录

## 背景

商城侧已经有 `payment_flow`、`refund_flow`、`third_party_bill` 和 `reconcile_case` 表，但 `OrderRepository` 仍然直接依赖 `IPaymentFlowDao`、`IRefundFlowDao`，同时实现订单主链路和对账扫描/重放能力。这样会导致订单仓储持续膨胀，支付事实、退款事实和对账事实继续混在一个基础设施适配器里。

## 规格

- 订单仓储只负责商城订单持久化、订单状态更新和普通订单支付成功事件发布。
- 支付流水必须作为独立领域事实建模，不能只依赖订单状态表示支付成功。
- 退款流水必须作为独立领域事实建模，支持退款申请和退款成功独立留痕。
- 对账仓储单独负责差错单扫描、MQ 重放、三方账单导入和操作日志，不再挂在 `OrderRepository` 上。
- domain 层继续只依赖领域端口和领域模型，不引入 Spring、MyBatis、DAO/PO。

## 设计

### 领域端口

- `IPaymentFlowPort`：记录支付成功流水，查询本地支付成功但缺三方账单的流水。
- `IRefundFlowPort`：记录退款申请/退款成功流水，查询本地退款成功但缺三方账单的流水。

### 领域模型

- `PaymentFlowEntity`：支付流水领域实体，包含 `flowNo/orderId/userId/payChannel/channelTradeNo/payAmount/payStatus/payTime/rawMessage`。
- `RefundFlowEntity`：退款流水领域实体，包含 `flowNo/orderId/userId/refundChannel/channelRefundNo/refundAmount/refundStatus/refundReason/refundTime/rawMessage`。

### 基础设施适配

- `PaymentFlowPort`：适配 `IPaymentFlowDao` 和 `payment_flow`。
- `RefundFlowPort`：适配 `IRefundFlowDao` 和 `refund_flow`。
- `OrderReconcileRepository`：独立实现 `IOrderReconcileRepository`，负责差错单、MQ 失败重放、三方账单导入和流水缺账单扫描。

## 实现结果

- `OrderService` 在支付回调成功或幂等重放时调用 `IPaymentFlowPort.recordPaySuccess`，保持支付流水幂等补录能力。
- `OrderService` 在退款申请和退款成功后调用 `IRefundFlowPort.recordRefund`，把退款事实从订单状态中独立出来。
- `OrderRepository` 不再实现 `IOrderReconcileRepository`，也不再直接依赖支付/退款流水 DAO、PO。
- `OrderReconcileRepository` 通过支付/退款流水端口拿领域实体，再生成 `PAY_FLOW_MISS_BILL`、`REFUND_FLOW_MISS_BILL` 差错单。

## 验收

- JDK 1.8 下商城服务编译通过。
- JDK 1.8 下营销服务编译通过。
- `DomainPurityTest` 增加商城 `OrderRepository` 边界守护，禁止对账、MQ 重放、支付/退款流水 DAO 回流。
- domain purity 脚本继续保证商城和营销 domain 不依赖 Spring 容器注解。

## 后续

- 对账差错处理还需要继续补合同测试，覆盖支付成功缺营销结算、退款成功缺库存恢复、三方账单缺本地流水等场景。
- 支付流水和退款流水目前满足本机闭环，真实生产仍需要接入正式支付渠道账单下载、退款回执和人工审核策略。
