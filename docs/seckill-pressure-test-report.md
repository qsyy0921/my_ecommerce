# 秒杀压测报告

## 目标

验证秒杀核心链路在并发请求下的三个问题：

- 不超卖：成功锁单数量不能超过秒杀活动库存。
- 不重复：同一用户、同一活动、同一订单号不会重复生成多笔有效秒杀单。
- 可观测：能拿到吞吐、平均耗时、P95/P99、业务失败码，方便定位 Redis、MySQL、接口层瓶颈。

## 压测脚本

当前提供两套脚本：

- `scripts/k6/seckill-lock.js`：适合安装了 k6 的机器。
- `scripts/pressure/seckill-pressure.js`：Node 版，无需额外安装，当前本机使用这一版。

两个脚本都会先调用一次 `query_seckill_market_config` 做 Redis 库存预热，避免第一波压测流量同时抢初始化锁。如果要关闭 Node 版预热，可以设置 `WARMUP=false`。

Node 版用法：

```bash
# 秒杀查询接口，不扣库存
node scripts/pressure/seckill-pressure.js

# 秒杀营销锁单接口，会扣库存
SCENARIO=seckill-lock TOTAL=20 CONCURRENCY=5 node scripts/pressure/seckill-pressure.js

# 商城秒杀支付单链路，会扣库存并生成商城订单
SCENARIO=mall-seckill-pay TOTAL=10 CONCURRENCY=5 node scripts/pressure/seckill-pressure.js
```

Windows PowerShell 写法：

```powershell
$env:SCENARIO='mall-seckill-pay'
$env:TOTAL='10'
$env:CONCURRENCY='5'
node scripts/pressure/seckill-pressure.js
```

## 本机压测环境

- 时间：2026-05-24
- 机器：Windows 本地开发环境
- JDK：1.8 运行项目，Node 24 运行压测脚本
- 营销服务：`http://127.0.0.1:8091`
- 支付商城：`http://127.0.0.1:8070`
- 静态前端：`http://127.0.0.1:8088`
- 中间件：Docker MySQL、Redis、RabbitMQ

## 本次结果

### 秒杀查询接口

接口：`POST /api/v1/gbm/seckill/query_seckill_market_config`

参数：

- `SCENARIO=seckill-query`
- `TOTAL=100`
- `CONCURRENCY=10`
- 不扣库存

结果：

| 指标 | 值 |
| --- | ---: |
| 总请求数 | 100 |
| HTTP 成功数 | 100 |
| 业务成功数 | 100 |
| 业务失败数 | 0 |
| 吞吐 | 424.81 req/s |
| 平均耗时 | 19.90 ms |
| P50 | 17.19 ms |
| P95 | 40.01 ms |
| P99 | 43.48 ms |
| 最大耗时 | 65.28 ms |

结论：秒杀活动查询接口在本地 10 并发、100 请求下稳定，业务成功率 100%。

### 商城秒杀支付单链路

接口：`POST /api/v1/alipay/create_pay_order`

参数：

- `SCENARIO=mall-seckill-pay`
- `TOTAL=10`
- `CONCURRENCY=5`
- `payChannel=mock`
- 会触发商城订单创建、营销秒杀锁单、模拟支付表单生成

结果：

| 指标 | 值 |
| --- | ---: |
| 总请求数 | 10 |
| HTTP 成功数 | 10 |
| 业务成功数 | 10 |
| 业务失败数 | 0 |
| 吞吐 | 35.72 req/s |
| 平均耗时 | 113.18 ms |
| P50 | 93.58 ms |
| P95 | 164.62 ms |
| P99 | 164.62 ms |
| 最大耗时 | 164.62 ms |

结论：商城秒杀支付单链路在本地 5 并发、10 请求下业务成功率 100%，支付单创建会正确触发营销秒杀锁单。

## 库存核对

压测后通过秒杀查询接口核对活动库存：

| 字段 | 值 |
| --- | ---: |
| `totalCount` | 100 |
| `availableCount` | 74 |
| `lockCount` | 26 |

核对公式：

```text
availableCount + lockCount = totalCount
74 + 26 = 100
```

结论：当前本地压测样本下没有出现超卖。

## 压测过程发现的问题

