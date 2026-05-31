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
- [x] 秒杀限流端口内部拆出 Redis 固定窗口限流支撑组件，端口保留三维限流策略和配置读取。
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
- [x] MQ 幂等记录仓储已拆出 `MessageRecordMapper` 和 `MessageProducerRetrySupport`，仓储门面不再直接持有生产者重投、routing key 解析和 PO/Entity builder 细节。
- [x] RabbitMQ 发布器已拆出 `MqMessageIdGenerator` 和 `MqProducerFailureRecorder`，发布器不再直接持有 MQ 记录 DAO/PO、MessageDigest 和失败台账构建细节。
- [x] 商城侧 MQ 发布器和消息记录仓储已按同构方式拆出 `MqMessageIdGenerator`、`MqProducerFailureRecorder`、`MessageRecordMapper` 和 `MessageProducerRetrySupport`。
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
- [x] 商城 `OrderService` 支付成功和退款用例拆到 `OrderPaySuccessProcessor`、`OrderRefundProcessor`，订单服务不再直接编排支付流水、营销结算、营销退单和退款流水。
- [x] 商城 `OrderReconcileService` 自动重放编排拆到 `ReconcileCaseReplayProcessor`，营销结算补偿复用 `MarketSettlementReconcileProcessor`，对账服务门面不再直接持有订单仓储、营销结算端口和退款重放细节。
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
- [x] 形成“当前剩余 DDD 架构问题 + 业务问题 + 本机环境边界”统一审计结论，避免继续做低收益机械拆分。
- [x] 形成“当前代码热点”专项审计结论，识别入口支撑类、Redis 公共适配层和少量领域服务中的技术决策残留，先审计、后观察，不急于继续拆分。
- [x] 形成“商城后台运营热点 + 跨服务重复实现”专项审计结论，识别 `ReconcileCaseOperationSupport` 和 `JobExecutionRecorder` 这类后续最可能继续带来维护成本的点。
- [x] 形成“模型层 + 装配层热点”专项审计结论，识别 `GroupBuyActivityDiscountVO` 的字符串协议、`MarketNode` 的异步编排、`OrderStateTransitionEntity` 的共享状态词典、`TradeRuleConfig` 的装配热点和 `GoodsMarketResponseDTO` 的展示逻辑边界。
- [x] 形成“通用网关 + 领域编排残留”专项审计结论，识别 `IRedisService` / `RedissonService` 的超宽技术接口风险、`SeckillService` 的本地技术决策残留、`AbstractOrderService` 的营销分支感知和 `ReconcileCaseOperationSupport` 的后台动作聚合风险。
- [x] 形成“拼团锁单等待策略 + 装配层边界”专项审计结论，识别 `TradeLockOrderService` 的阻塞轮询等待、等待超时测试缺口，并明确 `DomainServiceConfig` 当前仍是合理装配层、`LoginController` 只是低优先级传统入口。
- [x] 形成“架构守护体系热点”专项审计结论，识别 `DomainPurityTest` 已演进成大型字符串规则清单、`check-domain-purity.ps1` 与 JUnit 守护层级存在职责落差，以及后续新增守护规则的维护风险。
- [x] 形成“文档证据漂移”专项审计结论，识别按日期的 SDD 文档中存在大量历史验证快照、测试数量陈旧化和当前权威基线分散的问题。
- [x] 形成“面试文档同步漂移”专项审计结论，识别 `interview-baguwen.md` 对宏观边界的描述仍正确，但还没有完整同步最近几轮更细的剩余问题审计。
- [x] 形成“审计文档碎片化风险”专项审计结论，识别最近几轮 SDD 审计文档虽然单篇成立，但组合起来已经出现主题交叉、关键词重复和入口阅读成本上升的问题。
- [x] 形成“剩余问题优先级拉平风险”专项审计结论，识别当前虽然已经有大量局部优先级判断，但全局上还缺统一的“当前最重要问题排序视图”。
- [x] 形成“当前前 5 个残留风险 + Done/TODO/Open Items”现态清单，明确后续继续审核要优先维护统一风险排序，而不是继续发散新增局部审计。
- [x] 补齐拼团锁单等待超时语义单元测试，覆盖重复请求未拿到锁、缓存和 DB 都无结果、等待 5 次后抛 `E0010`。
- [x] 制定 Redis 通用接口新增能力准入规则，要求新增 Redis 业务能力优先设计业务语义端口，不继续把 `IRedisService` / `RedissonService` 扩成业务总线。
- [x] 整理 SDD README 文档入口分组，按现态与任务、交易主链路、基础设施边界、业务完备度、测试守护、文档与面试口径建立阅读导航。
- [x] 评估 `DomainPurityTest` 规则分层，明确粗筛、稳定结构守护、精细职责回流守护、脆弱文本快照守护和行为契约测试的边界。
- [x] 商城订单支付成功消息发布已从 `OrderRepository` 拆到 `IOrderPaySuccessMessagePort`，Repository 回归订单持久化职责。
- [x] 商城 `OrderRepository` 内部拆出 `PayOrderEntityMapper`，订单仓储和对账 mapper 复用统一订单 PO/Entity 映射。
- [x] 商城 `OrderService` 支付成功和退款用例处理器拆分完成，`DomainPurityTest` 增加回流守护。
- [x] 商城 `OrderReconcileService` 拆出 `ReconcileCaseReplayProcessor` 和 `MarketSettlementReconcileProcessor`，对账服务门面不再混入差错类型路由、订单关单、退款重放和 MQ 重放。
- [x] domain 去 Spring 注解，领域对象由 app 层配置类装配。
- [x] `scripts/check-domain-purity.ps1` 可扫描商城/营销 domain 包。
- [x] `DomainPurityTest` 可在 Maven 测试阶段防止 domain 重新引入 Spring/container 注解。
- [x] `OrderStateMachineTest` 覆盖秒杀订单、拼团订单、拼团队伍和售后流程的合法/非法状态迁移。
- [x] 抽象 `IDomainTaskExecutor`，domain 不再直接依赖 `ThreadPoolExecutor`。
- [x] `TradeTaskService` 改为依赖 `ITradeNotifyTaskExecutionPort`，`ITradeRepository` 不再暴露通知任务扫描和状态更新方法。
- [x] 拼团队伍库存占位拆到 `IGroupBuyTeamStockPort`，`ITradeRepository` 不再暴露 Redis 队伍名额占用和退单恢复方法。
- [x] 拼团队伍库存端口内部继续拆出预扣和恢复支撑组件，端口实现只保留门面委托。
- [x] 拼团锁单请求锁和结果缓存拆到 `ITradeLockRequestPort`，`ITradeRepository` 不再暴露 Redis 请求锁和缓存方法。
- [x] 拼团锁单请求端口内部继续拆出请求锁和锁单结果缓存支撑组件，端口实现只保留门面委托。
- [x] 拼团锁单落库拆到 `IGroupBuyOrderPort`，`ITradeRepository` 不再暴露锁单写方法。
- [x] 拼团锁单端口内部继续拆出队伍锁定和订单明细写入支撑组件，`GroupBuyOrderPort` 保留事务门面。
- [x] 拼团支付结算拆到 `IGroupBuySettlementPort`，`ITradeRepository` 不再暴露结算写方法。
- [x] 拼团结算端口内部继续拆出订单支付完成和队伍成团通知两个支撑组件，`GroupBuySettlementPort` 保留事务门面。
- [x] 拼团三类退单写操作拆到 `IGroupBuyRefundPort`，`ITradeRepository` 不再暴露退单写方法。
- [x] 拼团退单基础设施实现继续拆成未支付、已支付未成团、已支付已成团三个处理器，`GroupBuyRefundPort` 只保留门面委托。
- [x] 拼团读模型查询拆到 `IGroupBuyQueryPort`，超时未支付扫描拆到 `IGroupBuyTimeoutOrderPort`，渠道黑名单策略拆到 `ITradePolicyPort`，通用 `ITradeRepository` / `TradeRepository` 已删除。
- [x] 拼团交易 HTTP 入口 `MarketTradeController` 拆出 `GroupBuyTradeRequestValidator`、`GroupBuyTradeCommandAssembler` 和 `GroupBuyTradeResponseAssembler`，Controller 不再直接维护校验矩阵、通知类型解析、领域命令 builder 和响应 DTO builder。
- [x] 拼团交易 HTTP 入口继续拆出锁单、结算、退单 3 个用例支撑组件，Controller 不再直接编排首页试算、领域服务、结构化日志和异常响应。
- [x] 拼团首页 HTTP 入口 `MarketIndexController` 已拆出请求校验、领域命令组装和首页响应 DTO 组装组件。
- [x] 拼团通知任务发送已从泛化 `ITradePort` / `TradePort` 拆到显式 `ITradeNotificationPort`，并拆出 Redis 锁和 HTTP/MQ 渠道分发支撑组件。
- [x] 拼团通知任务端口已从通用 `ITradeNotifyTaskPort` 拆成创建端口和执行端口，并拆出 payload 工厂与 PO/Entity 映射组件。
- [x] 拼团锁单纯单元测试已补齐，覆盖重复请求、队伍满员、活动不可用、人群标签试算拦截、Redis 占位失败和 DB 唯一索引兜底回滚。
- [x] 秒杀库存同步、活动预热、超时未支付释放拆到 `ISeckillMaintenancePort`，`ISeckillRepository` 不再暴露 Job 维护方法。
- [x] 秒杀维护端口内部继续拆出库存同步、超时未支付释放和活动预热三个支撑组件，`SeckillMaintenancePort` 只保留门面委托。
- [x] 秒杀库存流水拆到 `ISeckillStockFlowPort`，`SeckillRepository` 不再直接构建库存流水 PO。
- [x] 秒杀结果缓存拆到 `ISeckillResultCachePort`，`SeckillRepository` 不再直接维护结果缓存 Key 和 Redis get/set。
- [x] 秒杀订单分片路由拆到 `SeckillOrderShardRouter`，分片表名和路由规则从主仓储移出。
- [x] 秒杀 Redis 库存预扣拆到 `ISeckillStockReservationPort`，`SeckillRepository` 不再直接依赖 `IRedisService`、库存桶 Key、用户占位 Key 和 Lua 预扣细节。
- [x] 秒杀库存预扣适配器内部继续拆出 `SeckillStockKeyBuilder`、`SeckillStockBucketRouter`、`SeckillStockInitializationCache`，预扣主适配器不再直接持有 Key 常量、CRC32 和本地初始化缓存。
- [x] 秒杀库存预扣适配器内部继续拆出库存桶初始化/查询和资格预扣/释放两个支撑组件，端口实现只保留门面委托。
- [x] 秒杀库存可用性端口内部继续拆出库存快照和库存初始化支撑组件，端口实现只保留配置和委托。
- [x] 秒杀锁单端口内部继续拆出库存闸门和预扣发布两个支撑组件，入口实现只保留配置和委托。
- [x] 秒杀订单创建、批量落库、支付结算和退款状态更新先拆到订单命令端口，后续继续拆成 `ISeckillOrderCreatePort`、`ISeckillSettlementPort`、`ISeckillRefundPort`，`ISeckillOrderCommandPort` 已删除。
- [x] 秒杀订单创建端口内部继续拆出单条创建和批量创建两个支撑组件，`SeckillOrderCreatePort` 只保留事务门面。
- [x] 秒杀结算端口内部继续拆出已支付结算支撑组件，`SeckillSettlementPort` 只保留查询、状态校验和结果缓存。
- [x] 秒杀退款端口内部继续拆出未支付取消和已支付退款两个状态支撑组件，`SeckillRefundPort` 只保留查询、幂等和路由。
- [x] 秒杀查询、库存可用性、锁单预扣和维护任务分别拆到 `ISeckillQueryPort`、`ISeckillStockAvailabilityPort`、`ISeckillOrderLockPort`、`ISeckillMaintenancePort`，通用 `ISeckillRepository` / `SeckillRepository` 已删除。
- [x] 秒杀查询端口内部继续拆出活动查询缓存和结果缓存回源支撑组件，`SeckillQueryPort` 只保留读模型门面委托。
- [x] 秒杀下单消息投递拆到 `ISeckillOrderMessagePort`，`SeckillOrderLockPort` 不再感知 Redis Stream、RabbitMQ、routing key 和 JSON 序列化。
- [x] 秒杀 Redis Stream 缓冲队列内部继续拆出 `SeckillStreamShardRouter`、`SeckillStreamMessageMapper`、`SeckillStreamMetricsSampler` 和 `SeckillOrderBufferMessage`，缓冲主类不再直接持有分片 hash、StreamAddArgs、DLQ payload 和指标采样 Lua。
- [x] 秒杀缓冲队列继续拆出本地队列、Redis Queue 和 Redis Stream 三种策略组件，`SeckillOrderCreateBuffer` 只保留模式选择和委托。
- [x] 秒杀 Redis Stream 策略继续拆出 registry、publisher、reader、acknowledger 和 failure isolator，`SeckillRedisStreamOrderCreateBuffer` 只保留策略门面委托。
- [x] 秒杀人工补偿 Stream 查询/重放从 `SeckillOrderCreateBuffer` 拆到 `SeckillManualCompensationStream` 和 `SeckillManualCompensationPort`，缓冲主类不再直接实现补偿台领域端口。
- [x] 秒杀 HTTP 入口 `SeckillMarketController` 拆出 `SeckillRequestValidator`、`ClientIpResolver` 和 `SeckillResponseAssembler`，Controller 不再直接维护校验矩阵、代理 IP 解析和 DTO 字段映射。
- [x] 秒杀 HTTP 入口继续拆出活动查询、锁单、结果查询、结算、退款 5 个用例支撑组件，Controller 不再直接编排领域服务、限流、指标和结构化日志。
- [x] 秒杀补偿台 HTTP 入口 `SeckillOpsController` 拆出管理员认证、补偿用例、审计记录和响应 DTO 组装，Controller 不再直接暴露人工补偿 domain entity。
- [x] MQ 运维 HTTP 入口 `MqOpsController` 拆出管理员认证、失败消息查询、标记处理、生产者失败重试和响应 DTO 组装，Controller 不再直接暴露 `MessageRecordEntity`。
- [x] 秒杀订单生命周期命令拆成创建、结算、退款三个端口，分片表访问、PO/Entity 转换、库存释放/回滚从主适配器移出。
- [x] 秒杀库存纯单元测试已补齐，覆盖预扣成功、重复参与、库存不足、售罄短路、异步入队失败回滚、pending retry 隔离策略和库存流水幂等键。
- [x] 专业 MQ 演进方案已补齐到 `docs/sdd/mq-evolution.md`，明确 Redis Stream、RabbitMQ、RocketMQ/Kafka/Pulsar 职责边界、消息模型、迁移步骤和回滚方案。
- [x] 拼团退款策略纯单元测试已补齐，覆盖未支付未成团、已支付未成团、已支付已成团、重复退款、非法状态退款和锁单库存恢复边界。
- [x] 秒杀人工补偿 Stream 已补齐操作审计，新增 `ISeckillManualCompensationAuditPort`、`seckill_manual_compensation_log`、`manual_logs` 接口和补偿台操作记录展示。
- [x] 售后状态机已扩展，覆盖部分退款、拒绝退款、履约后退款和重复退款拦截，并补充 `OrderStateMachineTest`。
- [x] 商城支付/退款流水拆到 `IPaymentFlowPort` / `IRefundFlowPort`，对账仓储拆到独立 `OrderReconcileRepository`，`OrderRepository` 只保留订单持久化和订单事件。
- [x] 商城商品端口和营销交易端口已拆分，通用 `IProductPort` 已删除，改为 `IProductQueryPort`、`IMarketOrderLockPort`、`IMarketSettlementPort`、`IMarketRefundPort`，`ProductPort` 只保留商品查询职责。
- [x] 商城 `OrderReconcileRepository` 内部继续拆出 `ReconcileCaseFactory`、`MqFailureReplaySupport`、`ThirdPartyBillCsvParser` 和 `OrderReconcileEntityMapper`，对账仓储不再直接持有差错单构建、MQ 重放、CSV 解析和实体映射细节。
- [x] 商城 `OrderReconcileRepository` 内部继续拆出 `ReconcileCaseScanSupport` 和 `ReconcileOperationLogSupport`，对账仓储不再直接持有多源差错扫描、差错 upsert、操作日志截断和操作日志查询细节。
- [x] 商城 `ReconcileCaseController` 拆出 `ReconcileAdminSupport`，后台入口不再直接持有管理员 token、操作人兜底解析、操作审计写入和导入账单预览截断细节。
- [x] 商城 `ReconcileCaseController` 继续拆出查询、处理、重放、账单导入和告警 webhook 用例支撑组件，Controller 不再直接编排对账服务、审计、批量循环和 JSON 请求快照。
- [x] 商城对账查询接口新增 `ReconcileCaseResponseDTO` / `ReconcileOperationLogResponseDTO`，`ReconcileCaseController` 不再把 `ReconcileCaseEntity` / `ReconcileOperationLogEntity` 作为 HTTP 响应契约。
- [x] 商城 `AliPayController` 拆出 `AlipayNotifySupport`、`ActivePayNotifySupport` 和 `OrderListResponseAssembler`，HTTP 入口不再直接持有支付宝 SDK、回调验签、主动查询和订单列表 DTO 映射细节。
- [x] 商城 `AliPayController` 继续拆出创建支付单、拼团通知、订单列表查询和营销退单 4 个用例支撑组件，HTTP 入口不再直接依赖 `IOrderService`、领域实体构建、结构化业务日志和响应组装。
- [x] 营销活动通用仓储已拆成 `IActivityTrialQueryPort`、`ICrowdTagPort`、`IActivitySwitchPort`、`IGroupBuyDisplayPort`，通用 `IActivityRepository` / `ActivityRepository` 已删除，首页试算、折扣人群标签、DCC 开关和队伍展示不再共用过宽端口。
- [x] 读模型适配器已完成不拆边界审计，`GroupBuyQueryPort`、`GroupBuyDisplayPort`、`ActivityTrialQueryPort` 当前保持查询聚合职责，并由架构测试防止写操作和补偿逻辑回流。
- [x] 对账差错处理补齐 `ReconcileCaseStatusVO` 和终态保护，`reconcile_case` 终态不会被扫描重新打开，重放前先校验待处理状态。
- [x] 对账重放契约测试已补齐，覆盖拼团/秒杀营销结算重放、待支付关闭、退款重放、MQ 失败重放、非 OPEN 跳过和失败备注。
- [x] `DomainPurityTest` 增加商城对账服务重放处理器边界守护，避免差错单重放分支和营销结算补偿细节回流到 `OrderReconcileService`。
- [x] `DomainPurityTest` 增加通用 `ITradeRepository` / `TradeRepository` 删除守护，以及 `IGroupBuyQueryPort` 只读职责守护。
- [x] `DomainPurityTest` 增加读模型适配器只读守护，避免拼团/活动查询适配器混入写操作、状态流水、消息发送和补偿逻辑。
- [x] `DomainPurityTest` 增加拼团锁单端口边界守护，避免队伍写入、订单明细、状态流水和库存流水细节回流。
- [x] `DomainPurityTest` 增加拼团结算端口边界守护，避免订单支付完成、队伍成团、通知任务和锁单结果清理细节回流。
- [x] `DomainPurityTest` 增加拼团锁单请求端口边界守护，避免 Redis Key、TTL 和 JSON 序列化细节回流。
- [x] `DomainPurityTest` 增加拼团队伍库存端口边界守护，避免 Redis 预扣、恢复幂等锁和 TTL 细节回流。
- [x] `DomainPurityTest` 增加 `ISeckillRepository` 维护任务方法回流守护。
- [x] `DomainPurityTest` 增加通用 `ISeckillRepository` / `SeckillRepository` 删除守护，以及秒杀查询、库存可用性、锁单端口职责守护。
- [x] `DomainPurityTest` 增加秒杀查询适配器边界守护，避免活动缓存、分片查询、PO 映射和结果缓存回源细节回流。
- [x] `DomainPurityTest` 增加秒杀维护端口边界守护，避免库存同步、超时释放、预热扫描和状态流水细节回流。
- [x] `DomainPurityTest` 增加秒杀订单创建端口边界守护，避免单条/批量落库、结果缓存、库存流水、状态流水、批量指标和失败回滚细节回流。
- [x] `DomainPurityTest` 增加秒杀结算端口边界守护，避免支付成功状态更新和状态流水细节回流。
- [x] `DomainPurityTest` 增加秒杀退款端口边界守护，避免未支付取消、已支付退款、库存释放和状态流水细节回流。
- [x] `DomainPurityTest` 增加秒杀锁单适配器消息中间件路由回流守护。
- [x] `DomainPurityTest` 增加秒杀锁单端口库存闸门和预扣发布边界守护，避免售罄缓存、预扣、消息投递和回滚细节回流。
- [x] `DomainPurityTest` 增加秒杀限流端口边界守护，避免 Redisson、Lua、Key 前缀和固定窗口计数细节回流。
- [x] `DomainPurityTest` 增加秒杀库存可用性端口边界守护，避免售罄缓存、初始化锁、DB 回源和 Redis 初始化细节回流。
- [x] `DomainPurityTest` 增强秒杀库存预扣端口边界守护，避免 Redis API、Lua 预扣和库存桶循环细节回流。
- [x] `DomainPurityTest` 增加秒杀补偿台 Controller 边界守护，避免 trigger 直接依赖 Redis Stream 实现类。
- [x] `DomainPurityTest` 增强秒杀补偿台 Controller 边界守护，避免管理员认证、审计记录、FastJSON 和 domain entity 响应契约回流到 HTTP 入口。
- [x] `DomainPurityTest` 增加 MQ 运维 Controller 边界守护，避免管理员认证、领域服务调用、`MessageRecordEntity` 响应契约和手动处理编排回流到 HTTP 入口。
- [x] `DomainPurityTest` 增加秒杀市场 Controller 边界守护，避免请求校验、客户端 IP 解析和 DTO 组装回流到 HTTP 入口。
- [x] `DomainPurityTest` 增加拼团交易 Controller 边界守护，避免请求校验、通知类型解析、领域命令组装和 DTO 组装回流到 HTTP 入口。
- [x] `DomainPurityTest` 增加商城对账 Controller 管理后台支撑守护，避免 token、操作人解析、审计写入和 CSV 预览截断回流到 HTTP 入口。
- [x] `DomainPurityTest` 增加商城对账查询 API DTO 边界守护，避免 domain entity 重新成为 HTTP 响应契约。
- [x] `DomainPurityTest` 增加商城 `AliPayController` 边界守护，避免支付宝 SDK、验签解析和 DTO 映射回流到 HTTP Controller。
- [x] `DomainPurityTest` 增强商城 `OrderRepository` 边界守护，避免 PO/Entity builder 和列表映射细节回流。
- [x] `DomainPurityTest` 增强商城 `OrderReconcileRepository` 边界守护，避免差错扫描源、支付/退款流水端口、MQ 失败扫描和操作日志截断细节回流。
- [x] `DomainPurityTest` 增加营销 MQ 记录仓储边界守护，避免 `EventPublisher`、routing key 解析、错误截断和 PO/Entity builder 回流到 `MessageRecordRepository`。
- [x] `DomainPurityTest` 增加营销 RabbitMQ 发布器边界守护，避免 DAO/PO、MessageDigest 和失败台账记录细节回流到 `EventPublisher`。
- [x] `DomainPurityTest` 增加商城 MQ 记录仓储和 RabbitMQ 发布器边界守护，保持商城/营销两个服务消息可靠性结构同构。
- [x] 当前本机可验证的 DDD 大仓储治理项已完成，后续继续按 `docs/sdd/ddd-sdd-todo-list.md` 做增量审计，不再保留泛化未完成项。
