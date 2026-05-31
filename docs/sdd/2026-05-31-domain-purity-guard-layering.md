# DomainPurityTest 规则分层评估

## 背景

当前项目已经有两层 DDD 守护：

- `scripts/check-domain-purity.ps1`
- `group-buy-market-app/src/test/java/cn/bugstack/test/architecture/DomainPurityTest.java`

本轮检查当前工作区后确认：

- `DomainPurityTest.java` 当前 2086 行。
- `check-domain-purity.ps1` 只扫描 domain 包中的 Spring/container 注解、`@Resource`、`@Autowired`、`@Value` 和 `ThreadPoolExecutor`。
- `DomainPurityTest` 已经承担大量精细架构守护，包括通用仓储删除、端口职责回流、Controller 编排回流、MQ/库存/补偿/对账边界等。

本轮不修改测试代码，只给出规则分层，避免后续继续无节制把所有规则堆进 `DomainPurityTest`。

## 当前分层

### L0：快速粗筛

承载位置：

- `scripts/check-domain-purity.ps1`

适合放：

- domain 包禁止 Spring/container 注解。
- domain 包禁止 `@Resource`、`@Autowired`、`@Value`。
- domain 包禁止直接依赖具体线程池实现。

不适合放：

- 某个 Controller 是否调用了某个支撑组件。
- 某个 Repository 是否包含某段 builder。
- 某个端口是否暴露了业务方法。

原因：

- PowerShell 脚本适合快速失败和简单正则，不适合表达复杂结构边界。

### L1：稳定结构守护

承载位置：

- `DomainPurityTest`

适合放：

- 某个通用仓储必须保持删除。
- 某个语义端口必须存在。
- 某个 Controller 只能依赖支撑组件，不直接依赖领域服务。
- 某个仓储不应重新依赖已拆出的端口。

这类规则相对稳定，因为它们检查的是文件存在性、接口存在性、包依赖或明确的结构边界。

### L2：精细职责回流守护

承载位置：

- `DomainPurityTest`

适合放：

- 禁止某个门面类重新出现 DAO/PO/JSON/Redis/MQ 细节。
- 禁止 Controller 重新承载请求校验、DTO 组装、结构化日志和领域命令构建。
- 禁止端口适配器重新吸收已经拆出的 support 职责。

这类规则仍然有价值，但应该克制新增。

新增前必须满足：

1. 该职责之前已经真实回流过，或极容易回流。
2. 没有更稳定的单元测试、契约测试或文件结构检查能表达。
3. 失败信息能清楚告诉开发者应该把逻辑放到哪里。

### L3：脆弱文本快照守护

承载位置：

- 当前部分仍在 `DomainPurityTest`

特征：

- 大量依赖 `source.contains("...")`。
- 禁止具体变量名、builder 片段、方法调用片段。
- 对重命名和重排敏感。

这类规则不是完全不能用，但应该作为最后手段。

后续新增时要特别谨慎：

- 如果只是为了防止“某行代码看起来不优雅”，不要加。
- 如果能通过单元测试覆盖行为，不要加。
- 如果能通过端口存在性或依赖方向表达，不要加。

### L4：行为和契约测试

承载位置：

- 领域单元测试。
- 基础设施端口单元测试。
- 对账契约测试。
- 状态机测试。

适合放：

- 拼团锁单等待超时语义。
- 秒杀库存预扣和失败回滚。
- 对账差错重放。
- 订单状态机合法迁移。

这些不应该塞进 `DomainPurityTest`。架构测试管结构，行为测试管语义。

## 新增守护规则决策顺序

以后要新增架构守护时，按这个顺序判断：

1. 能否用普通单元测试或契约测试验证行为。
2. 能否用文件存在性、接口存在性或包依赖验证结构。
3. 能否放到轻量脚本做粗筛。
4. 是否真的需要放进 `DomainPurityTest`。
5. 如果只能用字符串片段守护，是否有明确的回流风险和清晰的失败提示。

只有第 4 或第 5 步才进入 `DomainPurityTest`。

## 本轮结论

当前不建议立刻拆 `DomainPurityTest`。

原因：

- 它虽然大，但仍然有效挡住了大量 DDD 回流。
- 直接拆测试文件对业务价值不高，还可能引入新的维护成本。
- 现在更重要的是先明确新增规则准入标准。

当前建议：

- 现有规则保持。
- 后续新增规则先按 L0 到 L4 分层判断。
- 不再默认把所有新守护都加进 `DomainPurityTest`。
- 等它继续显著膨胀，或新增规则明显集中到某个主题时，再考虑拆成多个测试类。

## 验收

- 本轮只新增规则分层文档，不修改生产代码和测试代码。
- `README.md`、`tasks.md`、`ddd-sdd-todo-list.md` 和当前风险地图同步记录。
- Done/TODO/Open Items 更新：`DomainPurityTest` 规则分层评估从 TODO 移到 Done，结构拆分保留为暂不处理。

## 面试表述

如果面试官问“你怎么防止 DDD 回退”，可以这样说：

> 我做了两层守护：PowerShell 脚本负责 domain 包快速粗筛，JUnit 的 `DomainPurityTest` 负责精细结构边界。但我也意识到 `DomainPurityTest` 已经超过 2000 行，不能继续无节制加字符串规则。所以我把守护分成粗筛、稳定结构守护、精细职责回流守护、脆弱文本快照守护、行为契约测试五层。后续新增规则会先判断能不能用单元测试、契约测试或稳定结构检查表达，只有确实需要时才进入 `DomainPurityTest`。
