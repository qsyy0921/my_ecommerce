# SDD 任务清单

状态说明：

- `[x]` 已完成。
- `[~]` 已部分完成，需要继续补齐。
- `[ ]` 未开始。

## T1 本地可运行

- [x] 使用 JDK 1.8 和 Maven 3.8 构建项目。
- [x] Docker 本地启动 MySQL、Redis、RabbitMQ。
- [x] 启动支付商城、营销服务、静态前端。
- [x] 前端支持拼团、秒杀、订单页跳转。

## T2 支付渠道

- [x] 创建支付单支持 `payChannel=mock/alipay`。
- [x] 模拟支付不依赖真实支付宝，能本地确认支付。
- [x] 秒杀支付单走商城统一支付入口。
- [x] 支付回调增加独立幂等流水表，重复回调不重复触发 MQ 或营销结算。

## T3 秒杀大厂化

- [x] 增加秒杀活动查询和秒杀锁单接口。
- [x] 秒杀接入商城前端和商城支付流程。
- [x] Redis 库存预扣。
- [x] Redis Lua 原子完成用户占位和库存扣减。
- [x] DB 条件更新库存兜底。
- [x] DB 唯一索引兜底重复参与。
- [x] 活动预热任务：活动开始前批量加载热点库存和活动配置。
- [x] 秒杀入口限流：按活动、用户、IP 维度限流。
- [x] 秒杀排队削峰：热点活动可切换到 MQ/Redis Stream 排队下单。
- [x] 秒杀支付结算和退款库存闭环：`CREATE -> COMPLETE -> REFUND`、未支付取消和库存流水审计。

## T4 拼团大厂化

- [x] 拼团试算、锁单、结算、退单基础链路。
- [x] 责任链校验活动、用户、队伍规则。
- [x] Redis/DB 防超卖，队伍名额已使用 Redis Lua 原子扣减。
- [x] `TradeRepository` 拆出通知任务 Outbox 端口和拼团库存流水审计端口。
- [x] 拼团队伍名额和用户维度占位使用 Redis Lua 原子处理。
- [x] 拼团锁单结果支持 Redis 缓存 + DB 回源的强幂等查询。
- [x] 拼团退单补偿增加状态机校验和流水。

## T5 MQ 可靠性

- [x] 已有 RabbitMQ 结算通知能力。
- [x] 生产者 confirms 已接入，发送失败会落台账并由补偿任务重试。
- [x] 消费幂等表 `mq_message_record`。
- [x] 消费者手动 ACK。
- [x] 失败 NACK 后进入 DLQ。
- [x] DLQ 监听器记录死信消息。
- [x] DLQ 指标告警和人工处理入口。

## T6 可观测性

- [x] 引入 Actuator 和 Prometheus registry。
- [x] 有基础日志。
- [x] 全链路 traceId 过滤器。
- [x] 结构化 JSON 业务日志字段标准。
- [x] 本地 OpenTelemetry Java agent + Jaeger 链路追踪。
- [x] 自定义指标：秒杀库存不足、重复参与、锁单耗时、MQ/Stream 积压。
- [x] Grafana Dashboard。
- [x] 告警规则：错误率、P95、DLQ、库存不一致。

## T7 对账和补偿

- [x] 商城订单、营销订单、支付渠道三方对账任务。
- [x] 商城 `OrderService` 与 `OrderReconcileService` 拆分，订单主链路和对账差错处理分离。
- [x] 商城 `IOrderRepository` 与 `IOrderReconcileRepository` 拆分，订单主链路仓储端口不暴露对账台账能力。
- [x] 超时未支付退单补偿。
- [x] 秒杀已支付退款恢复库存和未支付取消释放资格。
- [x] 已支付未成团退款补偿。
- [x] 已支付已成团售后逆向补偿。
- [x] 补偿任务分布式锁和执行记录。

## T8 压测和容量

- [x] 增加 Node 无依赖压测脚本。
- [x] 输出秒杀查询和商城秒杀支付压测报告。
- [x] 拼团试算压测。
- [x] 拼团锁单压测。
- [x] 秒杀热点大并发压测：100、500、1000 并发梯度。
- [x] 压测后自动校验库存不变量。
- [x] 压测资源水位采集：JVM、Docker、Redis、MySQL、RabbitMQ、Actuator。
- [x] 写容量评估：单机 QPS、瓶颈、扩容策略。

## T9 DDD 治理

- [x] 明确商城服务和营销服务各自维护独立 DDD 分层，拼团和秒杀先作为营销上下文内的两个子域。
- [x] domain 去 Spring 注解，领域对象由 app 层配置类装配。
- [x] `scripts/check-domain-purity.ps1` 可扫描商城/营销 domain 包。
- [x] `DomainPurityTest` 可在 Maven 测试阶段防止 domain 重新引入 Spring/container 注解。
- [x] `OrderStateMachineTest` 覆盖秒杀订单、拼团订单、拼团队伍的合法/非法状态迁移。
- [x] 抽象 `IDomainTaskExecutor`，domain 不再直接依赖 `ThreadPoolExecutor`。
- [~] 继续拆分大 Repository、补偿编排和更多领域用例测试。
