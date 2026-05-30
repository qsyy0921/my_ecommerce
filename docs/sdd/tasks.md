# SDD 任务清单

状态说明：

- `[x]` 已完成。
- `[~]` 已部分完成，需要继续补齐。
- `[ ]` 未开始。

## T1 本地可运行

- [x] 使用 JDK 1.8 和 Maven 3.8 构建项目。
- [x] Docker 本地启动 MySQL、Redis、RabbitMQ。
- [x] 启动支付商城、营销服务、静态前端。
- [x] 前端支持拼团、秒杀、订单页跳转。

## T2 支付渠道

- [x] 创建支付单支持 `payChannel=mock/alipay`。
- [x] 模拟支付不依赖真实支付宝，能本地确认支付。
- [x] 秒杀支付单走商城统一支付入口。
- [x] 支付回调增加独立幂等流水表，重复回调不重复触发 MQ 或营销结算。

## T3 秒杀大厂化

- [x] 增加秒杀活动查询和秒杀锁单接口。
- [x] 秒杀接入商城前端和商城支付流程。
- [x] Redis 库存预扣。
- [x] Redis Lua 原子完成用户占位和库存扣减。
- [x] DB 条件更新库存兜底。
- [x] DB 唯一索引兜底重复参与。
- [x] 活动预热任务：活动开始前批量加载热点库存和活动配置。
- [x] 秒杀入口限流：按活动、用户、IP 维度限流。
- [x] 秒杀排队削峰：热点活动可切换到 MQ/Redis Stream 排队下单。
- [x] 秒杀支付结算和退款库存闭环：`CREATE -> COMPLETE -> REFUND`、未支付取消和库存流水审计。

## T4 拼团大厂化

- [x] 拼团试算、锁单、结算、退单基础链路。
- [x] 责任链校验活动、用户、队伍规则。
- [x] Redis/DB 防超卖，队伍名额已使用 Redis Lua 原子扣减。
- [x] `TradeRepository` 拆出通知任务 Outbox 端口和拼团库存流水审计端口。
- [x] 拼团队伍名额和用户维度占位使用 Redis Lua 原子处理。
- [x] 拼团锁单结果支持 Redis 缓存 + DB 回源的强幂等查询。
- [x] 拼团退单补偿增加状态机校验和流水。

## T5 MQ 可靠性

- [x] 已有 RabbitMQ 结算通知能力。
- [x] 生产者 confirms 已接入，发送失败会落台账并由补偿任务重试。
- [x] 消费幂等表 `mq_message_record`。
- [x] 消费者手动 ACK。
- [x] 失败 NACK 后进入 DLQ。
- [x] DLQ 监听器记录死信消息。
- [x] DLQ 指标告警和人工处理入口。

## T6 可观测性

- [x] 引入 Actuator 和 Prometheus registry。
- [x] 有基础日志。
- [x] 全链路 traceId 过滤器。
- [x] 结构化 JSON 业务日志字段标准。
- [x] 本地 OpenTelemetry Java agent + Jaeger 链路追踪。
- [x] 自定义指标：秒杀库存不足、重复参与、锁单耗时、MQ/Stream 积压。
- [x] Grafana Dashboard。
- [x] 告警规则：错误率、P95、DLQ、库存不一致。

## T7 对账和补偿

- [x] 商城订单、营销订单、支付渠道三方对账任务。
- [x] 支付流水和退款流水独立端口化，订单状态不再替代支付/退款事实。
- [x] 商城 `OrderService` 与 `OrderReconcileService` 拆分，订单主链路和对账差错处理分离。
- [x] 商城 `IOrderRepository` 与 `IOrderReconcileRepository` 拆分，订单主链路仓储端口不暴露对账台账能力。
- [x] 对账差错单支持确认、重放、忽略、关闭、备注和操作日志查询。
- [x] 超时未支付退单补偿。
- [x] 秒杀已支付退款恢复库存和未支付取消释放资格。
- [x] 已支付未成团退款补偿。
- [x] 已支付已成团售后逆向补偿。
- [x] 补偿任务分布式锁和执行记录。

## T8 压测和容量

- [x] 增加 Node 无依赖压测脚本。
- [x] 输出秒杀查询和商城秒杀支付压测报告。
- [x] 拼团试算压测。
- [x] 拼团锁单压测。
- [x] 秒杀热点大并发压测：100、500、1000 并发梯度。
- [x] 压测后自动校验库存不变量。
- [x] 压测资源水位采集：JVM、Docker、Redis、MySQL、RabbitMQ、Actuator。
- [x] 写容量评估：单机 QPS、瓶颈、扩容策略。

## T9 DDD 治理

