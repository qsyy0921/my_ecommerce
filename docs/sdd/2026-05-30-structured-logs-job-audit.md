# 结构化业务日志与补偿任务执行审计

## 背景

本轮继续处理本机可以完成的生产化缺口：关键交易链路需要机器可检索的结构化日志，补偿类定时任务需要分布式锁、执行记录和跳过记录，避免多实例部署时重复补偿且事后无法追踪。

## 设计

### 结构化业务日志

- 在营销服务和支付商城的 trigger 层分别增加 `StructuredBusinessLogger`。
- 统一输出 JSON message，固定字段包括：
  - `event`：业务事件名。
  - `phase`：阶段或结果，如 `success`、`business_error`、`rate_limited`。
  - `service`：服务名。
  - `traceId`：来自 `trace-id` MDC。
  - `timestamp`：毫秒时间戳。
  - 业务键：`userId`、`activityId`、`goodsId`、`teamId`、`orderId`、`outTradeNo`、`payChannel`、`costMs` 等。
- 结构化日志只放在入口编排层，不侵入领域层，保持 DDD 边界。

### 已接入事件

营销服务：

- `seckill_lock_order`：秒杀锁单成功、幂等命中、限流、业务错误、系统错误。
- `group_buy_lock_order`：拼团锁单成功、幂等命中、参数错误、队伍满员、活动不可见、业务错误、系统错误。
- `group_buy_settlement`：拼团支付结算成功、参数错误、业务错误、系统错误。
- `group_buy_refund`：拼团退单成功、参数错误、业务错误、系统错误。

支付商城：

- `mall_create_pay_order`：商城创建支付单成功、系统错误。
- `mall_pay_notify`：支付宝回调成功、交易状态忽略、验签失败。
- `mall_active_pay_notify`：模拟支付成功、支付宝主动查询成功、交易非成功、查询失败、系统错误。
- `mall_group_buy_notify`：成团通知成功、系统错误。
- `mall_refund_order`：用户退单成功、拒绝、系统错误。

### 补偿任务执行审计

- 在营销库和商城库增加 `job_lock`、`job_execution_record`。
- `JobExecutionRecorder` 先抢 MySQL 锁，再写执行记录。
- 获取不到锁时记录 `status=3 skipped`，证明多实例下任务被正确跳过。
- 成功记录 `status=1`、失败记录 `status=2`，并记录 `success_count`、`fail_count`、`duration_ms`、`result_message`、`error_message`。
- 锁不在任务结束时主动释放，而是保留 `lock_until`，避免同一个调度窗口内多个实例先后重复执行。

已覆盖任务：

- 营销服务：`GroupBuyNotifyJob`、`MqPublishRetryJob`、`SeckillPrewarmJob`、`SeckillStockSyncJob`、`SeckillTimeoutReleaseJob`、`TimeoutRefundJob`、`TradeReconciliationJob`。
- 支付商城：`MqPublishRetryJob`、`NoPayNotifyOrderJob`、`OrderReconciliationJob`、`ReconcileCaseScanJob`、`TimeoutCloseOrderJob`。

## 验证

编译验证：

```powershell
$env:JAVA_HOME='C:\Program Files\Eclipse Adoptium\jdk-8.0.492.9-hotspot'
$env:Path="$env:JAVA_HOME\bin;E:\java\qsyy-ecommerce-platform\.tools\apache-maven-3.8.8\bin;$env:Path"
E:\java\qsyy-ecommerce-platform\.tools\apache-maven-3.8.8\bin\mvn.cmd -q -DskipTests compile
```

验证结果：

- `qsyy-commerce-market` 编译通过。
- `qsyy-commerce-mall` 编译通过。

覆盖性验证：

```powershell
Get-ChildItem qsyy-commerce-market\group-buy-market-trigger\src\main\java\cn\bugstack\trigger\job -Filter *Job.java |
  Where-Object { -not (Select-String -Path $_.FullName -Pattern 'JobExecutionRecorder' -Quiet) }

Get-ChildItem qsyy-commerce-mall\s-pay-mall-ddd-trigger\src\main\java\cn\bugstack\trigger\job -Filter *Job.java |
  Where-Object { -not (Select-String -Path $_.FullName -Pattern 'JobExecutionRecorder' -Quiet) }
```

两个命令均无输出，说明当前补偿/对账类 Job 均已接入执行审计。

## 边界

- 结构化日志已覆盖核心交易入口，但普通 debug/info 文本日志不会一次性全部删除，避免影响排障上下文。
- 当前使用 MySQL 锁满足本机和小规模多实例；如果未来拆到真正分布式调度平台，可以演进为 XXL-Job/Scheduler 统一调度锁和执行台账。
- 本地 OpenTelemetry/Jaeger 已在 `2026-05-30-local-opentelemetry-jaeger.md` 中补齐；生产仍需要 Collector、采样、存储和告警联动。
