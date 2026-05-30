# DDD + SDD 后续治理 TODO List

本文档用于约束后续 AI 辅助开发：所有改动必须先明确规格，再进入编码；所有编码必须遵守 DDD 分层，避免继续把流程编排、基础设施细节和补偿逻辑堆进大 Repository。

## 开发约束

- SDD 顺序：先补规格文档，再写设计，再拆任务，再编码，再验证，再复盘。
- DDD 边界：domain 只保留领域模型、领域服务、领域端口和状态机，不直接依赖 Spring、MyBatis、Redis、RabbitMQ、HTTP Client 或 DAO/PO。
- 仓储职责：Repository Adapter 只做持久化适配，不承载复杂业务编排；跨表状态变更、补偿、重试和缓存策略优先抽端口或应用服务。
- 测试门禁：每次改动至少执行 JDK 1.8 编译、domain purity 扫描和相关架构/状态机测试。
- 提交规范：每次完成一个闭环任务后提交并推送 GitHub，提交说明写清楚“改了什么、为什么改、如何验证”。

## P0 当前优先级最高

- [ ] 拆分 `TradeRepository` 的剩余职责。
  - 目标：把拼团锁单落库、结算状态更新、退单状态更新拆成更小端口或仓储适配器。
  - 建议拆分：`GroupBuyOrderRepository`、`GroupBuySettlementRepository`、`GroupBuyRefundRepository`。
  - 验收：`ITradeRepository` 不再暴露结算和退单技术方法；拼团领域服务只依赖业务语义端口；现有拼团锁单、结算、退单流程编译通过。

- [x] 拆分 `SeckillRepository` 的 Redis 库存职责。
  - 目标：把库存桶、Lua 预扣、库存释放、用户占位从秒杀主仓储中移出。
  - 建议端口：`ISeckillStockPort` 或 `ISeckillStockReservationPort`。
  - 验收：`SeckillRepository` 不直接拼 Redis stock key，不直接执行库存预扣 Lua；库存不足、重复参与、释放库存语义保持不变。

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

## P1 高并发与消息可靠性

- [ ] 设计专业 MQ 演进方案。
  - 目标：明确 Redis Stream、RabbitMQ、RocketMQ/Kafka/Pulsar 的职责边界。
  - 当前策略：Redis 继续做资格预扣、防重和本机演示削峰；跨服务通知继续用 RabbitMQ；真正大促订单排队建议演进 RocketMQ 或 Kafka。
  - 验收：补充 `docs/sdd/mq-evolution.md` 的选型结论、迁移步骤、消息模型和回滚方案。

- [ ] 为秒杀异步下单增加 MQ 抽象端口。
  - 目标：业务代码不直接绑定 Redis Stream，后续可替换 RocketMQ/Kafka。
  - 建议端口：`ISeckillOrderMessagePort`。
  - 验收：Redis Stream 成为一个 Adapter；生产级 MQ 只需要新增 Adapter，不改秒杀领域服务主流程。

- [ ] 补齐 Stream 人工补偿治理。
  - 目标：人工补偿 Stream 不只是失败隔离，还要有可查询、可重放、可审计能力。
  - 本机可做：补偿查询接口、单条重放接口、操作日志表。
  - 生产边界：权限、审批流和 SLA 需要后台系统支撑。

## P2 售后和对账模型

- [ ] 扩展售后状态机。
  - 目标：覆盖部分退款、拒绝退款、履约后退款、重复退款拦截。
  - 验收：补充订单状态机测试，非法迁移必须失败。

- [ ] 增加独立支付流水和退款流水模型。
  - 目标：商城订单状态不再替代支付事实，支付成功、退款申请、退款成功、退款失败独立留痕。
  - 验收：对账中心可基于支付流水、退款流水、商城订单和营销订单生成差错单。

- [ ] 完善对账差错处理闭环。
  - 目标：差错单支持人工确认、重放、忽略、关闭、备注和审计。
  - 本机可做：接口和表模型。
  - 生产边界：完整后台页面、权限审批、SLA 报表后续再补。

## P3 测试和容量验证

- [ ] 补拼团锁单纯单元测试。
  - 覆盖：重复订单、队伍满员、活动不可用、人群标签不匹配、Redis 占位失败、DB 唯一索引兜底。

- [ ] 补秒杀库存纯单元测试。
  - 覆盖：库存不足、重复参与、预扣成功但异步落库失败、pending 重放、库存释放幂等。

- [ ] 补退款策略测试。
  - 覆盖：未支付释放、已支付未成团退款、已支付已成团退款、重复退款、非法状态退款。

- [ ] 补对账重放契约测试。
  - 覆盖：支付成功但营销未结算、营销结算成功但商城未完成、退款成功但库存未恢复。

- [ ] 保留生产容量边界。
  - 当前事实：本机 Windows + Docker Desktop 只能证明趋势，不能证明生产 QPS。
  - 后续条件：独立 Linux 压测机、多服务多实例、固定 CPU/内存水位、独立 Redis/MySQL/MQ 节点。

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
