# 商城 AliPayController 支撑组件拆分

日期：2026-05-30

## 背景

`AliPayController` 原来同时承担 HTTP 路由、支付回调验签、支付宝主动查询、模拟支付兼容、支付回调指标、订单列表 DTO 组装和异常兜底。这个类位于 trigger 层，如果继续膨胀，会让支付渠道 SDK、回调参数解析和接口响应组装混在一起，后续增加微信支付、退款回调或订单列表字段时容易变成 Controller 大杂烩。

## 规格

- Controller 只保留接口入参、基础参数校验、领域服务调用和响应包装。
- 支付宝异步回调的参数提取、验签、支付时间解析、支付流水原始报文保存，放到 trigger support 组件。
- 主动查询支付宝交易状态、模拟支付兼容、查询失败响应，放到 trigger support 组件。
- 用户订单列表的 Entity 到 DTO 映射，放到响应组装器。
- 不改变 HTTP 路径、请求参数、返回值和原有业务语义。

## 设计

新增三个 trigger support 组件：

- `AlipayNotifySupport`：处理支付宝异步回调，负责 `trade_status` 判断、参数提取、RSA 验签、支付成功领域服务调用和回调指标记录。
- `ActivePayNotifySupport`：处理主动支付查询，负责模拟支付短路、支付宝查询 SDK 调用、查询响应解析和成功后更新订单支付状态。
- `OrderListResponseAssembler`：处理用户订单列表响应组装，避免 Controller 内出现流式 DTO 映射和字段逐个 set。

`AliPayController` 改为委托这些组件，只保留 HTTP 入口、日志、异常兜底和下单/退单这类直接面向订单服务的轻量编排。

## 验收标准

- `AliPayController` 不直接依赖 `AlipayClient`、`AlipaySignature`、`AlipayTradeQueryModel`、`AlipayTradeQueryRequest`、`JSONObject`、`SimpleDateFormat`、`PaymentCallbackMetrics`。
- `AliPayController` 不直接 `getParameterMap`、不直接 `Collectors.toList`、不直接构造 `QueryOrderListResponseDTO.OrderInfo`。
- `DomainPurityTest` 增加 Controller 边界守护，防止支付渠道技术细节和 DTO 映射回流。
- 商城服务 JDK 1.8 编译通过。

## 验证命令

```powershell
cd E:\java\qsyy-ecommerce-platform
git diff --check
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\check-domain-purity.ps1
```

```powershell
cd E:\java\qsyy-ecommerce-platform\group-buy-market-master
& 'E:\java\qsyy-ecommerce-platform\.tools\apache-maven-3.8.8\bin\mvn.cmd' -pl group-buy-market-app -am -DskipTests=false -DfailIfNoTests=false "-Dtest=cn.bugstack.test.architecture.DomainPurityTest" test
```

```powershell
cd E:\java\qsyy-ecommerce-platform\s-pay-mall-ddd-market-master
& 'E:\java\qsyy-ecommerce-platform\.tools\apache-maven-3.8.8\bin\mvn.cmd' -q -DskipTests compile
```