第一次执行时曾把“秒杀查询压测”和“商城秒杀支付链路压测”并行运行，导致商城侧调用营销服务 `8091` 时出现 `ConnectException`。这不是库存扣减错误，而是本地单机压测方式互相干扰。

修正方式：

- 查询接口压测和写链路压测分开执行。
- 写链路压测控制库存消耗量。
- 如果要做正式压测，需要独立压测机、独立服务资源和固定数据初始化脚本。

## 压测后核对 SQL

```sql
select activity_id, total_count, available_count, lock_count
from seckill_activity
where activity_id = 900001;

select count(*) as order_count
from seckill_order
where activity_id = 900001;

select market_type, status, count(*) as count
from pay_order
group by market_type, status;
```

## 当前结论

当前本地轻量压测只能证明这套秒杀链路在开发机小并发下可以跑通，并且库存口径没有异常；还不能代表生产容量。

## 2026-05-25 全链路压测补充

除了秒杀锁单接口，当前脚本也补齐了拼团锁单和模拟支付回调链路：

```powershell
# 拼团营销锁单
$env:SCENARIO='group-buy-lock'
$env:TOTAL='200'
$env:CONCURRENCY='50'
$env:ACTIVITY_ID='100123'
node scripts\pressure\seckill-pressure.js

# 商城订单创建 + 模拟支付回调
$env:SCENARIO='mall-mock-pay-callback'
$env:TOTAL='100'
$env:CONCURRENCY='20'
node scripts\pressure\seckill-pressure.js

# 秒杀锁单 + 异步落库结果查询
$env:SCENARIO='seckill-lock'
$env:TOTAL='1200'
$env:CONCURRENCY='200'
$env:QUERY_RESULT='true'
node scripts\pressure\seckill-pressure.js
```

这三类压测分别覆盖：

- `group-buy-lock`：拼团锁单责任链、Redis/DB 防重、队伍名额占用。
- `mall-mock-pay-callback`：商城下单、模拟支付、支付成功状态流转、异步营销结算入口。
- `seckill-lock`：秒杀 Redis Lua、Redis Stream 分片、pending-list、批量落库和幂等查询。

RabbitMQ 堆积压测建议配合消费端短暂停止或 `scripts/chaos/rabbitmq-outage.ps1` 执行，观察 `market_notify_task_pending`、`mall_mq_consume_failed_messages`、RabbitMQ `messages_ready/messages_unacked` 指标。

## 2026-05-25 故障演练脚本

本地 Docker Desktop 环境新增三类可重复演练：

```powershell
powershell -ExecutionPolicy Bypass -File scripts\chaos\redis-jitter.ps1
powershell -ExecutionPolicy Bypass -File scripts\chaos\rabbitmq-outage.ps1
powershell -ExecutionPolicy Bypass -File scripts\chaos\mysql-pause.ps1
```

演练观察点：

- Redis 抖动：秒杀入口失败率、Redis Stream lag、接口恢复时间。
- RabbitMQ 不可用：通知任务积压、MQ 消费失败、DLQ 和对账差错单。
- MySQL 暂停：接口超时、批量落库耗时、`reconcile_case` 是否生成差错单。

本机演练只能验证补偿链路和告警指标是否可用，不能替代独立 Linux 压测机、多实例服务和真实资源隔离下的容量结论。

## 2026-05-25 补偿与观测继续完善

本轮继续补了这些单机可落地内容：

- 对账后台：`reconcile-admin.html` 支持差错单扫描、查询、批量处理/忽略和三方账单 CSV 导入。
- 流水台账：商城新增 `payment_flow`、`refund_flow`、`third_party_bill`，支付/退款状态有独立流水可对账。
- 监控交付：新增 `docs/observability/prometheus.yml`、`alertmanager.yml`、`grafana-transaction-dashboard.json`。
- 故障演练：新增 DB 死锁、HTTP 超时、慢消费/队列堆积脚本。
- 秒杀库存：新增超时未支付释放任务，关闭超时锁定订单并记录 `ROLLBACK_TIMEOUT` 库存流水。

仍需注意：多实例压测、正式账单下载、权限审批、Alertmanager 真实接收人和生产容量报告必须在独立部署环境中补齐。

