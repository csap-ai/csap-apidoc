---
title: Go 版
---

# Go 版

CSAP Apidoc 的 Go 实现,是仓库 `csap-apidoc-go/` 目录下的独立 Go 模块,与 Java 版协议兼容:

- **运行时生成接口文档**:启动时从路由、结构体和字段规则生成文档,打开 `/csap-api.html` 就能看,页面与 Java 版是同一份。
- **控制返回哪些字段**:返回字段按规则白名单输出,代码里返回了、但规则没放行的字段不会出现在响应里。文档里显示的字段与实际返回一致。
- **请求参数校验**:必填、正则、长度、范围等规则,支持"只记录不拦截"的观察模式。
- **后台管理**:内嵌 Java 版的 DevTools 页面(`/devtools-ui`),在线勾选字段、配置校验规则,保存后下一个请求就生效,有修改记录,可以回滚。
- **规则文件与 Java 通用**:YAML 规则与 Java 的 `method-field.yaml` 格式相同,Java 项目的规则文件可以直接拿来用。
- **导出**:OpenAPI 3.1(`/csap/openapi.json`)和 Postman Collection(`/csap/openapi/postman`)。

当前版本 **v0.1.1**,最低 Go 1.22,协议 Apache-2.0。源码与设计文档见 [csap-apidoc-go](https://github.com/csap-ai/csap-apidoc/tree/main/csap-apidoc-go)。

## 安装

```bash
go get github.com/csap-ai/csap-apidoc/csap-apidoc-go@v0.1.1
```

根包只依赖标准库;用到 gin 适配、YAML 规则时才会编译对应的依赖。SQL 规则来源不引入任何数据库驱动,由你自己 import。

## 接入 gin

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

// 统一外层包装,对应 Java 的 ApiResult。doc_force 的字段始终输出(对应 forceRep)
type Result struct {
    Code    string `json:"code" doc:"返回编码" doc_force:"true"`
    Message string `json:"message" doc:"返回的描述" doc_force:"true"`
    Data    any    `json:"data" doc:"返回的数据"`
}

type GetUserReq struct {
    ID int64 `uri:"id" doc:"用户ID"`
}

type User struct {
    ID    int64  `json:"id" doc:"用户ID" doc_example:"1001"`
    Name  string `json:"name" doc:"姓名"`
    Phone string `json:"phone" doc:"手机号"`
}

doc := apidoc.New(apidoc.Options{
    Wrapper:    &apidoc.Wrapper{Type: Result{}},
    Strategies: []strategy.Strategy{yaml.Dir("csap-api-doc/method-field")},
})
doc.Group(apidoc.GroupInfo{Name: "example.UserController", Title: "用户管理", Path: "/api/v1/users"})
ctx := context.Background() // 实际项目里用随进程退出而取消的 ctx
go doc.Watch(ctx) // 规则文件改了自动重新加载,不用重启

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

Go 的 handler 签名都一样,看不出请求和返回用的是哪个结构体,所以注册路由时用 `apidoc.Op(分组, 接口名)` 声明一次:

- `Req(v)`:请求结构体,每个字段按标签决定位置:`uri`→PATH、`header`→HEADER;与路径参数同名的字段也是 PATH;POST / PUT / PATCH 上 `form`→FORM、`json`→BODY,其他方法(GET、DELETE 等)上都是 QUERY。也可以用 `in:"query|path|header|body|form"` 显式指定。完整的优先级见 [DESIGN.md](https://github.com/csap-ai/csap-apidoc/blob/main/csap-apidoc-go/docs/DESIGN.md) 3.1.1。
- `Body(v)`:整个请求体(相当于 `@RequestBody`)。
- `Resp(v)`:业务返回类型,不含外层包装;文档和规则里它出现在 `data` 下。
- 分组名对应 Java 的 Controller 全名(规则文件的一级键),接口名对应方法名(二级键)。两者都可以留空,按 handler 的函数名推断。

没有声明的路由也会出现在文档里,只有方法和路径。

结构体标签:

| 标签 | 对应 Java | 说明 |
|---|---|---|
| `doc:"标题"` | `@ApiModelProperty.value` | 字段说明 |
| `doc_desc`、`doc_example`、`doc_default` | `description`、`example`、`defaultValue` | |
| `doc_required:"true"` | `required` | 也识别 gin 的 `binding:"required"` 和 `validate:"required"` |
| `doc_hidden:"true"` | `hidden` | 文档不显示,也不输出 |
| `doc_force:"true"` | `forceReq` / `forceRep` | 不受规则限制,始终显示和输出 |
| `doc_type:"Long"` | — | 覆盖字段的类型显示名(`dataType`) |
| `doc_position:"1"` | `position` | 字段排序 |

结构体(模型)在文档里的显示名默认是 Go 类型名(泛型显示成 `Page<Item>`),相当于 Java `@ApiModel` 的 `value`。有两种方式覆盖,优先级 `Options.TypeNames` > `ApidocName()` > 类型名:

```go
// 方式一:类型实现可选接口 apidoc.ModelNamer(值或指针接收者都可以,在零值上调用)
func (Page[T]) ApidocName() string { return "分页返回信息" }

// 方式二:在 Options 里按类型指定,适合改不了源码的类型
doc := apidoc.New(apidoc.Options{
    TypeNames: map[reflect.Type]string{reflect.TypeOf(User{}): "用户信息"},
})
```

覆盖只影响模型的显示名,字段的 `dataType`(如 `List<User>`)仍按类型名生成。

## 接入标准库 net/http

基于 Go 1.22 的 `http.ServeMux` 路由模式:

```go
import nethttpadapter "github.com/csap-ai/csap-apidoc/csap-apidoc-go/adapter/nethttp"

mux := http.NewServeMux()
m := nethttpadapter.Mount(mux, doc)
// 中间件作用于之后注册的路由
m.Use(
    nethttpadapter.ResponseFilter(doc),
    nethttpadapter.RequestValidator(validate.New(doc, validate.Options{Mode: validate.Enforce})),
)
api := m.Group("/api/v1/users")
api.GET("/{id}", apidoc.Op("example.UserHandler", "get").Req(GetUserReq{}).Resp(User{}), h.Get)
api.POST("", apidoc.Op("example.UserHandler", "create").Body(CreateUserReq{}).Resp(User{}), h.Create)
// 也可以写完整模式:m.HandleFunc("GET /healthz", nil, healthz)

http.ListenAndServe(":8080", mux)
```

路径参数用 `r.PathValue` 取。`ServeMux` 不能列出已注册的路由,所以只有通过 `Mux` 注册的路由会出现在文档里;过滤和校验中间件也要用 `m.Use` 注册(需要知道请求命中了哪条路由)。

完整示例:仓库里的 `examples/gin-basic`、`examples/nethttp-basic`,在 `csap-apidoc-go` 目录下执行 `go run ./examples/gin-basic` 后打开 `http://localhost:8080/csap-api.html`。

## 字段规则(YAML,与 Java 兼容)

```yaml
# csap-api-doc/method-field/UserController-method-field.yaml
example.UserController:          # 分组(Java 的 Controller 全名)
  get:                           # 接口(Java 的方法名)
    response:
      data.id:   {include: true}
      data.name: {include: true, description: 姓名}
  create:
    request:
      request.name:
        required: true
        validate:
          - {type: Length, pattern: "2,20", code: "1002", message: 姓名长度 2-20, level: 1}
      request.phone:
        validate:
          - {type: Pattern, pattern: PATTERN_35, code: "1003", message: 手机号格式不正确}
```

- 格式与 Java `method-field.yaml` 完全相同,Java 项目的文件可以直接用;Java 写出的 `code: 1001`(数字)、`-` 单独一行的数组写法都认。
- 规则路径与 Java 一致:返回字段是 `data.xxx`,请求体字段是 `request.xxx`(参数名可以用 `BodyName` 改),单值参数是 `参数名.参数名`(如 `id.id`)。路径同时认驼峰和下划线写法。
- 目录下所有 `.yaml/.yml` 都会加载,文件名不要求与分组对应。`yaml.FS(fsys, dir)` 可以从 `go:embed` 只读加载。
- Go 版新增的 `hidden`(只在文档隐藏)、`example` 字段,Java 读取时会忽略。
- 多实例部署、需要在线修改时,可以换成 SQL 规则来源 `strategy/sql`(SQLite / PostgreSQL / MySQL),保存时版本号加 1,其他实例轮询或经 Redis 通知后重新加载。
- v0.2.0 起 SQL 规则来源的表结构与 Java 1.4.0 的[数据库模式](/guide/database-mode)统一,见下面的 [SQL 规则来源(v0.2.0)](#sql-规则来源v020)。Redis 作为规则存储见 [Redis 模式](/guide/redis-mode)(`strategy/redis`,v0.2.0);把 YAML 等来源导入 SQL / Redis 用 `strategy.Import`,见[数据库模式 - 迁移](/guide/database-mode#迁移)。

规则来源按 **结构体标签 < YAML < SQL** 的顺序合并,后面的覆盖前面的。

### SQL 规则来源(v0.2.0)

:::warning 随 Go v0.2.0 发布
本节描述 v0.2.0 的行为,当前已发布版本是 v0.1.1(表没有 `service` 列)。
:::

SQL 规则来源 `strategy/sql` 改用与 Java 数据库模式相同的三张表(`csap_apidoc_rule` / `history` / `meta`,列名、建表 SQL 见[数据库模式](/guide/database-mode#建表-sql)),Go 和 Java 服务可以指向同一个库,用 `service` 列区分。

```go
s := sqlstrategy.New(sqlstrategy.Options{
    DB:      db,
    Dialect: sqlstrategy.MySQL, // SQLite / Postgres / MySQL / DM / Kingbase
    Service: "order-service",   // 服务名,默认 "default",最长 128;所有读写都按它过滤
})
if err := s.EnsureSchema(ctx); err != nil { /* ... */ }
```

- **方言**:新增 `DM`(达梦 DM8)、`Kingbase`(人大金仓,按 PostgreSQL 兼容模式;MySQL 兼容模式的库用 `MySQL` 方言试用,Oracle 兼容模式未验证、暂不支持)。达梦、金仓目前只有语句层面的单元测试,没有连真实数据库验证,属于试用。MySQL 有集成测试。
- `EnsureSchema` 逐表探测,不存在才建;发现 v0.1.x 的旧表时返回 `sqlstrategy.ErrLegacySchema`,**不会自动改旧表**。`Schema()` 返回建表语句,想自己执行(或交给 DBA)时用。
- 各服务的规则、版本号、修改记录、回滚互不影响;回滚别的服务的修改记录会返回 `strategy.ErrNoChange`。
- 通知:`Notifier`(如 Redis Pub/Sub)接口签名不变,消息不带服务名,多个服务共用一个频道时收到通知的实例多查一次本服务的版本号,结果正确。想避免多余查询,让通知实现同时实现 `ServiceNotifier`(`PublishService` / `SubscribeService`,消息带服务名),策略会优先使用它。

**从 v0.1.x 升级**:旧表没有 `service` 列,`meta` 是 `id = 1` 的单行。

1. 停掉所有旧版本实例,**备份数据库**;
2. 用 `Options.Service` 指定服务名(没填是 `default`),旧数据全部归到它名下;
3. 执行 `s.Migrate(ctx)`,可以重复执行;或者用 `s.MigrationSQL()` 取出 SQL 交给 DBA 手工执行(各方言的 SQL 见 [SQL.md](https://github.com/csap-ai/csap-apidoc/blob/main/csap-apidoc-go/docs/SQL.md))。SQLite / PostgreSQL / 金仓的 DDL 在事务里,失败整体回滚;MySQL 和达梦的 DDL 会隐式提交,中途失败要手工处理。

## 返回字段过滤

接口配置了返回规则后,`ResponseFilter` 只输出放行的字段;没有返回规则的接口不过滤、不缓冲。模式(`apidoc.Options.FilterMode`):

| 模式 | 说明 |
|---|---|
| `filter.ModePath`(默认) | 路径白名单:只输出 `include: true` 的路径、`doc_force` 字段,以及有下级被放行的上级字段。被放行的字段没有下级规则时整段输出。 |
| `filter.ModeCsap` | 完整复刻 Java 按类型过滤的行为(fastjson `SimplePropertyPreFilter`),包括同一类型在不同路径上规则互相影响等细节。从 Java 迁移、要求返回与 Java 逐字节一致时用它。 |
| `filter.ModeOff` | 不过滤。可以全局关闭,也可以只在 `FilterEndpoints` 里关掉个别接口。 |

```go
doc := apidoc.New(apidoc.Options{
    FilterMode: filter.ModePath,
    // 按接口覆盖模式,键为规则的键"分组.接口名"(对应 Java csap.apidoc.runtime.filter.endpoints)
    FilterEndpoints: map[string]filter.Mode{"example.UserController.export": filter.ModeOff},
    // 可选:自定义哪些响应要裁剪,默认只裁剪 2xx
    ShouldFilter: func(r *http.Request, status int) bool { return status == http.StatusOK },
})
```

- **只裁剪 2xx**:与 Java 只过滤控制器正常返回的对象一致,4xx/5xx 的错误体(参数错误、异常处理中间件写的失败体等)原样输出。业务失败也用 200 返回的项目,可以用 `ShouldFilter` 按请求或状态码进一步排除;失败信息在响应体里(`success=false`、`code≠0` 等)时,用下面 v0.2.0 的 `ShouldFilterBody`。
- **按响应体判断(v0.2.0)**:`Options.ShouldFilterBody func(r *http.Request, status int, body []byte) bool` 在响应已缓冲、裁剪之前调用,返回 `false` 时这次响应原样输出,不用再自写缓冲响应的中间件,效果与 Java 只过滤控制器正常返回的对象一致。先调用 `ShouldFilter`(粗筛),它返回 `true` 才会调用 `ShouldFilterBody`;两者都不设置时与 v0.1.1 相同(只裁剪 2xx)。现成的判断 `apidoc.SuccessField(path, want)` 按包装里某个字段判断成功,`path` 用点号分隔:

```go
doc := apidoc.New(apidoc.Options{
    Wrapper: &apidoc.Wrapper{Type: Result{}},
    ShouldFilterBody: apidoc.SuccessField("success", true), // {"success":false,...} 原样输出
})
// 也可以:SuccessField("code", 0)、SuccessField("code", "0000")、SuccessField("result.code", 1)
```

  响应体不是 JSON 对象、没有这个字段或字段为 null 时判断不了,按"裁剪"处理,不会因为格式不符漏过滤。更复杂的判断(多个字段、白名单错误码)自己写钩子;自定义适配器在裁剪前调用 `Doc.ShouldFilterBody(r, status, body)`。该功能随 Go v0.2.0 发布,v0.1.1 没有。
- **规则要命中返回字段**:与 Java 1.2.0 一致,只有至少一条返回规则命中了实际的返回字段路径(沿放行的字段展开),接口才会过滤。规则路径过期、和返回类型对不上(例如返回改成了 `Page<Item>`,规则还是 `data.title`,少了 `rows` 一层)时原样输出,而不是把字段全部裁掉。改正规则路径后即按规则过滤。
- **没声明 `Resp`**:有统一外层包装、但 `Op` 没有 `Resp(...)` 时,`data` 的类型未知,整段原样输出;外层包装上的规则照常生效。

裁剪在 JSON 字节流上进行,保持原有键顺序,不依赖用的是哪个 JSON 编码器。流式输出(SSE 等)的接口不要配置返回规则。

## 请求参数校验

```go
v := validate.New(doc, validate.Options{
    Mode: validate.Observe,                                       // 全局模式
    Endpoints: map[string]validate.Mode{"example.UserController.create": validate.Enforce}, // 按接口覆盖
})
```

| 模式 | 行为 |
|---|---|
| `validate.Off`(默认) | 不校验,与 Java `csap.validation.enabled` 默认不开一致 |
| `validate.Observe` | 校验失败只调用 `OnViolation` 记录(默认 slog Warn),放行请求。规则写了但从没生效过的项目,建议先用它跑一段时间 |
| `validate.Enforce` | 校验失败直接返回,不进业务 handler |

- 规则类型:Java 的 `NotNull`、`NotEmpty`、`Pattern`,以及 Go 版新增的 `NotBlank`、`Min`、`Max`、`Length`/`Size`、`Enum`;`validate.Register` 注册自定义规则。
- `pattern` 可以写内置规则名(`PATTERN_1` 到 `PATTERN_44`,如 `PATTERN_35` 手机号)。Java 内置正则里写错的几条已修正;Java 写法的 `\uXXXX` 会自动转换,中文正则可以直接用。
- `Violation.Field` 是出错的字段:路径、查询、请求头、表单参数为参数名(如 `updated_at`,与 Java 一致),请求体字段为规则路径(如 `request.items.name`)。
- 失败返回默认与 Java 的 `ApidocResult` 一致:HTTP 200,body 含 `code`、`message`、`success:false`、`language`、`time`,没有 `data`;`code`、`message` 取规则配置,信息格式按 `TipType`;`Options.Respond` 可以换成自己的格式。
- 规则改了,下一个请求就按新规则校验。
- **与 Java 的差别**:非必填字段上写的规则,只要请求里传了这个字段,Go 也会按规则校验;Java 1.2.0 只执行必填字段上的规则(见[参数校验](/guide/validation))。所以同一份规则在 Go 上可能更严格。两边是否对齐、以哪边为准,计划在 1.4.0 前确定。

## 后台管理

```go
import "github.com/csap-ai/csap-apidoc/csap-apidoc-go/devtools"

dt, err := devtools.New(doc, devtools.Options{
    Auth:  devtools.BasicAuth("admin", os.Getenv("DEVTOOLS_PASSWORD")), // 必填
    Realm: "csap-apidoc devtools",                                     // 浏览器弹出登录框
})
if err != nil { ... }
ginadapter.MountDevtools(r, dt)       // gin
// nethttpadapter.MountDevtools(mux, dt) // net/http
```

打开 `/devtools-ui`:选中接口,勾选返回字段、设置请求参数的必填和校验规则,保存后规则写回规则来源(默认是优先级最高的可写来源,如 YAML 目录),文档、返回过滤和请求校验在下一个请求生效。

- **默认不开启**:不调用 `devtools.New` 并挂载就没有后台;`Auth` 不传会返回错误。可用 `BasicAuth`、`Token`(网关注入的请求头)、`AllowLocal`(只允许本机)、`Any` 组合,或接自己的登录态。`AllowAll` 只用于本机开发。建议只在开发、测试环境或内网开启。
- **与 Java 前端兼容**:`/csap/yaml/*`、`/devtools/*` 接口的请求和返回格式与 Java 版一致,页面就是 Java 版的 DevTools 前端。
- **修改记录与回滚**:每次保存都记录修改人、时间、修改前后的规则。`GET /csap/yaml/history?className=&methodName=` 查看,`POST /csap/yaml/rollback {"id": 记录ID}` 恢复到该次修改之前(回滚本身也留记录)。YAML 规则的记录默认写在规则目录下的 `.csap-apidoc-history.jsonl`,SQL 规则写在 `<表前缀>history` 表(默认 `csap_apidoc_history`)。

## 在线调试的请求头和认证建议

对应 Java 1.1.0 的 `@DocGlobalHeader`、`@DocAuth`,文档页面"在线调试"时自动预填,不影响后端处理:

```go
import "github.com/csap-ai/csap-apidoc/csap-apidoc-go/model"

doc := apidoc.New(apidoc.Options{
    HeaderHints: []model.GlobalHeaderHint{{Name: "X-Tenant-Id", Example: "1", Required: true}}, // 全局
    Auth:        apidoc.NewAuthHint("bearer", "登录令牌", "", ""),
})
doc.Group(apidoc.GroupInfo{Name: "...", Auth: apidoc.NewAuthHint("basic", "", "", "")}) // 分组
apidoc.Op("...", "get").HeaderHint("X-Trace-Id", "链路 ID", "abc", false).APIKeyAuth("query", "token", "") // 接口
```

优先级为接口 > 分组 > 全局,请求头按名字合并。

## 文档接口

| 路径 | 说明 |
|---|---|
| `/csap-api.html` | 文档页面(静态资源在 `/csap-ui/assets/` 下,前缀可用 `Options.UIPrefix` 改,避免与业务的 `/assets/` 冲突) |
| `/csap/apidoc`、`/csap/apidoc/parent`、`/csap/apidoc/method`、`/csap/apidoc/{分组}` | 与 Java 版相同 |
| `/csap/openapi.json` | OpenAPI 3.1 |
| `/csap/openapi/postman` | Postman Collection v2.1,与 Java 输出一致 |

文档接口默认对外开放(与 Java 一致),需要时用 `Options.DocAuth` 加鉴权。

## 已知限制

- 目前只有 gin 和标准库 net/http 适配器;echo、chi、fiber 等在后续版本。
- 返回过滤需要缓冲整个响应,额外开销约为 JSON 编码耗时的一半;流式输出的接口不要配置返回规则。
- 规则来源按字段合并:后台写入 SQL 时,YAML 里已放行的字段无法通过 SQL"取消放行"。建议同一个接口的规则只放在一个可写来源里。
- 后台管理不提供 Java 版的"新增接口方法"、`hiddenMethod`(Java 也是空实现)等依赖 Java 代码生成的功能;`/csap/yaml/writeSelect` 改为"给还没有规则的接口按当前代码生成一份完整规则"。
- 文档页面里"在线调试"的跨域代理(Java 的 `/csap/apidoc/devtools/proxy`)尚未提供,调试同域接口不受影响。
- SQL 规则来源:v0.1.1 在 SQLite、PostgreSQL 上做过并发与多实例测试,MySQL 只实现了方言,尚未实测;v0.2.0 起 MySQL 有集成测试,达梦、金仓没有连真实数据库验证。
