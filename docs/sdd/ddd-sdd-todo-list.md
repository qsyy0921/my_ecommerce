# DDD + SDD 后续治理 TODO List

本文档用于约束后续 AI 辅助开发：所有改动必须先明确规格，再进入编码；所有编码必须遵守 DDD 分层，避免继续把流程编排、基础设施细节和补偿逻辑堆进大 Repository。

## 开发约束

- SDD 顺序：先补规格文档，再写设计，再拆任务，再编码，再验证，再复盘。
- DDD 边界：domain 只保留领域模型、领域服务、领域端口和状态机，不直接依赖 Spring、MyBatis、Redis、RabbitMQ、HTTP Client 或 DAO/PO。
- 仓储职责：Repository Adapter 只做持久化适配，不承载复杂业务编排；跨表状态变更、补偿、重试和缓存策略优先抽端口或应用服务。
- 测试门禁：每次改动至少执行 JDK 1.8 编译、domain purity 扫描和相关架构/状态机测试。
- 提交规范：每次完成一个闭环任务后提交并推送 GitHub，提交说明写清楚“改了什么、为什么改、如何验证”。
- 当前策略：本机可验证的 DDD 大仓储治理已基本完成，后续优先做“问题审计、边界说明和增量守护”，不再为了行数继续机械拆分类。

## P0 当前优先级最高

- [x] 拆分商城订单支付成功消息发布端口。
  - 目标：避免 `OrderRepository` 继续同时承担订单持久化和支付成功 MQ 发送。
  - 实际拆分：`IOrderPaySuccessMessagePort`、`OrderPaySuccessMessagePort`。
  - 验收：`OrderRepository` 不再依赖 `PaySuccessMessageEvent`、`EventPublisher`、`BaseEvent`、JSON 序列化和 MQ publish；普通订单支付成功、拼团/秒杀营销结算完成后仍通过统一端口发送支付成功消息。

- [x] 拆分商城订单仓储映射支撑。
  - 目标：避免 `OrderRepository` 和 `OrderReconcileEntityMapper` 重复维护 `PayOrder` 与 `OrderEntity` 字段映射。
  - 实际拆分：`PayOrderEntityMapper`。
  - 验收：`OrderRepository` 只负责 `IOrderDao` 调用和状态更新；PO/Entity builder、订单状态枚举映射和列表映射进入 mapper；对账 mapper 复用统一订单映射。

- [x] 拆分商城 `OrderService` 支付成功和退款用例处理器。
  - 目标：避免 `OrderService` 继续同时承担支付成功分发、支付流水、营销结算、营销退单、真实退款和退款流水。
  - 实际拆分：`OrderPaySuccessProcessor`、`OrderRefundProcessor`。
  - 验收：`OrderService` 不再直接依赖 `IMarketSettlementPort`、`IMarketRefundPort`、`IPaymentFlowPort`、`IRefundFlowPort`、`IOrderPaySuccessMessagePort`、`IDomainTaskExecutor`；架构测试防止支付成功和退款细节回流。

- [x] 拆分 `TradeRepository` 的剩余写职责。
  - 目标：把拼团锁单落库、结算状态更新、退单状态更新拆成更小端口或仓储适配器。
  - 建议拆分：`GroupBuyOrderRepository`、`GroupBuySettlementRepository`、`GroupBuyRefundRepository`。
  - 进展：拼团锁单落库已拆到 `IGroupBuyOrderPort`，拼团结算已拆到 `IGroupBuySettlementPort`，三类退单已拆到 `IGroupBuyRefundPort`；基础设施侧 `GroupBuyRefundPort` 继续拆成三类退单处理器，门面不再直接持有 DAO、事务和状态流水细节。
  - 验收：`ITradeRepository` 不再暴露锁单、结算和退单写方法；拼团领域服务只依赖业务语义端口；现有拼团锁单、结算、退单流程编译通过；架构测试防止 `GroupBuyRefundPort` 门面重新膨胀。

- [x] 拆分拼团锁单端口内部支撑。
  - 目标：避免 `GroupBuyOrderPort` 在拆出业务语义端口后继续同时承担队伍锁定、订单明细落库、状态流水和库存流水。
  - 实际拆分：`GroupBuyTeamLockSupport`、`GroupBuyOrderListCreateSupport`。
  - 验收：`GroupBuyOrderPort` 保留事务门面和 `MarketPayOrderEntity` 组装；队伍写入、订单明细写入、唯一索引异常转换、状态流水和库存流水进入支撑组件；架构测试防止回流。

- [x] 拆分拼团锁单请求端口内部支撑。
  - 目标：避免 `TradeLockRequestPort` 同时承担 Redis 请求锁、锁单结果缓存、Key 规则、TTL 计算和 JSON 序列化。
  - 实际拆分：`GroupBuyLockRequestSupport`、`GroupBuyLockResultCacheSupport`。
  - 验收：`TradeLockRequestPort` 保留领域端口门面和 5 个接口委托；架构测试防止 Redis API、Key、TTL 和 JSON 细节回流；纯单元测试覆盖请求锁和结果缓存。