## 2026-05-25 抗高并发改造

这次把秒杀链路从“能跑通”继续往“能抗突发流量”推进了一步，重点是减少瞬时请求对 DB 和 Tomcat 线程的冲击。

已落地能力：

- 活动配置本地短 TTL 缓存：`SeckillRepository#querySeckillActivity` 对活动和商品静态信息做 3 秒本地缓存，锁单高峰不会每次都查活动表和商品表。
- Redis 库存预热：查询秒杀配置会初始化 `seckill:stock:{activityId}`；压测脚本也会先预热，避免首波流量竞争初始化锁。
- 售罄本地短路：Redis 返回无库存或最后一件被抢完后，应用本地会短时间标记售罄，后续请求直接失败，减少对 Redis 和 DB 的无意义访问。
- 每活动本机并发闸门：`SeckillService#lockSeckillOrder` 按活动 ID 使用 `Semaphore` 限制进入核心锁单段的并发量，当前每个活动每个实例最多 1000 个并发。
- 用户级限流：`SeckillMarketController#lockSeckillOrder` 增加用户维度限流，防止单个用户或脚本反复刷接口。
- Redis 连接池和 Tomcat 参数调大：本地 `application-dev.yml` 将 Tomcat 最大连接、线程、等待队列和 Redis 连接池调到更适合秒杀压测的值。
- DB 仍做最终兜底：秒杀库存扣减依然先走 Redis Lua，成功后 DB 条件更新和唯一索引兜底，保证不超卖、不重复下单。

当前秒杀请求的抗压路径：

```text
用户请求
-> 用户级限流
-> 每活动本机并发闸门
-> 活动配置本地缓存
-> 售罄本地短路
-> Redis Lua 原子扣库存/用户防重
-> MySQL 条件更新 + 唯一索引兜底
-> 返回锁单结果
```

如果要形成面试里更有说服力的“压测报告”，下一步需要补：

- 阶梯压测：10、50、100、200、500 并发逐步拉升。
- 长稳压测：固定 30 到 60 分钟，看内存、连接池、Redis、MySQL 是否稳定。
- 容量结论：给出单机 QPS、P95、P99、错误率、CPU/内存水位。
- 数据初始化脚本：每轮压测前重置秒杀活动库存和 Redis 库存。
- 资源曲线：Prometheus/Grafana 截图或导出数据。

## 2026-05-25 大余量并发压测

本轮把测试活动 `900001` 的秒杀库存重置为大余量，避免库存售罄影响 QPS 判断。压测目标是观察“入口抢资格接口”在全成功写链路下的吞吐和延迟。

测试前重置：

```sql
delete from seckill_order where activity_id = 900001;

update seckill_activity
set total_count = 30000,
    available_count = 30000,
    lock_count = 0,
    status = 1,
    start_time = '2026-01-01 00:00:00',
    end_time = '2029-12-31 23:59:59',
    update_time = now()
where activity_id = 900001;
```

同时清理 Redis 测试 key：

```bash
redis-cli EVAL "for _,k in ipairs(redis.call('keys', ARGV[1])) do redis.call('del', k) end return 1" 0 "seckill:*900001*"
```

压测命令示例：

```powershell
$env:SCENARIO='seckill-lock'
$env:TOTAL='3000'
$env:CONCURRENCY='500'
$env:QUERY_RESULT='false'
node scripts\pressure\seckill-pressure.js
```

### 阶梯结果

| 版本 | 并发 | 请求数 | 业务成功 | 业务失败 | 吞吐 | 平均耗时 | P95 | P99 | 结论 |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | --- |
| 优化前 | 100 | 1000 | 1000 | 0 | 166.27 req/s | 587.60 ms | 1056.18 ms | 1286.59 ms | 可用 |
| 优化前 | 200 | 2000 | 2000 | 0 | 212.83 req/s | 923.14 ms | 1083.97 ms | 1113.01 ms | 可用 |
| 优化前 | 500 | 3000 | 3000 | 0 | 292.48 req/s | 1616.84 ms | 2019.71 ms | 2078.37 ms | 可用但已排队 |
| 优化前 | 1000 | 5000 | 5000 | 0 | 71.37 req/s | 3342.82 ms | 2947.76 ms | 60655.70 ms | 不可接受 |
| 优化后 | 500 | 3000 | 3000 | 0 | 319.87 req/s | 1471.28 ms | 2047.48 ms | 2369.20 ms | 当前较稳点 |
| 优化后 | 1000 | 5000 | 5000 | 0 | 69.49 req/s | 4280.61 ms | 3242.27 ms | 60455.74 ms | 不可接受 |

