# SDD: 商城商品端口与营销交易端口拆分

## 背景

商城领域层的 `IProductPort` 名义上是商品端口，但实际同时承担：

- 商品信息查询。
- 拼团营销锁单。
- 秒杀营销锁单。
- 拼团/秒杀支付结算。
- 拼团/秒杀退款恢复。

这会让商城订单领域服务依赖一个过宽端口。商品查询、营销锁单、营销结算、营销退款属于不同外部能力，混在一起会导致后续扩展时把营销交易编排继续堆到商品适配器里。

## 目标

按 DDD 语义拆成小端口：

- `IProductQueryPort`：商品查询。
- `IMarketOrderLockPort`：拼团/秒杀营销锁单。
- `IMarketSettlementPort`：拼团/秒杀营销结算。
- `IMarketRefundPort`：拼团/秒杀营销退款恢复。

基础设施侧拆成对应 adapter，删除通用 `IProductPort`，并用架构测试防止回流。

## 设计约束

- `AbstractOrderService` 只依赖商品查询和营销锁单端口。
- `OrderService` 的支付成功异步结算只依赖营销结算端口。
- `OrderService` 的退款流程只依赖营销退款端口。
- `OrderReconcileService` 的差错重放只依赖营销结算端口。
- 基础设施仍通过 Retrofit 调用营销服务，但 Retrofit DTO、HTTP 响应解析、source/channel 配置不能进入 domain。

## 验收

- `IProductPort.java` 不存在。
- `ProductPort` 只实现商品查询，不再持有营销锁单、结算、退款方法。
- `DomainServiceConfig` 不再注入 `IProductPort`。
- 架构测试验证商城商品端口和营销交易端口职责拆分。
- 商城服务 JDK 1.8 编译通过。
- 对账重放契约测试通过。
