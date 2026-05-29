# 本地 OpenTelemetry + Jaeger 链路追踪

## 背景

项目已经有 `trace-id` 过滤器、HTTP/MQ 透传、结构化业务日志和 Prometheus 指标，但缺少可以在本机直接查看的跨服务 Trace。本轮用 OpenTelemetry Java agent 做零侵入接入，用 Jaeger all-in-one 做本地 Trace 查询，不改 domain 和 infrastructure 业务代码。

## 设计

- `docs/dev-ops/docker-compose-tracing.yml`：启动 Jaeger all-in-one，打开 OTLP gRPC `4317`、OTLP HTTP `4318` 和 UI `16686`。
- `docs/observability/otel-javaagent.properties`：统一配置 Java agent，只导出 traces，关闭 metrics/logs，避免与已有 Micrometer/日志体系重复。
- `scripts/observability/download-otel-javaagent.ps1`：下载 OpenTelemetry Java agent 到 `.tools/otel/opentelemetry-javaagent.jar`。
- `scripts/observability/start-local-tracing.ps1`：启动并等待 Jaeger 就绪。
- `scripts/observability/start-services-with-otel.ps1`：用 JDK 1.8、`-javaagent` 和各自 `otel.service.name` 启动营销服务和商城服务。
- `scripts/observability/query-jaeger-services.ps1`：查询 Jaeger 当前已接收的服务列表。

## 启动命令

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\observability\start-local-tracing.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\observability\start-services-with-otel.ps1 -SkipBuild
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\observability\query-jaeger-services.ps1
```

Jaeger UI：

```text
http://127.0.0.1:16686
```

## 验证结果

本机验证结果：

- OpenTelemetry Java agent 下载成功：`.tools/otel/opentelemetry-javaagent.jar`。
- Jaeger 启动成功：`http://127.0.0.1:16686`。
- `group-buy-market` 健康检查为 `UP`。
- `s-pay-mall-ddd` 健康检查为 `UP`。
- Jaeger 服务列表包含：
  - `jaeger-all-in-one`
  - `group-buy-market`
  - `s-pay-mall-ddd`
- Jaeger trace 查询：
  - `group-buy-market traces=1`
  - `s-pay-mall-ddd traces=1`

## 面试口径

可以说：

> 本地已经用 OpenTelemetry Java agent 接入 Jaeger，不侵入业务代码。启动服务时通过 `-javaagent` 自动采集 Spring MVC、HTTP Client、JDBC、RabbitMQ 等链路，OTLP HTTP 上报到本机 Jaeger。这样可以把 `trace-id` 日志排查升级为可视化 Trace 排查。生产上还需要继续补采样策略、Trace 存储周期、Trace 与日志/指标跳转、告警联动和脱敏规则。

## 边界

- 当前是本地 Jaeger all-in-one，适合开发和面试演示，不适合直接作为生产 Trace 存储。
- 生产环境应引入 OpenTelemetry Collector、采样策略、后端存储和统一告警。
- 本轮没有删除原有 `trace-id`，因为它仍然适合日志检索、MQ 业务台账和人工排障。

## 参考

- OpenTelemetry Java agent 支持 Java 8+，可通过 `-javaagent` 零代码接入。
- OpenTelemetry Java agent 可用 `OTEL_SERVICE_NAME`、`OTEL_EXPORTER_OTLP_ENDPOINT` 或等价 system properties 配置服务名和 OTLP 地址。
- Jaeger all-in-one 支持 `COLLECTOR_OTLP_ENABLED=true` 并暴露 `4317/4318/16686` 用于 OTLP 和 UI。
