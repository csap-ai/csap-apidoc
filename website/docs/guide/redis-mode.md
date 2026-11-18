# Redis 模式

:::warning 1.4.0 实现中
Redis 模式计划随 **Java 1.4.0（2026-12-22）** 和 **Go v0.2.0** 发布，**当前已发布的版本没有**，实现还在进行中。本页写的是已经定下的数据约定和取舍；配置项和用法的细节标了"以实现为准"，实现验收后会定稿。数据约定见 [redis-mode.md](https://github.com/csap-ai/csap-apidoc/blob/main/docs/design/redis-mode.md)。
:::

Redis 模式把字段规则存进 Redis，而不是 YAML 文件、SQLite 文件或关系数据库。规则的内容、字段键、`validate` 写法与 [存储策略](/guide/storage-strategies) 中的 YAML 完全相同。Java 与 Go 使用同一种数据结构。

## 什么时候选它

| 场景 | 建议 |
|------|------|
| 项目已经在用 Redis，没有（或不想为规则新增）关系数据库 | Redis 模式 |
| 多实例部署，希望保存后立即在所有实例生效 | Redis 模式（订阅变更）或数据库模式 |
| 需要完整、长期的修改记录，满足审计留存要求 | [数据库模式](/guide/database-mode) |
| 规则不能丢，且 Redis 没有开持久化 | 数据库模式，或先给 Redis 开启 AOF / RDB |

## 和数据库模式的对比

| | 数据库模式 | Redis 模式 |
|---|---|---|
| 存放 | MySQL / PostgreSQL / 达梦 / 人大金仓 | Redis |
| 多实例同步 | 轮询版本号（默认 5 秒） | 订阅变更即时生效 + 轮询版本号兜底 |
| 修改记录 | 完整保留，只增不改 | 每个接口只保留最近 `history-limit` 条（默认 200） |
| 回滚 | 能 | 能（只能回滚还保留着的记录） |
| 持久化 | 数据库自己保证 | **需要 Redis 开启 AOF 或 RDB**，否则重启会丢规则 |
| 团队版的审计 | 基于数据库 | 不支持 |
| 只读模式 | 有 | 有 |

## 配置（以实现为准）

```java
@EnableApidoc(apiPackages = "com.example.controller", paramType = ApiStrategyType.REDIS)
```

```yaml
csap:
  apidoc:
    redis:
      key-prefix: "csap:apidoc:"   # 键前缀
      service: ""                  # 服务名；空时取 spring.application.name，再空为 default
      history-limit: 200           # 每个接口保留的修改记录条数
      poll-interval: 5s            # 版本号轮询兜底间隔
      subscribe: true              # 是否订阅变更频道即时同步
      read-only: false             # true：只读，禁止保存和回滚
```

:::info 待核对
上面的配置项和默认值来自方案，**以实现为准**，发布前会按代码核对。Java 使用应用已有的 `RedisConnectionFactory`（Spring Data Redis，可选依赖），不另建连接。
:::

## Go 用法（以实现为准）

Go 版由使用方传入实现了简单接口的 Redis 客户端，主模块不引入 Redis 依赖，go-redis 的适配示例会写在文档里。具体的 API 等 Go v0.2.0 实现定稿后补充。

## 数据结构

默认前缀 `csap:apidoc:`，服务名用 Redis Cluster 的 hash tag（花括号）包起来，同一服务的键落在同一个槽，保存用的 Lua 脚本在集群下也能执行。

| 键 | 类型 | 内容 |
|---|---|---|
| `{prefix}{{service}}:rules` | Hash | 字段是规则键（Java `类全名.方法名`，Go `分组.接口名`），值是规则 JSON |
| `{prefix}{{service}}:version` | 整数 | 全局版本号，每次保存加 1 |
| `{prefix}{{service}}:history:{规则键}` | List | 该接口的修改记录，最新的在表头 |
| `{prefix}{{service}}:changes` | Hash | 修改记录 id 到规则键的索引，用于按 id 回滚 |
| 频道 `{prefix}{{service}}:changed` | Pub/Sub | 保存后发布，消息是新版本号 |

例：服务 `order-service`、默认前缀时，规则 Hash 的键是 `csap:apidoc:{order-service}:rules`。

保存时由一段 Lua 脚本原子完成：版本号加 1、写规则、写修改记录、更新索引、发布通知；规则内容没有变化时不加版本号、不留记录。

## 多实例同步

- 订阅 `changed` 频道，其他实例保存后**立即**收到通知并重新加载；
- 同时按 `poll-interval`（默认 5 秒）比较版本号作为兜底，订阅断开时也不会漏；
- 重新加载后文档和字段规则随之更新，与数据库模式相同。

## Redis Cluster

规则相关的键都带服务名 hash tag，同一个服务的数据在同一个槽里，所以保存脚本在 Cluster 下可以执行。这也意味着单个服务的全部数据在一个节点上，不会分散。

## 修改记录与回滚

每次保存往该接口的修改记录里写一条：修改前后的规则、版本号、修改人、时间。**每个接口只保留最近 `history-limit` 条**，更早的会被裁掉，被裁掉的记录不能再回滚。需要完整、长期的审计追溯时请用数据库模式。

## 只读模式

与数据库模式相同：只读取规则，DevTools 保存和回滚都被拒绝（回滚返回 `code = 1007`）。适合生产环境只读取、规则在别处修改。

## 持久化与备份

- Redis 必须开启 **AOF 或 RDB** 持久化，否则 Redis 重启会丢掉规则；
- 建议定期把规则导出成 YAML 作为备份，纳入 Git 审查。导出用 [迁移工具](/guide/database-mode#迁移)（实现中）。

## 相关页面

- [数据库模式](/guide/database-mode)
- [存储策略与返回过滤](/guide/storage-strategies)
- [DevTools](/guide/devtools)
