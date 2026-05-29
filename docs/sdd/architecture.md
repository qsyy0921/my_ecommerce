# 架构规格

## 总体架构

```mermaid
flowchart LR
    U["用户浏览器"] --> FE["商城前端"]
    FE --> MALL["支付商城服务"]
    MALL --> PAY["支付宝 / 模拟支付"]
    MALL --> MARKET["拼团营销服务"]
    MARKET --> REDIS["Redis"]
    MARKET --> MYSQL["MySQL"]
    MARKET --> MQ["RabbitMQ"]
    MQ --> MALL
    MARKET --> PROM["Prometheus / Actuator"]
```

系统采用两个业务服务：

- 支付商城服务：商品页、支付单、支付渠道、支付回调、订单列表。
- 拼团营销服务：拼团活动、秒杀活动、营销锁单、结算、退单、通知。

DDD 拆分原则：

- 全系统统一 DDD 方法论，但不共享一套大一统 domain 模型。
- 每个服务内部维护自己的 `api/app/trigger/domain/infrastructure/types` 分层。
- 拼团和秒杀当前同属营销交易上下文，先作为营销服务内部两个子域。
- 当秒杀的流量规模、发布节奏、资源隔离和团队归属独立后，再拆出独立秒杀服务。

## DDD 分层

```mermaid
flowchart TB
    Trigger["trigger: HTTP / MQ / Job"] --> App["app: 启动与配置"]
    Trigger --> Domain["domain: 聚合、实体、领域服务、规则"]
    Domain --> InfraPort["domain repository port"]
    InfraPort --> Infra["infrastructure: MyBatis / Redis / MQ / RPC"]
    Types["types: 错误码、通用响应、异常"] --> Trigger
    Types --> Domain
    Types --> Infra
```

分层约束：

- `trigger` 只做协议转换、参数校验和调用编排。
- `domain` 表达业务规则，包含试算模板、责任链、策略、聚合行为。
- `infrastructure` 只适配 DB、Redis、MQ、外部接口，不写业务决策。
- `types` 放通用异常、响应码和值对象。

## 领域边界

### 拼团域

负责试算、活动可见性、人群过滤、队伍统计、锁单、结算、退单。核心聚合是拼团队伍和拼团订单。拼团锁单不是简单插入订单，而是对活动、队伍、用户参与关系和库存的一次聚合变更。

### 秒杀域

负责秒杀活动、秒杀价格、秒杀库存、用户限购、秒杀锁单。秒杀链路的热点在库存和重复提交，必须使用 Redis 原子预扣削峰，同时保留 DB 条件更新兜底。

### 支付域

负责支付单创建、支付渠道、支付回调、支付状态流转。支付成功是商城域事件，拼团成团或秒杀成交是营销域事件，两者不能混成一个本地事务。

### 通知与补偿域

负责 MQ 通知、定时扫描、退单补偿、对账任务。它不改变核心领域事实，只负责推动状态最终收敛。

## 核心流程

### 首页试算

```mermaid
sequenceDiagram
    participant FE as 前端/商城
    participant Market as 营销服务
    participant Domain as 拼团领域
    participant DB as MySQL
    participant Redis as Redis
    FE->>Market: query_market_config(user, channel, goods)
    Market->>Domain: 试算
    Domain->>DB: 查询活动/商品/标签/规则
    Domain->>Redis: 查询队伍统计缓存
    Domain-->>Market: 原价/优惠/实付/队伍/统计
    Market-->>FE: 展示结果
```

### 拼团锁单

```mermaid
sequenceDiagram
    participant Mall as 商城
    participant Market as 营销服务
    participant Chain as 责任链
    participant DB as MySQL
    participant Redis as Redis
    Mall->>Market: lock_group_buy_order
    Market->>Redis: 查询结果缓存/获取请求幂等锁
    Market->>Chain: 参数/活动/用户/队伍/库存校验
    Chain->>Redis: Lua 占用队伍名额和用户占位
    Chain->>DB: 条件更新队伍锁单量
    Market->>DB: 插入拼团订单和明细
    Market->>Redis: 写入锁单结果缓存
    Market-->>Mall: 优惠锁定结果
```

### 秒杀锁单

```mermaid
sequenceDiagram
    participant Mall as 商城
    participant Market as 营销服务
    participant Redis as Redis Lua
    participant DB as MySQL
    Mall->>Market: lock_seckill_order
    Market->>Redis: 原子判断用户占位 + 扣库存
    alt Redis 成功
        Market->>DB: 条件更新活动库存
        Market->>DB: 插入秒杀订单
        Market-->>Mall: 秒杀价和订单信息
    else 库存不足或重复
        Market-->>Mall: 失败码
    end
```

### 支付结算

```mermaid
sequenceDiagram
    participant User as 用户
    participant Mall as 商城
    participant Pay as 支付渠道
    participant Market as 营销服务
    participant MQ as RabbitMQ
    User->>Mall: 创建支付单
    Mall->>Pay: 支付宝/模拟支付
    Pay-->>Mall: 支付成功回调
    Mall->>Mall: 幂等更新支付状态
    Mall->>Market: settlement
    Market->>Market: 判断成团/成交
    Market->>MQ: 发送成交通知
    MQ-->>Mall: 消费通知并完成后续交易
```

### 退单退款

```mermaid
flowchart TB
    Start["用户退单或超时任务"] --> State{"订单状态"}
    State -->|未支付| Cancel["释放锁单量/库存"]
    State -->|已支付未成团| RefundWait["退款 + 释放队伍"]
    State -->|已支付已成团| RefundDone["售后退款 + 逆向通知"]
    Cancel --> Audit["写退单流水"]
    RefundWait --> Audit
    RefundDone --> Audit
```

## 中间件职责

- Redis：热点活动缓存、秒杀库存桶、用户占位、分布式锁、活动/用户/IP 限流计数。
- MySQL：活动、订单、队伍、流水和最终一致性约束。
- RabbitMQ：结算通知、成团通知、退单补偿、DLQ。
- Redis Stream：秒杀订单创建削峰、pending-list 接管和人工补偿 Stream。
- Prometheus：指标采集、压测前后对比、容量评估。
- Docker：本地开发环境，保证 MySQL/Redis/RabbitMQ 可重复启动。
