# 秒杀库存预扣适配器内部拆分

日期：2026-05-30

## 背景

`ISeckillStockReservationPort` 已经把 Redis 库存桶、Lua 预扣、用户占位、库存释放从通用 `SeckillRepository` 拆出。但基础设施实现 `SeckillStockReservationPort` 仍同时承担：

- Redis Key 拼接。
- 库存桶 hash 路由。
- 初始化短缓存。
- 初始化锁。
- Lua 预扣。
- 回滚和释放。

这会让预扣适配器继续膨胀，后续调整桶数、修改 Key 规范或替换 Redis 预扣实现时容易牵动主流程。

## 规格

- 领域端口保持 `ISeckillStockReservationPort` 不变。
- `SeckillStockReservationPort` 只保留预扣、初始化、查询、回滚、释放的流程编排。
- Redis Key 规范、库存桶路由、本地初始化缓存拆到独立 support 组件。
- 保持原有库存语义：重复参与返回 duplicate、库存不足继续尝试其他桶、成功时写入 stockBefore/stockAfter、失败回滚时清理用户占位和结果缓存。

## 设计

- `SeckillStockKeyBuilder`：统一生成库存桶 Key、初始化锁 Key、用户占位 Key。
- `SeckillStockBucketRouter`：统一处理桶数量、hash 路由和尝试桶数量。
- `SeckillStockInitializationCache`：统一处理本地库存已初始化短缓存。
- `SeckillStockReservationPort`：依赖以上组件，只处理 Redis 预扣/释放流程。

## 变更

- 新增 `SeckillStockKeyBuilder`。
- 新增 `SeckillStockBucketRouter`。
- 新增 `SeckillStockInitializationCache`。
- `SeckillStockReservationPort` 删除 Key 常量、CRC32、ConcurrentHashMap、本地 TTL 字段和私有桶路由方法。
- `DomainPurityTest` 增加 `seckillStockReservationPortShouldDelegateKeyRoutingAndInitCache`，防止这些技术细节回流到主适配器。

## 验收

- 营销服务 JDK 1.8 编译通过。
- 架构测试覆盖 Key 构建、桶路由、初始化缓存的拆分边界。

## 边界

本次仍然使用 Redis Lua 作为本机可验证的库存预扣方案。生产大促如果迁移到更复杂的库存服务或多级库存模型，优先替换 `SeckillStockReservationPort` 和这些 support 组件，不需要改领域服务和锁单入口。
