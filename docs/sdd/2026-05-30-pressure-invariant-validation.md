# 2026-05-30 压测矩阵与不变量校验

## 背景

之前项目已有秒杀、拼团压测脚本，但压测后主要靠人工查 SQL 判断是否超卖。这个方式不适合作为长期 SDD 验收标准，因为每次改动后很容易漏查 Redis 库存、DB 活动库存、订单数和拼团队伍统计是否一致。

## 本次目标

- 增加本机压测矩阵脚本，覆盖秒杀查询、秒杀锁单、拼团锁单。
- 增加压测后自动不变量校验脚本。
- 秒杀覆盖 100、500、1000 并发阶梯。
- 拼团补齐试算查询和锁单压测。
- 修复秒杀订单分片表下库存同步只统计主表的风险。

## 交付

- `scripts/pressure/run-local-pressure-matrix.ps1`
  - 初始化独立秒杀活动和 Redis 库存。
  - 执行秒杀查询、秒杀锁单阶梯压测和拼团锁单压测。
  - 每轮秒杀锁单后自动调用不变量校验。
  - 输出结果到 `docs/capacity-results/<timestamp>/`。
- `scripts/pressure/check-invariants.ps1`
  - 校验秒杀 `available_count + active_order_count = total_count`。
  - 校验秒杀 `lock_count = active_order_count`。
  - 校验 Redis 库存桶合计等于 DB 可用库存。
  - 校验秒杀用户活动、用户外部单号无重复。
  - 校验拼团队伍 `lock_count/complete_count` 与明细表一致。
- `scripts/pressure/seckill-pressure.js`
  - 新增 `group-buy-query` 场景，用于拼团首页试算压测。

## 代码修复

秒杀库存同步任务在开启 `app.seckill.order-shard-count > 1` 后，不再只统计 `seckill_order` 主表。现在会逐个统计 `seckill_order_00` 到 `seckill_order_15` 等分片表中的活跃订单，再回写 `seckill_activity.lock_count/available_count`。

## 本机验证

### 秒杀 100/500/1000 并发阶梯

结果目录：`docs/capacity-results/20260530-022638`

| 场景 | 请求数 | 并发 | 成功数 | 吞吐 | p95 | 不变量 |
| --- | ---: | ---: | ---: | ---: | ---: | --- |
| 秒杀查询 | 1000 | 100 | 1000 | 140.09 req/s | 1085.97ms | - |
| 秒杀锁单 | 1000 | 100 | 1000 | 254.57 req/s | 618.37ms | 通过 |
| 秒杀锁单 | 1000 | 500 | 1000 | 261.61 req/s | 3051.46ms | 通过 |
| 秒杀锁单 | 1000 | 1000 | 1000 | 285.31 req/s | 3371.29ms | 通过 |

最终校验：

- `total_count = 20000`
- `active_order_count = 3000`
- `available_count = 17000`
- `lock_count = 3000`
- Redis 库存桶合计 `17000`
- 重复用户活动订单 `0`
- 重复用户外部单号 `0`

### 拼团试算与锁单

结果目录：

- `docs/capacity-results/20260530-023006-groupbuy-query`
- `docs/capacity-results/20260530-022909-groupbuy`

| 场景 | 请求数 | 并发 | 成功数 | 吞吐 | p95 | 不变量 |
| --- | ---: | ---: | ---: | ---: | ---: | --- |
| 拼团试算 | 300 | 100 | 300 | 95.84 req/s | 1508.61ms | - |
| 拼团锁单 | 200 | 50 | 200 | 80.35 req/s | 997.47ms | 通过 |
| 拼团锁单 | 200 | 100 | 200 | 79.21 req/s | 1742.47ms | 通过 |

拼团最终校验：

- 压测队伍数 `400`
- 压测订单明细数 `400`
- 队伍统计不一致数 `0`
- 重复用户外部单号 `0`

## 边界

本机 Windows + Docker Desktop 压测只能证明链路闭环、趋势和一致性，不代表生产上限。生产容量仍需要独立 Linux 压测机、固定资源配额、多实例应用、独立 Redis/MySQL/MQ 和完整监控水位曲线。