### 本轮代码优化

压测中发现秒杀锁单热路径存在一个明显浪费：每次请求都会调用 `queryAvailableStock` 汇总 64 个 Redis 库存桶。这个逻辑适合查询库存，不适合每次抢购都执行。

已调整为：

- 热路径只判断库存桶是否初始化。
- 库存初始化仍由 `queryAvailableStock` 完成。
- 正常抢购只走 Redis Lua 资格预占。
- 所有库存桶都扣减失败时，再汇总库存并标记本地售罄。

对应代码：`SeckillRepository#ensureStockInitialized` 和 `SeckillRepository#lockSeckillOrder`。

### 结论

在当前本机开发环境中，秒杀入口能保证 1000 并发下不超卖、不重复，并最终落库成功；但从延迟和吞吐看，1000 并发已经不能算“抗住”。当前比较可接受的单机承载点是：

```text
并发：500 左右
入口 QPS：约 320 req/s
P99：约 2.37s
结果：3000/3000 业务成功，最终 DB 库存一致，MQ 队列清空
```

更高并发下的问题不是库存正确性，而是排队和消费滞后：

- `RabbitMQ publisher confirm` 是入口同步等待点，会限制锁单接口 QPS。
- 异步落单消费者会产生 MQ 堆积，DB 最终落库吞吐低于入口抢资格吞吐。
- 本地压测机、服务、MySQL、Redis、RabbitMQ 都在同一台 Windows 开发机上，结果偏保守，不能直接代表生产容量。

下一步要继续提升 QPS，需要做这些工程改造：

- 将入口返回和 MQ confirm 解耦，改成可靠消息表或事务消息 outbox，由后台批量投递。
- 秒杀订单落库改成批量消费或分库分表，避免单表单行活动库存更新成为写瓶颈。
- Redis 库存按活动预热，压测前不在请求路径触发初始化。
- 消费者按队列分片，提升每个活动的异步落库吞吐。
- 使用独立压测机和 Linux 部署，避免 Windows 本机 Node 压测和 Docker Desktop 干扰。

## 2026-05-25 QPS 二次改造

在上一轮结论基础上，本轮继续把入口链路从“同步确认写链路”改成“资格预占快速返回 + 异步落单 + 冗余库存周期同步”。目标是让秒杀入口只负责抢资格，订单落库、活动表库存字段刷新都从入口线程中移出。

### 已落地改造

- MQ 发送不再等待 broker confirm：秒杀锁单从 `EventPublisher#publish` 切到 `EventPublisher#publishWithoutConfirm`，入口不再阻塞等待 RabbitMQ confirm future。
- DB 活动库存不再每单同步更新：`SeckillRepository#createSeckillOrder` 只插入秒杀订单并写 Redis 结果，`seckill_activity.available_count/lock_count` 改为定时同步。
- 新增库存同步任务：`SeckillStockSyncJob` 每 5 秒按 `seckill_order` 统计回写活动表冗余库存字段，多实例用 Redisson 分布式锁避免重复执行。
- RabbitMQ 消费能力调大：秒杀落单消费者并发从 8 起步，最大 32，prefetch 调到 100。
- 热路径日志瘦身：秒杀入口、服务、消费者日志降到 DEBUG，并把 `RateLimiterAOP` 的逐请求 INFO 日志压到 WARN；本地 dev 关闭 Logstash 直连输出。
- 库存初始化本地标记：预热完成后用 60 秒本地 TTL 标记库存桶已初始化，减少锁单路径上的重复 Redis 初始化判断。

### 最新压测结果

测试活动仍为 `900001`，库存统一重置为 50000，请求数统一为 5000，`QUERY_RESULT=false`，即只统计入口锁单返回。

