# 与团队版对接

:::tip 1.5.0 新增(计划与 Go v0.3.0 同期发布)
本页的两项能力都**默认关闭**,不配置时行为与 1.4.0 完全相同,也不依赖团队版的任何代码。协议以 [team-integration.md](https://github.com/csap-ai/csap-apidoc/blob/main/docs/design/team-integration.md) 为准。
:::

团队版(商业产品,独立部署)把多个服务的文档集中到"文档中心",并在同一界面汇总 MCP 调用审计。开源版为此提供两样东西,语言无关:

| 能力 | 做什么 | 语言 |
|------|--------|------|
| [服务注册](#服务注册) | 服务启动后把自己的地址报给团队版,团队版据此拉取 `/csap/apidoc` | Java、Go |
| [MCP 审计写入数据库](#mcp-审计写入数据库) | 在数据库模式的库里多一张 `{前缀}mcp_audit` 表,团队版直接查询 | Java(Go 没有 MCP) |

## 服务注册

### Java / Spring Boot

`csap-apidoc-boot-starter` 已带上注册客户端(模块 `csap-apidoc-team-client`,Spring Boot 2 和 3 通用),只需要配置:

```yaml
csap:
  apidoc:
    team:
      enabled: true
      url: http://team.example.com:8090
      token: ${CSAP_TEAM_TOKEN}      # 团队版控制台创建的 REGISTER 令牌,建议用环境变量
      # service: order-service       # 默认见下
      # base-url: http://10.0.0.5:8080
      # instance-id: 10.0.0.5:8080
      # doc-path: /csap/apidoc
```

完整配置项见 [配置参考](/guide/configuration#csapapidocteam)。

### Go

```go
reg := team.Register(ctx, team.Options{
    URL: "http://team.example.com:8090", Token: os.Getenv("CSAP_TEAM_TOKEN"),
    Service: "order-service", BaseURL: "http://10.0.0.5:8080",
    // InstanceID、DocPath、HTTPClient、Logger(slog)可选
})
defer reg.Close() // 发送注销并停止心跳
```

Go 无法可靠得知监听地址,所以 `BaseURL` **必填**,为空时不注册并 warn 一次。

### 默认值

| 字段 | 规则 |
|------|------|
| `service` | Java 依次取 `csap.apidoc.team.service` → `csap.apidoc.database.service` → `spring.application.name` → `default`;Go 依次取 `TeamOptions.Service` → 数据库模式的 `Service` → `default` |
| `baseUrl` | 团队版能访问到的本服务地址,含 context-path,不带末尾 `/`。Java 可不填,自动推断为 `http://{本机首个非回环 IPv4}:{server.port}{context-path}`(启用 `server.ssl.enabled` 时为 `https`),推断不出时 warn 并不注册;容器、网关或多网卡环境请显式配置 |
| `instanceId` | 同一服务内唯一,默认 `baseUrl` 的 `host:port` |
| `docPath` | 默认 `/csap/apidoc` |

### 行为

- **绝不影响业务**:注册在后台线程进行,团队版不可达或返回错误都不会阻止应用启动,也不会抛到业务代码。
- 注册成功后按团队版返回的 `heartbeatSeconds`(限制在 10–300 秒,无效时 30 秒)周期**重复发送同一个请求**作为心跳;应用正常关闭时发送一次注销(超时 2 秒,失败忽略)。
- 连接 + 读取共 3 秒超时。失败后按心跳周期重试;连续失败时日志降噪:第一次 warn,之后每 10 次 warn 一次,恢复时 info 一次。
- 返回 `401` / `403`:warn 一次"令牌无效或权限不足",之后改为每 300 秒重试。
- **令牌不会出现在日志和异常信息里。**
- 未开启(`enabled=false` 或 `url` 为空)时不创建线程、不发请求。

### 协议

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

响应 `200 {"heartbeatSeconds": 30}`。注销:`POST {url}/api/open/v1/services/deregister`,body 为 `{"service": ..., "instanceId": ...}`。`language` 为 `java` 或 `go`,`frameworkVersion` 是 csap-apidoc 的版本(Java 取构建时写入的版本)。

## MCP 审计写入数据库

只有 Java 有 MCP,所以只有 Java 提供:

```yaml
csap:
  apidoc:
    mcp:
      enabled: true
      audit:
        store: database     # memory(默认) / sqlite(等同 persist: true) / database
        max-rows: 10000
```

需要先启用[数据库模式](/guide/database-mode)。审计使用同一个 DataSource、方言、表前缀和 `service`,写入 `{前缀}mcp_audit` 表(默认 `csap_apidoc_mcp_audit`)。表结构、异步写入、清理和退回规则见 [MCP 的审计日志](/guide/mcp#审计写入数据库)。

## 不用团队版也有用

两项能力都只依赖约定好的 HTTP 协议和表结构,不绑定团队版:你可以自己实现一个接收注册的服务,或者直接用 SQL 查询 `mcp_audit` 表做自己的审计报表。
