# 商城订单支付成功消息端口拆分

## 背景

商城 `OrderRepository` 目前同时负责订单表读写和支付成功 MQ 发送。普通订单支付成功、拼团成团结算完成、秒杀营销结算完成后，都会在仓储内构造 `PaySuccessMessageEvent` 并通过 `EventPublisher` 发布。这让 Repository 直接感知消息事件、JSON 序列化和 MQ 发布细节，职责偏宽。

## 目标

- 新增 `IOrderPaySuccessMessagePort`，用领域语义表达“发布订单支付成功消息”。
- 新增 `OrderPaySuccessMessagePort` 基础设施适配器，封装 `PaySuccessMessageEvent`、`EventPublisher` 和 JSON 序列化。
- `OrderRepository` 只保留订单持久化，不再直接依赖 MQ 事件发布组件。
- `OrderService#changeOrderPaySuccess` 和 `OrderService#changeOrderMarketSettlement` 负责在订单状态变化成功后调用消息端口。
- 对账重放里的秒杀营销结算完成后，复用 `OrderService#changeOrderMarketSettlement`，不直接绕过消息端口。

## 设计

- domain 端口：`IOrderPaySuccessMessagePort`
  - `publish(String orderId)`
  - `publishAll(List<String> orderIds)`
- infrastructure 适配器：`OrderPaySuccessMessagePort`
  - 构造 `PaySuccessMessageEvent.PaySuccessMessage`
  - 调用 `EventPublisher`
- `OrderRepository`：
  - `changeOrderPaySuccess` 只做条件更新。
  - `changeOrderMarketSettlement` 只做批量状态更新。

## 验收

- JDK 1.8 下商城服务编译通过。
- 营销服务架构测试通过。
- `DomainPurityTest` 增加/更新架构守护：
  - `OrderRepository` 不再出现 `PaySuccessMessageEvent`、`EventPublisher`、`BaseEvent`、`JSON.toJSONString` 和 `publish`。
  - `OrderPaySuccessMessagePort` 文件必须存在。
- 更新 TODO、SDD 目录和八股文档。
