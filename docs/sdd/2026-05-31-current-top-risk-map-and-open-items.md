# 当前前五残留风险与开放任务清单

## 背景

前几轮 SDD 审计已经把问题记录得比较完整，但也暴露出一个新问题：审计文档多、局部优先级多，缺少一张统一的“当前最该盯什么”排序图。

本轮不修改生产代码，目标是把分散在多篇审计文档里的结论收敛成：

- 当前前 5 个残留风险。
- Done List。
- TODO List。
- 所有未完成任务清单。

## 证据来源

本轮排序基于当前工作区文件，而不是记忆判断。主要参考：

- `docs/sdd/2026-05-31-current-ddd-business-gap-audit.md`
- `docs/sdd/2026-05-31-gateway-and-domain-orchestration-audit.md`
- `docs/sdd/2026-05-31-lock-idempotency-and-assembly-boundary-audit.md`
- `docs/sdd/2026-05-31-architecture-guard-hotspot-audit.md`
- `docs/sdd/2026-05-31-interview-sync-drift-audit.md`
- `docs/sdd/2026-05-31-audit-fragmentation-risk.md`
- `docs/sdd/2026-05-31-priority-flattening-risk-audit.md`
- `docs/sdd/tasks.md`
- `docs/sdd/ddd-sdd-todo-list.md`
- `docs/interview-baguwen.md`

## 当前前 5 个残留风险

### 1. 秒杀生产化消息链路和容量证明仍未完成

- 类型：生产边界 / 代码风险
- 优先级：P0
- 当前状态：暂不处理
- 现状：秒杀链路已经有 Redis Lua、Redis Stream 分片、pending-list、人工补偿 Stream、批量落库和库存流水，但主方案仍是 Redis Stream，本机压测也不能证明真实生产容量。
- 不完成的影响：面试或评审时如果把本机 QPS 和 Redis Stream 说成大促终局方案，会明显夸大系统成熟度。
- 当前为什么还没做：缺少真实多机环境、独立压测机和专业 MQ 集群；本机 Docker 环境只能做趋势验证。
- 后续触发条件：有 Linux 多实例环境，或明确要把秒杀下单队列从 Redis Stream 演进到 RocketMQ/Kafka/Pulsar。
- 下一步建议：先做 MQ 演进方案的实现边界评估，再决定是否本机接入 RocketMQ 作为可切换 adapter。

### 2. Redis 通用基础设施接口仍然过宽

- 类型：代码风险 / 基础设施边界
- 优先级：P0
- 当前状态：准入规则已补，拆分暂不处理
- 现状：`IRedisService` / `RedissonService` 仍然像一个 Redis 技术总线，既暴露多种 Redisson 能力，也混入秒杀、拼团队伍库存这类业务语义脚本。
- 不完成的影响：后续新增 Redis 能力时，开发者容易继续往公共接口里塞方法，绕过更窄的业务端口。
- 当前为什么还没做：新增能力准入规则已补齐；现有核心业务端口已经把 Redis 细节隔离住，现在直接大拆 `IRedisService` 改动面很大，收益不如先控制新增使用范围。
- 后续触发条件：新增 Redis 能力、改库存/限流/锁/队列能力，或 `IRedisService` 再继续增加业务语义方法。
- 下一步建议：执行准入规则；必要时把 Redis gateway 按 lock、kv、queue、script 逐步拆小。

### 3. 拼团锁单幂等等待仍是同步阻塞策略

- 类型：代码风险 / 测试缺口
- 优先级：P0
- 当前状态：测试已补，策略暂不处理
- 现状：`TradeLockOrderService` 在没拿到请求锁时，会固定轮询 5 次，每次 `Thread.sleep(50L)`，最多阻塞请求线程约 250ms。
- 不完成的影响：高并发竞争场景下会放大线程占用和 RT 抖动；等待策略直接留在 domain service 中，长期看不够干净。
- 当前为什么还没做：等待超时语义单元测试已补齐，但该等待只发生在幂等竞争路径，当前本机规模下没有形成明显功能故障；贸然重构可能扩大改动面。
- 后续触发条件：拼团锁单压测出现明显 RT 抖动，或继续增强锁单幂等策略。
- 下一步建议：在测试保护下评估是否把等待策略移到应用层或独立策略对象。

### 4. 对账、售后和支付能力仍是最小闭环

