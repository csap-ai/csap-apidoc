# 与团队版对接:服务注册与 MCP 审计落库

状态:契约冻结(2026-11-18,主会话制定)。Java、Go 两边以本文为准,改动先找主会话。

团队版(商业产品,独立部署)把多个服务的文档集中到"文档中心",并在同一界面查 MCP 调用审计。开源版需要提供两样东西:

1. **服务注册客户端**(Java、Go 都做):服务启动后把自己的地址报给团队版,团队版据此拉取 `/csap/apidoc`。
2. **MCP 审计写入数据库**(只有 Java,Go 没有 MCP):在数据库模式的库里多一张 `{prefix}mcp_audit` 表。

两项都**默认关闭**,不配置时行为与 1.4.0 完全相同;不依赖团队版的任何代码。

## 1. 服务注册协议

### 1.1 请求

```
POST {url}/api/open/v1/services/register
Authorization: Bearer {token}
Content-Type: application/json

{
  "service": "order-service",
  "instanceId": "10.0.0.5:8080",
  "baseUrl": "http://10.0.0.5:8080",
  "docPath": "/csap/apidoc",
  "language": "java",
  "frameworkVersion": "1.5.0"
}
```

| 字段 | 必填 | 说明 |
|---|---|---|
| `service` | 是 | 与数据库模式的 `service` 相同含义;**两边默认值规则一致**,见 1.3 |
| `instanceId` | 是 | 同一服务内唯一;默认 `host:port`(Go 默认取 `baseUrl` 的 host:port) |
| `baseUrl` | 是 | 团队版能访问到的服务地址,不带末尾 `/`,包含 context-path |
| `docPath` | 否 | 文档接口路径,默认 `/csap/apidoc` |
| `language` | 是 | `java` / `go`(以后的语言用小写名) |
| `frameworkVersion` | 是 | csap-apidoc 版本,Java 取构建时写入的版本,Go 取模块常量 |

响应 `200 {"heartbeatSeconds": 30}`。客户端按返回值(限制在 10–300 之间,无效时用 30)周期性**重复发送同一个请求**作为心跳。

### 1.2 注销

服务正常关闭时发送一次(超时 2 秒,失败忽略):

```
POST {url}/api/open/v1/services/deregister
Authorization: Bearer {token}
{"service": "order-service", "instanceId": "10.0.0.5:8080"}
```

### 1.3 客户端行为(两边必须一致)

- **绝不影响业务**:注册在后台线程 / goroutine 进行;团队版不可达、返回错误都不能阻止启动或抛到业务代码。
- 超时:连接 + 读取共 3 秒。
- 失败重试:按心跳周期继续发;连续失败时日志降噪(首次失败 warn,之后每 10 次 warn 一次,恢复时 info 一次)。
- `401` / `403`:warn 一次"令牌无效或权限不足",之后按 300 秒周期重试。
- **日志、异常信息里不得出现令牌**。
- `service` 默认值:Java 依次取 `csap.apidoc.team.service` → `csap.apidoc.database.service` → `spring.application.name` → `default`;Go 依次取 `TeamOptions.Service` → 数据库模式的 `Service` → `default`。
- `baseUrl`:Java 可不填,自动推断为 `http://{本机首个非回环 IPv4}:{server.port}{server.servlet.context-path}`,推断不出时 warn 并不注册;Go **必填**(Go 无法可靠得知监听地址),为空时不注册并 warn 一次。
- 未开启(`enabled=false` 或 `url` 为空)时不创建线程、不发请求。

### 1.4 配置

Java(`csap.apidoc.team.*`,新模块 `csap-apidoc-team-client`,由 `csap-apidoc-boot-starter` 引入,Spring Boot 2 / 3 都支持,Java 11 字节码):

```yaml
csap:
  apidoc:
    team:
      enabled: false
      url: ""              # 团队版地址,例如 http://team.example.com:8090
      token: ""            # 团队版控制台创建的 REGISTER 令牌,建议用环境变量
      service: ""
      base-url: ""
      instance-id: ""
      doc-path: /csap/apidoc
```

Go(新包 `github.com/csap-ai/csap-apidoc/csap-apidoc-go/team`):

```go
reg := team.Register(ctx, team.Options{
    URL: "http://team.example.com:8090", Token: os.Getenv("CSAP_TEAM_TOKEN"),
    Service: "order-service", BaseURL: "http://10.0.0.5:8080",
    // InstanceID、DocPath、HTTPClient、Logger(slog)可选
})
defer reg.Close() // 发送注销并停止心跳
```

### 1.5 测试要求

- 用本地 HTTP 测试服务器校验:请求路径、方法、header、body 字段;心跳间隔取自响应;注销;401 降频;团队版不可达时启动不受影响;日志中无令牌。
- Java 与 Go 对同一组配置发出的请求体字段一致(主会话验收时比对)。

## 2. MCP 审计写入数据库(Java)

### 2.1 开关

```yaml
csap:
  apidoc:
    mcp:
      audit:
        store: memory      # memory(默认,1.3.0 行为) / sqlite(等同 persist: true) / database
```

- `store: database` 时使用数据库模式(`csap-apidoc-database`)的 DataSource、方言和表前缀;未启用数据库模式时启动 warn 并退回 `memory`。
- 原有 `persist: true` 继续有效,等同 `store: sqlite`(兼容)。
- 内存环形缓冲照旧保留(DevTools 的 MCP 审计页仍从内存读)。写库为异步、有界队列(满了丢弃并计数 warn),不阻塞 MCP 请求。

### 2.2 表 `{prefix}mcp_audit`

| 列 | 类型 | 说明 |
|---|---|---|
| `id` | 自增 BIGINT 主键 | |
| `service` | VARCHAR(128) | 同数据库模式 `service` |
| `at` | BIGINT | 毫秒 |
| `caller` | VARCHAR(128) | 调用方(令牌名) |
| `remote_address` | VARCHAR(64) | |
| `tool` | VARCHAR(128) | |
| `target` | VARCHAR(512) | 目标接口 |
| `arguments` | TEXT/CLOB | 已脱敏、已截断的参数摘要 |
| `status` | INT | |
| `success` | SMALLINT | 0/1 |
| `result` | TEXT/CLOB | 已脱敏、已截断的结果摘要 |
| `duration_ms` | BIGINT | |

- 索引:`(service, at)`、`(at)`。
- 六种方言(MySQL、PostgreSQL、达梦、人大金仓、SQLite、H2)各一份建表脚本,风格与 `{prefix}rule` 相同(不写 `DEFAULT ''`,程序显式写值);`create-tables: false` 或只读模式下不建表,表不存在时 warn 并退回 `memory`。
- 保留:`csap.apidoc.mcp.audit.max-rows`(默认 10000)对 database 同样生效,按 `service` 定期删除超出的旧记录(每 10 分钟一次)。
- 写入内容与内存审计相同,脱敏规则(`mask-fields`)先于写库生效。

### 2.3 测试要求

H2 + Testcontainers(MySQL、PostgreSQL)各验证建表、写入、清理;未开数据库模式时的退回;队列满时不阻塞。

## 3. 版本

开源 Java 1.5.0、Go v0.3.0 提供上述能力,与团队版 1.0 同期发布(日期以路线图为准)。
