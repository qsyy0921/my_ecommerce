# 拼团锁单等待超时语义测试

## 背景

前一轮风险地图把 `TradeLockOrderService` 的同步阻塞等待列为当前前 5 个残留风险之一。

具体残留点是：

- 拼团锁单请求没拿到 Redis 请求锁时，会进入 `waitLockResult(...)`。
- 该方法固定轮询 5 次，每次 `Thread.sleep(50L)`。
- 如果缓存和 DB 都查不到锁单结果，最终抛 `E0010`。

这条路径此前只被审计记录，没有专门的单元测试固化。

## 本轮改动

本轮不重构生产代码，只补测试。

新增测试：

- `TradeLockOrderServiceUnitTest#duplicateLockRequestWithoutCachedOrPersistedResultShouldTimeoutAsDuplicateSubmit`

覆盖场景：

1. `tryAcquireLockRequest(...)` 返回 `false`，模拟重复请求没有拿到请求锁。
2. `queryLockResult(...)` 始终返回 `null`，模拟缓存里没有前序锁单结果。
3. `queryMarketPayOrderEntityByOutTradeNo(...)` 始终返回 `null`，模拟 DB 也没有前序锁单结果。
4. 等待 5 次后抛出 `ResponseCode.E0010`。
5. 不创建订单、不占用队伍库存、不释放未持有的请求锁。

## 验收

本轮执行：

```powershell
E:\java\group_buy_market\.tools\apache-maven-3.8.8\bin\mvn.cmd -q -pl group-buy-market-app -am "-Dtest=cn.bugstack.test.domain.trade.TradeLockOrderServiceUnitTest" test
```

结果：通过。

## 结论

本轮解决的是测试缺口，不是等待策略本身。

当前状态：

- 等待超时语义已经被单测固化。
- `Thread.sleep(50L)` 仍然存在于 `TradeLockOrderService`。
- 后续如果继续治理拼团主链路，应该在这个测试保护下评估是否把等待策略移到应用层或独立策略对象。
