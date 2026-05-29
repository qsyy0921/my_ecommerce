# 容量验证记录

## 本机验证环境

- 日期：2026-05-29
- 机器：Windows + Docker Desktop，单机资源共享。
- Docker / WSL2 资源：12 线程、24GB 内存、8GB Swap。
- GPU：NVIDIA GeForce RTX 4060 Ti，Docker `nvidia` runtime 可用，`docker run --gpus all ... nvidia-smi` 已验证。
- 基础组件：MySQL 8.0（23306）、Redis 7.4（26379）、RabbitMQ 3.12.9（5672）。
- 应用形态：营销服务本机三实例，端口 8091 / 8092 / 8093；Nginx 容器统一入口 8080。
- 构建结果：`mvn -q -DskipTests package` 通过。
- SQL 迁移：`docs/sql/2026-05-29-group-buy-seckill-production-hardening.sql` 已执行。

> 说明：Docker Hub 拉取 `eclipse-temurin:8-jre-jammy` 时出现 EOF，所以本次用本机 JVM 多进程跑营销服务，Docker 跑 MySQL / Redis / RabbitMQ / Nginx。项目仍按 Java 8 bytecode 编译；生产容量必须在固定 Linux 资源、JDK 8 运行时、独立压测机上复测。

## 验证链路

### 秒杀查询

- 压测参数：1000 请求，100 并发。
- 结果：1000/1000 成功。
- 吞吐：196.81 req/s。
- 延迟：avg 497.08ms，p95 1231.25ms，p99 1322.67ms。

12 线程 + Nginx 入口复测：

- 压测参数：3000 请求，200 并发。
- 结果：3000/3000 成功。
- 吞吐：321.74 req/s。
- 延迟：avg 611.96ms，p95 811.13ms，p99 943.59ms。

### 秒杀锁单

- 压测参数：1000 请求，200 并发。
- 结果：1000/1000 成功，入口全部返回 `PROCESSING`。
- 吞吐：290.04 req/s。
- 延迟：avg 668.50ms，p95 1408.51ms，p99 1612.53ms。
- 异步结果：`seckill_order` 新增 1000 条，`seckill_stock_flow` 新增 1000 条 `RESERVE`。
- 库存结果：容量测试活动库存从 10000 扣到 9000，锁定量 1000。
- Stream 结果：4 个 Stream 分片 `XLEN=0`，消费组 `pending=0`，`lag=0`。
- 三实例 ACK 分摊：8091 / 8092 / 8093 三个实例合计 ACK 1000 条。

12 线程 + Nginx 入口复测：

- 压测参数：5000 请求，1000 并发。
- 结果：5000/5000 成功，入口全部返回 `PROCESSING`。
- 吞吐：619.29 req/s。
- 延迟：avg 1587.47ms，p95 2818.18ms，p99 2987.54ms。
- 异步结果：`seckill_order` 新增 5000 条，`seckill_stock_flow` 新增 5000 条 `RESERVE`。
- 库存结果：容量测试活动库存从 30000 扣到 25000，锁定量 5000。
- Stream 结果：4 个 Stream 分片 `XLEN=0`，消费组 `pending=0`，`lag=0`。
- 批量落库指标：`seckill_order_batch_insert_seconds_max=0.2427714s`。

### 拼团锁单

- 压测参数：100 请求，50 并发。
- 结果：100/100 成功。
- 吞吐：115.55 req/s。
- 延迟：avg 365.30ms，p95 496.97ms，p99 499.23ms。
- 审计结果：`group_buy_stock_flow` 新增 100 条 `RESERVE`。

12 线程 + Nginx 入口复测：

- 压测参数：300 请求，150 并发。
- 结果：300/300 成功。
- 吞吐：227.18 req/s。
- 延迟：avg 575.34ms，p95 988.11ms，p99 1007.69ms。
- 审计结果：`group_buy_stock_flow` 新增 300 条 `RESERVE`。

## 2026-05-30 压测矩阵和自动校验

本轮新增 `scripts/pressure/run-local-pressure-matrix.ps1` 和 `scripts/pressure/check-invariants.ps1`，压测后不再只靠人工 SQL 抽查，而是自动校验秒杀库存和拼团队伍统计不变量。

