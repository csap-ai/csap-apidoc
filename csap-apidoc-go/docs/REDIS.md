# Redis 规则来源

`strategy/redis` 把字段规则存进 Redis,数据约定与 Java 1.4.0 的 Redis 模式一致(键、修改记录 JSON、Lua 保存脚本、回滚规则见仓库 `docs/design/redis-mode.md`)。同一个 Redis 里 Go 服务和 Java 服务用各自的 `service` 名即可共存;规则 JSON 两边相同,规则键 Go 是 `分组.接口名`、Java 是 `类全名.方法名`。

主模块不依赖任何 Redis 客户端:实现下面这个最小接口即可(`Eval`、`Get`、`HGet`、`HGetAll`、`LRange`、`Subscribe`),找不到的键用 `ok=false`,不要返回错误。

## 接入(go-redis v9)

```go
package yourapp

import (
	"context"
	"errors"

	"github.com/redis/go-redis/v9"
)

// GoRedis 把 go-redis v9 适配成 strategy/redis 的 Client 接口。
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
    HistoryLimit: 200,             // 每个接口保留的修改记录条数,默认 200
    Poll:         5 * time.Second, // 轮询版本号兜底,默认 5 秒;小于 0 只靠订阅
})
doc := apidoc.New(apidoc.Options{Strategies: []strategy.Strategy{s}})
go doc.Watch(ctx)
```

`integration/goredis.go` 里有同一份适配器(测试在用)。

## 行为

- **键**:`{前缀}{服务名}:rules`(Hash)、`:version`、`:history:{规则键}`(List)、`:changes`(id → 规则键索引)、频道 `:changed`。服务名放在 `{}` 里,是 Redis Cluster 的 hash tag,同一服务的键落在同一个槽,Lua 脚本在集群下可以执行。
- **保存**:一段 Lua 脚本原子完成"版本号加 1、写规则、写修改记录、裁剪记录、发布通知"。脚本文本在 `strategy/redis/save.lua`(`redisstrategy.SaveScript`),与 Java 内嵌的是同一段。规则内容没变(按 JSON 结构比较)时什么都不做,返回当前版本号,不留记录。
- **同步**:订阅 `changed` 频道,收到消息立即检查;同时按 `Poll` 比较版本号兜底,订阅断开(自动重连)期间不会漏。
- **修改记录与回滚**:只保留最近 `HistoryLimit` 条,被裁掉的记录不能回滚(返回 `strategy.ErrNoChange`);需要完整审计请用数据库模式。回滚本身也是一次保存。
- **持久化**:Redis 需要开启 AOF 或 RDB,否则重启会丢规则。想留备份,可以把规则导出成 YAML,或用 [规则导入](IMPORT.md) 反向导入。
