# DDD + SDD 后续治理 TODO List

本文档用于约束后续 AI 辅助开发：所有改动必须先明确规格，再进入编码；所有编码必须遵守 DDD 分层，避免继续把流程编排、基础设施细节和补偿逻辑堆进大 Repository。

## 开发约束

- SDD 顺序：先补规格文档，再写设计，再拆任务，再编码，再验证，再复盘。
- DDD 边界：domain 只保留领域模型、领域服务、领域端口和状态机，不直接依赖 Spring、MyBatis、Redis、RabbitMQ、HTTP Client 或 DAO/PO。
- 仓储职责：Repository Adapter 只做持久化适配，不承载复杂业务编排；跨表状态变更、补偿、重试和缓存策略优先抽端口或应用服务。
- 测试门禁：每次改动至少执行 JDK 1.8 编译、domain purity 扫描和相关架构/状态机测试。
- 提交规范：每次完成一个闭环任务后提交并推送 GitHub，提交说明写清楚“改了什么、为什么改、如何验证”。

## P0 当前优先级最高

- [x] 拆分 `TradeRepository` 的剩余写职责。
  - 目标：把拼团锁单落库、结算状态更新、退单状态更新拆成更小端口或仓储适配器。
  - 建议拆分：`GroupBuyOrderRepository`、`GroupBuySettlementRepository`、`GroupBuyRefundRepository`。
  - 进展：拼团锁单落库已拆到 `IGroupBuyOrderPort`，拼团结算已拆到 `IGroupBuySettlementPort`，三类退单已拆到 `IGroupBuyRefundPort`；基础设施侧 `GroupBuyRefundPort` 继续拆成三类退单处理器，门面不再直接持有 DAO、事务和状态流水细节。
  - 验收：`ITradeRepository` 不再暴露锁单、结算和退单写方法；拼团领域服务只依赖业务语义端口；现有拼团锁单、结算、退单流程编译通过；架构测试防止 `GroupBuyRefundPort` 门面重新膨胀。

- [x] 拆分 `TradeRepository` 的剩余读职责。
  - 目标：把活动、队伍、订单、进度、超时未支付扫描拆成更清晰的读模型端口。
  - 实际拆分：`IGroupBuyQueryPort`、`IGroupBuyTimeoutOrderPort`、`ITradePolicyPort`。
  - 验收：`ITradeRepository` / `TradeRepository` 已删除，架构测试防止通用交易仓储回流。

- [x] 拆分拼团交易 HTTP Controller 支撑逻辑。
  - 目标：避免 `MarketTradeController` 继续承担请求校验矩阵、通知类型解析、API DTO 到领域命令转换和响应 DTO 组装。
  - 实际拆分：`GroupBuyTradeRequestValidator`、`GroupBuyTradeCommandAssembler`、`GroupBuyTradeResponseAssembler`。
  - 验收：`DomainPurityTest` 防止 `StringUtils` 校验、`NotifyTypeEnumVO.valueOf`、领域命令 builder 和拼团交易响应 DTO builder 回流到 Controller。

- [x] 拆分 `SeckillRepository` 的 Redis 库存职责。
  - 目标：把库存桶、Lua 预扣、库存释放、用户占位从秒杀主仓储中移出。
  - 建议端口：`ISeckillStockPort` 或 `ISeckillStockReservationPort`。
  - 本次深化：`SeckillStockReservationPort` 内部继续拆出 `SeckillStockKeyBuilder`、`SeckillStockBucketRouter`、`SeckillStockInitializationCache`，预扣主适配器不再直接维护 Redis Key 常量、CRC32 路由和本地初始化缓存。
  - 验收：`SeckillRepository` 不直接拼 Redis stock key，不直接执行库存预扣 Lua；`SeckillStockReservationPort` 只保留预扣/初始化/查询/释放流程；库存不足、重复参与、释放库存语义保持不变。

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

- [x] 拆分 `SeckillRepository` 的查询和库存可用性职责。
  - 目标：把活动查询、订单查询、结果查询、库存初始化/查询和本地售罄短缓存继续拆开。
  - 实际拆分：`ISeckillQueryPort`、`ISeckillStockAvailabilityPort`、`ISeckillOrderLockPort`、`ISeckillMaintenancePort`。
  - 验收：`ISeckillRepository` / `SeckillRepository` 已删除，架构测试防止通用秒杀仓储回流。

## P1 高并发与消息可靠性

