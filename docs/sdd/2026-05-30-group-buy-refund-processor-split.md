# 拼团退单处理器拆分

日期：2026-05-30

## 背景

`IGroupBuyRefundPort` 已经把三类退单写操作从通用 `ITradeRepository` 拆出，但基础设施实现 `GroupBuyRefundPort` 仍然同时处理未支付释放、已支付未成团退款、已支付已成团退款三套流程，包含订单明细更新、队伍更新、状态流水、锁单缓存清理、退款通知任务和库存流水。

这个实现类容易继续膨胀，因此按退单场景继续拆分。

## 规格

- 领域端口 `IGroupBuyRefundPort` 保持不变，因为它表达的是“拼团退单写端口”。
- 基础设施实现只做门面委托，不直接依赖 DAO、不直接拼状态流水、不直接创建通知任务。
- 三类退单分别由独立处理器承接，避免互相污染。
- 保持原事务语义和原异常语义：每个退单场景仍是一个事务闭环，更新数量不是 1 时抛 `UPDATE_ZERO`。

## 设计

- `GroupBuyRefundPort`：薄门面，委托到具体 Processor。
- `GroupBuyUnpaidRefundProcessor`：未支付未成团释放，更新订单明细、释放队伍锁定位、创建退款通知任务和库存流水。
- `GroupBuyPaidUnformedRefundProcessor`：已支付未成团退款，更新订单明细、队伍锁定数/完成数、创建退款通知任务和库存流水。
- `GroupBuyPaidFormedRefundProcessor`：已支付已成团退款，按队伍状态更新为部分退款或全部失败，创建退款通知任务和库存流水。
- `GroupBuyRefundSupport`：沉淀公共构造、更新断言、锁单结果清理、通知任务创建和库存流水记录。

## 变更

- 重写 `GroupBuyRefundPort` 为薄门面。
- 新增 `GroupBuyUnpaidRefundProcessor`、`GroupBuyPaidUnformedRefundProcessor`、`GroupBuyPaidFormedRefundProcessor`。
- 新增 `GroupBuyRefundSupport`。
- `DomainPurityTest` 增加 `groupBuyRefundPortAdapterShouldStayFacadeOnly`，防止 DAO、事务和状态流水重新回流到门面。

## 验收

- 营销服务 JDK 1.8 编译通过。
- 拼团退单策略单元测试继续通过。
- 架构测试能守住 `GroupBuyRefundPort` 薄门面边界。

## 边界

本次拆分不改变三类退单领域策略，也不新增售后业务类型。部分退款、拒绝退款、履约后退款仍由扩展后的售后状态机和后续售后模型承接。
