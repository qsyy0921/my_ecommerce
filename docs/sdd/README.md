# 拼团交易平台 SDD 说明

本文档集用 SDD（Spec Driven Development）方式约束项目后续演进：先写清楚需求、领域边界、接口契约、数据模型和验收标准，再进入编码实现。

当前目标不是简单堆中间件，而是在 DDD 架构下把拼团、秒杀、支付、退单、通知、监控、压测逐步对齐互联网交易系统的工程标准。

## 文档入口

- `requirements.md`：业务目标、非功能目标和验收标准。
- `architecture.md`：DDD 分层、领域边界、核心链路和中间件职责。
- `contracts.md`：商城、营销、支付、前端之间的接口契约。
- `data-model.md`：聚合、表模型、缓存键、幂等键和消息模型。
- `tasks.md`：按 SDD 拆分的实现任务和验收状态。
- `ddd-sdd-todo-list.md`：后续 DDD 治理 TODO，约束所有后续改动必须按 SDD 先规格、后设计、再实现。
- `big-factory-alignment.md`：面向大厂工程实践的差距分析和路线图。
- `mq-evolution.md`：秒杀消息队列从 Redis Stream 演进 RocketMQ 的职责边界、消息模型、迁移步骤和回滚方案。
- `order-state-machine.md`：拼团、秒杀和售后状态机说明。
- `2026-05-30-ddd-boundary-audit.md`：记录本次 AI 辅助开发的 DDD 边界治理、SDD 输入文档、审核结论和验证命令。
- `2026-05-30-ddd-architecture-test-guard.md`：记录 DDD 架构测试、domain 纯净化测试和核心状态机单测。
- `2026-05-30-domain-task-executor-port.md`：记录把 `ThreadPoolExecutor` 从 domain 层抽到 `IDomainTaskExecutor` 端口的治理。
- `2026-05-30-mall-order-reconcile-service-split.md`：记录商城 `OrderService` 与 `OrderReconcileService` 职责拆分。
- `2026-05-30-mall-order-service-usecase-processor-split.md`：记录商城 `OrderService` 支付成功和退款用例处理器拆分。
- `2026-05-30-trade-repository-split.md`：记录 `TradeRepository` 通知任务、库存流水职责拆分方案和验收标准。
- `2026-05-30-group-buy-lock-idempotency.md`：记录拼团锁单请求幂等、结果缓存和用户维度 Redis 占位方案。
- `2026-05-30-group-buy-order-port-split.md`：记录拼团锁单落库从 `ITradeRepository` 拆到 `IGroupBuyOrderPort`。
- `2026-05-31-group-buy-order-support-split.md`：记录拼团锁单端口内部拆出队伍锁定和订单明细写入两个支撑组件。
- `2026-05-30-group-buy-settlement-refund-port-split.md`：记录拼团支付结算和退单写操作从 `ITradeRepository` 拆到独立端口。
- `2026-05-30-group-buy-refund-processor-split.md`：记录 `GroupBuyRefundPort` 继续拆成三类退单处理器，门面只保留委托职责。
- `2026-05-30-group-buy-query-timeout-port-split.md`：记录拼团读模型、超时扫描和渠道策略从通用 `TradeRepository` 拆到独立端口，并删除通用交易仓储。
- `2026-05-30-group-buy-trade-controller-support-split.md`：记录 `MarketTradeController` 拆出请求校验、领域命令组装和响应 DTO 组装组件。
- `2026-05-30-group-buy-trade-controller-usecase-support-split.md`：记录 `MarketTradeController` 继续拆出锁单、结算、退单 3 个 HTTP 用例支撑组件，入口层只保留路由。
- `2026-05-30-payment-callback-idempotency.md`：记录支付回调流水幂等和重复回调后续动作拦截方案。
- `2026-05-30-seckill-prewarm-rate-limit.md`：记录秒杀活动预热、活动/用户/IP 三维限流和入口指标方案。
- `2026-05-30-rabbitmq-dlq-ops.md`：记录 RabbitMQ DLQ 失败台账、指标和人工处理入口方案。
- `2026-05-30-mq-producer-outbox-retry.md`：记录 MQ 生产者 confirm 失败台账、定时重试和人工补偿入口方案。
- `2026-05-30-pressure-invariant-validation.md`：记录本机压测矩阵、秒杀库存不变量、拼团队伍统计不变量和分片库存同步修复。
- `2026-05-30-structured-logs-job-audit.md`：记录结构化业务日志字段、核心事件覆盖、补偿任务分布式锁和执行审计。
- `2026-05-30-local-opentelemetry-jaeger.md`：记录本机 OpenTelemetry Java agent、Jaeger、启动脚本和验证结果。
- `2026-05-30-pressure-resource-watermark.md`：记录压测资源水位采集、联动报告脚本和本机验证结果。
- `2026-05-30-seckill-refund-stock-closure.md`：记录秒杀支付结算、已支付退款、未支付取消和库存流水闭环。
- `2026-05-30-seckill-maintenance-port-split.md`：记录秒杀维护任务端口拆分，隔离用户主链路和 Job 补偿能力。
- `2026-05-31-seckill-maintenance-support-split.md`：记录秒杀维护端口内部拆出库存同步、超时释放和活动预热三个支撑组件。
- `2026-05-30-seckill-repository-split.md`：记录秒杀库存流水、结果缓存、订单分片路由从主仓储拆出。
- `2026-05-30-seckill-stock-reservation-port.md`：记录秒杀 Redis 库存桶、Lua 预扣、用户占位和库存释放从主仓储拆出。
- `2026-05-30-seckill-stock-reservation-internal-split.md`：记录 `SeckillStockReservationPort` 内部继续拆出 Redis Key、库存桶路由和初始化短缓存。
- `2026-05-30-seckill-order-command-port-split.md`：记录秒杀订单创建、批量落库、支付结算和退款状态更新从 `ISeckillRepository` 拆到 `ISeckillOrderCommandPort`。
- `2026-05-30-seckill-order-command-decomposition.md`：记录继续删除 `ISeckillOrderCommandPort`，把秒杀订单创建、支付结算、退款拆到三个生命周期端口。
- `2026-05-30-seckill-repository-delete-port-split.md`：记录删除通用 `ISeckillRepository` / `SeckillRepository`，拆成查询、库存可用性、锁单和维护端口。
- `2026-05-31-seckill-query-support-split.md`：记录秒杀查询端口内部拆出活动查询缓存和结果缓存回源支撑组件。
- `2026-05-30-seckill-order-message-port.md`：记录秒杀下单消息投递从锁单适配器拆到 `ISeckillOrderMessagePort`。
- `2026-05-30-seckill-buffer-internal-split.md`：记录 `SeckillOrderCreateBuffer` 内部拆出 Stream 分片路由、消息映射和指标采样组件。
- `2026-05-30-seckill-buffer-strategy-split.md`：记录秒杀缓冲队列继续拆出本地队列、Redis Queue 和 Redis Stream 三种策略组件。
- `2026-05-30-seckill-redis-stream-lifecycle-split.md`：记录 Redis Stream 策略继续拆出 registry、publisher、reader、acknowledger 和 failure isolator。
- `2026-05-30-seckill-manual-compensation-port-split.md`：记录秒杀人工补偿 Stream 从缓冲队列大类中拆出，改为专门支撑组件和端口 Adapter。
- `2026-05-30-seckill-controller-support-split.md`：记录 `SeckillMarketController` 拆出请求校验、客户端 IP 解析和响应 DTO 组装组件。
- `2026-05-30-seckill-controller-usecase-support-split.md`：记录 `SeckillMarketController` 继续拆出 5 个 HTTP 用例支撑组件，入口层只保留路由和限流注解。
- `2026-05-30-seckill-manual-compensation-audit.md`：记录秒杀人工补偿 Stream 查询、重放和操作审计闭环。
- `2026-05-30-after-sale-state-machine.md`：记录售后状态机扩展，覆盖部分退款、拒绝退款、履约后退款和重复退款拦截。
- `2026-05-30-mall-payment-refund-flow-port-split.md`：记录商城支付流水、退款流水和对账仓储从 `OrderRepository` 拆分出来。
- `2026-05-30-reconcile-case-closed-loop.md`：记录对账差错单确认、重放、忽略、关闭、备注和审计查询闭环。
- `2026-05-30-mall-reconcile-repository-internal-split.md`：记录 `OrderReconcileRepository` 内部拆出差错单工厂、MQ 重放、账单 CSV 解析和实体映射组件。
- `2026-05-30-reconcile-controller-admin-support-split.md`：记录 `ReconcileCaseController` 拆出管理员认证、操作人解析、审计和请求预览支撑组件。
- `2026-05-30-reconcile-api-dto-boundary.md`：记录对账查询接口从直接返回 domain entity 改为 API DTO。
- `2026-05-30-reconcile-controller-usecase-support-split.md`：记录 `ReconcileCaseController` 继续拆出查询、处理、重放、账单导入和告警 webhook 用例支撑组件。
- `2026-05-30-mall-alipay-controller-support-split.md`：记录商城 `AliPayController` 拆出支付宝回调、主动查询和订单列表响应组装支撑组件。
- `2026-05-30-mall-product-market-port-split.md`：记录商城商品查询端口和营销交易锁单/结算/退款端口拆分。
- `2026-05-30-mall-order-pay-success-message-port.md`：记录商城订单支付成功 MQ 发布从 `OrderRepository` 拆到 `IOrderPaySuccessMessagePort`。
- `2026-05-30-activity-repository-port-split.md`：记录营销活动通用仓储拆成试算查询、人群标签、DCC 开关和队伍展示四类语义端口。
- `2026-05-30-group-buy-index-controller-support-split.md`：记录 `MarketIndexController` 拆出请求校验、领域命令组装和首页响应 DTO 组装组件。
- `2026-05-30-trade-notification-port-split.md`：记录拼团通知发送从泛化 `ITradePort` 拆到语义通知端口，并拆出 Redis 锁和 HTTP/MQ 渠道分发支撑。
- `2026-05-30-trade-notify-task-port-split.md`：记录拼团通知任务通用端口拆成创建端口和执行端口，并拆出 payload 工厂与 PO/Entity 映射。
- `2026-05-30-group-buy-lock-unit-tests.md`：记录拼团锁单纯单元测试，覆盖幂等、活动、队伍、库存占位、唯一索引兜底和人群标签试算边界。
- `2026-05-30-seckill-stock-unit-tests.md`：记录秒杀库存纯单元测试，覆盖库存不足、重复参与、异步入队失败回滚、pending 重试策略和库存流水幂等。
- `2026-05-30-refund-strategy-unit-tests.md`：记录拼团退单策略单元测试，覆盖未支付释放、已支付未成团、已支付已成团、重复退款和非法状态退款。
- `2026-05-30-reconcile-replay-contract-tests.md`：记录商城对账差错重放契约测试和 surefire 测试门禁修复。
- `2026-05-30-production-capacity-boundary.md`：记录本机压测和生产容量证明之间的边界，避免面试和文档夸大。
- `2026-05-30-mall-alipay-controller-usecase-support-split.md`：记录商城支付入口继续拆出创建支付单、拼团通知、订单查询和退单用例支撑组件。

## SDD 工作流

1. 写清业务场景：拼团试算、拼团锁单、秒杀锁单、支付结算、退单退款、通知补偿。
2. 写清架构决策：哪些能力属于领域层，哪些属于应用编排，哪些属于基础设施适配。
3. 写清契约：每个接口的入参、出参、幂等键、失败码、重试语义。
4. 写清数据：DB 表、唯一索引、状态机、Redis Key、MQ 消息和补偿任务。
5. 写清验收：功能正确性、并发库存一致性、消费幂等、链路追踪、监控告警和压测报告。
6. 按任务实现：每次只改一个明确能力，提交前必须能编译并说明验证结果。

## 大厂对齐原则

- 核心交易链路先保证正确性，再追求吞吐。
- 秒杀入口抗峰值，Redis 做热点削峰，DB 做最终约束。
- MQ 通知必须可重试、可幂等、可进 DLQ、可补偿。
- 跨服务一致性不用分布式事务硬绑，采用本地事务、领域事件、幂等消费和对账补偿。
- 所有关键链路必须有 traceId、业务日志、指标、告警和压测基线。