- [x] 拆分拼团队伍库存端口内部支撑。
  - 目标：避免 `GroupBuyTeamStockPort` 同时承担 Redis Lua 预扣、用户占位释放、普通恢复、退单恢复幂等锁和异常回滚。
  - 实际拆分：`GroupBuyTeamStockReservationSupport`、`GroupBuyTeamStockRecoverySupport`。
  - 验收：`GroupBuyTeamStockPort` 保留领域端口门面和 4 个接口委托；架构测试防止 Redis API、幂等锁和 TTL 细节回流；纯单元测试覆盖预扣、恢复、释放和退单幂等。

- [x] 拆分拼团结算端口内部支撑。
  - 目标：避免 `GroupBuySettlementPort` 同时承担订单明细支付完成、锁单结果清理、队伍完成数量累加、成团通知任务和状态流水。
  - 实际拆分：`GroupBuyOrderPaidSupport`、`GroupBuyTeamFormationSupport`。
  - 验收：`GroupBuySettlementPort` 只保留事务门面和聚合对象解包；架构测试防止 DAO、状态流水、通知任务和锁单结果清理细节回流。

- [x] 拆分 `TradeRepository` 的剩余读职责。
  - 目标：把活动、队伍、订单、进度、超时未支付扫描拆成更清晰的读模型端口。
  - 实际拆分：`IGroupBuyQueryPort`、`IGroupBuyTimeoutOrderPort`、`ITradePolicyPort`。
  - 验收：`ITradeRepository` / `TradeRepository` 已删除，架构测试防止通用交易仓储回流。

- [x] 拆分营销活动通用仓储端口。
  - 目标：避免 `IActivityRepository` 同时承载首页试算、商品查询、人群标签、DCC 开关和拼团队伍展示统计。
  - 实际拆分：`IActivityTrialQueryPort`、`ICrowdTagPort`、`IActivitySwitchPort`、`IGroupBuyDisplayPort`。
  - 验收：`IActivityRepository` / `ActivityRepository` 已删除；试算节点、折扣策略、开关节点、队伍展示服务只依赖各自最小语义端口；架构测试防止通用活动仓储回流。

- [x] 审计读模型适配器并记录不拆边界。
  - 目标：避免为了行数机械拆分 `GroupBuyQueryPort`、`GroupBuyDisplayPort`、`ActivityTrialQueryPort`，同时防止后续把写操作和补偿塞回读模型。
  - 本轮结论：这些适配器当前只承担查询聚合、缓存回源和 PO/Entity 映射，不混入状态更新、MQ、补偿或 Redis 锁，暂不拆生产代码。
  - 验收：新增 SDD 审计文档和架构测试，读模型适配器禁止出现写操作、交易命令、状态流水、消息发送和补偿端口依赖。

- [x] 拆分拼团交易 HTTP Controller 支撑逻辑。
  - 目标：避免 `MarketTradeController` 继续承担请求校验矩阵、通知类型解析、API DTO 到领域命令转换和响应 DTO 组装。
  - 实际拆分：`GroupBuyTradeRequestValidator`、`GroupBuyTradeCommandAssembler`、`GroupBuyTradeResponseAssembler`。
  - 验收：`DomainPurityTest` 防止 `StringUtils` 校验、`NotifyTypeEnumVO.valueOf`、领域命令 builder 和拼团交易响应 DTO builder 回流到 Controller。

- [x] 拆分拼团交易 HTTP 用例编排。
  - 目标：避免 `MarketTradeController` 继续承担锁单、试算、人群可见性、队伍满员、结算、退单和结构化日志编排。
  - 实际拆分：`GroupBuyLockOrderSupport`、`GroupBuySettlementSupport`、`GroupBuyRefundSupport`。
  - 验收：`MarketTradeController` 不再直接依赖拼团交易领域服务、首页试算服务、结构化日志、请求校验器、命令组装器、响应组装器和领域实体；架构测试防止这些用例编排细节回流。

- [x] 拆分拼团首页 HTTP Controller 支撑逻辑。
  - 目标：避免 `MarketIndexController` 继续承担请求校验、API DTO 到领域命令转换和首页响应 DTO 组装。
  - 实际拆分：`GroupBuyMarketConfigRequestValidator`、`GroupBuyMarketConfigCommandAssembler`、`GroupBuyMarketConfigResponseAssembler`。
  - 验收：`DomainPurityTest` 防止 `StringUtils` 校验、`MarketProductEntity.builder`、`GoodsMarketResponseDTO` builder 和队伍列表遍历回流到 Controller。

