// Package redis 是把字段规则存进 Redis 的规则来源,数据约定与 Java 1.4.0 的 Redis 模式一致
// (见仓库 docs/design/redis-mode.md)。本包不依赖任何 Redis 客户端,使用方实现最小接口 Client
// (go-redis 的适配器见 docs/REDIS.md)。
package redis
