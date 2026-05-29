# 单机故障演练脚本

这些脚本只面向本地 Docker Desktop 开发环境，用来形成可重复的故障演练记录。

## Redis 抖动

```powershell
powershell -ExecutionPolicy Bypass -File scripts\chaos\redis-jitter.ps1
```

默认暂停 `gbm-redis` 5 秒，适合验证秒杀 Redis 抖动下接口失败率、恢复时间和告警。

## RabbitMQ 不可用

```powershell
powershell -ExecutionPolicy Bypass -File scripts\chaos\rabbitmq-outage.ps1
```

默认暂停 `gbm-rabbitmq` 10 秒，适合验证 MQ 发送失败、通知任务积压和恢复补偿。

## MySQL 短暂停顿

```powershell
powershell -ExecutionPolicy Bypass -File scripts\chaos\mysql-pause.ps1
```

默认暂停 `gbm-mysql` 5 秒，适合验证 DB 抖动下的 HTTP 超时、对账差错单生成和服务恢复。

## DB 死锁

```powershell
powershell -ExecutionPolicy Bypass -File scripts\chaos\mysql-deadlock.ps1
```

在两个事务中反向更新测试行，验证 DB 死锁日志、接口错误率、对账任务和告警。

## HTTP 超时

```powershell
powershell -ExecutionPolicy Bypass -File scripts\chaos\http-timeout.ps1
```

临时暂停营销服务进程，验证商城调用营销服务超时后的订单中间态、对账差错单和恢复补偿。

## 慢消费/队列堆积

```powershell
powershell -ExecutionPolicy Bypass -File scripts\chaos\slow-consumer-backlog.ps1
```

临时停止商城服务，向 RabbitMQ 写入成团通知消息，验证队列堆积告警和服务恢复后的消费能力。

## 自动生成演练报告

```powershell
powershell -ExecutionPolicy Bypass -File scripts\chaos\run-chaos-report.ps1 -Scenario redis-jitter
```

支持 `redis-jitter`、`rabbitmq-outage`、`mysql-pause`、`mysql-deadlock`、`http-timeout`、`slow-consumer-backlog`。脚本会采集演练前后的 Docker 状态、健康检查和 Prometheus 指标，并在 `docs/chaos-reports` 生成 Markdown 报告。