- [x] 拆分拼团通知发送泛化端口。
  - 目标：避免 `ITradePort` / `TradePort` 继续用泛化交易端口承载拼团通知发送、Redis 抢占锁、HTTP 回调和 MQ 投递。
  - 实际拆分：`ITradeNotificationPort`、`TradeNotificationPort`、`TradeNotificationLockSupport`、`TradeNotificationChannelDispatcher`。
  - 验收：删除 `ITradePort` / `TradePort`；`TradeTaskService` 只依赖通知发送语义端口；架构测试防止 Redis、HTTP、MQ 和通知类型判断回流到通知端口门面。

- [x] 拆分拼团通知任务端口。
  - 目标：避免 `ITradeNotifyTaskPort` / `TradeNotifyTaskPort` 同时承载通知任务创建、扫描、状态更新、payload 构建和 PO/Entity 映射。
  - 实际拆分：`ITradeNotifyTaskCreatePort`、`ITradeNotifyTaskExecutionPort`、`TradeNotifyTaskCreatePort`、`TradeNotifyTaskExecutionPort`、`TradeNotifyTaskFactory`、`TradeNotifyTaskMapper`。
  - 验收：删除 `ITradeNotifyTaskPort` / `TradeNotifyTaskPort`；结算/退款只依赖创建端口，任务服务只依赖执行端口；架构测试防止通用端口和 payload/映射细节回流。

- [x] 拆分 `SeckillRepository` 的 Redis 库存职责。
  - 目标：把库存桶、Lua 预扣、库存释放、用户占位从秒杀主仓储中移出。
  - 建议端口：`ISeckillStockPort` 或 `ISeckillStockReservationPort`。
  - 本次深化：`SeckillStockReservationPort` 内部继续拆出 `SeckillStockKeyBuilder`、`SeckillStockBucketRouter`、`SeckillStockInitializationCache`，预扣主适配器不再直接维护 Redis Key 常量、CRC32 路由和本地初始化缓存。
  - 验收：`SeckillRepository` 不直接拼 Redis stock key，不直接执行库存预扣 Lua；`SeckillStockReservationPort` 只保留预扣/初始化/查询/释放流程；库存不足、重复参与、释放库存语义保持不变。

- [x] 拆分秒杀库存预扣端口内部支撑。
  - 目标：避免 `SeckillStockReservationPort` 同时承担库存初始化/查询和资格预扣/释放两套 Redis 流程。
  - 实际拆分：`SeckillStockBucketInventorySupport`、`SeckillQualificationReservationSupport`。
  - 验收：`SeckillStockReservationPort` 只保留 `ISeckillStockReservationPort` 门面委托；Redis API、Lua 预扣、JSON 序列化、初始化锁、库存桶汇总和用户占位释放进入支撑组件；库存单元测试通过。

- [x] 拆分秒杀库存可用性端口内部支撑。
  - 目标：避免 `SeckillStockAvailabilityPort` 同时承担售罄短缓存、Redis 库存快照、初始化锁竞争、DB 回源和 Redis 库存初始化。
  - 实际拆分：`SeckillStockSnapshotSupport`、`SeckillStockInitializationSupport`。
  - 验收：`SeckillStockAvailabilityPort` 只保留配置和门面委托；库存快照、售罄缓存刷新、初始化锁和 DB 回源进入支撑组件；纯单元测试覆盖已初始化、售罄、DB 回源和活动不存在异常。

- [x] 拆分秒杀锁单端口内部支撑。
  - 目标：避免 `SeckillOrderLockPort` 作为高并发入口继续同时承担售罄短路、库存初始化、资格预扣分支、消息投递和失败回滚。
  - 实际拆分：`SeckillStockGuardSupport`、`SeckillReservationPublishSupport`。
  - 验收：`SeckillOrderLockPort` 只保留入口门面和库存桶尝试次数配置；库存闸门、预扣发布、消息失败回滚和指标记录进入支撑组件；架构测试防止回流。

- [x] 拆分 `SeckillRepository` 的结果缓存职责。
  - 目标：把秒杀结果缓存、DB 回源后的结果补缓存、缓存失效从主仓储移出。
  - 建议端口：`ISeckillResultCachePort`。
  - 验收：查询秒杀结果仍支持 Redis 快查和 DB 回源；主仓储不再直接维护 result cache key。

- [x] 拆分 `SeckillRepository` 的库存流水职责。
  - 目标：把 `seckill_stock_flow` 构建和落库从主仓储移出，统一用领域语义记录 `RESERVE/ROLLBACK`。
  - 建议端口：`ISeckillStockFlowPort`。
  - 验收：库存流水具备幂等 `flowNo`，支持压测后审计；主仓储不直接依赖库存流水 DAO/PO。

- [x] 抽象秒杀订单分片路由组件。
  - 目标：把 `seckill_order_00` 到 `seckill_order_15` 的路由规则从仓储编排中独立出来。
  - 建议组件：`SeckillOrderShardRouter`。
  - 验收：分片数可配置；路由规则稳定；后续接 ShardingSphere/TDDL 时改动范围可控。

