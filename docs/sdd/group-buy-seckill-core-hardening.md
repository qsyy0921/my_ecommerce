# 拼团与秒杀核心链路加固记录

## 目标

当前阶段只收敛拼团和秒杀两个核心功能，不继续扩展支付、对账、告警和后台审批。

## 拼团加固

- 锁单入口补齐 `requestDTO`、`outTradeNo`、`notifyConfigVO`、`notifyType` 校验，避免非法请求进入领域流程。
- 外部单号幂等返回已有订单，不再只对 `CREATE` 状态返回，避免重复请求造成用户侧结果不一致。
- 同一 `userId + outTradeNo` 增加短 TTL 请求幂等锁，重复请求会短轮询锁单结果，避免并发重试重复占用队伍名额。
- 锁单结果增加 Redis 结果缓存，锁单入口先查缓存再回源 DB；结算和退单仍查 DB，避免缓存状态滞后污染状态机判断。
- 参团前校验队伍存在、活动匹配、状态为拼单中、未过期、未满员。
- 队伍名额 Redis Lua 增加用户维度占位 Key，同一用户不能用不同外部单号并发重复占用同一队伍名额。
- 数据库 `updateAddLockCount` 增加 `status = 0` 和 `valid_end_time > now()` 条件，`group_buy_order_list` 增加 `user_id + out_trade_no` 和 `biz_id` 唯一索引作为并发兜底。
- `GroupBuyOrderEnumVO` 增加 `canJoin/isFormed/isFailed`，减少代码里散落的状态数字判断。

## 秒杀加固

- 秒杀结果查询区分 `NOT_FOUND` 和 `PROCESSING`，未抢购的订单不再误显示处理中。
- 锁单入口先查询 DB 订单，再查询 Redis 结果缓存；同一 `outTradeNo` 重试会返回当前 `PROCESSING/SUCCESS` 状态。
- 活动预热任务提前加载即将开始和正在进行的活动配置、商品信息和 Redis 库存桶，降低第一波请求抖动。
- 锁单入口新增活动、用户、IP 三维 Redis 原子限流，限流细节通过 `ISeckillRateLimitPort` 隔离。
- 秒杀入口新增锁单耗时、限流、库存不足、重复抢购业务指标，供 Prometheus 压测和告警使用。
- 批量落库后逐条回查 DB，只有真实存在的订单才写 `SUCCESS` 结果和 `RESERVE` 库存流水。
- 被唯一索引忽略且 DB 不存在的订单会写 `DUPLICATE` 结果，并回滚 Redis 库存资格和用户防重 key。
- 分片表模式下超时未支付释放会逐 shard 扫描 `seckill_order_00` 到 `seckill_order_15`，不再跳过释放任务。

## 压测

拼团脚本：

```powershell
k6 run scripts\k6\group-buy-lock.js
```

压同一个队伍，验证并发参团不会超过队伍目标人数：

```powershell
$env:MODE="same_team"; $env:RATE="100"; $env:DURATION="1m"; k6 run scripts\k6\group-buy-lock.js
```

秒杀脚本：

```powershell
k6 run scripts\k6\seckill-lock.js
```

## 仍保留的边界

- Redis Stream 仍是当前项目规模下的可靠削峰方案，不等价于 Kafka/RocketMQ 级别的生产消息平台。
- 拼团和秒杀压测需要在 Docker Desktop 之外的独立 Linux 环境重新给容量结论。
- 支付、退款、对账和审批后台暂不纳入本阶段。
