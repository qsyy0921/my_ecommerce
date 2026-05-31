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
cd E:\java\qsyy-ecommerce-platform
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

- [x] 把秒杀 Outbox 运维查询服务测试纳入 `seckill` profile。
  - 文件：`scripts/verify-current-baseline.ps1`、`SeckillOrderOutboxServiceUnitTest.java`
  - 验证：`powershell -NoProfile -ExecutionPolicy Bypass -File scripts\verify-current-baseline.ps1 -ProfileName seckill`
  - 提交：`6f59a58`

- [x] 把秒杀订单号生成测试纳入 `seckill` profile。
  - 文件：`scripts/verify-current-baseline.ps1`、`SeckillOrderIdPortUnitTest.java`
  - 验证：`powershell -NoProfile -ExecutionPolicy Bypass -File scripts\verify-current-baseline.ps1 -ProfileName seckill`
  - 提交：`a7f37d0`

## TODO List

- [ ] P1：审计本地 `Semaphore` 是否需要独立为入口并发策略。
  - 原因：订单号生成已从领域服务移出，剩余最容易被误读为生产能力的是单机活动级并发闸门。
  - 范围：`SeckillService`、秒杀 HTTP 入口、Redis 限流端口和文档口径；只有压测或代码证据证明它造成问题才修改代码。
  - 验收：明确保留、迁移或删除判断；面试口径明确它不是全局限流。

## 所有未完成任务清单

| 任务名称 | 当前状态 | 所属类型 | 优先级 | 不完成的影响 | 当前为什么还没做 | 后续触发条件 |
| --- | --- | --- | --- | --- | --- | --- |
| RocketMQ/Kafka/Pulsar adapter 实现 | 暂不处理 | 生产边界 / 代码风险 | P0 | Redis Stream 容量和堆积能力不能包装成大促终局方案 | 本轮决策认为单机 adapter 不能证明生产能力，且当前端口/契约已足够支撑后续切换 | 有独立 MQ 环境，或明确接受本机 profile 只做演示 |
| 真实多实例容量验证 | 已阻塞 | 生产边界 | P0 | 本机 QPS 不能证明生产容量 | 只有当前单机环境 | 有独立 Linux 压测机、多服务实例和独立中间件节点 |
| 拼团锁单等待策略重构 | 暂不处理 | 代码风险 | P1 | domain service 继续保留 `Thread.sleep` 技术等待 | 等待超时测试已补齐，但当前没有功能故障 | 压测暴露 RT 抖动，或继续增强锁单幂等策略 |
| Redis 通用接口拆分 | 暂不处理 | 代码风险 / 基础设施边界 | P0 | 公共 Redis 总线继续扩大 | 新增能力准入规则已补齐；直接拆改动面大，现有业务端口暂时守住边界 | 新增 Redis 能力或公共接口继续膨胀 |
| 本地 `Semaphore` 入口闸门治理 | 暂不处理 | 代码风险 / 面试口径 | P1 | 单机闸门容易被误解为全局限流，高并发下也可能放大 RT 抖动 | 当前没有证据显示它造成功能问题，Redis 限流和资格预扣才是主要削峰能力 | 锁单压测出现 RT 抖动，或准备做多实例入口治理 |
| 分布式全局订单号 | 暂不处理 | 生产边界 / 数据模型 | P1 | 当前 12 位兼容订单号不能等同订单中心或全局发号服务 | 表结构是 `varchar(12)`，直接引入 Snowflake 会扩大 SQL 和兼容改动 | 明确升级订单号模型，或引入订单中心/发号服务 |
| 商城 `AbstractOrderService` 营销类型分支治理 | 暂不处理 | 代码风险 / 业务边界 | P1 | 新增营销类型时 if/else 会继续增长 | 当前只有拼团和秒杀，拆分收益有限 | 新增第三种营销类型 |
| 对账后台权限、审批和 SLA | 未开始 | 业务边界 | P1 | 对账中心只能算最小闭环 | 属于新产品范围 | 明确建设运营后台 |
| 完整售后体系 | 未开始 | 业务边界 | P1 | 不能包装成完整电商售后 | 会引入部分退款、拒绝退款、履约后退款等新模型 | 明确建设售后子系统 |
| 完整支付中台能力 | 未开始 | 业务边界 | P2 | `mock/alipay` 适合演示，不等于支付中台 | 当前项目目标是交易营销，不是支付平台 | 接入更多支付渠道或账单文件 |
| `DomainPurityTest` 结构拆分 | 暂不处理 | 测试缺口 / 文档治理 | P2 | 架构守护可能继续变成大型文本快照测试 | 规则分层已评估，当前测试仍能有效挡回归，直接拆分收益不高 | 新增大量同类守护规则，或该测试继续显著膨胀 |
| 八股文档细粒度短板同步 | 进行中 | 面试口径 | P1 | 面试材料可能把“锁单主流程可替换”误讲成“专业 MQ 已落地” | 本轮同步专业 MQ 最小边界口径 | 每次新增 MQ 边界或主链路结论 |

## 本轮判断

本轮脚本化解决的是“验证命令容易漏跑”的工程效率问题，不改变 DDD 架构、不改变业务链路，也不改变面试口径。因此脚本化本身不需要同步 `interview-baguwen.md`。

下一轮如果继续推进，应回到当前前 5 风险的第一项：秒杀生产化消息链路和容量证明。当前已完成最小切换边界审计、消息 Envelope 契约测试、Outbox 代码闭环、Outbox 查询台账/告警、专业 MQ key/tag/partition key 契约、RocketMQ adapter 最小 profile 决策和订单号生成端口治理。下一步优先审计本地 `Semaphore` 边界和真实生产容量证明之间的差距。
