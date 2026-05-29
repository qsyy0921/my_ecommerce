# 2026-05-30 压测资源水位联动报告

## 背景

上一轮本机压测已经覆盖秒杀查询、秒杀锁单、拼团试算、拼团锁单，并补了库存不变量和队伍统计不变量校验。但压测报告仍然偏“接口结果”，缺少同一时间窗口内的 CPU、内存、GC、Redis、MySQL、RabbitMQ 和连接池水位，无法判断瓶颈到底落在哪一层。

本次 SDD 目标是补齐本机可落地的资源水位采集脚本，让后续压测报告可以同时回答：

- 应用 JVM 是否出现 CPU、内存、线程数异常。
- Docker 容器是否出现 CPU 或内存水位过高。
- Redis 是否出现连接数、blocked client、ops/sec 异常。
- MySQL 是否出现运行线程、慢查询、行锁等待异常。
- RabbitMQ 是否出现 ready/unacked 堆积。
- Actuator 指标是否暴露 Tomcat 线程和 Hikari 连接池水位。

## 设计

新增两个脚本：

- `scripts/pressure/collect-resource-watermark.ps1`
  - 单独采集资源水位。
  - 输出 `resource-samples.json`、`resource-summary.json`、`resource-watermark-report.md`。
  - 支持 `StopFile`，方便被压测脚本控制生命周期。
- `scripts/pressure/run-local-pressure-with-watermark.ps1`
  - 先启动资源采集器。
  - 再执行指定压测命令。
  - 压测结束后写入 stop file，等待采集器落盘。

采集范围：

- Java 进程：`group-buy-market-app.jar`、`s-pay-mall-ddd-app.jar` 的 CPU、内存、线程数。
- Docker 容器：`gbm-mysql`、`gbm-redis`、`gbm-rabbitmq`、`gbm-jaeger` 的 CPU、内存、网络和块 IO。
- Redis：`used_memory`、`instantaneous_ops_per_sec`、连接数、blocked clients。
- MySQL：`Threads_connected`、`Threads_running`、`Slow_queries`、`Innodb_row_lock_waits`。
- RabbitMQ：队列数量、ready、unacked、total messages。
- Actuator：`process.cpu.usage`、`system.cpu.usage`、`jvm.memory.used`、`tomcat.threads.busy`、`hikaricp.connections.active`。

## 使用方式

单独采集：

```powershell
powershell -ExecutionPolicy Bypass -File scripts\pressure\collect-resource-watermark.ps1 `
  -DurationSeconds 120 `
  -IntervalSeconds 5 `
  -OutputDir docs\capacity-results\20260530-watermark
```

和压测矩阵一起执行：

```powershell
powershell -ExecutionPolicy Bypass -File scripts\pressure\run-local-pressure-with-watermark.ps1 `
  -OutputDir docs\capacity-results\20260530-pressure-watermark `
  -PressureCommand "powershell -ExecutionPolicy Bypass -File scripts\pressure\run-local-pressure-matrix.ps1 -MarketHost http://127.0.0.1:8091"
```

## 验收

本机已完成脚本级验证：

- PowerShell 语法检查通过。
- `collect-resource-watermark.ps1` 可以在 `.run-logs` 下生成三类产物。
- `run-local-pressure-with-watermark.ps1` 可以启动采集器、执行压测命令、结束后自动停止采集器。
- MySQL 容器 stderr warning 已做容忍处理，不会中断采集。
- PowerShell cmdlet 类型压测命令不会因为 `$LASTEXITCODE=$null` 被误判为失败。

## 边界

这不是生产容量证明，只是本机压测的资源水位补充。真实生产压测仍需要：

- 独立 Linux 压测机和服务机器。
- 固定 CPU、内存、磁盘、网络规格。
- 多服务多实例部署。
- Grafana 面板截图和 Prometheus 原始指标归档。
- Redis/MySQL/RabbitMQ 独立节点或集群指标。
