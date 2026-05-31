# 拼团交易 HTTP Controller 支撑组件拆分

日期：2026-05-30

## 背景

`MarketTradeController` 是拼团交易入口，承载锁单、支付结算和退单三个 HTTP 接口。前序已经把通用 `ITradeRepository` / `TradeRepository` 删除，并将拼团锁单落库、结算、退单、读模型、超时扫描、队伍库存、锁单缓存都拆到独立端口。但 Controller 里仍然混有：

- 请求参数校验。
- 通知类型字符串到 `NotifyTypeEnumVO` 的解析。
- HTTP 通知地址校验。
- `MarketProductEntity`、`PayActivityEntity`、`PayDiscountEntity`、`TradePaySuccessEntity`、`TradeRefundCommandEntity` 构建。
- `LockMarketPayOrderResponseDTO`、`SettlementMarketPayOrderResponseDTO`、`RefundMarketPayOrderResponseDTO` 组装。

这些逻辑属于接口适配层的“入参/出参转换”和“协议字段校验”，不应继续堆在 Controller 主流程里。否则后续加通知渠道、改锁单请求字段、调整返回值时，Controller 会继续膨胀。

## 规格

- Controller 保留 HTTP 路由、流程顺序、领域服务调用、业务日志和异常兜底。
- 请求合法性判断、通知类型解析和通知地址规则收敛到支撑组件。
- API DTO 到领域命令对象的转换收敛到支撑组件。
- 领域实体到 API 响应 DTO 的转换收敛到支撑组件。
- 不改变接口路径、请求字段、返回码和现有业务语义。

## 设计

新增三个 trigger support 组件：

- `GroupBuyTradeRequestValidator`：封装锁单、结算、退单请求校验，以及通知类型解析和 HTTP 通知地址合法性判断。
- `GroupBuyTradeCommandAssembler`：封装 API 请求和试算结果到领域命令/实体的转换。
- `GroupBuyTradeResponseAssembler`：封装拼团锁单、结算、退单响应 DTO 组装。

`MarketTradeController` 改为委托这些组件，不再直接出现 `StringUtils` 校验矩阵、`NotifyTypeEnumVO.valueOf`、领域命令 builder 和响应 DTO builder。

## 验收标准

- `MarketTradeController` 不直接调用 `StringUtils.isBlank` / `StringUtils.isNotBlank`。
- `MarketTradeController` 不直接调用 `NotifyTypeEnumVO.valueOf`。
- `MarketTradeController` 不直接构建拼团领域命令对象和响应 DTO。
- `DomainPurityTest` 增加架构守护，防止这些接口适配细节回流到 Controller。
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
