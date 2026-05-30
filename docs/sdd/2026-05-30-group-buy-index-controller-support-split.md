# SDD: 拼团首页 Controller 支撑逻辑拆分

## 背景

`MarketIndexController` 负责首页拼团营销配置查询，但当前直接承担了：

- 请求参数校验。
- API DTO 到领域查询命令的组装。
- 试算结果、队伍列表、统计数据到 API DTO 的响应组装。

这类逻辑留在 HTTP Controller 里，会让 trigger 层逐步堆积业务流程和字段映射细节。后续首页试算扩展更多展示字段时，Controller 会继续膨胀。

## 目标

拆出支撑组件：

- `GroupBuyMarketConfigRequestValidator`：校验首页查询请求。
- `GroupBuyMarketConfigCommandAssembler`：组装 `MarketProductEntity`。
- `GroupBuyMarketConfigResponseAssembler`：组装 `GoodsMarketResponseDTO`。

`MarketIndexController` 只保留 HTTP 入口、限流注解、调用领域服务和统一响应。

## 设计约束

- Controller 不直接调用 `StringUtils.isBlank`。
- Controller 不直接 builder `MarketProductEntity`。
- Controller 不直接遍历 `UserGroupBuyOrderDetailEntity` 组装 `GoodsMarketResponseDTO.Team`。
- Controller 不直接 builder `GoodsMarketResponseDTO`。
- 架构测试防止这些细节回流。

## 验收

- 营销服务 JDK 1.8 编译通过。
- `DomainPurityTest` 新增拼团首页 Controller 边界守护。
- `MarketIndexController` 只负责入口编排。
