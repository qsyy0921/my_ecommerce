# 2026-05-30 TradeRepository 职责拆分 SDD 记录

## 背景

`TradeRepository` 已经承载拼团锁单、结算、退单、通知任务、库存流水和状态流水。状态流水已经通过 `IOrderStateFlowPort` 移出仓储，但通知任务构建和库存流水构建仍直接散落在仓储方法内，导致 Repository 偏厚。后续检查又发现 `TradeTaskService` 只是执行通知任务，却依赖整个 `ITradeRepository`，这会让任务编排看到锁单、结算、退款等无关能力。

本次目标不是增加新的中间件，而是在现有 DDD 架构下把职责边界拆清楚。拼团成团和退单通知继续使用 RabbitMQ，本地 `notify_task` 表作为 Outbox 和补偿台账；拼团库存流水继续落 MySQL，作为审计和异常恢复依据。

## 规格

- `TradeRepository` 不再直接依赖 `INotifyTaskDao`、`IGroupBuyStockFlowDao`、`NotifyTask`、`GroupBuyStockFlow`。
- 通知任务当时先由 `ITradeNotifyTaskPort` 表达；后续已继续拆成 `ITradeNotifyTaskCreatePort` 和 `ITradeNotifyTaskExecutionPort`，分别负责创建通知任务和扫描/更新执行状态。
- `TradeTaskService` 后续已改为直接依赖 `ITradeNotifyTaskExecutionPort` 查询和更新通知任务状态，不再依赖 `ITradeRepository`。
- `ITradeRepository` 不再暴露通知任务查询和状态更新方法，只保留交易主链路需要的锁单、结算、退款和库存占位方法。本条是本次拆分时的阶段边界，后续锁单、结算、退款和库存占位已继续拆到独立端口。
- 拼团队伍库存占位由 `IGroupBuyTeamStockPort` 表达，Redis Lua 占位、用户占位释放和退单恢复量写入不再挂在 `ITradeRepository` 上。
- 拼团锁单请求锁和锁单结果缓存由 `ITradeLockRequestPort` 表达，`ITradeRepository` 不再暴露 Redis 请求锁、缓存写入和缓存清理方法。
- 拼团库存流水由 `IGroupBuyStockFlowPort` 表达，领域实体 `GroupBuyStockFlowEntity` 负责承载业务语义。
- 结算和退单方法只保留交易状态更新、状态机流水调用、通知任务端口调用、库存审计端口调用。
- 不改变现有接口、表结构、MQ 路由和业务行为。

## 设计

```mermaid
flowchart LR
    A["TradeRepository"] --> B["ITradeNotifyTaskCreatePort / ITradeNotifyTaskExecutionPort"]
    A --> C["IGroupBuyStockFlowPort"]
    A --> D["IOrderStateFlowPort"]
    I["TradeLockOrderService / Rule / Refund Strategy"] --> J["IGroupBuyTeamStockPort"]
    I --> M["ITradeLockRequestPort"]
    B --> E["TradeNotifyTaskCreatePort / TradeNotifyTaskExecutionPort"]
    C --> F["GroupBuyStockFlowPort"]
    J --> K["GroupBuyTeamStockPort"]
    M --> N["TradeLockRequestPort"]
    E --> G["notify_task"]
    F --> H["group_buy_stock_flow"]
    K --> L["Redis Lua / Recovery Key"]
    N --> O["Redis Request Lock / Result Cache"]
```

端口职责：

- `ITradeNotifyTaskCreatePort`
  - 创建成团结算通知任务。
  - 创建三类退单通知任务。

- `ITradeNotifyTaskExecutionPort`
  - 查询未执行通知任务。
  - 更新通知任务成功、重试、失败状态。

- `IGroupBuyStockFlowPort`
  - 记录锁单预占流水。
  - 记录未支付退单释放流水。
  - 记录已支付未成团退单释放流水。
  - 记录已支付已成团退单流水。

- `IGroupBuyTeamStockPort`
  - 参团时通过 Redis Lua 原子占用队伍名额和用户占位。
  - 锁单失败时恢复恢复量并释放用户队伍占位。
  - 退款成功后按订单维度幂等增加恢复量。

- `ITradeLockRequestPort`
  - 获取和释放 `userId + outTradeNo` 请求锁。
  - 读写锁单结果缓存。
  - 支付结算和退单后清理锁单结果缓存。

## 验收

- `TradeRepository` 中不能再出现 `NotifyTask` PO 和 `GroupBuyStockFlow` PO。
- 后续架构测试已升级为删除守护：通用 `ITradeRepository` / `TradeRepository` 不能回流，通知任务、队伍库存、请求锁和结果缓存必须继续走专用端口。
- domain 不依赖 infrastructure。
- `scripts/check-domain-purity.ps1` 通过。
- `mvn -q -DskipTests compile` 通过。
- 本地 8091/8092/8093 可以重新启动，Nginx 8080 健康检查通过。

## 实现结果

- 新增 `GroupBuyStockFlowEntity`，用领域语义表达拼团锁单预占、未支付退单释放、已支付未成团退单释放、已支付已成团退单流水。
- 新增 `IGroupBuyStockFlowPort`，由 `GroupBuyStockFlowPort` 适配 `group_buy_stock_flow` 表。
- 阶段性新增的 `ITradeNotifyTaskPort` 后续已删除，改为 `ITradeNotifyTaskCreatePort` / `ITradeNotifyTaskExecutionPort`，由 `TradeNotifyTaskCreatePort` / `TradeNotifyTaskExecutionPort` 适配 `notify_task` 本地消息表。
- `TradeRepository` 不再直接依赖 `INotifyTaskDao`、`IGroupBuyStockFlowDao`、`NotifyTask`、`GroupBuyStockFlow`。
- 通知任务查询和状态更新不再挂在 `ITradeRepository` 上，`TradeTaskService` 直接通过 `ITradeNotifyTaskExecutionPort` 完成任务扫描和状态推进。
- 新增 `IGroupBuyTeamStockPort` 和 `GroupBuyTeamStockPort`，锁单规则、锁单失败补偿和退单策略通过专用端口处理 Redis 队伍库存占位。
- 新增 `ITradeLockRequestPort` 和 `TradeLockRequestPort`，锁单请求锁、结果缓存和缓存清理通过专用端口处理。
- `DomainPurityTest` 增加回归用例，后续已升级为防止通用 `ITradeRepository` / `TradeRepository` 重新出现。

## 当前验证

- `scripts/check-domain-purity.ps1`：通过。
- `E:\java\group_buy_market\.tools\apache-maven-3.8.8\bin\mvn.cmd -q -DskipTests compile`：通过。
- `E:\java\group_buy_market\.tools\apache-maven-3.8.8\bin\mvn.cmd -q -DskipTests package`：通过。
- 8091、8092、8093 三个实例重新启动成功。
- Nginx 8080 `/actuator/health` 返回 `UP`。
- 拼团烟测：通过 8080 完成 3 人开团、参团、结算，`group_buy_stock_flow` 写入 3 条，`notify_task` 写入 1 条 `trade_settlement`，`order_state_flow` 写入状态迁移记录。
