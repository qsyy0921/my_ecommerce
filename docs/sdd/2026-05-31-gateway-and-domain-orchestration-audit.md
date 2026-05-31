# 通用网关与领域编排热点审计

## 背景

前几轮已经把大仓储、大 Controller、消息端口、补偿端口和读模型边界基本审过了。继续往下看，当前真正还值得警惕的，不再是“有没有明显越层调用”，而是两类更隐蔽的长期风险：

- 通用基础设施网关过宽，容易重新变成技术能力总线。
- 少量领域服务和应用支撑类仍保留技术决策或营销类型分支，后续需求一多就容易重新长胖。

本轮重点审计：

- `IRedisService`
- `RedissonService`
- `SeckillService`
- `AbstractOrderService`
- `ReconcileCaseOperationSupport`

## 审计结论

### 1. `IRedisService` / `RedissonService` 仍是当前最典型的“通用基础设施总线”风险

这组类的问题已经不是简单的“行数偏大”，而是接口宽度本身在传递一种错误使用习惯：

- `IRedisService` 同时暴露了 bucket、queue、blocking queue、delayed queue、atomic long、set、list、map、sorted set、bitset、lock、fair lock、read write lock、semaphore、countdown latch、bloom filter。
- 接口里还直接混入了业务语义方法，如 `reserveSeckillStock`、`reserveSeckillQualification`、`reserveTeamStock`。
- 默认方法里还带了 `MD5` 路由索引生成逻辑。

这说明它已经不是单纯的“Redis 基础 API 包装”，而是：

- 一半像 Redisson 能力门面；
- 一半像业务 Redis 工具箱；
- 一半又开始承担业务语义原子脚本。

真实风险是：

- 后续新需求最容易顺手往这里继续加 Redis 能力，而不是先设计更窄的业务端口。
- 业务端口虽然已经拆出来了，但底层如果继续依赖这个超宽接口，仍然会形成技术细节回流的通道。
- 这个接口一旦变化，影响面会非常广，很难做稳定治理。

当前结论：继续审计，不立刻重构。

原因：

- 现在各个核心业务端口已经把 Redis 细节收敛住了，`IRedisService` 的问题更像“基础设施惯性风险”，不是主链路硬伤。
- 直接现在重构它，改动面会很大，收益不如继续通过业务端口约束新增使用范围。

后续最合理的方向不是再造一个更大的 Redis 门面，而是：

- 新能力优先做成业务语义端口。
- 逐步减少新代码直接依赖 `IRedisService`。
- 在必要时再按技术语义拆成更小的 gateway，例如 lock、queue、script、kv、bitmap 等。

### 2. `SeckillService` 仍保留三个明显的技术决策残留

`SeckillService` 现在已经比早期干净很多，但还保留三个清晰的残留点：

1. 本地 `ConcurrentHashMap<Long, Semaphore>` 活动级并发闸门。
2. `RandomStringUtils.randomNumeric(12)` 本地订单号生成。
3. 锁单前自己做活动查询、时间判断、订单实体构建，再调用 `ISeckillOrderLockPort`。

这三个点本身不一定错，但它们说明领域服务里仍残留了“本地单机优化 + 技术实现策略”：

- `Semaphore` 只能限制单实例并发，不能代表全局容量控制。
- 本地订单号生成对演示足够，但不是长期稳定的订单号治理方式。
- 领域服务仍在承担一部分命令构建和技术兜底，而不是完全把这些职责下沉到专门组件。

当前结论：这是领域层里最值得优先盯住的大类之一。

后续如果继续演进，更合适的方向是：

- 把活动级并发闸门显式解释成“单实例保护”，不要包装成全局限流能力。
- 订单号生成逐步改成独立 `OrderIdGenerator` 或商城统一号段/雪花号能力。
- 如果锁单前置校验继续增长，再把命令构建和预检查拆成更细的领域支撑组件。