- 类型：业务边界
- 优先级：P1
- 当前状态：暂不处理
- 现状：对账中心已有差错单、重放、忽略、关闭、备注和操作日志；售后已有未支付释放、已支付退款、库存恢复和状态机扩展；支付有 mock/alipay 和流水，但仍不是完整支付中台或完整售后系统。
- 不完成的影响：项目可以讲交易闭环，但不能包装成完整电商交易中台。
- 当前为什么还没做：继续补权限、审批、SLA、运营报表、部分退款、拒绝退款、履约后退款和三方账单深度核对，会进入新的产品范围。
- 后续触发条件：明确要把对账后台或售后体系作为下一阶段产品目标。
- 下一步建议：如果继续做业务完备度，优先选一个子系统，不要同时扩展对账、售后和支付。

### 5. 守护、文档和面试口径需要继续收敛

- 类型：文档治理 / 面试口径 / 测试缺口
- 优先级：P1
- 当前状态：进行中
- 现状：`DomainPurityTest` 已经超过 2000 行并依赖大量字符串规则；`docs/sdd` 下审计文档增多，出现证据漂移、碎片化和优先级拉平；`interview-baguwen.md` 的宏观边界正确，但细粒度残留问题需要继续同步。
- 不完成的影响：代码边界可能能守住，但工程叙事会变散，面试口径和 SDD 现态会继续漂移。
- 当前为什么还没做完：这是持续治理问题，不是一次性代码修复；需要每轮维护 Done/TODO/Open Items。
- 后续触发条件：新增审计文档、新增架构测试、新增面试内容或继续改主链路。
- 下一步建议：保持当前这份风险地图和 README 主题入口作为现态入口；后续新增审计必须同步更新清单，而不是只追加新文档。

## Done List

- [x] 审计当前剩余 DDD 架构问题、业务完备度和本机环境边界。
  - 文件：`docs/sdd/2026-05-31-current-ddd-business-gap-audit.md`
  - 验证：`git diff --check`
  - 提交：`6fc6cd1`

- [x] 审计通用 Redis 网关和领域编排残留。
  - 文件：`docs/sdd/2026-05-31-gateway-and-domain-orchestration-audit.md`
  - 验证：`git diff --check`
  - 提交：`c2f75b7`

- [x] 审计拼团锁单幂等等待和装配层边界。
  - 文件：`docs/sdd/2026-05-31-lock-idempotency-and-assembly-boundary-audit.md`
  - 验证：`git diff --check`
  - 提交：`848b707`

- [x] 审计架构守护体系维护风险。
  - 文件：`docs/sdd/2026-05-31-architecture-guard-hotspot-audit.md`
  - 验证：`git diff --check`
  - 提交：`7f3935e`

- [x] 审计文档证据漂移。
  - 文件：`docs/sdd/2026-05-31-documentation-evidence-drift-audit.md`
  - 验证：`git diff --check`
  - 提交：`d497ab0`

- [x] 审计八股文档同步漂移。
  - 文件：`docs/sdd/2026-05-31-interview-sync-drift-audit.md`
  - 验证：`git diff --check`
  - 提交：`5a3b24d`

- [x] 审计 SDD 审计文档碎片化风险。
  - 文件：`docs/sdd/2026-05-31-audit-fragmentation-risk.md`
  - 验证：`git diff --check`
  - 提交：`e54ae4e`

- [x] 审计剩余问题优先级拉平风险。
  - 文件：`docs/sdd/2026-05-31-priority-flattening-risk-audit.md`
  - 验证：`git diff --check`
  - 提交：`82c4896`

- [x] 补齐拼团锁单等待超时语义单元测试。
  - 文件：`group-buy-market-master/group-buy-market-app/src/test/java/cn/bugstack/test/domain/trade/TradeLockOrderServiceUnitTest.java`、`docs/sdd/2026-05-31-group-buy-lock-wait-timeout-test.md`
  - 验证：`mvn -q -pl group-buy-market-app -am "-Dtest=cn.bugstack.test.domain.trade.TradeLockOrderServiceUnitTest" test`
  - 提交：`7dd160a`

- [x] 同步八股文档中的当前前 5 风险口径。
  - 文件：`docs/interview-baguwen.md`
  - 验证：`git diff --check`
  - 提交：`cc80e99`

- [x] 制定 Redis 通用接口新增能力准入规则。
  - 文件：`docs/sdd/2026-05-31-redis-gateway-admission-rule.md`
  - 验证：`git diff --check`
  - 提交：`48b5e8c`

- [x] 整理 SDD README 文档入口分组。
  - 文件：`docs/sdd/README.md`、`docs/sdd/2026-05-31-sdd-readme-entry-grouping.md`
  - 验证：`git diff --check`
  - 提交：`9ecfc10`

