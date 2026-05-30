# 拼团通知发送端口拆分

## 背景

`ITradePort` / `TradePort` 名称过于泛化，但实际只服务拼团通知任务发送。基础设施实现还同时承担 Redis 分布式锁、HTTP 回调、MQ 投递、通知类型判断和无效 URL 判断，后续继续扩展通知渠道时容易重新形成大端口。

## 目标

- 删除泛化 `ITradePort` / `TradePort`。
- 新增语义更明确的 `ITradeNotificationPort` / `TradeNotificationPort`。
- 把 Redis 任务抢占锁拆到 `TradeNotificationLockSupport`。
- 把 HTTP/MQ 渠道分发拆到 `TradeNotificationChannelDispatcher`。
- `TradeTaskService` 只感知“发送通知”这个领域端口，不感知具体通知渠道和锁实现。

## 设计

- domain 端口：`ITradeNotificationPort#notify(NotifyTaskEntity)`。
- infrastructure 适配器：`TradeNotificationPort` 只编排锁获取、渠道分发、锁释放和异常结果。
- infrastructure 支撑组件：
  - `TradeNotificationLockSupport`：封装 Redisson 锁获取和释放。
  - `TradeNotificationChannelDispatcher`：根据 `NotifyTypeEnumVO` 分发 HTTP 或 MQ。

## 验收

- JDK 1.8 下营销服务编译通过。
- domain purity 脚本通过。
- `DomainPurityTest` 增加架构守护：
  - `ITradePort` / `TradePort` 不允许回流。
  - `TradeNotificationPort` 不直接依赖 Redis、HTTP 网关、MQ Publisher 和通知类型判断。
  - `TradeTaskService` 不再依赖泛化 `ITradePort`。
- 更新 TODO、SDD 目录和八股文档。
