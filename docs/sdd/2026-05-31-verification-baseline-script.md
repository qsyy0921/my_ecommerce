# 验证基线脚本化审计

## 背景

上一轮已经把当前目标 Prompt 和验证命令收敛到 `2026-05-31-current-verification-baseline.md`。但只靠文档仍有一个现实风险：后续每轮需要人工从 L0-L7 中挑命令，命令越多，越容易漏跑或跑错模块。

本轮不修改业务代码，只审计验证基线是否值得脚本化，并在判断成立后补一个低风险 PowerShell 入口。

## 结论

验证基线值得脚本化，但不应该做成复杂 CI 系统。

当前更合适的做法是新增一个本地 profile runner：

- `docs-only`：文档变更检查。
- `market-domain`：domain purity 脚本和营销服务架构/状态机测试。
- `market-compile`：营销服务编译。
- `mall-compile`：商城服务编译。
- `group-buy`：拼团锁单和退单领域测试。
- `seckill`：秒杀库存、限流、锁单和结算相关测试。
- `mall-reconcile`：商城支付、MQ 记录和对账重放测试。
- `full-local`：串行执行以上所有本机可验证项。

## 本轮实现

新增 `scripts/verify-current-baseline.ps1`，只封装现有命令，不引入新依赖，不改 Maven 配置，不改业务代码。

示例：

```powershell
cd E:\java\group_buy_market
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\verify-current-baseline.ps1 -ProfileName docs-only
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\verify-current-baseline.ps1 -ProfileName market-domain
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\verify-current-baseline.ps1 -ProfileName seckill
```

## Done List

- [x] 审计验证基线脚本化必要性。
  - 文件：`docs/sdd/2026-05-31-verification-baseline-script.md`
  - 验证：`git diff --check`
  - 提交：`0fe4ca8`

- [x] 新增本地验证 profile runner。
  - 文件：`scripts/verify-current-baseline.ps1`
  - 验证：`powershell -NoProfile -ExecutionPolicy Bypass -File scripts\verify-current-baseline.ps1 -ProfileName docs-only`
  - 提交：`0fe4ca8`

- [x] 同步当前 SDD 清单入口。
  - 文件：`docs/sdd/README.md`、`docs/sdd/tasks.md`、`docs/sdd/ddd-sdd-todo-list.md`、`docs/sdd/2026-05-31-current-top-risk-map-and-open-items.md`、`docs/sdd/2026-05-31-current-verification-baseline.md`
  - 验证：`git diff --check`
  - 提交：`0fe4ca8`

- [x] 把秒杀 Envelope 契约测试纳入 `seckill` profile。
  - 文件：`scripts/verify-current-baseline.ps1`、`docs/sdd/2026-05-31-current-verification-baseline.md`
  - 验证：`powershell -NoProfile -ExecutionPolicy Bypass -File scripts\verify-current-baseline.ps1 -ProfileName seckill`
  - 提交：`9186d8f`

- [x] 把秒杀 Outbox 重试契约测试纳入 `seckill` profile。
  - 文件：`scripts/verify-current-baseline.ps1`、`docs/sdd/2026-05-31-current-verification-baseline.md`
  - 验证：`powershell -NoProfile -ExecutionPolicy Bypass -File scripts\verify-current-baseline.ps1 -ProfileName seckill`
  - 提交：`bf9ebc0`

- [x] 把专业 MQ key/tag/partition key 契约测试纳入 `seckill` profile。
  - 文件：`SeckillOrderCreateMessageContractTest.java`
  - 验证：`powershell -NoProfile -ExecutionPolicy Bypass -File scripts\verify-current-baseline.ps1 -ProfileName seckill`
  - 提交：`40e67db`

- [x] 评估是否实现 RocketMQ adapter 最小 profile。
  - 文件：`docs/sdd/2026-05-31-seckill-rocketmq-adapter-profile-decision.md`
  - 验证：`powershell -NoProfile -ExecutionPolicy Bypass -File scripts\verify-current-baseline.ps1 -ProfileName docs-only`
  - 提交：`77761c0`

## TODO List

- [ ] P1：补秒杀 Outbox 查询、状态台账和告警。
  - 原因：Outbox 已经具备投递和重试闭环，但运维侧还不能直接查询 INIT/FAILED/DEAD 明细，也没有专门积压和 DEAD 增长告警。
  - 范围：运维查询接口、响应 DTO、Micrometer 指标、Prometheus 告警规则。
  - 验收：能查询 Outbox 状态明细，指标和告警能发现失败积压。