- [x] 评估 `DomainPurityTest` 规则分层。
  - 文件：`docs/sdd/2026-05-31-domain-purity-guard-layering.md`
  - 验证：`git diff --check`
  - 提交：本轮提交

## TODO List

- [ ] P1：建立当前验证基线文档。
  - 原因：多篇 SDD 文档记录了历史测试通过情况，后续需要一个现态验证入口，避免证据继续分散。
  - 范围：`docs/sdd`。
  - 验收：明确当前推荐的编译、单测、架构检查和文档检查命令。

## 所有未完成任务清单

| 任务名称 | 当前状态 | 所属类型 | 优先级 | 不完成的影响 | 当前为什么还没做 | 后续触发条件 |
| --- | --- | --- | --- | --- | --- | --- |
| 秒杀专业 MQ 演进落地 | 暂不处理 | 生产边界 / 代码风险 | P0 | Redis Stream 容量和堆积能力不能包装成大促终局方案 | 缺真实 MQ 集群和多机压测环境 | 明确引入 RocketMQ/Kafka/Pulsar 或有生产化演练目标 |
| 真实多实例容量验证 | 已阻塞 | 生产边界 | P0 | 本机 QPS 不能证明生产容量 | 只有当前单机环境 | 有独立 Linux 压测机、多服务实例和独立中间件节点 |
| 拼团锁单等待策略重构 | 暂不处理 | 代码风险 | P1 | domain service 继续保留 `Thread.sleep` 技术等待 | 等待超时测试已补齐，但当前没有功能故障 | 压测暴露 RT 抖动，或继续增强锁单幂等策略 |
| Redis 通用接口拆分 | 暂不处理 | 代码风险 / 基础设施边界 | P0 | 公共 Redis 总线继续扩大 | 新增能力准入规则已补齐；直接拆改动面大，现有业务端口暂时守住边界 | 新增 Redis 能力或公共接口继续膨胀 |
| `SeckillService` 本地技术决策治理 | 暂不处理 | 代码风险 | P1 | 单机 `Semaphore` 和本地订单号生成容易被误解为生产能力 | 现阶段没有新增秒杀发布范围 | 要做秒杀生产化或订单号治理 |
| 商城 `AbstractOrderService` 营销类型分支治理 | 暂不处理 | 代码风险 / 业务边界 | P1 | 新增营销类型时 if/else 会继续增长 | 当前只有拼团和秒杀，拆分收益有限 | 新增第三种营销类型 |
| 对账后台权限、审批和 SLA | 未开始 | 业务边界 | P1 | 对账中心只能算最小闭环 | 属于新产品范围 | 明确建设运营后台 |
| 完整售后体系 | 未开始 | 业务边界 | P1 | 不能包装成完整电商售后 | 会引入部分退款、拒绝退款、履约后退款等新模型 | 明确建设售后子系统 |
| 完整支付中台能力 | 未开始 | 业务边界 | P2 | `mock/alipay` 适合演示，不等于支付中台 | 当前项目目标是交易营销，不是支付平台 | 接入更多支付渠道或账单文件 |
| `DomainPurityTest` 结构拆分 | 暂不处理 | 测试缺口 / 文档治理 | P2 | 架构守护可能继续变成大型文本快照测试 | 规则分层已评估，当前测试仍能有效挡回归，直接拆分收益不高 | 新增大量同类守护规则，或该测试继续显著膨胀 |
| 当前验证基线文档 | 未开始 | 文档治理 / 测试缺口 | P1 | 编译、单测、架构检查和文档检查命令继续分散在历史文档里 | 本轮先完成守护规则分层 | 下一轮继续做验证证据收敛 |
| 八股文档细粒度短板同步 | 暂不处理 | 面试口径 | P1 | 面试材料可能落后于 SDD 现态 | 当前前 5 风险口径已同步，暂无新的面试口径变化 | 每次新增风险排序或主链路结论 |

## 本轮判断

当前最值得优先做的不是再新增单点审计，而是维护这份风险地图。

后续每轮审核都应该先问：

1. 这次发现的问题是否进入前 5 风险。
2. Done/TODO/Open Items 是否需要更新。
3. 八股文档是否需要同步。
4. 是否真的需要改代码，还是只需要记录边界。

## 验收

- 本轮新增当前前 5 风险排序。
- 本轮新增 Done List、TODO List 和所有未完成任务清单。
- 本轮同步 `README.md`、`tasks.md`、`ddd-sdd-todo-list.md`。
- 本轮判断八股文档需要同步，并更新 `interview-baguwen.md` 的当前问题口径。