- [x] 设计专业 MQ 演进方案。
  - 目标：明确 Redis Stream、RabbitMQ、RocketMQ/Kafka/Pulsar 的职责边界。
  - 当前策略：Redis 继续做资格预扣、防重和本机演示削峰；跨服务通知继续用 RabbitMQ；真正大促订单排队建议演进 RocketMQ 或 Kafka。
  - 验收：`docs/sdd/mq-evolution.md` 已补齐选型结论、MQ 对比、目标架构、消息模型、路由策略、Outbox 兜底、迁移步骤、回滚方案和本机可验证项。

- [x] 为秒杀异步下单增加 MQ 抽象端口。
  - 目标：业务代码不直接绑定 Redis Stream，后续可替换 RocketMQ/Kafka。
  - 实际端口：`ISeckillOrderMessagePort` / `SeckillOrderMessagePort`。
  - 验收：Redis Stream、RabbitMQ、Redis Queue、本地队列投递选择收敛到消息 Adapter；锁单适配器不再感知具体中间件。

- [x] 拆分秒杀 Redis Stream 缓冲队列内部技术细节。
  - 目标：避免 `SeckillOrderCreateBuffer` 继续承载分片路由、消息映射、DLQ payload 和指标采样等细节。
  - 实际拆分：`SeckillOrderBufferMessage`、`SeckillStreamShardRouter`、`SeckillStreamMessageMapper`、`SeckillStreamMetricsSampler`。
  - 验收：`DomainPurityTest` 防止 CRC32、StreamAddArgs、JSON payload、指标 Lua 和内部 BufferMessage 回流到缓冲主类。

- [x] 拆分秒杀 HTTP Controller 支撑逻辑。
  - 目标：避免 `SeckillMarketController` 继续承担请求校验矩阵、客户端 IP 解析和 Entity 到 DTO 字段映射。
  - 实际拆分：`SeckillRequestValidator`、`ClientIpResolver`、`SeckillResponseAssembler`。
  - 验收：`DomainPurityTest` 防止 `StringUtils.isBlank` 校验、`X-Forwarded-For`/`X-Real-IP`/`getRemoteAddr` 解析和秒杀响应 DTO builder 回流到 Controller。

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

- [x] 完善对账差错处理闭环。
  - 目标：差错单支持人工确认、重放、忽略、关闭、备注和审计。
  - 本次完成：新增 `ReconcileCaseStatusVO`，补齐确认、忽略、关闭、备注、操作日志查询接口和前端入口；终态差错单不会被扫描 upsert 重新打开，重放只允许待处理差错单执行。
  - 生产边界：统一登录、权限审批、SLA 报表和正式告警通知路由后续再补。

- [x] 拆分对账后台 Controller 管理员支撑细节。
  - 目标：避免 `ReconcileCaseController` 继续承担管理员 token 配置、操作人解析、审计写入和 CSV 请求预览截断。
  - 实际拆分：`ReconcileAdminSupport`。
  - 验收：`DomainPurityTest` 防止 `@Value`、`adminToken`、`recordReconcileOperation`、`local-admin` 和 CSV `substring` 预览截断回流到 Controller。

- [x] 治理对账查询 HTTP 响应 DTO 边界。
  - 目标：避免 `ReconcileCaseController` 直接把 `ReconcileCaseEntity` / `ReconcileOperationLogEntity` 暴露成 HTTP API 契约。
  - 实际拆分：`ReconcileCaseResponseDTO`、`ReconcileOperationLogResponseDTO`、`ReconcileResponseAssembler`、`ReconcileQuerySupport`。
  - 验收：`DomainPurityTest` 防止对账查询接口重新声明 `Response<List<ReconcileCaseEntity>>` 或 `Response<List<ReconcileOperationLogEntity>>`。

- [x] 拆分商城对账仓储内部技术细节。
  - 目标：避免 `OrderReconcileRepository` 继续承担差错单构建、MQ 重放、三方账单 CSV 解析和 PO/Entity 映射。
  - 实际拆分：`ReconcileCaseFactory`、`MqFailureReplaySupport`、`ThirdPartyBillCsvParser`、`OrderReconcileEntityMapper`。
  - 验收：`DomainPurityTest` 防止 `EventPublisher`、routing key 解析、CSV 解析、金额/时间解析和 builder 映射回流到对账仓储。

- [x] 拆分商城支付 Controller 技术细节。
  - 目标：避免 `AliPayController` 继续承担支付宝回调验签、主动查询、回调指标和用户订单列表 DTO 映射。
  - 实际拆分：`AlipayNotifySupport`、`ActivePayNotifySupport`、`OrderListResponseAssembler`。
  - 验收：`DomainPurityTest` 防止 `AlipayClient`、`AlipaySignature`、`AlipayTradeQueryModel`、`JSONObject`、`SimpleDateFormat`、`getParameterMap`、`Collectors.toList` 和 `QueryOrderListResponseDTO.OrderInfo` 构造回流到 Controller。

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