- [x] 拆分 `SeckillRepository` 的订单命令职责。
  - 目标：把异步落库、批量落库、支付结算、退款状态更新从秒杀主仓储中移出。
  - 实际拆分：先拆到订单命令端口，后续继续拆成 `ISeckillOrderCreatePort`、`ISeckillSettlementPort`、`ISeckillRefundPort`，并用 `SeckillOrderTableGateway`、`SeckillOrderAssembler`、`SeckillStockReleaseSupport` 隔离分片表访问、对象转换和库存释放/回滚。
  - 验收：`ISeckillRepository` / `SeckillRepository` 已删除；`ISeckillOrderCommandPort` / `SeckillOrderCommandPort` 已删除；秒杀领域服务按订单生命周期依赖创建、结算、退款端口。

- [x] 拆分秒杀订单创建端口内部支撑。
  - 目标：避免 `SeckillOrderCreatePort` 在生命周期端口拆分后继续同时承担单条创建、批量创建、结果缓存、库存流水、状态流水、批量指标和失败回滚。
  - 实际拆分：`SeckillSingleOrderCreateSupport`、`SeckillBatchOrderCreateSupport`。
  - 验收：`SeckillOrderCreatePort` 只保留事务门面和单条/批量委托；架构测试防止落库、缓存、流水、指标和回滚细节回流。

- [x] 拆分秒杀结算端口内部支撑。
  - 目标：避免 `SeckillSettlementPort` 继续直接承担支付成功状态更新、并发更新兜底、状态流水和分片表回查。
  - 实际拆分：`SeckillPaidSettlementSupport`。
  - 验收：`SeckillSettlementPort` 只保留事务门面、订单查询、状态合法性校验和结果缓存；架构测试防止状态更新和状态流水细节回流。

- [x] 拆分秒杀退款端口内部状态支撑。
  - 目标：避免 `SeckillRefundPort` 同时承担未支付取消、已支付退款、库存恢复、状态流水和状态更新 SQL 细节。
  - 实际拆分：`SeckillUnpaidCancelSupport`、`SeckillPaidRefundSupport`。
  - 验收：`SeckillRefundPort` 只保留事务门面、订单查询、终态幂等和状态路由；架构测试防止状态更新、库存释放和状态流水细节回流。

- [x] 拆分 `SeckillRepository` 的查询和库存可用性职责。
  - 目标：把活动查询、订单查询、结果查询、库存初始化/查询和本地售罄短缓存继续拆开。
  - 实际拆分：`ISeckillQueryPort`、`ISeckillStockAvailabilityPort`、`ISeckillOrderLockPort`、`ISeckillMaintenancePort`。
  - 验收：`ISeckillRepository` / `SeckillRepository` 已删除，架构测试防止通用秒杀仓储回流。

- [x] 拆分秒杀查询端口内部支撑。
  - 目标：避免 `SeckillQueryPort` 同时承担活动短缓存、活动/SKU 映射、订单分片查询和结果缓存回源。
  - 实际拆分：`SeckillActivityQuerySupport`、`SeckillResultQuerySupport`，订单查询复用 `SeckillOrderTableGateway`。
  - 验收：`SeckillQueryPort` 只保留 `ISeckillQueryPort` 门面委托；架构测试防止 DAO、缓存 Map、分片路由和结果缓存回源细节回流。

- [x] 拆分秒杀维护端口内部场景支撑。
  - 目标：避免 `SeckillMaintenancePort` 在删除通用仓储后继续膨胀成新的维护任务大类。
  - 实际拆分：`SeckillActivityStockSyncSupport`、`SeckillTimeoutUnpaidReleaseSupport`、`SeckillActivityPrewarmSupport`。
  - 验收：`SeckillMaintenancePort` 只保留 `ISeckillMaintenancePort` 门面委托；库存同步、超时释放、预热扫描、库存释放和状态流水细节进入支撑组件；架构测试防止回流。

## P1 高并发与消息可靠性

- [x] 设计专业 MQ 演进方案。
  - 目标：明确 Redis Stream、RabbitMQ、RocketMQ/Kafka/Pulsar 的职责边界。
  - 当前策略：Redis 继续做资格预扣、防重和本机演示削峰；跨服务通知继续用 RabbitMQ；真正大促订单排队建议演进 RocketMQ 或 Kafka。
  - 验收：`docs/sdd/mq-evolution.md` 已补齐选型结论、MQ 对比、目标架构、消息模型、路由策略、Outbox 兜底、迁移步骤、回滚方案和本机可验证项。

- [x] 为秒杀异步下单增加 MQ 抽象端口。
  - 目标：业务代码不直接绑定 Redis Stream，后续可替换 RocketMQ/Kafka。
  - 实际端口：`ISeckillOrderMessagePort` / `SeckillOrderMessagePort`。
  - 验收：Redis Stream、RabbitMQ、Redis Queue、本地队列投递选择收敛到消息 Adapter；锁单适配器不再感知具体中间件。