- [x] 明确商城服务和营销服务各自维护独立 DDD 分层，拼团和秒杀先作为营销上下文内的两个子域。
- [x] domain 去 Spring 注解，领域对象由 app 层配置类装配。
- [x] `scripts/check-domain-purity.ps1` 可扫描商城/营销 domain 包。
- [x] `DomainPurityTest` 可在 Maven 测试阶段防止 domain 重新引入 Spring/container 注解。
- [x] `OrderStateMachineTest` 覆盖秒杀订单、拼团订单、拼团队伍和售后流程的合法/非法状态迁移。
- [x] 抽象 `IDomainTaskExecutor`，domain 不再直接依赖 `ThreadPoolExecutor`。
- [x] `TradeTaskService` 改为依赖 `ITradeNotifyTaskPort`，`ITradeRepository` 不再暴露通知任务扫描和状态更新方法。
- [x] 拼团队伍库存占位拆到 `IGroupBuyTeamStockPort`，`ITradeRepository` 不再暴露 Redis 队伍名额占用和退单恢复方法。
- [x] 拼团锁单请求锁和结果缓存拆到 `ITradeLockRequestPort`，`ITradeRepository` 不再暴露 Redis 请求锁和缓存方法。
- [x] 拼团锁单落库拆到 `IGroupBuyOrderPort`，`ITradeRepository` 不再暴露锁单写方法。
- [x] 拼团支付结算拆到 `IGroupBuySettlementPort`，`ITradeRepository` 不再暴露结算写方法。
- [x] 拼团三类退单写操作拆到 `IGroupBuyRefundPort`，`ITradeRepository` 不再暴露退单写方法。
- [x] 拼团退单基础设施实现继续拆成未支付、已支付未成团、已支付已成团三个处理器，`GroupBuyRefundPort` 只保留门面委托。
- [x] 拼团读模型查询拆到 `IGroupBuyQueryPort`，超时未支付扫描拆到 `IGroupBuyTimeoutOrderPort`，渠道黑名单策略拆到 `ITradePolicyPort`，通用 `ITradeRepository` / `TradeRepository` 已删除。
- [x] 拼团锁单纯单元测试已补齐，覆盖重复请求、队伍满员、活动不可用、人群标签试算拦截、Redis 占位失败和 DB 唯一索引兜底回滚。
- [x] 秒杀库存同步、活动预热、超时未支付释放拆到 `ISeckillMaintenancePort`，`ISeckillRepository` 不再暴露 Job 维护方法。
- [x] 秒杀库存流水拆到 `ISeckillStockFlowPort`，`SeckillRepository` 不再直接构建库存流水 PO。
- [x] 秒杀结果缓存拆到 `ISeckillResultCachePort`，`SeckillRepository` 不再直接维护结果缓存 Key 和 Redis get/set。
- [x] 秒杀订单分片路由拆到 `SeckillOrderShardRouter`，分片表名和路由规则从主仓储移出。
- [x] 秒杀 Redis 库存预扣拆到 `ISeckillStockReservationPort`，`SeckillRepository` 不再直接依赖 `IRedisService`、库存桶 Key、用户占位 Key 和 Lua 预扣细节。
- [x] 秒杀库存预扣适配器内部继续拆出 `SeckillStockKeyBuilder`、`SeckillStockBucketRouter`、`SeckillStockInitializationCache`，预扣主适配器不再直接持有 Key 常量、CRC32 和本地初始化缓存。
- [x] 秒杀订单创建、批量落库、支付结算和退款状态更新先拆到订单命令端口，后续继续拆成 `ISeckillOrderCreatePort`、`ISeckillSettlementPort`、`ISeckillRefundPort`，`ISeckillOrderCommandPort` 已删除。
- [x] 秒杀查询、库存可用性、锁单预扣和维护任务分别拆到 `ISeckillQueryPort`、`ISeckillStockAvailabilityPort`、`ISeckillOrderLockPort`、`ISeckillMaintenancePort`，通用 `ISeckillRepository` / `SeckillRepository` 已删除。
- [x] 秒杀下单消息投递拆到 `ISeckillOrderMessagePort`，`SeckillOrderLockPort` 不再感知 Redis Stream、RabbitMQ、routing key 和 JSON 序列化。
- [x] 秒杀订单生命周期命令拆成创建、结算、退款三个端口，分片表访问、PO/Entity 转换、库存释放/回滚从主适配器移出。
- [x] 秒杀库存纯单元测试已补齐，覆盖预扣成功、重复参与、库存不足、售罄短路、异步入队失败回滚、pending retry 隔离策略和库存流水幂等键。
- [x] 专业 MQ 演进方案已补齐到 `docs/sdd/mq-evolution.md`，明确 Redis Stream、RabbitMQ、RocketMQ/Kafka/Pulsar 职责边界、消息模型、迁移步骤和回滚方案。
- [x] 拼团退款策略纯单元测试已补齐，覆盖未支付未成团、已支付未成团、已支付已成团、重复退款、非法状态退款和锁单库存恢复边界。
- [x] 秒杀人工补偿 Stream 已补齐操作审计，新增 `ISeckillManualCompensationAuditPort`、`seckill_manual_compensation_log`、`manual_logs` 接口和补偿台操作记录展示。
- [x] 售后状态机已扩展，覆盖部分退款、拒绝退款、履约后退款和重复退款拦截，并补充 `OrderStateMachineTest`。
- [x] 商城支付/退款流水拆到 `IPaymentFlowPort` / `IRefundFlowPort`，对账仓储拆到独立 `OrderReconcileRepository`，`OrderRepository` 只保留订单持久化和订单事件。
- [x] 对账差错处理补齐 `ReconcileCaseStatusVO` 和终态保护，`reconcile_case` 终态不会被扫描重新打开，重放前先校验待处理状态。
- [x] 对账重放契约测试已补齐，覆盖拼团/秒杀营销结算重放、待支付关闭、退款重放、MQ 失败重放、非 OPEN 跳过和失败备注。
- [x] `DomainPurityTest` 增加通用 `ITradeRepository` / `TradeRepository` 删除守护，以及 `IGroupBuyQueryPort` 只读职责守护。
- [x] `DomainPurityTest` 增加 `ISeckillRepository` 维护任务方法回流守护。
- [x] `DomainPurityTest` 增加通用 `ISeckillRepository` / `SeckillRepository` 删除守护，以及秒杀查询、库存可用性、锁单端口职责守护。
- [x] `DomainPurityTest` 增加秒杀锁单适配器消息中间件路由回流守护。
- [x] `DomainPurityTest` 增加秒杀补偿台 Controller 边界守护，避免 trigger 直接依赖 Redis Stream 实现类。
- [x] 当前本机可验证的 DDD 大仓储治理项已完成，后续继续按 `docs/sdd/ddd-sdd-todo-list.md` 做增量审计，不再保留泛化未完成项。
