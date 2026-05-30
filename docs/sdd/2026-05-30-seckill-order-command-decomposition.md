# 秒杀订单命令端口继续拆分

日期：2026-05-30

## 背景

上一轮已经删除通用 `ISeckillRepository` / `SeckillRepository`，并把订单创建、批量落库、支付结算、退款状态更新收敛到 `ISeckillOrderCommandPort`。这个端口虽然比通用仓储清晰，但仍然把订单生命周期的多个写职责聚在一起：异步创建、支付结算、退款恢复库存。

本次继续按 SDD + DDD 约束拆分，目标是让领域服务依赖更窄的业务语义端口，避免新的大命令端口继续膨胀。

## 规格

- 秒杀异步创建只依赖订单创建端口。
- 秒杀支付成功只依赖结算端口。
- 秒杀退款只依赖退款端口。
- 分片表路由、PO/Entity 转换、库存释放和失败回滚属于基础设施细节，不进入领域层。
- 保留原有幂等语义：重复订单、重复支付、重复退款仍能安全返回。

## 设计

领域端口：

- `ISeckillOrderCreatePort`：订单创建、批量创建。
- `ISeckillSettlementPort`：支付结算。
- `ISeckillRefundPort`：退款和库存释放。

基础设施组件：

- `SeckillOrderCreatePort`：处理单条/批量落库、唯一索引兜底、结果缓存、库存流水和状态流水。
- `SeckillSettlementPort`：处理 `CREATE -> COMPLETE`、重复结算幂等和结果缓存。
- `SeckillRefundPort`：处理 `CREATE -> CLOSE`、`COMPLETE -> REFUND`、库存释放和状态流水。
- `SeckillOrderTableGateway`：封装分片表路由和 DAO 调用。
- `SeckillOrderAssembler`：封装 `SeckillOrder` 与 `SeckillOrderEntity` 转换。
- `SeckillStockReleaseSupport`：封装库存释放、失败回滚、售罄缓存清理和库存流水。

## 变更

- 删除 `ISeckillOrderCommandPort`。
- 删除 `SeckillOrderCommandPort`。
- `SeckillService` 改为注入 `ISeckillOrderCreatePort`、`ISeckillSettlementPort`、`ISeckillRefundPort`。
- `DomainServiceConfig` 改为按新端口装配秒杀领域服务。
- `DomainPurityTest` 增加 `seckillOrderCommandPortShouldStaySplitByLifecycle`，防止大命令端口回流。

## 验收

- 营销服务 JDK 1.8 编译通过。
- 架构测试守护通用命令端口删除。
- 领域层不依赖 Spring、MyBatis、Redis、RabbitMQ、DAO/PO。

## 边界

- 本次拆的是秒杀订单生命周期命令职责，不改变 Redis Stream / RabbitMQ 的消息选型。
- 分片表仍是应用侧路由，后续接入 ShardingSphere/TDDL 时只需要优先替换 `SeckillOrderTableGateway` 和 `SeckillOrderShardRouter`。