- [x] 拆分 MQ 记录仓储内部支撑。
  - 目标：避免 `MessageRecordRepository` 同时承担 MQ 幂等记录、PO/Entity 映射、生产者失败消息重投、routing key 解析和错误截断。
  - 实际拆分：`MessageRecordMapper`、`MessageProducerRetrySupport`。
  - 验收：`MessageRecordRepository` 只保留记录读写和状态更新门面；生产者失败重试由支撑组件处理；架构测试防止重投和映射细节回流。

- [x] 拆分 RabbitMQ 发布器内部支撑。
  - 目标：避免 `EventPublisher` 同时承担 RabbitMQ 发布、消息 ID 生成、生产者失败台账落库、PO 构建和错误截断。
  - 实际拆分：`MqMessageIdGenerator`、`MqProducerFailureRecorder`。
  - 验收：`EventPublisher` 只保留 RabbitMQ 发送、confirm 和 returns callback；失败记录由支撑组件处理；架构测试防止 DAO/PO 和 MessageDigest 细节回流。

- [x] 拆分商城 MQ 发布器和记录仓储内部支撑。
  - 目标：避免商城服务继续保留 MQ 可靠性旧结构，导致支付成功消息和对账重放链路与营销侧架构不一致。
  - 实际拆分：`MqMessageIdGenerator`、`MqProducerFailureRecorder`、`MessageRecordMapper`、`MessageProducerRetrySupport`。
  - 验收：商城 `EventPublisher` 只保留 RabbitMQ 发布；商城 `MessageRecordRepository` 只保留记录读写门面；商城 app 单元测试和全局架构测试防止细节回流。

- [x] 拆分秒杀限流端口内部固定窗口支撑。
  - 目标：避免 `SeckillRateLimitPort` 同时承担三维限流策略、Redis Key、Lua、固定窗口计数和 Redisson 调用。
  - 实际拆分：`SeckillFixedWindowRateLimitSupport`。
  - 验收：`SeckillRateLimitPort` 保留启停开关、活动/用户/IP 配置和限流顺序；Redis 固定窗口执行细节进入支撑组件；架构测试防止 Redisson/Lua 细节回流。

- [x] 拆分秒杀 Redis Stream 缓冲队列内部技术细节。
  - 目标：避免 `SeckillOrderCreateBuffer` 继续承载分片路由、消息映射、DLQ payload 和指标采样等细节。
  - 实际拆分：`SeckillOrderBufferMessage`、`SeckillStreamShardRouter`、`SeckillStreamMessageMapper`、`SeckillStreamMetricsSampler`。
  - 验收：`DomainPurityTest` 防止 CRC32、StreamAddArgs、JSON payload、指标 Lua 和内部 BufferMessage 回流到缓冲主类。

- [x] 拆分秒杀缓冲队列策略。
  - 目标：避免 `SeckillOrderCreateBuffer` 同时承载本地队列、Redis Queue、Redis Stream、ACK、pending 回收和失败隔离。
  - 实际拆分：`SeckillLocalOrderCreateBuffer`、`SeckillRedisQueueOrderCreateBuffer`、`SeckillRedisStreamOrderCreateBuffer`。
  - 验收：`SeckillOrderCreateBuffer` 只保留模式选择和委托；架构测试防止 Redis Stream API、BlockingQueue、Redis Queue 和 pending retry 细节回流。

- [x] 拆分 Redis Stream 缓冲策略生命周期。
  - 目标：避免 `SeckillRedisStreamOrderCreateBuffer` 自身继续承担 Stream 初始化、投递、读取、ACK、pending 回收和失败隔离。
  - 实际拆分：`SeckillRedisStreamRegistry`、`SeckillRedisStreamPublisher`、`SeckillRedisStreamReader`、`SeckillRedisStreamAcknowledger`、`SeckillRedisStreamFailureIsolator`。
  - 验收：`SeckillRedisStreamOrderCreateBuffer` 只保留策略门面委托；架构测试防止 Redisson/Stream API、consumer 游标、pending retry 和人工补偿隔离细节回流。

- [x] 拆分秒杀人工补偿 Stream 端口实现。
  - 目标：避免 `SeckillOrderCreateBuffer` 同时承担缓冲队列和人工补偿台领域端口实现。
  - 实际拆分：`SeckillManualCompensationStream`、`SeckillManualCompensationPort`。
  - 验收：`SeckillOrderCreateBuffer` 不再实现 `ISeckillManualCompensationPort`，不再暴露人工补偿查询、重放和 Stream Key 方法；补偿台仍通过领域端口查询和重放人工补偿消息。