| 版本 | 并发 | 请求数 | 业务成功 | 业务失败 | 吞吐 | 平均耗时 | P95 | P99 | 结论 |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | --- |
| 异步化后首轮 | 500 | 5000 | 5000 | 0 | 518.77 req/s | 955.62 ms | 1845.65 ms | 2281.63 ms | 稳定 |
| 异步化后首轮 | 1000 | 5000 | 5000 | 0 | 734.77 req/s | 1335.12 ms | 2649.23 ms | 3010.24 ms | 本机峰值 |
| 日志瘦身复测 | 1000 | 5000 | 5000 | 0 | 545.57 req/s | 1807.17 ms | 3979.39 ms | 4771.11 ms | 保守稳定点 |
| 日志瘦身复测 | 2000 | 5000 | 5000 | 0 | 651.02 req/s | 3002.04 ms | 5636.26 ms | 5887.51 ms | 全成功但明显排队 |

### 一致性核对

2000 并发压测结束后，等待异步消费和库存同步任务完成，数据库状态如下：

```text
seckill_activity:
activity_id=900001,total_count=50000,available_count=45000,lock_count=5000,status=1

seckill_order:
activity_id=900001,count=5000

RabbitMQ:
group_buy_market_queue_2_topic_seckill_order_create messages_ready=0,messages_unacknowledged=0
```

说明：入口全部成功，异步订单最终全部落库，活动表冗余库存字段最终一致，MQ 无积压。本地 `mq_message_record` 中仍有 1 条历史失败记录，是之前 DLQ 验证遗留，不属于本轮秒杀压测。

### 当前容量结论

这版代码已经从原来的 500 并发约 320 QPS、1000 并发不可接受，提升到：

```text
保守单机点：1000 并发，约 545 QPS，P99 约 4.8 秒
本机峰值点：1000 并发，约 735 QPS，P99 约 3.0 秒
高并发排队点：2000 并发，约 651 QPS，P99 约 5.9 秒
```

2000 并发能全成功，但已经不是低延迟状态。现在的主要瓶颈不再是超卖控制，而是入口线程排队、RabbitMQ 客户端发送、异步消费者和本机 Docker Desktop 资源争抢。

### 继续提高 QPS 的方向

- 入口不要直接发 RabbitMQ，改成 Redis Stream 或本地 disruptor/ring buffer 快速入队，再由后台批量投递 MQ。
- 使用可靠 outbox 表或 Redis Stream pending-list 补偿，替代单纯不等 confirm 的高吞吐模式，兼顾吞吐和可靠性。
- 秒杀订单消费者做批量 insert，并按活动或用户 hash 分片队列，减少单消费者串行落库压力。
- 把压测机、应用、Redis、MySQL、RabbitMQ 拆到独立 Linux 节点，避免本机 Windows + Docker Desktop 的资源抖动。
- 对 Redis 和 RabbitMQ 做连接池、pipeline、队列分片专项压测，再定最终生产容量水位。

## 2026-05-25 QPS 三次改造：入口缓冲队列

上一轮入口仍然要在请求线程里调用 RabbitTemplate。即使不等待 broker confirm，RabbitMQ 客户端发送、序列化、网络栈和消费者资源争抢仍会影响入口响应。本轮增加一层可配置的秒杀下单缓冲队列，把“抢资格”和“创建订单”进一步解耦。

### 已落地改造

- 新增 `SeckillOrderCreateBuffer`，支持三种模式：
  - `mq`：入口直接投递 RabbitMQ，走上一版链路。
  - `redis_queue`：入口写 Redis 队列，后台 worker 消费落库，适合多实例共享缓冲。
  - `local_queue`：入口写本机有界队列，后台 worker 消费落库，吞吐最高，但进程宕机会丢失未消费消息。
- 新增 `SeckillOrderCreateBufferWorker`，后台轮询缓冲队列并调用 `ISeckillService#createSeckillOrder` 创建真实秒杀订单。
- `SeckillRepository#lockSeckillOrder` 改为抢到 Redis 资格后进入缓冲层；缓冲队列满时回滚 Redis 资格并返回限流失败。
- `application-dev.yml` 使用 `local_queue`，容量 50000，worker 数为 4；压测时让入口优先承接峰值，落库异步追平。
- `application-prod.yml` 已升级为 `redis_stream`，通过消费组和 pending-list 保留多实例共享队列能力。

