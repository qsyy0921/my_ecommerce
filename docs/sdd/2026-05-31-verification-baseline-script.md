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
  - 提交：本轮提交

- [x] 新增本地验证 profile runner。
  - 文件：`scripts/verify-current-baseline.ps1`
  - 验证：`powershell -NoProfile -ExecutionPolicy Bypass -File scripts\verify-current-baseline.ps1 -ProfileName docs-only`
  - 提交：本轮提交

- [x] 同步当前 SDD 清单入口。
  - 文件：`docs/sdd/README.md`、`docs/sdd/tasks.md`、`docs/sdd/ddd-sdd-todo-list.md`、`docs/sdd/2026-05-31-current-top-risk-map-and-open-items.md`、`docs/sdd/2026-05-31-current-verification-baseline.md`
  - 验证：`git diff --check`
  - 提交：`0fe4ca8`

## TODO List

- [ ] P0：定义秒杀订单创建消息 Envelope 和契约测试。
  - 原因：当前消息体仍直接序列化 `SeckillOrderEntity`，不利于后续 RocketMQ/Kafka adapter 稳定演进。
  - 范围：`docs/sdd`、秒杀消息适配器、消息映射测试。
  - 验收：形成稳定 Envelope schema，并验证 `messageId`、`routeKey`、schema 版本和 traceId 不随中间件切换变化。

## 所有未完成任务清单

| 任务名称 | 当前状态 | 所属类型 | 优先级 | 不完成的影响 | 当前为什么还没做 | 后续触发条件 |
| --- | --- | --- | --- | --- | --- | --- |
| 秒杀订单创建消息 Envelope | 未开始 | 代码风险 / 生产边界 | P0 | 后续切 RocketMQ/Kafka 时仍可能依赖 domain entity JSON，消息兼容性不稳定 | 本轮先审计切换边界，不改代码 | 开始实现专业 MQ adapter 或 outbox |
| 秒杀专业 MQ 演进落地 | 暂不处理 | 生产边界 / 代码风险 | P0 | Redis Stream 容量和堆积能力不能包装成大促终局方案 | 缺真实 MQ 集群、多机压测、Envelope 和 outbox 代码闭环 | Envelope、outbox、consumer 契约测试完成后，或明确有生产化演练目标 |
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

下一轮如果继续推进，应回到当前前 5 风险的第一项：秒杀生产化消息链路和容量证明。当前已完成最小切换边界审计，下一步应先定义秒杀订单创建消息 Envelope 和契约测试。
