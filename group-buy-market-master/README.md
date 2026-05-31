# qsyy-commerce-market

DDD 营销服务，负责拼团首页试算、拼团锁单、拼团结算、退单退款、秒杀资格预扣、秒杀异步下单、库存流水、MQ 通知和补偿任务。

## 维护者

- Maintainer: qsyy

## 边界说明

- Java package 暂保留 `cn.bugstack`，避免一次性重命名破坏 MyBatis、Spring 扫描和历史测试。
- `xfg-wrench-*` 是外部扳手工程依赖名，当前按依赖坐标保留。
