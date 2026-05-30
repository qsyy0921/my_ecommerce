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
- `2026-05-30-ddd-boundary-audit.md`：记录本次 AI 辅助开发的 DDD 边界治理、SDD 输入文档、审核结论和验证命令。
- `2026-05-30-ddd-architecture-test-guard.md`：记录 DDD 架构测试、domain 纯净化测试和核心状态机单测。
- `2026-05-30-domain-task-executor-port.md`：记录把 `ThreadPoolExecutor` 从 domain 层抽到 `IDomainTaskExecutor` 端口的治理。
- `2026-05-30-mall-order-reconcile-service-split.md`：记录商城 `OrderService` 与 `OrderReconcileService` 职责拆分。
- `2026-05-30-trade-repository-split.md`：记录 `TradeRepository` 通知任务、库存流水职责拆分方案和验收标准。
- `2026-05-30-group-buy-lock-idempotency.md`：记录拼团锁单请求幂等、结果缓存和用户维度 Redis 占位方案。
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
- `2026-05-30-seckill-repository-split.md`：记录秒杀库存流水、结果缓存、订单分片路由从主仓储拆出。

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