### 秒杀 100/500/1000 阶梯

结果目录：`docs/capacity-results/20260530-022638`

- 秒杀查询：1000 请求，100 并发，1000/1000 成功，吞吐 140.09 req/s，p95 1085.97ms。
- 秒杀锁单：1000 请求，100 并发，1000/1000 成功，吞吐 254.57 req/s，p95 618.37ms。
- 秒杀锁单：1000 请求，500 并发，1000/1000 成功，吞吐 261.61 req/s，p95 3051.46ms。
- 秒杀锁单：1000 请求，1000 并发，1000/1000 成功，吞吐 285.31 req/s，p95 3371.29ms。

每个锁单阶梯结束后均通过自动不变量校验：

- `available_count + active_order_count = total_count`
- `lock_count = active_order_count`
- Redis 库存桶合计等于 DB 可用库存。
- 用户活动订单无重复。
- 用户外部单号无重复。

最终状态：`total_count=20000`，`active_order_count=3000`，`available_count=17000`，`lock_count=3000`，Redis 库存桶合计 `17000`。

### 拼团试算和锁单

结果目录：

- `docs/capacity-results/20260530-023006-groupbuy-query`
- `docs/capacity-results/20260530-022909-groupbuy`

结果：

- 拼团试算：300 请求，100 并发，300/300 成功，吞吐 95.84 req/s，p95 1508.61ms。
- 拼团锁单：200 请求，50 并发，200/200 成功，吞吐 80.35 req/s，p95 997.47ms。
- 拼团锁单：200 请求，100 并发，200/200 成功，吞吐 79.21 req/s，p95 1742.47ms。

拼团队伍统计自动校验通过：压测队伍数 `400`，订单明细数 `400`，队伍 `lock_count/complete_count` 与明细统计不一致数 `0`，重复用户外部单号 `0`。

### 分片库存同步修复

秒杀库存同步任务已经修复分片表口径：开启 `app.seckill.order-shard-count > 1` 后，会逐个统计 `seckill_order_00` 到 `seckill_order_15` 等物理表中的活跃订单，再回写 `seckill_activity.lock_count/available_count`，避免生产分片模式只统计主表造成库存展示偏差。

## 已解决的生产化缺口

- 容量验证：新增 Docker 三实例编排和本机三进程压测脚本，能重复验证秒杀查询、秒杀锁单、拼团锁单。
- 自动校验：新增压测后库存不变量和拼团队伍统计不变量校验脚本。
- 资源水位：新增 `scripts/pressure/collect-resource-watermark.ps1` 和 `scripts/pressure/run-local-pressure-with-watermark.ps1`，本机压测可同步采集 JVM、Docker、Redis、MySQL、RabbitMQ 和 Actuator 水位。
- 完整状态机：补齐秒杀订单状态值对象，并沉淀拼团队伍、拼团明细、秒杀订单状态图。
- 库存流水审计：秒杀已有 `seckill_stock_flow`，拼团新增 `group_buy_stock_flow`，锁单和退单都会记录 RESERVE / ROLLBACK。
- 专业 MQ 演进：保留 Redis Stream 作为当前轻量削峰方案，同时明确选择 RocketMQ 作为生产订单消息演进目标，并补充 RocketMQ 本地编排与 `seckill_order_outbox` 表。

## 仍需生产复测

- 本机 Windows + Docker Desktop 只能证明链路和趋势，不能作为生产容量上限。
- 需要在 Linux 上按固定 CPU、内存、JDK 8、独立压测机重新执行。
- 本机已有资源水位联动脚本；生产仍需补 Grafana 截图、Prometheus 原始指标归档和多节点水位曲线，形成可审计压测报告。
- Redis Stream 当前适合课程项目规模；如果订单流量继续上升，应演进到 RocketMQ 这类有队列分区、副本、重试、DLQ 和成熟堆积治理能力的专业消息队列。

## 复测命令

```powershell
docker start gbm-mysql gbm-redis gbm-rabbitmq
Get-Content docs\sql\2026-05-29-group-buy-seckill-production-hardening.sql | docker exec -i gbm-mysql mysql -uroot -p123456
powershell -ExecutionPolicy Bypass -File scripts\capacity\run-core-capacity-local.ps1
```
