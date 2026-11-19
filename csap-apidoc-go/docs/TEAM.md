# 注册到团队版

团队版(商业产品,独立部署)把多个服务的文档集中到"文档中心",据此拉取各服务的 `/csap/apidoc`。`team` 包是 Go 侧的**服务注册客户端**:服务启动后把自己的地址报给团队版,定时心跳,关闭时注销。协议由仓库 `docs/design/team-integration.md` 第 1 节定义,Java 与 Go 一致。Go 版没有 MCP,所以没有审计相关功能。

**默认关闭**:`Options.URL` 为空时 `Register` 不启动 goroutine、不发任何请求,行为与不引入此包完全相同。不依赖团队版的任何代码。

## 用法

```go
import "github.com/csap-ai/csap-apidoc/csap-apidoc-go/team"

reg := team.Register(ctx, team.Options{
    URL:     "http://team.example.com:8090",
    Token:   os.Getenv("CSAP_TEAM_TOKEN"), // 团队版控制台创建的 REGISTER 令牌,别写进代码
    Service: "order-service",
    BaseURL: "http://10.0.0.5:8080",
    // InstanceID、DocPath、HTTPClient、Logger 可选
})
defer reg.Close()
```

注意 `log.Fatal` / `os.Exit` 不会执行 `defer`;用 `http.Server.Shutdown` 优雅关闭时在其后调用 `reg.Close()`,才能发出注销。

## 参数

| 字段 | 必填 | 说明 |
|---|---|---|
| `URL` | 否 | 团队版地址。为空 = 不启用 |
| `Token` | 否* | Bearer 令牌(*团队版要求,不填则不带 `Authorization`)。不会出现在日志和错误里 |
| `Service` | 否 | 服务名,默认 `default`。见下 |
| `BaseURL` | **是** | 团队版能访问到的服务地址,不带末尾 `/`,含路径前缀。Go 无法可靠得知监听地址,为空时不注册并 warn 一次 |
| `InstanceID` | 否 | 同一服务内唯一,默认取 `BaseURL` 的 `host:port` |
| `DocPath` | 否 | 文档接口路径,默认 `/csap/apidoc` |
| `HTTPClient` | 否 | 自定义 `*http.Client`(代理、TLS 等);每次请求另有 3 秒超时 |
| `Logger` | 否 | `*slog.Logger`,默认 `slog.Default()` |

上报的 `language` 固定为 `go`,`frameworkVersion` 是本模块版本(`internal/version.Version`)。

### 服务名与数据库模式

服务名取值顺序:`Options.Service` → `default`。如果同时用了 SQL 规则来源,请把同一个服务名传给两边,团队版里的服务才和规则库里的 `service` 对得上:

```go
sqlStrat := sqlstrategy.New(sqlstrategy.Options{DB: db, Dialect: sqlstrategy.MySQL, Service: "order-service"})
reg := team.Register(ctx, team.Options{URL: u, Token: t, BaseURL: b, Service: sqlStrat.Service()})
```

`sqlStrat.Service()` 返回生效的服务名(没配置时是 `default`)。`team` 包本身不依赖 `strategy/sql`,不会替你读取它的配置。

## 行为

- **后台进行**:注册、心跳在单个 goroutine 里;`Register` 立即返回,团队版不可达或返回错误既不阻止启动,也不会 panic 到调用方。
- **请求**:`POST {URL}/api/open/v1/services/register`,`Authorization: Bearer {Token}`,JSON 体 `service`、`instanceId`、`baseUrl`、`docPath`、`language`、`frameworkVersion`。响应 `{"heartbeatSeconds": 30}`;之后按该间隔重复发送同一个请求作为心跳。间隔小于 10 / 大于 300 取边界值,缺失或无法解析按 30。
- **超时**:每次请求 3 秒。
- **失败**:按当前心跳周期继续重试。日志降噪:首次失败 `warn`,之后每 10 次连续失败 `warn` 一次,恢复时 `info` 一次。
- **401 / 403**:`warn` 一次"令牌无效或权限不足",之后按 300 秒周期重试;令牌修好后恢复正常并 `info`。
- **注销**:`Close()` 停止心跳并发送 `POST .../services/deregister`(`{"service","instanceId"}`,2 秒超时,失败忽略)。可重复调用;从未注册成功过则不发注销。`ctx` 取消只停止心跳,不发注销。
- **日志与令牌**:日志和错误信息会先抹掉令牌。

## 测试

`go test -race ./team` 用 `httptest` 覆盖请求路径 / 方法 / header / body、心跳间隔、注销、401 降频、服务不可达、日志无令牌。测试会把固定配置发出的请求体写到 `team/testdata/register-request.json`,用于和 Java 客户端的请求体比对(`frameworkVersion` 两边版本号不同)。

## 发布注意

`frameworkVersion` 来自 `internal/version/version.go`。**每次发布(打 `csap-apidoc-go/vX.Y.Z` tag)之前,把 `Version` 改成 `X.Y.Z`。**
