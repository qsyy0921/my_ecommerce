# 对账 Controller 管理员认证与审计支撑拆分

日期：2026-05-30

## 背景

`ReconcileCaseController` 是商城对账中心的后台入口，覆盖扫描、列表、确认、忽略、关闭、备注、批量处理、重放、批量重放、操作日志和三方账单导入。前序已经把对账领域服务、对账仓储和仓储内部 CSV/MQ/映射细节拆开，但 Controller 里仍然直接维护：

- 管理员 token 配置和校验。
- 操作人 header/request 兜底解析。
- 对账操作审计写入。
- 导入账单时的原始 CSV 预览截断。

这些是后台接口的横切支撑能力，不是单个 HTTP 方法的业务流程。继续留在 Controller 中，会让每个操作入口重复混入认证、审计和字符串处理细节。

## 规格

- Controller 保留 HTTP 路由、请求参数绑定、领域服务调用和异常兜底。
- 管理员 token 校验收敛到 trigger support。
- 操作人解析和默认操作人收敛到 trigger support。
- 对账操作审计收敛到 trigger support。
- CSV 预览截断收敛到 trigger support。
- 不改变接口路径、请求头、请求体、返回码和现有业务语义。

## 设计

新增 `ReconcileAdminSupport`：

- 读取 `reconcile.admin-token` 配置。
- 提供 `authorized(token)`、`noLogin()`。
- 提供 `resolveOperator(headerOperator, requestOperator)`。
- 提供 `audit(operator, operationType, bizId, requestBody, result)`。
- 提供 `preview(text, maxLength)`。

`ReconcileCaseController` 只调用该支撑组件，不再直接持有 `adminToken`、`@Value`、`recordReconcileOperation` 和 operator 默认值。

## 验收标准

- `ReconcileCaseController` 不再直接出现 `@Value`、`adminToken`、`authorized`、`noLogin`、`resolveOperator`、`audit`、`recordReconcileOperation`。
- `ReconcileCaseController` 不再直接做 CSV `substring(0, Math.min(...))` 预览截断。
- `DomainPurityTest` 增加架构守护，防止认证、操作人解析和审计细节回流到 Controller。
- 商城服务 JDK 1.8 编译通过。

## 验证命令

```powershell
cd E:\java\qsyy-ecommerce-platform
git diff --check
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\check-domain-purity.ps1
```

```powershell
cd E:\java\qsyy-ecommerce-platform\s-pay-mall-ddd-market-master
& 'E:\java\qsyy-ecommerce-platform\.tools\apache-maven-3.8.8\bin\mvn.cmd' -q -DskipTests compile
```

```powershell
cd E:\java\qsyy-ecommerce-platform\group-buy-market-master
& 'E:\java\qsyy-ecommerce-platform\.tools\apache-maven-3.8.8\bin\mvn.cmd' -pl group-buy-market-app -am -DskipTests=false -DfailIfNoTests=false "-Dtest=cn.bugstack.test.architecture.DomainPurityTest" test
```
