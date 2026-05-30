# 拼团通知任务端口拆分

## 背景

`ITradeNotifyTaskPort` 同时暴露通知任务创建、未执行任务扫描和任务状态更新。基础设施实现 `TradeNotifyTaskPort` 还承担结算/退款通知 payload 构建、退款类型到任务分类映射、PO/Entity 映射和 DAO 调用。这个端口虽然已经从交易仓储中拆出，但仍然偏宽。

## 目标

- 删除通用 `ITradeNotifyTaskPort` / `TradeNotifyTaskPort`。
- 按使用方拆成两个语义端口：
  - `ITradeNotifyTaskCreatePort`：结算/退款流程只创建通知任务。
  - `ITradeNotifyTaskExecutionPort`：任务服务只扫描和更新通知任务状态。
- 基础设施拆成：
  - `TradeNotifyTaskCreatePort`
  - `TradeNotifyTaskExecutionPort`
  - `TradeNotifyTaskFactory`
  - `TradeNotifyTaskMapper`

## 设计

- `GroupBuySettlementPort` 和 `GroupBuyRefundSupport` 注入创建端口。
- `TradeTaskService` 注入执行端口和通知发送端口。
- `TradeNotifyTaskFactory` 只负责构建通知任务 PO 和 payload。
- `TradeNotifyTaskMapper` 只负责 PO/Entity/key 映射。

## 验收

- JDK 1.8 下营销服务编译通过。
- domain purity 脚本通过。
- `DomainPurityTest` 增加架构守护：
  - `ITradeNotifyTaskPort` / `TradeNotifyTaskPort` 不允许回流。
  - 创建端口不能暴露扫描/状态更新。
  - 执行端口不能暴露结算/退款创建。
  - 创建适配器不能直接写 payload JSON、HashMap、退款分类 switch 或 PO/Entity builder。
- 更新 TODO、SDD 目录和八股文档。