### 压测结果

测试活动 `900001`，库存 50000，入口锁单接口，`QUERY_RESULT=false`。

| 版本 | 并发 | 请求数 | 业务成功 | 业务失败 | 吞吐 | 平均耗时 | P95 | P99 | 结论 |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | --- |
| RabbitMQ 异步化 | 1000 | 5000 | 5000 | 0 | 545.57 req/s | 1807.17 ms | 3979.39 ms | 4771.11 ms | 保守稳定点 |
| RabbitMQ 异步化 | 2000 | 5000 | 5000 | 0 | 651.02 req/s | 3002.04 ms | 5636.26 ms | 5887.51 ms | 全成功但排队 |
| local_queue 32 workers | 1000 | 5000 | 5000 | 0 | 565.75 req/s | 1745.36 ms | 3984.47 ms | 4445.04 ms | worker 过多，入口仍被争抢 |
| local_queue 4 workers | 1000 | 5000 | 5000 | 0 | 944.08 req/s | 1042.37 ms | 1771.34 ms | 1979.59 ms | 当前最佳稳定点 |
| local_queue 4 workers | 2000 | 5000 | 5000 | 0 | 666.89 req/s | 2921.96 ms | 5533.78 ms | 5795.06 ms | 并发过高后仍排队 |
| local_queue 4 workers | 3000 | 10000 | 10000 | 0 | 818.99 req/s | 3563.32 ms | 5971.29 ms | 6821.02 ms | 高并发入口可承接，延迟偏高 |

调参说明：尝试把 Tomcat 线程、Redis 连接池、每活动并发闸门继续放大后，3000 并发吞吐反而下降到约 662 QPS，说明本机环境已经出现线程调度和连接竞争；最终保留 400 Tomcat 线程、64 Redis 连接池、每活动 1000 并发闸门。

### 一致性核对

最佳稳定点 1000 并发压测后，等待后台 worker 和库存同步任务完成：

```text
seckill_activity:
activity_id=900001,total_count=50000,available_count=45000,lock_count=5000,status=1

seckill_order:
activity_id=900001,count=5000

RabbitMQ:
group_buy_market_queue_2_topic_seckill_order_create messages_ready=0,messages_unacknowledged=0
```

说明：入口 5000/5000 成功，最终订单全部落库，活动冗余库存最终一致。`local_queue` 模式下 RabbitMQ 秒杀队列不会承载本轮秒杀订单，因此队列为空是预期结果。

### 当前结论

这次改造把本机 1000 并发稳定点从约 545 QPS 提升到约 944 QPS，P99 从约 4.77 秒降到约 1.98 秒。提升来自两个点：

- 请求线程不再做 RabbitMQ 客户端发送。
- 后台落库 worker 数收敛到 4，避免和入口线程争抢 CPU、DB 连接和锁资源。

这版适合面试表达为“入口资格预占 + 有界缓冲削峰 + 异步落库 + 最终一致”。但要注意：`local_queue` 是极限吞吐模式，不是生产最终可靠方案。生产要把 `local_queue` 替换成 Redis Stream、RocketMQ/Kafka、可靠 outbox 或支持 pending-list 的持久化队列，再做消费幂等、重试和补偿。

## 2026-05-25 QPS 四次改造：Redis Stream Pending-List

本轮把上一版 `local_queue` 极限吞吐模式升级为 Redis Stream 消费组模式。目标不是继续追求单机最高 QPS，而是补齐生产可靠性：消息持久化、pending-list、批量落库、消费幂等和 ACK 后删除。

### 已落地改造

- `SeckillOrderCreateBuffer` 新增 `redis_stream` 模式：
  - 入口抢到 Redis 资格后执行 `XADD seckill:order:create:stream`。
  - 启动时创建 consumer group：`seckill-order-create-group`。
  - 每条 Stream 消息只保存订单 JSON body，入口线程不再触达 MySQL。
