# 商城后台与重复实现审计

## 背景

前序治理已经把商城支付入口、对账后台入口、对账仓储和对账服务做了明显收敛，`ReconcileCaseController`、`AliPayController`、`OrderReconcileService` 都不再承担最厚的业务细节。

本轮继续审计两个容易被忽略的问题：

- 商城后台 support 是否仍然容易继续膨胀。
- 两个服务之间是否已经出现明显重复实现，后续会带来维护成本。

## 发现

### 1. `ReconcileCaseController` 已经不是问题本身，`ReconcileCaseOperationSupport` 才是后台热点

`ReconcileCaseController` 现在已经基本符合触发层预期：只做路由和委托。

但 [ReconcileCaseOperationSupport.java](/E:/java/qsyy-ecommerce-platform/qsyy-commerce-mall/s-pay-mall-ddd-trigger/src/main/java/cn/bugstack/trigger/support/ReconcileCaseOperationSupport.java) 仍然集中承担：

- 管理员鉴权。
- 操作人解析。
- `handle/confirm/ignore/close/remark/batchHandle` 多个后台动作。
- 批量循环处理。
- 操作审计记录。
- 统一响应封装。

结论：

- 这不是 DDD 分层错误，因为它仍在 trigger/support 层。
- 但它已经是“后台运营动作聚合类”，未来如果继续加入审批流、SLA、操作原因模板、批量回滚、任务重试策略，会继续膨胀。

建议：

- 当前不继续拆。
- 如果后台运营能力继续扩展，应优先按“单条操作”和“批量操作”或按“状态处理”和“备注审计”拆成更小的应用支撑对象。

### 2. `AliPayController` 已明显收敛，`LoginController` 还保持传统轻量入口风格

[AliPayController.java](/E:/java/qsyy-ecommerce-platform/qsyy-commerce-mall/s-pay-mall-ddd-trigger/src/main/java/cn/bugstack/trigger/http/AliPayController.java) 当前已经只做入口委托，问题不大。

[LoginController.java](/E:/java/qsyy-ecommerce-platform/qsyy-commerce-mall/s-pay-mall-ddd-trigger/src/main/java/cn/bugstack/trigger/http/LoginController.java) 仍然保留：

- 多个接口内联 `try/catch`。
- 响应对象 builder 重复。
- 登录结果判断和日志输出直接写在 Controller。

结论：

- 这还是可接受的轻量入口。
- 但如果后续新增更多登录方式、票据续期、登录审计、风控校验，应该考虑把登录入口也迁到 support 模式，而不是继续直接在 Controller 里堆流程。

### 3. `JobExecutionRecorder` 已经在两个服务中重复

当前两个服务各自都有：

- [group-buy JobExecutionRecorder.java](/E:/java/qsyy-ecommerce-platform/qsyy-commerce-market/group-buy-market-trigger/src/main/java/cn/bugstack/trigger/job/JobExecutionRecorder.java)
- [mall JobExecutionRecorder.java](/E:/java/qsyy-ecommerce-platform/qsyy-commerce-mall/s-pay-mall-ddd-trigger/src/main/java/cn/bugstack/trigger/job/JobExecutionRecorder.java)

两份实现几乎一致，差异主要只有类注释文本。

它们都承担：

- MySQL job lock 兜底。
- job 执行记录写入。
- 结构化日志。
- trace-id 透传。

结论：

- 这不是当前功能 bug。
- 但这是明确的跨服务重复实现，后续如果要调整锁语义、审计字段、trace 结构、错误截断规则，需要双份维护。

建议：

- 当前先记录，不急于抽公共模块。
- 触发条件设为：当两个服务对 Job 审计能力继续演进时，再考虑抽到 `types` 之外的新 shared-support 模块，或者统一成复制模板的脚手架约束。

## 总结

这轮审计的重点不是“哪里写错了”，而是指出：

- 商城后台 support 已经成为新的运营能力热点。
- 跨服务的 job 审计组件已经出现真实重复实现。

下一阶段仍然不建议立刻重构，因为当前收益不高；但这两个点应该进入后续观察名单。
