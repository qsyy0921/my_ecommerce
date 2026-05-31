# 秒杀 HTTP Controller 支撑组件拆分

日期：2026-05-30

## 背景

`SeckillMarketController` 承担秒杀查询、锁单、结果查询、结算和退款五个 HTTP 接口。前序已经把秒杀仓储、库存、消息、订单生命周期和 Stream 缓冲队列拆到独立端口/组件，但 Controller 里仍然混有：

- 请求参数校验。
- 客户端 IP 解析。
- 秒杀活动、锁单、结算、退款响应 DTO 组装。
- 限流、指标和领域服务调用编排。

这些逻辑虽然还在 trigger 层，没有污染 domain，但 Controller 继续膨胀会让后续加字段、换网关、改限流或调整响应模型时牵动整个 HTTP 入口。

## 规格

- HTTP Controller 只保留路由、限流注解、领域服务调用、业务日志和异常兜底。
- 请求合法性判断收敛到支撑组件。
- 客户端 IP 解析收敛到支撑组件，兼容 `X-Forwarded-For`、`X-Real-IP` 和 remote addr。
- Entity 到 API DTO 的字段映射收敛到响应组装器。
- 不改变 `ISeckillMarketService` 接口契约和现有 HTTP 路径。

## 设计

新增三个 trigger support 组件：

- `SeckillRequestValidator`：封装查询配置、锁单、结果查询、结算、退款五类请求校验。
- `ClientIpResolver`：封装代理头和 remote addr 的客户端 IP 解析。
- `SeckillResponseAssembler`：封装 `SeckillActivityEntity` / `SeckillOrderEntity` 到秒杀响应 DTO 的转换。

`SeckillMarketController` 委托这些组件后，不再直接出现 `StringUtils.isBlank` 校验矩阵、DTO builder 字段映射和 HTTP 头解析。

## 验收标准

- `SeckillMarketController` 不直接调用 `StringUtils.isBlank`。
- `SeckillMarketController` 不直接构造 `SeckillMarketResponseDTO`、`LockSeckillOrderResponseDTO`、`SettlementSeckillOrderResponseDTO`、`RefundSeckillOrderResponseDTO`。
- `SeckillMarketController` 不直接读取 `X-Forwarded-For`、`X-Real-IP` 或 `getRemoteAddr`。
- `DomainPurityTest` 增加架构守护，防止这些支撑逻辑回流到 Controller。
- 营销服务 JDK 1.8 编译通过。

## 验证命令

```powershell
cd E:\java\qsyy-ecommerce-platform
git diff --check
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\check-domain-purity.ps1
```

```powershell
cd E:\java\qsyy-ecommerce-platform\qsyy-commerce-market
& 'E:\java\qsyy-ecommerce-platform\.tools\apache-maven-3.8.8\bin\mvn.cmd' -q -DskipTests compile
& 'E:\java\qsyy-ecommerce-platform\.tools\apache-maven-3.8.8\bin\mvn.cmd' -pl group-buy-market-app -am -DskipTests=false -DfailIfNoTests=false "-Dtest=cn.bugstack.test.architecture.DomainPurityTest" test
```
