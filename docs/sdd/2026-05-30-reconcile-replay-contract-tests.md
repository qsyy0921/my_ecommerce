# 对账重放契约测试

日期：2026-05-30

## 背景

商城侧已经把对账职责从 `OrderService` 拆到 `OrderReconcileService`，并把对账差错单、MQ 失败重放、三方账单导入拆到 `IOrderReconcileRepository`。剩余风险是：差错重放逻辑如果被改坏，支付成功但营销未结算、MQ 消费失败、退款超时等补偿入口可能表面可用但实际没有调用正确端口。

## 规格

`OrderReconcileService.replayReconcileCase` 必须满足：

- 只允许 `OPEN` 差错单重放。
- 拼团营销结算超时重放时调用拼团营销结算。
- 秒杀营销结算超时重放时调用秒杀营销结算，并补商城侧营销结算状态。
- 待支付超时重放时关闭商城订单。
- 退款超时重放时调用商城退款入口。
- MQ 消费失败重放时调用原始 MQ 失败消息重放能力。
- 重放失败时只备注差错单，不确认完成。

## 变更

- 新增 `OrderReconcileServiceReplayContractTest`。
- 测试使用 fake `IOrderRepository`、`IOrderReconcileRepository`、`IMarketSettlementPort`、`IOrderService`，不依赖 Spring 容器和真实 MySQL/RabbitMQ。
- 修复商城 app 的 surefire 配置，把硬编码 `<skipTests>true</skipTests>` 改为 `<skipTests>${skipTests}</skipTests>`，确保 `-DskipTests=false` 能真正运行测试。

## 覆盖用例

- `MARKET_SETTLEMENT_TIMEOUT:<orderId>` 拼团重放。
- `MARKET_SETTLEMENT_TIMEOUT:<orderId>` 秒杀重放。
- `PAY_WAIT_TIMEOUT:<orderId>` 关闭订单。
- `REFUND_TIMEOUT:<orderId>` 退款重放。
- `MQ_CONSUME_FAIL:<messageId>` MQ 重放。
- 非 `OPEN` 差错单跳过重放。
- 重放异常时备注失败原因，不确认差错单。

## 验收

```powershell
cd E:\java\group_buy_market\s-pay-mall-ddd-market-master
mvn -pl s-pay-mall-ddd-app -am -DskipTests=false -DfailIfNoTests=false "-Dtest=cn.bugstack.test.domain.OrderReconcileServiceReplayContractTest" test
```

本机执行结果：`Tests run: 7, Failures: 0, Errors: 0, Skipped: 0`。

## 边界

本测试验证领域服务和端口调用契约，不替代真实 MQ、真实 MySQL 和 HTTP 超时故障演练。真实中间件行为仍由故障演练脚本和集成压测覆盖。
