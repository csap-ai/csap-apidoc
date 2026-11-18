# Go 版(gin、net/http)

当前版本 v0.2.0,最低 Go 1.22。本文件只适用于 Go 项目;Java 项目见 [java.md](java.md)。共用的 YAML 规则格式见 [yaml-rules.md](yaml-rules.md)。Go 版是仓库 `csap-apidoc-go/` 下的独立 Go 模块,不走 Maven Central。完整说明以官网 <https://apidoc.csap.ai/go/> 和仓库里的 `csap-apidoc-go/examples/`(`gin-basic`、`nethttp-basic`)为准。

动手前先看项目现状:`go.mod` 里有没有 `github.com/csap-ai/csap-apidoc/csap-apidoc-go`、有没有 `apidoc.New`。

## 1. 安装

```bash
go get github.com/csap-ai/csap-apidoc/csap-apidoc-go@v0.2.0
```

根包只依赖标准库;用到 gin 适配、YAML 规则时才会编译对应依赖。SQL 规则来源不引入任何数据库驱动,由项目自己 import。tag 格式是 `csap-apidoc-go/vX.Y.Z`,与 Java 的版本号互相独立。

## 2. 创建 Doc 并挂载(gin)

```go
import (
    "context"

    "github.com/gin-gonic/gin"

    apidoc "github.com/csap-ai/csap-apidoc/csap-apidoc-go"
    ginadapter "github.com/csap-ai/csap-apidoc/csap-apidoc-go/adapter/gin"
    "github.com/csap-ai/csap-apidoc/csap-apidoc-go/strategy"
    "github.com/csap-ai/csap-apidoc/csap-apidoc-go/strategy/yaml"
    "github.com/csap-ai/csap-apidoc/csap-apidoc-go/validate"
)

doc := apidoc.New(apidoc.Options{
    Wrapper:    &apidoc.Wrapper{Type: Result{}},                         // 统一外层包装,可省略
    Strategies: []strategy.Strategy{yaml.Dir("csap-api-doc/method-field")}, // 规则来源,可省略
})
doc.Group(apidoc.GroupInfo{Name: "example.UserController", Title: "用户管理", Path: "/api/v1/users"})
go doc.Watch(context.Background()) // 规则文件改了自动重新加载

r := gin.New()
r.Use(
    ginadapter.ResponseFilter(doc), // 按规则裁剪返回字段
    ginadapter.RequestValidator(validate.New(doc, validate.Options{Mode: validate.Observe})),
)
g := ginadapter.Mount(r, doc) // 挂 /csap/apidoc*、/csap/openapi*、/csap-api.html
g.GET("/api/v1/users/:id",
    apidoc.Op("example.UserController", "get").Summary("查询用户").Req(GetUserReq{}).Resp(User{}),
    getUser)
```

`ginadapter.Mount` 返回的 `Router` 有 `GET/POST/PUT/DELETE/PATCH/Handle/Group/Use`,路由第二个参数是 `*apidoc.Operation`(可传 `nil`)。没有声明的路由也会出现在文档里,只有方法和路径,分组和名字按 handler 函数名推断。

## 3. 创建 Doc 并挂载(net/http)

```go
import nethttpadapter "github.com/csap-ai/csap-apidoc/csap-apidoc-go/adapter/nethttp"

mux := http.NewServeMux()
m := nethttpadapter.Mount(mux, doc)
m.Use(nethttpadapter.ResponseFilter(doc), nethttpadapter.RequestValidator(v)) // 作用于之后注册的路由
api := m.Group("/api/v1/users")
api.GET("/{id}", apidoc.Op("example.UserHandler", "get").Req(GetUserReq{}).Resp(User{}), h.Get)
```

`ServeMux` 不能列出已注册的路由,所以只有通过 `nethttpadapter.Mux` 注册的路由才会出现在文档里,过滤和校验中间件也必须用 `m.Use` 注册。路径参数用 `r.PathValue` 取。

## 4. 声明接口:`apidoc.Op`

Go 的 handler 签名都一样,看不出请求和返回用的是哪个结构体,所以注册路由时用 `apidoc.Op(分组, 接口名)` 声明一次,链式方法:

