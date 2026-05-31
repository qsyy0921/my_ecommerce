# 秒杀 Outbox 运维查询与告警闭环

## 背景

上一轮已经决策当前单机环境不硬接 RocketMQ adapter，优先补本机可闭环的 Outbox 运维可观测性。审计当前代码后确认：`seckill_order_outbox` 已有 `INIT/SENT/FAILED/DEAD` 状态机、自动重试和人工重放入口，但运维侧缺少按状态查询、状态数量台账和 Prometheus 告警。

本轮只补这个真实缺口，不改秒杀锁单、库存预扣、消息投递主流程，也不扩大到完整前端后台。

## 本轮改动

### 运维查询

- `ISeckillOrderOutboxService#queryMessages(status, limit)`：按状态查询 Outbox 明细，状态为空时查询最近记录。
- `ISeckillOrderOutboxService#countMessages(status)`：按状态统计 Outbox 数量。
- `MqOpsController#seckill_order_outbox_messages`：运维接口，支持 `status` 和 `limit`。
- `MqOpsController#seckill_order_outbox_status_counts`：运维接口，返回 `INIT/SENT/FAILED/DEAD` 四类数量。
- API DTO 不直接暴露 domain entity，HTTP 响应使用 `SeckillOrderOutboxResponseDTO` 和 `SeckillOrderOutboxStatusCountResponseDTO`。

### 指标和告警

- 新增 `market_seckill_order_outbox_messages{status="init|sent|failed|dead"}` Gauge。
- 新增 `market_seckill_order_outbox_retry_seconds{mode="auto|manual",outcome="success|failed"}` Timer。
- `docs/observability-alert-rules.yml` 新增：
  - `SeckillOrderOutboxPendingHigh`
  - `SeckillOrderOutboxDeadIncrease`
  - `SeckillOrderOutboxRetrySlow`

## DDD 边界

- domain 层只增加 Outbox 查询和计数语义，不依赖 MyBatis、Micrometer、HTTP 或 Spring MVC。
- infrastructure 层负责 DAO 查询、PO/Entity 映射和 Micrometer 指标。
- trigger 层负责管理员鉴权、HTTP 路由和 DTO 组装。
- 可靠投递、重试、人工重放仍走既有 Outbox 状态机，未修改锁单主流程。

## Done List

- [x] 补秒杀 Outbox 按状态查询和状态数量台账。
  - 文件：`ISeckillOrderOutboxPort.java`、`ISeckillOrderOutboxService.java`、`SeckillOrderOutboxService.java`、`ISeckillOrderOutboxDao.java`、`seckill_order_outbox_mapper.xml`、`MqOpsController.java`、`MqOpsSupport.java`、`MqOpsResponseAssembler.java`
  - 验证：`powershell -NoProfile -ExecutionPolicy Bypass -File scripts\verify-current-baseline.ps1 -ProfileName seckill`
  - 提交：`6f59a58`

- [x] 补秒杀 Outbox 指标和 Prometheus 告警。
  - 文件：`SeckillOrderOutboxMetrics.java`、`SeckillOrderOutboxRetrySupport.java`、`docs/observability-alert-rules.yml`
  - 验证：`git diff --check`
  - 提交：`6f59a58`

- [x] 补秒杀 Outbox 运维查询服务单测。
  - 文件：`SeckillOrderOutboxServiceUnitTest.java`、`scripts/verify-current-baseline.ps1`
  - 验证：`seckill` profile 通过 28 个测试
  - 提交：`6f59a58`

## TODO List

- [ ] P1：审计 `SeckillService` 本地技术决策边界。
  - 原因：Outbox 运维可观测性已经补齐，下一类容易被误读为生产能力的是秒杀领域服务中的本地 `Semaphore` 和本地订单号生成。
  - 范围：先审计 `SeckillService`、订单号生成、入口限流和文档口径；只有发现真实代码风险才修改。
  - 验收：形成 SDD 审计结论，明确哪些是演示/单机能力，哪些需要生产化演进。

## 所有未完成任务清单

| 任务名称 | 当前状态 | 所属类型 | 优先级 | 不完成的影响 | 当前为什么还没做 | 后续触发条件 |
| --- | --- | --- | --- | --- | --- | --- |
| RocketMQ/Kafka/Pulsar adapter 实现 | 暂不处理 | 生产边界 / 代码风险 | P0 | Redis Stream 容量和堆积能力不能包装成大促终局方案 | 单机 adapter 不能证明生产能力，当前端口/契约已足够支撑后续切换 | 有独立 MQ 环境，或明确接受本机 profile 只做演示 |
| 真实多实例容量验证 | 已阻塞 | 生产边界 | P0 | 本机 QPS 不能证明生产容量 | 只有当前单机环境 | 有独立 Linux 压测机、多服务实例和独立中间件节点 |
| `SeckillService` 本地技术决策治理 | 未开始 | 代码风险 / 面试口径 | P1 | 单机 `Semaphore` 和本地订单号生成容易被误解为生产能力 | 本轮只补 Outbox 运维可观测性，避免扩大范围 | 下一轮继续做秒杀生产化边界审计 |
| Redis 通用接口拆分 | 暂不处理 | 代码风险 / 基础设施边界 | P0 | 公共 Redis 总线继续扩大 | 新增能力准入规则已补齐，直接拆改动面大 | 新增 Redis 能力或公共接口继续膨胀 |
| 拼团锁单等待策略重构 | 暂不处理 | 代码风险 | P1 | 高并发幂等竞争下可能放大 RT 抖动 | 等待超时测试已补齐，当前没有功能故障 | 压测暴露 RT 抖动，或继续增强锁单幂等策略 |

## 面试口径

可以这样说：

> 秒杀订单 Outbox 现在不只是有表和重试任务，还补了运维查询、状态计数、Micrometer 指标和 Prometheus 告警。运维侧可以按 `INIT/FAILED/DEAD` 查明细，也能看到积压和 DEAD 增长。但这仍然不是专业 MQ 已落地，RocketMQ/Kafka/Pulsar adapter 和真实多机容量验证仍然是生产演进项。
