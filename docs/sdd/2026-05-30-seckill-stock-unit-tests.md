# 秒杀库存纯单元测试 SDD 记录

日期：2026-05-30

## 规格

本次任务围绕秒杀库存一致性补单元测试，并做一个小的技术策略拆分。目标不是重新写秒杀主链路，而是把高并发秒杀中最容易出错的库存预扣、重复参与、售罄短路、异步入队失败回滚、pending 失败隔离和库存释放幂等用测试固定下来。

## 设计

秒杀锁单链路保持现有 DDD 边界：

- `SeckillService`：领域入口，负责活动有效性、活动维度并发闸门和订单实体构建。
- `ISeckillOrderLockPort` / `SeckillOrderLockPort`：基础设施适配，负责 Redis 资格预扣、结果缓存、消息入队、售罄短路和失败回滚。
- `ISeckillStockReservationPort`：Redis 库存桶、用户占位和库存释放端口。
- `ISeckillOrderMessagePort`：秒杀下单消息投递端口，隔离 RabbitMQ、Redis Stream、Redis Queue 和本地队列。
- `ISeckillStockFlowPort`：库存流水审计端口，通过稳定 `flowNo` 和 `insert ignore` 支持幂等落库。

本次额外拆出：

- `SeckillPendingRetryPolicy`：把 Redis Stream pending 消息失败次数是否进入人工补偿 Stream 的判断从 `SeckillOrderCreateBuffer` 中移出，避免 pending 重试阈值散落在缓冲队列实现里。

## 测试覆盖

新增测试文件：

- `group-buy-market-master/group-buy-market-app/src/test/java/cn/bugstack/test/infrastructure/seckill/SeckillOrderLockPortUnitTest.java`

覆盖用例：

- 预扣成功：调用库存预扣后投递下单消息，不触发回滚。
- 重复参与：库存端口返回重复占位时抛 `E0204`，不投递消息。
- 库存不足：库存端口返回库存不足且 DB 可用库存为 0 时抛 `E0203`，标记本地售罄短缓存。
- 售罄短路：本地售罄缓存命中时，不再访问库存预扣端口，直接快速失败。
- 预扣成功但异步入队失败：投递失败时回滚 Redis 资格，清理结果缓存，记录 `ROLLBACK` 库存流水。
- pending 重试策略：失败次数达到配置阈值才进入人工补偿隔离，配置异常时最小阈值为 1。
- 库存释放幂等：同一订单同一释放类型生成稳定 `flowNo`，可依赖 `insert ignore` 避免重复流水；不同释放类型生成不同 `flowNo`。

## SDD 审核结论

- 测试不启动 Spring，不访问 Redis/MySQL/RabbitMQ。
- `SeckillOrderLockPort` 通过 fake port 注入，验证的是库存适配器的业务语义和失败补偿，而不是具体中间件。
- pending retry 判断被拆成独立策略，后续从 Redis Stream 演进到 RocketMQ/Kafka 时可以复用“失败次数进入人工补偿”的策略语义。
- 真正的 Redis Stream `XAUTOCLAIM`、ACK、DLQ 写入仍需要集成测试或故障演练脚本验证，纯单元测试负责覆盖 retry policy 和库存回滚不变量。

## 验证

```powershell
$env:JAVA_HOME='C:\Program Files\Eclipse Adoptium\jdk-8.0.492.9-hotspot'
$env:Path="$env:JAVA_HOME\bin;E:\java\qsyy-ecommerce-platform\.tools\apache-maven-3.8.8\bin;$env:Path"
cd E:\java\qsyy-ecommerce-platform\group-buy-market-master
& 'E:\java\qsyy-ecommerce-platform\.tools\apache-maven-3.8.8\bin\mvn.cmd' -pl group-buy-market-app -am -DskipTests=false -DfailIfNoTests=false "-Dtest=cn.bugstack.test.infrastructure.seckill.SeckillOrderLockPortUnitTest" test
```

结果：7 个用例通过。