- `Req(v)`:请求结构体。每个字段的位置按标签决定:`uri` → PATH、`header` → HEADER;与路径参数同名 → PATH;POST / PUT / PATCH 上 `form` → FORM、`json` → BODY,其他方法上都是 QUERY;也可以用 `in:"query|path|header|body|form"` 显式指定。
- `Body(v)`:整个请求体(相当于 Java 的 `@RequestBody`);`BodyName(name)` 改规则路径里的参数名(默认 `request`)。
- `Resp(v)`:业务返回类型,**不含**外层包装,在文档和规则里出现在 `data` 下;`NoWrap()` 表示这个接口不套包装。
- `Summary`、`Description`、`Tags`、`Hidden`、`Deprecated`:接口说明、标签、隐藏、弃用。
- `HeaderHint`、`Auth`、`APIKeyAuth`:在线调试的请求头和鉴权建议(对应 Java 的 `@DocGlobalHeader` / `@DocAuth`),优先级 接口 > 分组 > 全局。全局在 `Options.HeaderHints` / `Options.Auth`,分组在 `GroupInfo`。
- 分组名对应 Java 的 Controller 全名,是规则文件的一级键;接口名是二级键。两者可留空。

## 5. 结构体标签

| 标签 | 对应 Java | 说明 |
|---|---|---|
| `doc:"标题"` | `@ApiModelProperty.value` | 字段说明 |
| `doc_desc`、`doc_example`、`doc_default` | `description`、`example`、`defaultValue` | |
| `doc_required:"true"` | `required` | 也识别 gin 的 `binding:"required"` 和 `validate:"required"` |
| `doc_hidden:"true"` | `hidden` | 文档不显示,也不输出 |
| `doc_force:"true"` | `forceReq` / `forceRep` | 不受规则限制,始终显示和输出 |
| `doc_type:"Long"` | — | 覆盖字段类型显示名 |
| `doc_position:"1"` | `position` | 字段排序 |

没有 `doc` 标签的字段也会进入类型结构,是否显示取决于规则(返回字段要 `include: true` 或 `doc_force`)。模型显示名:`Options.TypeNames`(按 `reflect.Type`)> 类型实现 `ApidocName() string` > Go 类型名。

## 6. 规则来源

`Options.Strategies` 按顺序合并,后面的覆盖前面的,结构体标签总是最先生效:标签 < YAML < SQL。

