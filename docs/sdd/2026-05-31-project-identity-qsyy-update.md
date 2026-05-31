# 项目可见标识 qsyy 迁移记录

## 背景

本轮处理的是项目可见标识问题：根目录名、README 标题、POM 开发者信息、Dockerfile maintainer、前端展示文案和样例用户容易让人误解为原始课程代码未做个人维护。迁移目标是把可见维护者和项目名称收敛为 qsyy，同时不破坏已经能编译运行的 DDD 分层和中间件配置。

## SDD 规格

- 只迁移展示层、文档层、样例数据和维护者元数据。
- 保留 Java package `cn.bugstack`，避免一次性命名空间迁移影响 Spring 扫描、MyBatis、测试路径和历史文档链接。
- 保留外部依赖坐标 `xfg-wrench-*`，因为这是当前 Maven 可解析的三方/课程扩展依赖名。
- 保留数据库名、MQ exchange/queue、Redis key 前缀里的 `group_buy_market`，因为这些属于运行兼容标识，不是展示品牌。
- 根目录在提交和推送完成后从 `E:\java\group_buy_market` 重命名为 `E:\java\qsyy-ecommerce-platform`。
- 顶层服务目录从历史课程命名收敛为 `qsyy-commerce-market` 和 `qsyy-commerce-mall`；内部 Maven module、Java package 和运行时标识暂不做级联重命名。

## Done List

- [x] 根 README 改为 `qsyy-ecommerce-platform`，说明项目由 qsyy 维护。
- [x] 根聚合 POM 改为 `io.github.qsyy0921:qsyy-ecommerce-suite`。
- [x] 两个服务 README 改为 `qsyy-commerce-market` 和 `qsyy-commerce-mall`。
- [x] 两个顶层服务目录改为 `qsyy-commerce-market` 和 `qsyy-commerce-mall`，并同步根 POM、脚本、架构测试和文档路径引用。
- [x] 两个服务 POM 的 developer 元数据改为 qsyy。
- [x] Dockerfile maintainer 改为 qsyy。
- [x] 当前前端展示页、样例用户和测试样例中的 xfg/xiaofuge/小傅哥 标识改为 qsyy。
- [x] SDD 当前目标 Prompt 的本地根目录改为 `E:\java\qsyy-ecommerce-platform`。
- [x] 记录保留边界：package、外部依赖坐标、数据库/MQ/Redis 运行标识暂不改。
- [x] 完成本轮验证：`git diff --check`、`docs-only`、`market-compile`、`mall-compile`、根聚合 `mvn -q -DskipTests compile`。

## TODO List

- [ ] P2：评估是否需要后续做 package 命名空间迁移。
  - 原因：可见展示已经收敛，但源码 package 仍是历史兼容命名。
  - 范围：仅限明确需要品牌级命名空间迁移时再做，不纳入当前轮。
  - 验收：有完整迁移规格、脚本、MyBatis/Spring 扫描验证和回滚方案。

## 所有未完成任务清单

| 任务名称 | 当前状态 | 所属类型 | 优先级 | 不完成的影响 | 当前为什么还没做 | 后续触发条件 |
| --- | --- | --- | --- | --- | --- | --- |
| Java package 全量命名空间迁移 | 暂不处理 | 代码风险 / 兼容边界 | P2 | package 仍保留历史命名，视觉上不完全统一 | 会影响 Spring 扫描、MyBatis mapper、测试包路径和大量文档链接，风险大于本轮收益 | 明确要做品牌级命名空间迁移 |
| `xfg-wrench-*` 依赖坐标替换 | 暂不处理 | 依赖边界 | P2 | 外部依赖名仍带历史标识 | 当前依赖坐标可解析，强行改名会导致 Maven 解析失败 | 自建 fork 并发布 qsyy 坐标 |
| 数据库/MQ/Redis 运行标识迁移 | 暂不处理 | 生产边界 / 运维边界 | P2 | 运行标识仍使用 `group_buy_market` | 这些标识影响 SQL、缓存、MQ、告警和压测脚本，迁移需要运维窗口 | 明确需要生产级重命名并准备数据迁移 |
| 根目录物理重命名 | 进行中 | 本地环境 | P1 | 文件资源管理器仍显示旧目录 | 需要先完成提交和推送，避免路径切换中断验证 | 本轮 push 成功后执行 `Move-Item` |
| 内部 Maven artifactId 级联重命名 | 暂不处理 | 构建边界 | P2 | `group-buy-market-*`、`s-pay-mall-ddd-*` 仍作为内部模块名存在 | 会影响依赖关系、脚本、jar 名、部署命令和历史验证基线，本轮只解决顶层目录识别问题 | 明确要做构建坐标级品牌迁移并准备全量验证 |

## 验收

本轮验收不以“所有历史命名都消失”为目标，而以“可见维护者归属清楚、运行兼容不被破坏”为目标。后续如继续迁移 package 或运行标识，必须单独写 SDD 规格和回滚方案。