- [x] 拆分秒杀 HTTP Controller 支撑逻辑。
  - 目标：避免 `SeckillMarketController` 继续承担请求校验矩阵、客户端 IP 解析和 Entity 到 DTO 字段映射。
  - 实际拆分：`SeckillRequestValidator`、`ClientIpResolver`、`SeckillResponseAssembler`。
  - 验收：`DomainPurityTest` 防止 `StringUtils.isBlank` 校验、`X-Forwarded-For`/`X-Real-IP`/`getRemoteAddr` 解析和秒杀响应 DTO builder 回流到 Controller。

- [x] 拆分秒杀 HTTP 用例编排。
  - 目标：避免 `SeckillMarketController` 继续承担活动查询、锁单幂等、限流、指标、结算、退款和结构化日志编排。
  - 实际拆分：`SeckillMarketConfigQuerySupport`、`SeckillLockOrderSupport`、`SeckillOrderResultQuerySupport`、`SeckillSettlementSupport`、`SeckillRefundSupport`。
  - 验收：`SeckillMarketController` 不再直接依赖秒杀领域服务、限流端口、指标端口、结构化日志、请求校验器、响应组装器和实体对象；架构测试防止这些用例编排细节回流。

- [x] 拆分秒杀补偿台 HTTP 用例编排。
  - 目标：避免 `SeckillOpsController` 继续承担管理员 token 校验、人工补偿查询/重放、审计写入、JSON 序列化和 domain entity 响应契约。
  - 实际拆分：`SeckillOpsAdminSupport`、`SeckillManualCompensationOpsSupport`、`SeckillManualCompensationResponseAssembler`、`ReplaySeckillManualRequestDTO`、`SeckillManualMessageResponseDTO`、`SeckillManualCompensationLogResponseDTO`。
  - 验收：补偿台接口路径保持不变；Controller 只保留 HTTP 路由委托；架构测试防止认证、审计、FastJSON 和领域实体回流。

- [x] 拆分 MQ 运维 HTTP 用例编排。
  - 目标：避免 `MqOpsController` 继续承担管理员 token 校验、失败消息查询、标记处理、生产者失败重试、操作人兜底和 `MessageRecordEntity` 响应契约。
  - 实际拆分：`MqOpsAdminSupport`、`MqOpsSupport`、`MqOpsResponseAssembler`、`MarkMqMessageHandledRequestDTO`、`MqFailedMessageResponseDTO`。
  - 验收：MQ 运维接口路径保持不变；Controller 只保留 HTTP 路由委托；架构测试防止认证、领域服务调用和领域实体响应回流；DTO 组装单元测试通过。

- [x] 补齐 Stream 人工补偿治理。
  - 目标：人工补偿 Stream 不只是失败隔离，还要有可查询、可重放、可审计能力。
  - 本机已做：补偿查询接口、单条/批量重放接口、`seckill_manual_compensation_log` 操作日志表、操作记录查询接口和补偿台展示。
  - 生产边界：权限、审批流和 SLA 需要后台系统支撑。

## P2 售后和对账模型

- [x] 扩展售后状态机。
  - 目标：覆盖部分退款、拒绝退款、履约后退款、重复退款拦截。
  - 验收：`OrderStateMachine` 已补 `REFUNDING/PARTIAL_REFUND/REFUND_REJECTED/FULFILLED` 和 `REFUND_APPLY/REFUND_PARTIAL_SUCCESS/REFUND_REJECT/FULFILL`，`OrderStateMachineTest` 已覆盖合法售后迁移和非法重复退款拦截。

- [x] 增加独立支付流水和退款流水模型。
  - 目标：商城订单状态不再替代支付事实，支付成功、退款申请、退款成功、退款失败独立留痕。
  - 本次完成：新增 `PaymentFlowEntity`、`RefundFlowEntity`、`IPaymentFlowPort`、`IRefundFlowPort`，并把支付/退款流水 DAO 适配收敛到独立端口。
  - 验收：`OrderService` 记录支付/退款事实，`OrderRepository` 不再依赖支付/退款流水 DAO/PO，对账仓储可基于支付流水、退款流水、商城订单和营销订单生成差错单。

- [x] 拆分商城商品端口和营销交易端口。
  - 目标：避免 `IProductPort` 同时承载商品查询、拼团/秒杀锁单、营销结算和营销退款。
  - 实际拆分：`IProductQueryPort`、`IMarketOrderLockPort`、`IMarketSettlementPort`、`IMarketRefundPort`。
  - 验收：`IProductPort` 删除；`ProductPort` 只负责商品查询；订单主链路、对账重放、退款流程按最小语义端口依赖；架构测试防止通用商品端口回流。

- [x] 完善对账差错处理闭环。
  - 目标：差错单支持人工确认、重放、忽略、关闭、备注和审计。
  - 本次完成：新增 `ReconcileCaseStatusVO`，补齐确认、忽略、关闭、备注、操作日志查询接口和前端入口；终态差错单不会被扫描 upsert 重新打开，重放只允许待处理差错单执行。
  - 生产边界：统一登录、权限审批、SLA 报表和正式告警通知路由后续再补。

