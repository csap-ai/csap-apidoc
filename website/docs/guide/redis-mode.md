# Redis 模式

:::tip 1.4.0 新增
Redis 模式从 **Java 1.4.0、Go v0.2.0** 起提供。数据约定见 [redis-mode.md](https://github.com/csap-ai/csap-apidoc/blob/main/docs/design/redis-mode.md)。Redis Cluster、哨兵、密码和 TLS 没有实测，见下文。
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

## Java 用法

模块 `csap-apidoc-redis`，已包含在 `csap-apidoc-boot-starter` 中；Spring Boot 2 和 3 用同一个 jar。**Spring Data Redis 是可选依赖**，由应用自己引入（例如 `spring-boot-starter-data-redis`）并配置连接；框架使用应用里唯一的 `StringRedisTemplate`，没有时用唯一的 `RedisConnectionFactory`，不另建连接。缺少依赖或容器里没有 Redis 连接时，应用启动即报错并提示怎么处理。

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
      poll-interval: 5s            # 版本号轮询兜底间隔，0 = 不轮询
      subscribe: true              # 是否订阅变更频道，其他实例保存后即时重新加载
      read-only: false             # true：只读，禁止保存和回滚
```

| 配置项 | 默认值 | 说明 |
|--------|--------|------|
| `key-prefix` | `csap:apidoc:` | 键前缀；服务名用 hash tag 包在前缀后面 |
| `service` | 空 | 服务标识。空时用 `spring.application.name`，仍为空则是 `default`；同一个 Redis 存多个服务时每个服务必须不同 |
| `history-limit` | `200` | 每个接口最多保留的修改记录条数 |
| `poll-interval` | `5s` | 轮询版本号的间隔（订阅断开时的兜底），发现变化就重新加载；`0` 表示不轮询 |
| `subscribe` | `true` | 是否订阅 Redis 频道，别的实例保存后即时重新加载 |
| `read-only` | `false` | 只读模式，见 [只读模式](#只读模式) |

保存用的 Lua 脚本在 jar 内 `META-INF/csap-apidoc/redis/save.lua`，Java 与 Go 使用同一段脚本。

DevTools 的「规则记录」页（`/devtools-ui/rules`）对 Redis 模式同样可用，用法见 [数据库模式 - DevTools「规则记录」页](/guide/database-mode#devtools规则记录页)。

:::info 集群、哨兵和认证
Redis Cluster、哨兵、密码和 TLS 都由应用自己的 Redis 连接配置决定，框架只靠 hash tag 约定保证同一服务的键在同一个槽。这几种部署**没有实测**，上线前请先在测试环境验证。
:::

## Go 用法

Go 版 `strategy/redis`（v0.2.0）不依赖任何 Redis 客户端：使用方实现一个最小的 `Client` 接口（`Eval`、`Get`、`HGet`、`HGetAll`、`LRange`、`Subscribe`；找不到的键返回 `ok=false`，不要当作错误）。下面是 go-redis v9 的适配器：

```go
import "github.com/redis/go-redis/v9"

type GoRedis struct{ C redis.UniversalClient }

func (g GoRedis) Eval(ctx context.Context, script string, keys []string, args ...any) (int64, error) {
	return redis.NewScript(script).Run(ctx, g.C, keys, args...).Int64()
}

func (g GoRedis) Get(ctx context.Context, key string) (string, bool, error) {
	v, err := g.C.Get(ctx, key).Result()
	if errors.Is(err, redis.Nil) {
		return "", false, nil
	}
	return v, err == nil, err
}

func (g GoRedis) HGet(ctx context.Context, key, field string) (string, bool, error) {
	v, err := g.C.HGet(ctx, key, field).Result()
	if errors.Is(err, redis.Nil) {
		return "", false, nil
	}
	return v, err == nil, err
}

func (g GoRedis) HGetAll(ctx context.Context, key string) (map[string]string, error) {
	return g.C.HGetAll(ctx, key).Result()
}

func (g GoRedis) LRange(ctx context.Context, key string, start, stop int64) ([]string, error) {
	return g.C.LRange(ctx, key, start, stop).Result()
}

func (g GoRedis) Subscribe(ctx context.Context, channel string, fn func(string)) error {
	ps := g.C.Subscribe(ctx, channel)
	defer ps.Close()
	for {
		select {
		case <-ctx.Done():
			return ctx.Err()
		case m, ok := <-ps.Channel():
			if !ok {
				return errors.New("subscription closed")
			}
			fn(m.Payload)
		}
	}
}
```

```go
rdb := redis.NewClient(&redis.Options{Addr: "127.0.0.1:6379"}) // 集群用 redis.NewClusterClient
s := redisstrategy.New(redisstrategy.Options{
    Client:       GoRedis{rdb},
    Service:      "order-service", // 默认 "default"
    KeyPrefix:    "csap:apidoc:",  // 默认值
    HistoryLimit: 200,             // 每个接口保留的修改记录条数，默认 200；负数表示不裁剪
    Poll:         5 * time.Second, // 轮询版本号兜底，默认 5 秒；小于 0 只靠订阅
})
doc := apidoc.New(apidoc.Options{Strategies: []strategy.Strategy{s}})
go doc.Watch(ctx)
```

同一个 Redis 里 Go 服务和 Java 服务用各自的 `service` 名即可共存，规则 JSON 结构相同，规则键不同（Go 是 `分组.接口名`，Java 是 `类全名.方法名`）。

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
- Java 订阅需要 `subscribe: true`（默认）；关闭后只靠轮询。

## Redis Cluster

规则相关的键都带服务名 hash tag，同一个服务的数据在同一个槽里，所以保存脚本在 Cluster 下可以执行。这也意味着单个服务的全部数据在一个节点上，不会分散。

## 修改记录与回滚

每次保存往该接口的修改记录里写一条：修改前后的规则、版本号、修改人、时间。**每个接口只保留最近 `history-limit` 条**，更早的会被裁掉，被裁掉的记录不能再回滚。需要完整、长期的审计追溯时请用数据库模式。

## 只读模式

与数据库模式相同：只读取规则，DevTools 保存和回滚都被拒绝（回滚返回 `code = 1007`）。适合生产环境只读取、规则在别处修改。

## 持久化与备份

- Redis 必须开启 **AOF 或 RDB** 持久化，否则 Redis 重启会丢掉规则；
- 建议定期把规则导出成 YAML 作为备份，纳入 Git 审查。导出用 [迁移工具](/guide/database-mode#迁移)。

## 相关页面

- [数据库模式](/guide/database-mode)
- [存储策略与返回过滤](/guide/storage-strategies)
- [DevTools](/guide/devtools)
