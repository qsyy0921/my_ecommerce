# qsyy-ecommerce-platform

本仓库是 qsyy 维护的 DDD 交易营销练习项目，包含拼团、秒杀、支付商城、对账补偿、MQ 可靠性和可观测性能力。代码仍按两个独立 Spring Boot 服务组织：

- `qsyy-commerce-market`：拼团营销服务，负责首页试算、拼团锁单、支付结算、退单退款、成团/退单通知。
- `qsyy-commerce-mall`：支付商城服务，负责商城下单、支付单创建、支付宝回调、订单列表、退单入口。

两个服务仍然保持独立 Spring Boot 应用。这样做符合项目原本的微服务和 DDD 边界：支付商城关注订单和支付，拼团营销关注活动、优惠、队伍和拼团状态。

## 目录结构

```text
qsyy-ecommerce-platform/
├── pom.xml                         # 根级聚合工程
├── docs/                           # 面试八股文和项目文档
├── qsyy-commerce-market/           # 拼团营销服务
└── qsyy-commerce-mall/             # 支付商城服务
```

## 环境要求

- JDK 1.8
- Maven 3.8.x
- MySQL
- Redis
- RabbitMQ

当前本地开发配置使用：

- MySQL：`127.0.0.1:23306`
- Redis：`127.0.0.1:26379`
- RabbitMQ：`127.0.0.1:5672`
- 拼团营销服务端口：`8091`
- 支付商城服务端口：`8070`

## 统一构建

在根目录执行：

```bash
mvn -DskipTests package
```

这个命令会同时构建拼团营销服务和支付商城服务。

## 单独构建

只构建拼团营销服务：

```bash
mvn -f qsyy-commerce-market/pom.xml -DskipTests package
```

只构建支付商城服务：

```bash
mvn -f qsyy-commerce-mall/pom.xml -DskipTests package
```

## 启动服务

拼团营销服务：

```bash
java -jar qsyy-commerce-market/group-buy-market-app/target/group-buy-market-app.jar
```

支付商城服务：

```bash
java -jar qsyy-commerce-mall/s-pay-mall-ddd-app/target/s-pay-mall-ddd-app.jar
```

## 主要链路

1. 首页试算：前端/商城调用拼团营销服务查询活动、优惠、队伍列表和统计。
2. 拼团锁单：商城创建支付单前调用营销服务锁定拼团优惠。
3. 支付结算：支付宝回调商城，商城更新支付状态后调用营销服务做拼团结算。
4. 退单退款：商城发起退单，营销服务按订单状态和队伍状态执行逆向流程。

详细面试说明维护在：

```text
docs/interview-baguwen.md
```

生产级架构改造说明维护在：

```text
docs/full-score-architecture.md
```

## SDD Spec

后续按 Spec-Driven Development 维护，规格文档在：

```text
docs/sdd/README.md
docs/sdd/requirements.md
docs/sdd/architecture.md
docs/sdd/contracts.md
docs/sdd/data-model.md
docs/sdd/tasks.md
```

当前开发现态入口：

```text
docs/sdd/2026-05-31-current-top-risk-map-and-open-items.md
docs/sdd/2026-05-31-current-verification-baseline.md
docs/sdd/2026-05-31-seckill-professional-mq-switch-boundary.md
docs/sdd/2026-05-31-seckill-order-message-envelope-contract.md
docs/sdd/2026-05-31-seckill-order-outbox-code-closure.md
docs/sdd/2026-05-31-seckill-professional-mq-adapter-contract.md
docs/sdd/2026-05-31-seckill-rocketmq-adapter-profile-decision.md
docs/sdd/2026-05-31-seckill-outbox-ops-observability.md
docs/sdd/2026-05-31-seckill-service-local-decision-boundary.md
```

后续每轮改动必须同步维护 Done List、TODO List 和所有未完成任务清单，并按 `scripts/verify-current-baseline.ps1` 选择对应验证 profile。

当前前 5 风险清单已在 Outbox、专业 MQ 契约和秒杀订单号生成端口治理后刷新：本机暂不接入具体 RocketMQ/Kafka/Pulsar adapter；秒杀 Outbox 已补查询台账、指标和告警；`SeckillService` 不再直接生成随机订单号；真实容量证明仍需要独立环境。

配套交付物：

```text
docs/sql/2026-05-24-full-score-upgrade.sql
docs/observability-alert-rules.yml
docs/seckill-pressure-test-report.md
scripts/k6/seckill-lock.js
```
