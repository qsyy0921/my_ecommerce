# 对账查询接口 API DTO 边界治理

日期：2026-05-30

## 背景

前序已经把商城对账的领域服务、仓储、仓储内部映射、后台认证和审计支撑拆开。但 `ReconcileCaseController` 的查询接口仍然直接返回：

- `Response<List<ReconcileCaseEntity>>`
- `Response<List<ReconcileOperationLogEntity>>`

这会把 domain entity 变成 HTTP API 契约。短期看少写 DTO，长期会带来两个问题：

- 领域对象字段一旦调整，会直接影响前端和外部调用方。
- Controller 与 domain model 绑定过深，API 层没有自己的响应模型。

## 规格

- 对账差错单列表接口返回 API DTO，不直接暴露 `ReconcileCaseEntity`。
- 对账操作日志接口返回 API DTO，不直接暴露 `ReconcileOperationLogEntity`。
- DTO 字段名保持与当前 JSON 输出一致，避免前端页面改动。
- Entity 到 DTO 的转换放在 trigger support 组装器中。
- 不改变接口路径、请求参数、返回码和分页语义。

## 设计

新增 API DTO：

- `ReconcileCaseResponseDTO`
- `ReconcileOperationLogResponseDTO`

新增 trigger support：

- `ReconcileResponseAssembler`
- `ReconcileQuerySupport`

`ReconcileQuerySupport` 负责调用 `IOrderReconcileService` 获取领域对象，并通过 `ReconcileResponseAssembler` 转换成 API DTO。`ReconcileCaseController` 只看到 API DTO，不直接感知查询响应所用的 domain entity 类型。

## 验收标准

- `ReconcileCaseController` 不再声明 `Response<List<ReconcileCaseEntity>>`。
- `ReconcileCaseController` 不再声明 `Response<List<ReconcileOperationLogEntity>>`。
- `ReconcileCaseController` 不再直接 import 对账查询响应用的 domain entity。
- `DomainPurityTest` 增加架构守护，防止 domain entity 重新成为对账 HTTP 查询响应契约。
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
