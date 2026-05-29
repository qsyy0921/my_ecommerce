# 接口契约

接口契约按“调用方能否安全重试”来设计。所有写接口必须有业务幂等键，所有失败必须返回稳定错误码。

## 通用约定

- 响应结构：`code + info + data`。
- 入口必须生成或透传 `traceId`。
- 写接口必须记录 `userId`、`outTradeNo`、`activityId`。
- 可重试错误：网络超时、HTTP 5xx、MQ 消费失败。
- 不可重试错误：参数非法、活动不存在、库存不足、重复参与。

## 商城到营销

### 拼团试算

`POST /api/v1/gbm/index_market_trial`

入参：

- `userId`
- `source`
- `channel`
- `goodsId`

出参：

- `activityId`
- `originalPrice`
- `deductionPrice`
- `payPrice`
- `teamList`
- `teamStatistic`

幂等：读接口天然幂等，可缓存。

### 拼团锁单

`POST /api/v1/gbm/lock_market_pay_order`

入参：

- `userId`
- `source`
- `channel`
- `goodsId`
- `activityId`
- `teamId`
- `outTradeNo`

幂等键：`outTradeNo`。同一外部交易单重复请求，应返回同一锁单结果或明确重复错误。

关键错误码：

- `E0101` 活动未生效。
- `E0102` 不在活动时间。
- `E0103` 用户参与次数达到上限。
- `E0006` 队伍已满。
- `E0008` 缓存库存不足。

### 秒杀查询

`POST /api/v1/gbm/seckill/query_seckill_market_config`

入参：

- `userId`
- `activityId`
- `source`
- `channel`
- `goodsId`

出参：

- `activityId`
- `activityName`
- `goodsId`
- `goodsName`
- `originalPrice`
- `seckillPrice`
- `totalCount`
- `availableCount`
- `startTime`
- `endTime`

### 秒杀锁单

`POST /api/v1/gbm/seckill/lock_seckill_order`

入参：

- `userId`
- `activityId`
- `source`
- `channel`
- `goodsId`
- `outTradeNo`

幂等键：

- 请求幂等：`outTradeNo`
- 用户限购：`activityId + userId`
- Redis 占位：`seckill:user:lock:{activityId}:{userId}`

关键错误码：

- `E0201` 活动不存在。
- `E0202` 活动不可用。
- `E0203` 库存不足。
- `E0204` 重复参与。
- `E0205` 库存初始化失败。
- `E0206` 秒杀订单不存在。
- `E0207` 秒杀订单状态不允许当前操作。

### 秒杀支付结算

`POST /api/v1/gbm/seckill/settlement_seckill_order`

入参：

- `userId`
- `outTradeNo`
- `source`
- `channel`
- `outTradeTime`

行为：

- `CREATE -> COMPLETE`。
- 重复结算命中 `COMPLETE` 时按幂等成功返回。
- `CLOSE/REFUND` 等终态不允许结算，返回稳定业务错误。

### 秒杀退单退款

`POST /api/v1/gbm/seckill/refund_seckill_order`

入参：

- `userId`
- `outTradeNo`
- `source`
- `channel`
- `refundReason`

行为：

- `CREATE -> CLOSE`：未支付取消，释放 Redis 用户占位，恢复库存桶，写 `ROLLBACK_CANCEL` 流水。
- `COMPLETE -> REFUND`：已支付退款，释放 Redis 用户占位，恢复库存桶，写 `ROLLBACK_REFUND` 流水。
- `CLOSE/REFUND`：幂等成功，不重复恢复库存。

## 前端到商城

### 创建支付单

`POST /api/v1/alipay/create_pay_order`

入参：

- `userId`
- `productId`
- `marketType`：`1` 拼团，`2` 秒杀。
- `activityId`
- `teamId`
- `payChannel`：`mock` 或 `alipay`。

出参：

- 支付表单 HTML。模拟支付返回本地确认支付页面，支付宝返回真实支付宝表单。

### 模拟支付确认

`GET|POST /api/v1/mock-pay/confirm`

入参：

- `outTradeNo`

行为：

- 幂等更新商城订单为支付成功。
- 调用营销结算。
- 返回支付成功页面。

## MQ 契约

### 成团/成交通知

消息键：

- `eventId`
- `eventType`
- `outTradeNo`
- `teamId`
- `activityId`
- `userId`
- `occurredAt`

要求：

- 生产者使用 confirms，失败进入补偿任务。
- 消费者先写消费幂等表，再执行业务。
- 业务成功后 ACK。
- 重试耗尽后进入 DLQ。

### 退单补偿通知

消息键：

- `eventId`
- `refundNo`
- `outTradeNo`
- `refundScene`
- `activityId`
- `userId`

要求：

- 退单动作必须可重复执行。
- Redis 回补和 DB 状态变更必须有状态判断。