### 3. `AbstractOrderService` 仍保留商城侧营销类型分支和订单补单编排

商城 `AbstractOrderService` 当前已经不是仓储大类问题，但它仍然承担：

- 未支付订单补单判断；
- CREATE 状态订单补支付单；
- `GROUP_BUY_MARKET` / `SECKILL_MARKET` 分支路由；
- 营销锁单与支付单创建编排；
- `marketDeductionAmount` 是否存在的分支处理。

这说明商城抽象订单服务仍是一个“带营销类型意识的总模板”。

它现在没有明显越层，但长期风险很清楚：

- 只要再加新的营销类型，`if/else` 会继续长。
- “补单 + 锁单 + 创建支付单” 这套流程会越来越像一个总编排模板。
- 商城主链路会继续感知营销类型，而不是完全通过更明确的策略端口隔离。

当前结论：这是商城域里最值得持续观察的领域编排热点。

当前不继续拆的原因也很明确：

- 它现在还保持在订单创建主流程这一条单一业务线上。
- 如果没有新增第三种营销类型，现在继续拆很容易只是把 if/else 挪位置。

真正触发继续治理的条件应该是：

- 新增新的营销类型；
- 支付单补建逻辑继续扩展；
- 不同营销类型的支付前行为开始显著分叉。

### 4. `ReconcileCaseOperationSupport` 还是运营动作聚合点，但问题重心已经从“拆类”转向“后台能力边界”

这个类上一轮已经审过是后台操作热点，这一轮看得更具体一些：

- 单条处理、确认、忽略、关闭、备注、批量处理都集中在这里。
- 它统一处理管理员认证、操作人解析、审计和响应包装。
- `handleWithStatus`、`batchHandle` 已经说明操作类型正在汇聚。

当前真正的风险不是“它有 118 行”，而是：

- 一旦加审批流、批量重放策略、权限矩阵、SLA 统计、操作模板，这里会再次成为后台总入口。
- 现在的后台能力仍是最小闭环，所以这个类还会自然承接新增运营动作。

当前结论：暂不继续拆生产代码。

后续如果继续做完整后台，应该优先按“运营动作用例”拆，而不是再做通用 util 拆分。

## 优先级判断

这一轮确认后的优先级如下：

1. `IRedisService` / `RedissonService`：最强的长期基础设施惯性风险。
2. `SeckillService`：领域服务中最明显的技术决策残留。
3. `AbstractOrderService`：商城主链路的营销类型编排热点。
4. `ReconcileCaseOperationSupport`：后台运营动作聚合点，但主要取决于后台范围是否继续扩张。

## 本轮结论

当前项目剩下的重点问题，已经非常清楚：

- 不是继续拆 Repository。
- 不是继续拆 Controller。
- 而是防止“超宽基础设施接口”重新成为总线，以及防止少量领域服务重新吸收技术决策和营销分支。

所以后续再有新需求时，最应该先问两个问题：

1. 这次改动是不是又要往 `IRedisService` 里加能力？
2. 这次改动是不是又要把技术判断或营销类型判断塞回 `SeckillService` / `AbstractOrderService`？

如果答案是“是”，那就应该先停下来重新做 SDD 设计。

## 验收

- 本轮只新增审计文档，不修改生产代码。
- `README.md`、`ddd-sdd-todo-list.md`、`tasks.md` 记录本轮审计结论。
- Git 工作区保持可提交状态。

## 面试表述

我会诚实说，这个项目现在最值得警惕的剩余问题，不是传统意义上的大仓储，而是通用基础设施总线和少量领域服务中的技术决策残留。比如 `IRedisService` 仍然过宽，后续很容易重新吸收各种 Redis 能力；`SeckillService` 里还保留了本地 `Semaphore` 和订单号生成；商城 `AbstractOrderService` 仍然感知营销类型分支。这些点现在还没坏到必须立刻重构，但它们已经是后续最该优先盯住的架构热点。
