# RabbitMQ DLQ 指标与人工处理入口

## 背景

RabbitMQ 主业务队列已经配置 DLX，消费者失败后可以 NACK 进入死信队列。但原来的死信监听器只打印错误日志，消息被 ACK 后缺少可查询台账，后续人工处理无法稳定定位消息体和失败原因。

## 目标

- 死信监听器收到消息后写入 `mq_message_record`，状态标记为失败。
- 暴露 DLQ 计数指标 `market_mq_dlq_total`，用于 Prometheus 告警。
- 提供本地运维接口查询失败消息并标记已处理。
- 保留 RabbitMQ DLQ 作为失败隔离区，不让失败消息反复阻塞正常消费队列。

## 接口

- `GET /api/v1/gbm/mq/ops/failed_messages?limit=20`
- `POST /api/v1/gbm/mq/ops/mark_handled`

两个接口都需要请求头 `x-admin-token`。开发环境默认值复用 `app.seckill.admin-token`。

## 边界

- 当前人工入口支持查询和标记已处理，不自动重放业务消息。
- 要做自动重放，需要从 `x-death` 中解析原始 exchange/routing-key，并按不同业务主题补充重放校验规则。
- 死信消息已经落入 `mq_message_record`，可以与对账/补偿中心关联，避免只依赖日志排查。