## 所有未完成任务清单

| 任务名称 | 当前状态 | 所属类型 | 优先级 | 不完成的影响 | 当前为什么还没做 | 后续触发条件 |
| --- | --- | --- | --- | --- | --- | --- |
| RocketMQ/Kafka/Pulsar adapter 实现 | 暂不处理 | 生产边界 / 代码风险 | P0 | Redis Stream 容量和堆积能力不能包装成大促终局方案 | 本轮决策认为单机 adapter 不能证明生产能力，且当前端口/契约已足够支撑后续切换 | 有独立 MQ 环境，或明确接受本机 profile 只做演示 |
| 秒杀 Outbox 查询、状态台账和告警 | 未开始 | 业务边界 / 运维边界 | P1 | 目前有自动重试和手动重放，但没有专门查询接口、pending/dead 指标和告警展示 INIT/FAILED/DEAD 明细 | 本轮优先完成 RocketMQ adapter profile 决策，尚未进入运维台账实现 | 明确要完善补偿后台、运维页面或 Outbox 告警 |
| 真实多实例容量验证 | 已阻塞 | 生产边界 | P0 | 本机 QPS 不能证明生产容量 | 只有当前单机环境 | 有独立 Linux 压测机、多服务实例和独立中间件节点 |
| 拼团锁单等待策略重构 | 暂不处理 | 代码风险 | P1 | domain service 继续保留 `Thread.sleep` 技术等待 | 等待超时测试已补齐，但当前没有功能故障 | 压测暴露 RT 抖动，或继续增强锁单幂等策略 |
| Redis 通用接口拆分 | 暂不处理 | 代码风险 / 基础设施边界 | P0 | 公共 Redis 总线继续扩大 | 新增能力准入规则已补齐；直接拆改动面大，现有业务端口暂时守住边界 | 新增 Redis 能力或公共接口继续膨胀 |
| `SeckillService` 本地技术决策治理 | 暂不处理 | 代码风险 | P1 | 单机 `Semaphore` 和本地订单号生成容易被误解为生产能力 | 现阶段没有新增秒杀发布范围 | 要做秒杀生产化或订单号治理 |
| 商城 `AbstractOrderService` 营销类型分支治理 | 暂不处理 | 代码风险 / 业务边界 | P1 | 新增营销类型时 if/else 会继续增长 | 当前只有拼团和秒杀，拆分收益有限 | 新增第三种营销类型 |
| 对账后台权限、审批和 SLA | 未开始 | 业务边界 | P1 | 对账中心只能算最小闭环 | 属于新产品范围 | 明确建设运营后台 |
| 完整售后体系 | 未开始 | 业务边界 | P1 | 不能包装成完整电商售后 | 会引入部分退款、拒绝退款、履约后退款等新模型 | 明确建设售后子系统 |
| 完整支付中台能力 | 未开始 | 业务边界 | P2 | `mock/alipay` 适合演示，不等于支付中台 | 当前项目目标是交易营销，不是支付平台 | 接入更多支付渠道或账单文件 |
| `DomainPurityTest` 结构拆分 | 暂不处理 | 测试缺口 / 文档治理 | P2 | 架构守护可能继续变成大型文本快照测试 | 规则分层已评估，当前测试仍能有效挡回归，直接拆分收益不高 | 新增大量同类守护规则，或该测试继续显著膨胀 |
| 八股文档细粒度短板同步 | 进行中 | 面试口径 | P1 | 面试材料可能把“锁单主流程可替换”误讲成“专业 MQ 已落地” | 本轮同步专业 MQ 最小边界口径 | 每次新增 MQ 边界或主链路结论 |

## 本轮判断

本轮脚本化解决的是“验证命令容易漏跑”的工程效率问题，不改变 DDD 架构、不改变业务链路，也不改变面试口径。因此脚本化本身不需要同步 `interview-baguwen.md`。

下一轮如果继续推进，应回到当前前 5 风险的第一项：秒杀生产化消息链路和容量证明。当前已完成最小切换边界审计、消息 Envelope 契约测试、Outbox 代码闭环、专业 MQ key/tag/partition key 契约和 RocketMQ adapter 最小 profile 决策。下一步优先补 Outbox 查询、状态台账和告警。