- [x] 拆分对账后台 Controller 管理员支撑细节。
  - 目标：避免 `ReconcileCaseController` 继续承担管理员 token 配置、操作人解析、审计写入和 CSV 请求预览截断。
  - 实际拆分：`ReconcileAdminSupport`。
  - 验收：`DomainPurityTest` 防止 `@Value`、`adminToken`、`recordReconcileOperation`、`local-admin` 和 CSV `substring` 预览截断回流到 Controller。

- [x] 拆分对账后台 HTTP 用例编排。
  - 目标：避免 `ReconcileCaseController` 继续承担扫描、查询、处理、确认、忽略、关闭、备注、批量处理、重放、批量重放、账单导入和告警 webhook 编排。
  - 实际拆分：`ReconcileCaseQueryEndpointSupport`、`ReconcileCaseOperationSupport`、`ReconcileCaseReplaySupport`、`ReconcileBillImportSupport`、`ReconcileAlertWebhookSupport`，并把请求体拆成独立 trigger request。
  - 验收：`ReconcileCaseController` 不再直接依赖对账领域服务、管理员支撑、查询支撑和 JSON 序列化；架构测试防止这些用例编排细节回流。

- [x] 治理对账查询 HTTP 响应 DTO 边界。
  - 目标：避免 `ReconcileCaseController` 直接把 `ReconcileCaseEntity` / `ReconcileOperationLogEntity` 暴露成 HTTP API 契约。
  - 实际拆分：`ReconcileCaseResponseDTO`、`ReconcileOperationLogResponseDTO`、`ReconcileResponseAssembler`、`ReconcileQuerySupport`。
  - 验收：`DomainPurityTest` 防止对账查询接口重新声明 `Response<List<ReconcileCaseEntity>>` 或 `Response<List<ReconcileOperationLogEntity>>`。

- [x] 拆分商城对账仓储内部技术细节。
  - 目标：避免 `OrderReconcileRepository` 继续承担差错单构建、MQ 重放、三方账单 CSV 解析和 PO/Entity 映射。
  - 实际拆分：`ReconcileCaseFactory`、`MqFailureReplaySupport`、`ThirdPartyBillCsvParser`、`OrderReconcileEntityMapper`。
  - 验收：`DomainPurityTest` 防止 `EventPublisher`、routing key 解析、CSV 解析、金额/时间解析和 builder 映射回流到对账仓储。

- [x] 拆分商城对账仓储扫描和操作日志支撑。
  - 目标：避免 `OrderReconcileRepository` 继续承担多源差错扫描、差错 upsert、操作人兜底、请求/结果截断和操作日志查询。
  - 实际拆分：`ReconcileCaseScanSupport`、`ReconcileOperationLogSupport`。
  - 验收：`OrderReconcileRepository` 保留对账端口门面；扫描规则和操作日志细节进入支撑组件；架构测试和操作日志支撑单元测试通过。

- [x] 拆分商城对账服务自动重放处理器。
  - 目标：避免 `OrderReconcileService` 在完成主链路拆分后继续承担差错单类型路由、订单关单、退款重放、MQ 重放和营销结算补偿。
  - 实际拆分：`ReconcileCaseReplayProcessor`、`MarketSettlementReconcileProcessor`。
  - 验收：`OrderReconcileService` 只保留对账查询、人工处理、备注、操作日志和账单导入门面；重放分支和营销结算差异进入处理器；架构测试防止重放细节回流。

- [x] 审计当前剩余 DDD 架构问题与业务问题。
  - 目标：在大仓储和大 Controller 治理基本完成后，明确当前真正剩下的是哪些代码边界问题、业务完备度问题和本机环境边界，避免继续低收益拆分类。
  - 本次结论：当前主要剩余风险在入口支撑类偏厚、Redis 公共基础设施接口偏大、秒杀消息系统仍以 Redis Stream 为主、对账/售后仍是最小闭环，以及本机压测不能代表生产容量。
  - 验收：新增 `docs/sdd/2026-05-31-current-ddd-business-gap-audit.md`，并同步八股文档和任务清单，后续优先采用增量审计而非继续机械拆分。

- [x] 审计当前代码热点，不做低收益重构。
  - 目标：在“剩余问题”之外，进一步识别最容易重新膨胀的大类，明确哪些属于观察点而不是立即拆分点。
  - 本次结论：`GroupBuyLockOrderSupport`、`SeckillLockOrderSupport` 属于入口编排热点；`IRedisService` / `RedissonService` 属于基础设施大接口；`AbstractOrderService`、`SeckillService` 仍保留少量营销分支和本地技术决策。
  - 验收：新增 `docs/sdd/2026-05-31-code-hotspot-audit.md`，后续新增需求优先检查这些热点是否再次跨越职责边界。

