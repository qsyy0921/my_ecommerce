# 拼团与秒杀订单状态机

## 拼团队伍状态

```mermaid
stateDiagram-v2
    [*] --> PROGRESS: 开团
    PROGRESS --> COMPLETE: complete_count == target_count
    PROGRESS --> FAIL: 超时/未成团退单
    COMPLETE --> COMPLETE_FAIL: 已成团后部分退单
    COMPLETE --> FAIL: 已成团后全部退单
    COMPLETE_FAIL --> FAIL: 剩余完成用户全部退单
```

状态语义：

- `PROGRESS(0)`：拼单中，可继续参团。
- `COMPLETE(1)`：已成团，不允许继续参团。
- `FAIL(2)`：拼团失败，不允许继续参团。
- `COMPLETE_FAIL(3)`：已成团但发生退单，不允许继续参团。

代码约束：

- 是否允许参团只通过 `GroupBuyOrderEnumVO#canJoin` 判断。
- 新增 `OrderStateMachine` 统一校验状态迁移，非法迁移直接失败。
- 新增 `order_state_flow` 记录 `biz_type/biz_id/from_status/to_status/event/trace_id/source_message_id`。
- 参团 DB 更新必须带 `status = 0`、`lock_count < target_count`、`valid_end_time > now()`。
- 退单通过策略区分未支付、已支付未成团、已支付已成团三类行为。

## 拼团明细状态

```mermaid
stateDiagram-v2
    [*] --> CREATE: 锁单
    CREATE --> COMPLETE: 支付结算成功
    CREATE --> CLOSE: 未支付退单/超时退单
    COMPLETE --> CLOSE: 已支付退单
```

状态语义：

- `CREATE(0)`：锁单成功，等待支付。
- `COMPLETE(1)`：支付完成，参与成团统计。
- `CLOSE(2)`：退单关闭。

## 秒杀订单状态

```mermaid
stateDiagram-v2
    [*] --> PROCESSING: Redis Lua 抢到资格
    PROCESSING --> CREATE: 异步落库成功
    PROCESSING --> DUPLICATE: 落库被唯一索引拦截且 DB 不存在
    PROCESSING --> FAIL: 消费失败进入补偿
    CREATE --> COMPLETE: 支付成功
    CREATE --> CLOSE: 超时未支付释放库存
    COMPLETE --> REFUND: 已支付退款
```

状态语义：

- `PROCESSING` 是结果缓存状态，表示抢到资格但订单还在异步创建。
- `CREATE(0)` 是 DB 订单状态，表示订单已创建但未支付。
- `COMPLETE(1)` 预留给支付成功后的秒杀订单状态。
- `CLOSE(2)` 表示超时未支付或取消关闭，释放库存。
- `REFUND(3)` 预留给已支付退款后释放库存。
- `DUPLICATE/FAIL/NOT_FOUND` 是接口结果状态，不直接作为 DB 主状态。

## 库存流水

- 秒杀使用 `seckill_stock_flow` 记录 `RESERVE/ROLLBACK/ROLLBACK_TIMEOUT`。
- 拼团使用 `group_buy_stock_flow` 记录 `RESERVE/ROLLBACK_UNPAID/ROLLBACK_PAID_UNFORMED/ROLLBACK_PAID_FORMED`。
- 所有流水使用唯一 `flow_no` 做幂等，异常恢复时按 `activity_id/team_id/order_id/out_trade_no` 追踪。
- 流水新增 `stock_before/stock_after/biz_event/trace_id/source_message_id`，支持从库存变化追溯到请求链路和消息来源。
