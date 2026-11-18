# Redis 模式数据约定(Java 与 Go 共用)

状态:已定,实现中(1.4.0 / Go v0.2.0)。总体方案见 [database-mode.md](database-mode.md) 3.5 节;本文是两种语言实现必须一致的细节,改动前先改本文。

## 1. 键

默认前缀 `csap:apidoc:`(`key-prefix` 可改),`{service}` 为服务名(Java 默认 `spring.application.name`,再空为 `default`;Go `Options.Service` 默认 `default`)。服务名用 Redis Cluster 的 hash tag 包起来,同一服务的键落在同一个槽,Lua 脚本在集群下也能执行。

| 键 | 类型 | 内容 |
|---|---|---|
| `{prefix}{{service}}:rules` | Hash | 字段 = 规则键(Java `类全名.方法名`,Go `分组.接口名`),值 = 规则 JSON(与数据库模式 `rules` 列、YAML 规则同一结构) |
| `{prefix}{{service}}:version` | String(整数) | 全局版本号,每次保存加 1,不存在视为 0 |
| `{prefix}{{service}}:history:{ruleKey}` | List | 该接口的修改记录,最新的在表头,最多保留 `history-limit` 条(默认 200) |
| `{prefix}{{service}}:changes` | Hash | 修改记录索引:字段 = 修改记录 id(十进制字符串),值 = 规则键;记录被裁掉时同时删除,用于按 id 回滚 |
| 频道 `{prefix}{{service}}:changed` | Pub/Sub | 保存后发布,消息体 = 新版本号(十进制字符串) |

例:服务 `order-service`、默认前缀时,规则 Hash 为 `csap:apidoc:{order-service}:rules`。

## 2. 修改记录

List 元素是一个 JSON 对象(键名固定,不省略):

```json
{"id": 12, "ruleKey": "com.example.OrderController.create", "before": "<修改前规则 JSON 字符串或 null>", "after": "<修改后规则 JSON 字符串或 null>", "version": 12, "updatedBy": "devtools", "updatedAt": 1795004394671}
```

- `id` 等于这次保存后的版本号(同一服务内唯一),与数据库模式的 `RuleChange` 字段一一对应。
- `before` / `after` 是**字符串**(规则 JSON 原文),新增时 `before` 为 null,删除时 `after` 为 null。
- `updatedAt` 为毫秒时间戳;`updatedBy` 为空时写 `unknown`。

## 3. 保存(Lua 脚本,原子执行)

`KEYS = [rules, version, history:{ruleKey}, changes]`,`ARGV = [ruleKey, after, updatedBy, updatedAt, historyLimit, channel]`;`after` 为空字符串表示删除。

1. `before = HGET rules ruleKey`(不存在为 nil)。
2. `before` 与 `after` 相同(都不存在,或字符串完全相同)时返回 `-1`,不加版本号、不留记录。
3. `v = INCR version`。
4. `after` 为空则 `HDEL rules ruleKey`,否则 `HSET rules ruleKey after`。
5. 用 `cjson.encode` 生成第 2 节的记录,`LPUSH history`;把下标 `historyLimit` 起的旧记录逐条解出 `id` 并 `HDEL changes id`,再 `LTRIM history 0 historyLimit-1`。
6. `HSET changes v ruleKey`。
7. `PUBLISH channel v`,返回 `v`。

两种语言使用**同一段脚本文本**(各自内嵌,互通测试比对),客户端在调用前可先按 JSON 结构比较,内容相同则不调用。

## 4. 读取、同步、回滚

- 加载:先 `GET version`,再 `HGETALL rules`(顺序与数据库模式相同,读到的版本偏旧时下次检查会再加载)。
- 同步:订阅 `changed` 频道即时重新加载;同时按 `poll-interval`(默认 5s)比较 `version` 兜底,订阅断开不会漏。
- 修改记录:`LRANGE history:{ruleKey} 0 n-1`;不带规则键时,遍历 `changes` 取各键记录合并后按 id 倒序。
- 回滚:`HGET changes id` 取规则键,在该键的记录里找到 id,把 `before` 作为新内容保存(回滚本身也是一次保存);id 不存在或已被裁掉时返回"修改记录不存在"。

## 5. 持久化与限制

- Redis 需开启 AOF 或 RDB,否则重启丢规则;官网写明,并提供"导出成 YAML"作为备份。
- 修改记录只保留最近 `history-limit` 条;需要完整审计用数据库模式。
- 只读模式与数据库模式相同:不能保存、不能回滚(1007)。
