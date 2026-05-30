# 退款策略单元测试 SDD 记录

日期：2026-05-30

## 规格

本次任务补齐拼团逆向流程的策略测试，重点覆盖退单状态组合和策略路由。目标是避免后续修改退单策略时，把未支付释放、已支付未成团、已支付已成团、重复退款和非法状态退款混成一段不可维护的 if/else。

## 设计

拼团退单保持现有策略模式：

- `TradeRefundOrderService`：逆向流程入口，只执行退单规则链和恢复锁单库存入口。
- `DataNodeFilter`：加载订单、队伍数据。
- `UniqueRefundNodeFilter`：重复退单幂等返回 `REPEAT`。
- `RefundOrderNodeFilter`：根据 `RefundTypeEnumVO` 选择具体策略。
- `Unpaid2RefundStrategy`：未支付未成团，锁单量释放。
- `Paid2RefundStrategy`：已支付未成团，锁单量和完成量回退，并发送退单通知。
- `PaidTeam2RefundStrategy`：已支付已成团，队伍状态进入 `COMPLETE_FAIL` 或 `FAIL`，不再恢复锁单量。

本次小改动：

- 新增业务错误码 `E0108`：不支持的拼团退单状态组合。
- `RefundTypeEnumVO#getRefundStrategy` 从普通 `RuntimeException` 改为抛 `AppException(E0108)`，便于接口层和测试识别业务失败。

## 测试覆盖

新增测试文件：

- `group-buy-market-master/group-buy-market-app/src/test/java/cn/bugstack/test/domain/trade/TradeRefundOrderServiceUnitTest.java`

覆盖用例：

- 未支付未成团：路由到 `Unpaid2RefundStrategy`，聚合锁单量 `-1`。
- 已支付未成团：路由到 `Paid2RefundStrategy`，锁单量和完成量均 `-1`。
- 已支付已成团：路由到 `PaidTeam2RefundStrategy`，非最后一笔退单时队伍状态为 `COMPLETE_FAIL`。
- 已支付已成团最后一笔：队伍状态为 `FAIL`。
- 重复退款：订单已关闭时返回 `REPEAT`，不调用任何退单策略，不发送通知任务。
- 非法状态退款：例如队伍已失败但订单仍为创建态，抛 `E0108`，不调用退单策略。
- 恢复锁单库存：未支付和已支付未成团会恢复 Redis recovery key；已成团退单不恢复锁单量。

## SDD 审核结论

- 测试通过 fake port 和同步 `IDomainTaskExecutor` 驱动，不依赖 Spring、MySQL、Redis 或 RabbitMQ。
- 三个退单策略仍保持独立类，不引入新的编排大类。
- 非法状态从非结构化运行时异常收敛成业务错误码，接口层可以明确返回业务失败。
- 该测试补上了状态机测试之外的策略路由保护：状态机负责“状态是否能转”，策略单测负责“对应状态组合走哪个业务策略”。

## 验证

```powershell
$env:JAVA_HOME='C:\Program Files\Eclipse Adoptium\jdk-8.0.492.9-hotspot'
$env:Path="$env:JAVA_HOME\bin;E:\java\group_buy_market\.tools\apache-maven-3.8.8\bin;$env:Path"
cd E:\java\group_buy_market\group-buy-market-master
& 'E:\java\group_buy_market\.tools\apache-maven-3.8.8\bin\mvn.cmd' -pl group-buy-market-app -am -DskipTests=false -DfailIfNoTests=false "-Dtest=cn.bugstack.test.domain.trade.TradeRefundOrderServiceUnitTest" test
```

结果：7 个用例通过。