- 新增秒杀人工补偿运维端口：
  - domain 层新增 `ISeckillManualCompensationPort` 和 `SeckillManualMessageEntity`，trigger 只依赖端口，不直接依赖 infrastructure。
  - infrastructure 的 `SeckillOrderCreateBuffer` 实现人工补偿 Stream 查询、按消息 ID 重放和按前 N 条重放。
  - 前端新增 `seckill-ops.html`，支持运维口令、操作人、补偿消息查询和重放。
- `SeckillOrderCreateBufferWorker` 改为批量消费：
  - `XREADGROUP GROUP ... COUNT 100 BLOCK 1000` 拉取新消息。
  - 通过 `XAUTOCLAIM` 回收空闲超过 `pending-idle-millis` 的 pending 消息。
  - 业务落库成功后执行 `XACK`，再 `XDEL` 删除已处理消息，避免 Stream 无限增长。
- `SeckillRepository#createSeckillOrders` 新增批量落库：
  - 使用 `insert ignore into seckill_order (...) values (...)` 批量写入。
  - `seckill_order` 已有唯一索引：`uq_order_id`、`uq_user_out_trade_no`、`uq_user_activity`。
  - Redis Stream 重投、worker 重启、ACK 失败后的重复消费，都会被唯一索引和 `insert ignore` 幂等吸收。
- 查询结果仍写 Redis result key：
  - 成功落库后把订单结果写为 `SUCCESS`。
  - 入口返回后，前端/商城可继续轮询结果。
- 配置切换：
  - `dev/prod` 当前均使用 `redis_stream`。
  - `local_queue` 保留为极限压测模式。
  - `mq` 保留为原 RabbitMQ 模式。

### 最新压测结果

测试活动 `900001`，库存 50000，入口锁单接口，`QUERY_RESULT=false`。

| 版本 | 并发 | 请求数 | 业务成功 | 业务失败 | 吞吐 | 平均耗时 | P95 | P99 | 结论 |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | --- |
| local_queue 4 workers | 1000 | 5000 | 5000 | 0 | 944.08 req/s | 1042.37 ms | 1771.34 ms | 1979.59 ms | 极限吞吐最高，但不持久 |
| redis_stream 8 workers batch=100 | 1000 | 5000 | 5000 | 0 | 644.64 req/s | 1536.21 ms | 3477.51 ms | 4017.59 ms | 可靠模式稳定点 |
| redis_stream 8 workers batch=100 | 2000 | 5000 | 5000 | 0 | 671.20 req/s | 2905.82 ms | 5595.89 ms | 5874.40 ms | 全成功但排队明显 |

### 一致性和 Pending-List 核对

2000 并发压测后，等待 worker 和库存同步任务追平：

```text
seckill_activity:
activity_id=900001,total_count=50000,available_count=45000,lock_count=5000,status=1

seckill_order:
activity_id=900001,count=5000

Redis Stream:
XLEN seckill:order:create:stream = 0
group=seckill-order-create-group, consumers=8, pending=0, entries-read=15000, lag=0
```

说明：

- 入口请求全部成功。
- 订单最终全部批量落库。
- 消息处理成功后已 `XACK + XDEL`，Stream 无积压。
- pending-list 为 0，说明没有未确认消息残留。
- 如果 worker 在落库前宕机，消息会停留在 pending-list，超过空闲阈值后由其他 worker 通过 `XAUTOCLAIM` 接管。

### 当前取舍

`redis_stream` 模式 QPS 低于 `local_queue`，但可靠性明显更接近生产：进程宕机不会直接丢消息，消费失败有 pending-list，可通过唯一索引 + `insert ignore` 做幂等。面试里可以这样表达：

```text
入口链路：限流 -> 活动缓存 -> Redis Lua 原子扣库存/防重 -> hash 分片 XADD Redis Stream -> 快速返回 PROCESSING
消费链路：XAUTOCLAIM pending -> XREADGROUP 分片批量拉取 -> insert ignore 批量落库/分片表 -> 写结果缓存 -> XACK + XDEL
补偿链路：失败计数 -> 超阈值写人工补偿 Stream -> 运维台按消息 ID 幂等重放 -> 定时任务同步活动库存
审计链路：seckill_stock_flow 记录 RESERVE/ROLLBACK，支持异常恢复和对账追踪
```

本次成熟化补齐：