- [x] 拆分商城支付 Controller 技术细节。
  - 目标：避免 `AliPayController` 继续承担支付宝回调验签、主动查询、回调指标和用户订单列表 DTO 映射。
  - 实际拆分：`AlipayNotifySupport`、`ActivePayNotifySupport`、`OrderListResponseAssembler`。
  - 验收：`DomainPurityTest` 防止 `AlipayClient`、`AlipaySignature`、`AlipayTradeQueryModel`、`JSONObject`、`SimpleDateFormat`、`getParameterMap`、`Collectors.toList` 和 `QueryOrderListResponseDTO.OrderInfo` 构造回流到 Controller。

- [x] 拆分商城支付 Controller 用例编排。
  - 目标：避免 `AliPayController` 继续承担创建支付单、拼团通知结算、用户订单分页和营销退单编排。
  - 实际拆分：`MallPayOrderCreateSupport`、`MallGroupBuyNotifySupport`、`MallOrderQuerySupport`、`MallRefundOrderSupport`。
  - 验收：`AliPayController` 不再直接依赖 `IOrderService`、领域实体构建、结构化业务日志、FastJSON 和响应组装；`DomainPurityTest` 防止这些编排细节回流。

## P3 测试和容量验证

- [x] 补拼团锁单纯单元测试。
  - 覆盖：重复订单、队伍满员、活动不可用、人群标签不匹配、Redis 占位失败、DB 唯一索引兜底。
  - 本次完成：新增 `TradeLockOrderServiceUnitTest`，用 fake port 覆盖锁单幂等、活动状态、参与次数、队伍容量、Redis 占位失败、DB 唯一索引冲突回滚、新开团和参团成功。
  - 边界说明：人群标签不匹配属于首页试算 `TagNode` 职责，本次同步用纯单元测试覆盖试算返回不可见、不可参与，锁单服务不重复混入标签过滤逻辑。

- [x] 补秒杀库存纯单元测试。
  - 覆盖：库存不足、重复参与、预扣成功但异步落库失败、pending 重放、库存释放幂等。
  - 本次完成：新增 `SeckillOrderLockPortUnitTest`，覆盖预扣成功、重复参与、库存不足、售罄短路、消息入队失败回滚、pending retry 隔离策略和库存流水幂等 `flowNo`。
  - 边界说明：Redis Stream `XAUTOCLAIM`、ACK 和人工补偿 Stream 写入属于中间件集成行为，纯单元测试先覆盖 retry policy 和库存回滚不变量，故障演练脚本继续验证真实 Redis 行为。

- [x] 补退款策略测试。
  - 覆盖：未支付释放、已支付未成团退款、已支付已成团退款、重复退款、非法状态退款。
  - 本次完成：新增 `TradeRefundOrderServiceUnitTest`，覆盖三类拼团退单策略路由、重复退单幂等、非法状态业务异常和锁单库存恢复边界。
  - 本次治理：新增 `E0108` 业务错误码，`RefundTypeEnumVO` 不再用普通 `RuntimeException` 表达非法退单状态组合。

- [x] 补对账重放契约测试。
  - 覆盖：支付成功但营销未结算、营销结算成功但商城未完成、退款成功但库存未恢复。
  - 本次完成：新增 `OrderReconcileServiceReplayContractTest`，覆盖拼团/秒杀营销结算重放、待支付关闭、退款重放、MQ 失败重放、非 OPEN 差错单跳过和重放失败备注。
  - 门禁修复：商城 app 的 surefire 配置改为 `<skipTests>${skipTests}</skipTests>`，`-DskipTests=false` 能真实运行对账契约测试。

- [x] 保留生产容量边界。
  - 当前事实：本机 Windows + Docker Desktop 只能证明趋势，不能证明生产 QPS。
  - 后续条件：独立 Linux 压测机、多服务多实例、固定 CPU/内存水位、独立 Redis/MySQL/MQ 节点。
  - 本次完成：新增 `docs/sdd/2026-05-30-production-capacity-boundary.md`，明确本机能证明的内容、不能证明的内容和面试表达边界。

## 每次任务验收命令

```powershell
$env:JAVA_HOME='C:\Program Files\Eclipse Adoptium\jdk-8.0.492.9-hotspot'
$env:Path="$env:JAVA_HOME\bin;E:\java\group_buy_market\.tools\apache-maven-3.8.8\bin;$env:Path"
```

```powershell
cd E:\java\group_buy_market\group-buy-market-master
mvn -q -DskipTests compile
```

```powershell
cd E:\java\group_buy_market\s-pay-mall-ddd-market-master
mvn -q -DskipTests compile
```

```powershell
cd E:\java\group_buy_market
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\check-domain-purity.ps1
```

```powershell
cd E:\java\group_buy_market\group-buy-market-master
mvn -pl group-buy-market-app -am -DskipTests=false -DfailIfNoTests=false "-Dtest=cn.bugstack.test.architecture.DomainPurityTest,cn.bugstack.test.domain.shared.OrderStateMachineTest" test
```