- YAML:`yaml.Dir(目录)` 读写,`yaml.FS(fsys, 目录)` 从 `go:embed` 只读加载;目录下所有 `.yaml` / `.yml` 都加载,文件名不限;格式见 [yaml-rules.md](yaml-rules.md)。`doc.Watch(ctx)` 监听文件变化,改了自动生效。
- SQL:`strategy/sql`(SQLite / PostgreSQL / MySQL),保存时版本号加 1,多实例轮询(默认 5 秒)或经 Redis 通知后重新加载;表名前缀默认 `csap_apidoc_`。
  - **v0.2.0 起(与 Java 1.4.0 数据库模式共用的表结构;v0.1.x 没有)**:表加 `service` 列(`Options.Service`,默认 `default`,≤128),列名 `rules` / `before_rules` / `after_rules`;方言增加 `sqlstrategy.DM`、`sqlstrategy.Kingbase`(达梦、金仓只做了语句层面的测试,没连真实库,属于试用);`EnsureSchema` 建表,发现 v0.1.x 旧表返回 `ErrLegacySchema` 且不自动改;`Schema()` 返回建表语句;可选 `ServiceNotifier` 让通知带服务名。
  - **从 v0.1.x 升级**:停掉旧实例并备份 → 用 `Options.Service` 指定服务名(旧数据归到它名下)→ `s.Migrate(ctx)`(可重复执行),或 `s.MigrationSQL()` 交 DBA 手工执行。MySQL / 达梦 DDL 隐式提交,中途失败要手工处理。详见官网[数据库模式](https://apidoc.csap.ai/guide/database-mode)和仓库 `csap-apidoc-go/docs/SQL.md`。
  - Redis 规则来源(v0.2.0 起,v0.1.x 没有):`strategy/redis`,`redisstrategy.New(Options{Client, KeyPrefix(默认 csap:apidoc:), Service(默认 default), HistoryLimit(默认 200), Poll(默认 5s)})`,放进 `Options.Strategies`,配合 `doc.Watch(ctx)`;主模块不依赖 Redis 客户端,使用方实现最小 `Client` 接口(`Eval/Get/HGet/HGetAll/LRange/Subscribe`),go-redis v9 适配器见官网 Redis 模式页。YAML 等来源导入 SQL / Redis 用 `strategy.Import(ctx, src, dst, ImportOptions{Overwrite, DryRun})`,默认不覆盖,修改人 `migrate`。

## 7. 返回字段过滤

配置了返回规则的接口,`ResponseFilter` 只输出放行的字段;没有返回规则的接口不过滤。`apidoc.Options` 里:

- `FilterMode`:`filter.ModePath`(默认,路径白名单)、`filter.ModeCsap`(复刻 Java 按类型过滤的行为,从 Java 迁移且要求逐字节一致时用)、`filter.ModeOff`。
- `FilterEndpoints map[string]filter.Mode`:按接口覆盖,键是规则的键 `分组.接口名`(对应 Java 的 `csap.apidoc.runtime.filter.endpoints`)。
- `ShouldFilter func(r *http.Request, status int) bool`:自定义哪些响应要裁剪,默认只裁剪 2xx(4xx/5xx 错误体原样输出,与 Java 一致);业务失败也用 200 返回的项目可以用它排除。
- `ShouldFilterBody func(r *http.Request, status int, body []byte) bool`(**v0.2.0 起**,v0.1.x 没有):按响应体判断是否裁剪,在 `ShouldFilter` 返回 true 之后调用,返回 false 原样输出。现成的 `apidoc.SuccessField("success", true)` / `SuccessField("code", 0)` 按包装字段判断成功;失败体(业务失败也返回 200)不裁剪,与 Java 一致。

行为要点:规则路径要**命中实际返回字段**才过滤,命不中(例如返回类型改了而规则还是旧路径)时原样输出;外层有包装但 `Op` 没写 `Resp` 时,`data` 整段原样输出;流式输出(SSE 等)的接口不要配置返回规则。

## 8. 请求参数校验

```go
v := validate.New(doc, validate.Options{
    Mode:      validate.Observe, // validate.Off(默认)/ Observe / Enforce
    Endpoints: map[string]validate.Mode{"example.UserController.create": validate.Enforce},
})
```

- 规则类型:`NotNull`、`NotEmpty`、`Pattern`(与 Java 相同),以及 Go 新增的 `NotBlank`、`Min`、`Max`、`Length` / `Size`、`Enum`;`validate.Register` 注册自定义规则。`pattern` 可以写内置名 `PATTERN_1`～`PATTERN_44`。
- 其他 `Options`:`TipType`(默认 `TIP_1`)、`MaxBodyBytes`(默认 1MB)、`RequiredCode` / `RequiredMessage`(必填无规则时,默认 `"400"` / `不能为空`)、`OnViolation`(默认 slog Warn)、`Respond`(自定义拦截时的返回,默认与 Java 一致:HTTP 200 + `ApidocResult`)。
- **与 Java 的关系**:非必填字段上写的规则,只要请求里传了这个字段 Go 就会校验。Java 1.4.0 起行为相同(以 Go 为准,两边共用 71 条黄金用例);Java 1.3.0 及以前只执行必填字段上的规则,同一份规则在 Go 上更严格。
- 规则改了,下一个请求就按新规则校验。

## 9. DevTools 后台

```go
import "github.com/csap-ai/csap-apidoc/csap-apidoc-go/devtools"

dt, err := devtools.New(doc, devtools.Options{
    Auth:  devtools.BasicAuth("admin", os.Getenv("DEVTOOLS_PASSWORD")), // 必填
    Realm: "csap-apidoc devtools",
})
ginadapter.MountDevtools(r, dt)         // gin
// nethttpadapter.MountDevtools(mux, dt) // net/http
```

- **默认不开启**,`Auth` 为空返回 `devtools.ErrNoAuth`。鉴权可用 `BasicAuth`、`Token(header, token)`、`AllowLocal`(只允许本机)、`Any(...)` 组合;`AllowAll` 只用于本机开发。建议只在开发、测试环境或内网开启。
- 保存规则写回优先级最高的可写来源(如 YAML 目录),文档、返回过滤和请求校验在下一个请求生效;每次保存都有修改记录,`GET /csap/yaml/history`、`POST /csap/yaml/rollback` 查看和回滚。

## 10. 其他 Options

`Info`(文档首页信息)、`MethodHeaders`(每个接口默认请求头,默认只有 Content-Type)、`DocAuth func(*http.Request) bool`(给文档接口加鉴权,默认不鉴权)、`UIPrefix`(页面静态资源前缀,默认 `/csap-ui`)、`DisableUI`。

## 11. 写完后的检查

1. 启动后访问 `/csap-api.html` 和 `/csap/apidoc`,确认接口和字段。
2. 对照 `csap-apidoc-go/examples/gin-basic`、`nethttp-basic`(在 `csap-apidoc-go` 目录下 `go run ./examples/gin-basic`)。
3. 返回里字段不见了:先查该接口有没有返回规则、规则路径是否命中实际字段,再用 `FilterEndpoints` 把该接口设成 `filter.ModeOff` 对比。