- Redis Stream 分片：`stream-shard-count` 默认开发环境 4、生产环境 16，入口按活动、用户和外部单号 hash 写入不同 Stream。
- Pending 失败隔离：pending 消息超过 `pending-idle-millis` 后回收，失败达到 `pending-max-retry` 后写入 `seckill:order:create:manual`。
- 批量落库分片：`order-shard-count` 开启后按用户和外部单号 hash 写入 `seckill_order_00` 到 `seckill_order_15`。
- 库存流水审计：新增 `seckill_stock_flow`，记录库存占用和回滚流水，异常恢复时可按活动、用户、外部单号追踪。
- 监控告警：新增 Stream lag、pending、DLQ、消费批次耗时、批量写入耗时指标和 Prometheus 告警规则。
- 故障注入：新增 Redis 抖动、DB 批量落库失败、Stream ACK 失败开关，验证 pending-list 和幂等重放。
- 人工补偿闭环：新增秒杀补偿台和 `SeckillOpsController`，后端已在 8091 验证 `manual_messages` 接口，前端已在 8088 验证页面无控制台错误。
- 领域层纯净化：营销 domain 包已移除 Spring 注解和 `@Resource`，交易规则链、退单策略、首页试算节点、折扣策略改由 app 层配置类装配；新增 `scripts/check-domain-purity.ps1` 架构守护，本轮已验证首页试算接口仍返回活动、价格和拼团统计。

仍需单独补的验证材料：

- 生产压测环境：当前 Windows + Docker Desktop 压测结果只能说明本机趋势，不能代表生产容量。
- 故障演练报告：已新增 `scripts/chaos/run-chaos-report.ps1`，可在本机自动采集 Docker 状态、健康检查和 Prometheus 指标生成 Markdown 报告；生产上还需要补 Grafana 截图、日志摘要、恢复耗时曲线和正式演练审批记录。

## 2026-05-30 压测矩阵与自动不变量校验

本轮补齐两个脚本：

- `scripts/pressure/run-local-pressure-matrix.ps1`：本机压测矩阵，支持秒杀查询、秒杀锁单 100/500/1000 并发阶梯、拼团锁单。
- `scripts/pressure/check-invariants.ps1`：压测后自动校验秒杀库存、Redis 库存桶、订单数、重复订单和拼团队伍统计。

秒杀结果目录：`docs/capacity-results/20260530-022638`

| 场景 | 请求数 | 并发 | 业务成功 | 吞吐 | P95 | 自动校验 |
| --- | ---: | ---: | ---: | ---: | ---: | --- |
| 秒杀查询 | 1000 | 100 | 1000 | 140.09 req/s | 1085.97 ms | - |
| 秒杀锁单 | 1000 | 100 | 1000 | 254.57 req/s | 618.37 ms | 通过 |
| 秒杀锁单 | 1000 | 500 | 1000 | 261.61 req/s | 3051.46 ms | 通过 |
| 秒杀锁单 | 1000 | 1000 | 1000 | 285.31 req/s | 3371.29 ms | 通过 |

最终状态：`total_count=20000`，活跃订单 `3000`，DB 可用库存 `17000`，DB 锁定量 `3000`，Redis 库存桶合计 `17000`，重复用户活动订单 `0`，重复用户外部单号 `0`。

拼团结果目录：

- `docs/capacity-results/20260530-023006-groupbuy-query`
- `docs/capacity-results/20260530-022909-groupbuy`

| 场景 | 请求数 | 并发 | 业务成功 | 吞吐 | P95 | 自动校验 |
| --- | ---: | ---: | ---: | ---: | ---: | --- |
| 拼团试算 | 300 | 100 | 300 | 95.84 req/s | 1508.61 ms | - |
| 拼团锁单 | 200 | 50 | 200 | 80.35 req/s | 997.47 ms | 通过 |
| 拼团锁单 | 200 | 100 | 200 | 79.21 req/s | 1742.47 ms | 通过 |

拼团最终校验：压测队伍数 `400`，订单明细数 `400`，队伍统计不一致数 `0`，重复用户外部单号 `0`。

同时修复秒杀库存同步在分片表模式下的统计口径：开启 `order-shard-count > 1` 后，库存同步任务会聚合所有 `seckill_order_XX` 分片表中的活跃订单，再回写活动库存。
