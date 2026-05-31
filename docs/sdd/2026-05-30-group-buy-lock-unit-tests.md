# 拼团锁单领域单元测试 SDD 记录

日期：2026-05-30

## 规格

本次任务只处理拼团锁单领域规则测试，不修改主链路业务代码。目标是把锁单过程中最容易出错的并发和幂等边界用纯单元测试固化，避免后续只能依赖 SpringBoot 集成测试或人工压测发现问题。

## 领域边界

拼团锁单领域服务 `TradeLockOrderService` 只负责锁单聚合行为：

- `ITradeLockRequestPort`：锁单请求短 TTL 幂等锁、锁单结果缓存。
- `IGroupBuyQueryPort`：读取活动、队伍、用户参与次数和已存在订单。
- `IGroupBuyTeamStockPort`：参团队伍名额 Redis 占位、失败恢复、用户占位释放。
- `IGroupBuyOrderPort`：拼团订单聚合落库。

人群标签不在锁单服务里重复判断。它属于首页试算链路 `TagNode` 的职责，试算结果会把 `isVisible/isEnable` 返回给商城或前端。这样可以避免把展示、试算、锁单规则混在一个服务里。

## 本次测试覆盖

新增测试文件：

- `group-buy-market-master/group-buy-market-app/src/test/java/cn/bugstack/test/domain/trade/TradeLockOrderServiceUnitTest.java`

覆盖用例：

- 重复锁单请求：请求锁未获取时，优先返回缓存/DB 幂等结果，不重复落库、不重复占位。
- 活动不可用：活动非生效状态时抛 `E0101`，不会占位 Redis，也不会写订单。
- 用户参与次数达到上限：抛 `E0103`，不会进入队伍占位。
- 队伍满员或不可加入：抛 `E0107`，不会执行 Redis 占位。
- Redis 队伍名额占位失败：抛 `E0008`，释放请求锁。
- DB 唯一索引冲突：模拟 `INDEX_EXCEPTION`，验证已占用队伍名额会恢复，用户队伍占位会释放。
- 新开团：不占用已有队伍名额，只执行订单聚合落库和结果缓存。
- 参团成功：验证队伍库存 key、恢复 key、用户占位 key、目标人数和有效期传参正确。
- 人群标签不匹配：通过 `TagNode` 纯单元测试验证试算阶段返回不可见、不可参与。

## SDD 审核结论

- 没有把 Spring、MyBatis、Redis、DAO/PO 引入测试目标。
- 通过 fake port 驱动领域服务，测试的是领域规则而不是基础设施实现。
- 人群标签边界单独测试试算节点，避免把标签过滤塞回锁单领域服务。
- 该测试可以作为后续继续拆锁单、退单、结算端口时的回归保护。

## 验证

```powershell
$env:JAVA_HOME='C:\Program Files\Eclipse Adoptium\jdk-8.0.492.9-hotspot'
$env:Path="$env:JAVA_HOME\bin;E:\java\qsyy-ecommerce-platform\.tools\apache-maven-3.8.8\bin;$env:Path"
cd E:\java\qsyy-ecommerce-platform\group-buy-market-master
& 'E:\java\qsyy-ecommerce-platform\.tools\apache-maven-3.8.8\bin\mvn.cmd' -pl group-buy-market-app -am -DskipTests=false -DfailIfNoTests=false "-Dtest=cn.bugstack.test.domain.trade.TradeLockOrderServiceUnitTest" test
```

结果：9 个用例通过。
