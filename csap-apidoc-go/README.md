# csap-apidoc-go

CSAP Apidoc 的 Go 实现,协议为 Apache-2.0。与 Java 版(1.1.x)协议兼容。

- **运行时生成接口文档**:启动时从路由、结构体和字段规则生成文档,规则改了不用重启。文档页面与 Java 版是同一份。
- **控制文档里显示哪些字段**:每个接口的请求参数和返回字段都可以单独设成显示或隐藏。
- **控制实际返回哪些字段**:返回字段按白名单过滤,代码里返回了、但规则没放行的字段不会输出。
- **请求参数校验**:必填、正则、长度、范围等,关闭 / 观察 / 拦截三种模式。
- **后台管理**:内嵌 Java 版 DevTools 页面,在线修改字段规则,实时生效,保留修改记录,可以回滚,多实例自动同步。
- **导出**:OpenAPI 3.1 和 Postman。

> 状态:**可用,v0.1.1**。gin 与标准库 net/http 适配;YAML / SQL 规则来源;后台管理。更新见 [CHANGELOG.md](CHANGELOG.md),设计见 [docs/DESIGN.md](docs/DESIGN.md)。完整的接入文档在官网 Go 版页面(仓库 `website/docs/go/index.md`)。

## 安装

```bash
go get github.com/csap-ai/csap-apidoc/csap-apidoc-go@v0.1.1
```

最低 Go 1.22。根包只依赖标准库;SQL 规则来源不引入数据库驱动,由使用方自己 import。

## 用法(gin)

```go
import (
    apidoc "github.com/csap-ai/csap-apidoc/csap-apidoc-go"
    ginadapter "github.com/csap-ai/csap-apidoc/csap-apidoc-go/adapter/gin"
    "github.com/csap-ai/csap-apidoc/csap-apidoc-go/devtools"
    "github.com/csap-ai/csap-apidoc/csap-apidoc-go/strategy"
    "github.com/csap-ai/csap-apidoc/csap-apidoc-go/strategy/yaml"
    "github.com/csap-ai/csap-apidoc/csap-apidoc-go/validate"
)

type GetUserReq struct {
    ID int64 `uri:"id" doc:"用户ID"`
}

type User struct {
    ID   int64  `json:"id" doc:"用户ID" doc_example:"1001"`
    Name string `json:"name" doc:"姓名" doc_desc:"用户的真实姓名"`
}

doc := apidoc.New(apidoc.Options{
    Wrapper: &apidoc.Wrapper{Type: Result{}}, // 统一外层包装 {code,message,data},可选
    // 字段规则:与 Java method-field.yaml 同格式;多实例部署可换成 strategy/sql
    Strategies: []strategy.Strategy{yaml.Dir("csap-api-doc/method-field")},
})
go doc.Watch(ctx) // 规则文件 / 数据库改了自动重新加载,不用重启
doc.Group(apidoc.GroupInfo{Name: "example.UserController", Title: "用户管理", Path: "/api/v1/users"})

r := gin.New()
r.Use(
    ginadapter.ResponseFilter(doc), // 按规则只输出白名单字段
    ginadapter.RequestValidator(validate.New(doc, validate.Options{
        Mode: validate.Observe, // Off(默认) / Observe(只记录) / Enforce(拦截)
    })),
)
g := ginadapter.Mount(r, doc) // 挂 /csap/apidoc*、/csap/openapi.json、/csap/openapi/postman、/csap-api.html
g.GET("/api/v1/users/:id",
    apidoc.Op("example.UserController", "get").Summary("查询用户").Req(GetUserReq{}).Resp(User{}),
    getUser)

// 后台管理 /devtools-ui:默认不开启,必须配置鉴权
ginadapter.MountDevtools(r, devtools.MustNew(doc, devtools.Options{
    Auth:  devtools.BasicAuth("admin", os.Getenv("DEVTOOLS_PASSWORD")),
    Realm: "csap-apidoc devtools",
}))
```

## 用法(标准库 net/http)

```go
import nethttpadapter "github.com/csap-ai/csap-apidoc/csap-apidoc-go/adapter/nethttp"

mux := http.NewServeMux()
m := nethttpadapter.Mount(mux, doc)
m.Use(nethttpadapter.ResponseFilter(doc), nethttpadapter.RequestValidator(v)) // 作用于之后注册的路由
m.GET("/api/v1/users/{id}", apidoc.Op("example.UserHandler", "get").Req(GetUserReq{}).Resp(User{}), h.Get)
nethttpadapter.MountDevtools(mux, dt)
```

完整示例:[examples/gin-basic](examples/gin-basic/main.go)、[examples/nethttp-basic](examples/nethttp-basic/main.go)。

```bash
go run ./examples/gin-basic      # http://localhost:8080/csap-api.html、/devtools-ui
go run ./examples/nethttp-basic  # http://localhost:8081/csap-api.html、/devtools-ui
```

示例的后台默认只允许本机访问;设置 `CSAP_DEVTOOLS_PASSWORD` 后改为 Basic 认证(用户名 admin)。标签说明、请求参数位置的推断规则见 [docs/DESIGN.md](docs/DESIGN.md) 第 3.1 节。

## 接口

| 路径 | 说明 |
|---|---|
| `/csap-api.html` | 文档页面(静态资源在 `/csap-ui/` 下,`Options.UIPrefix` 可改) |
| `/csap/apidoc`、`/csap/apidoc/parent`、`/csap/apidoc/method`、`/csap/apidoc/{分组}` | 与 Java 版相同 |
| `/csap/openapi.json` | OpenAPI 3.1 |
| `/csap/openapi/postman` | Postman Collection v2.1,与 Java 输出一致 |
| `/devtools-ui` | 后台管理页面(需 `devtools.New` 并挂载) |
| `/csap/yaml/*`、`/devtools/*` | 后台管理接口,与 Java DevTools 前端兼容 |
| `/csap/yaml/history`、`/csap/yaml/rollback` | 修改记录与回滚(Go 版新增) |

## 与 Java 版的关系

| Java | Go |
|---|---|
| `@ApiModelProperty` 等注解 | 结构体标签 `doc:"..."` |
| `@DocGlobalHeader`、`@DocAuth` | `Options` / `GroupInfo` / `Operation` 的 `HeaderHint`、`Auth` |
| `method-field.yaml`(YAML 策略) | 同格式,可直接使用 |
| SQLite 策略 | SQL 策略(SQLite / PostgreSQL / MySQL) |
| csap-validation | `validate` 包,默认关闭,另有观察模式 |
| DevTools | 接口兼容,页面相同;新增修改记录和回滚 |

## 开发

```bash
go vet ./... && go test -race ./...
(cd integration && go test -race ./...)  # SQL 策略集成测试,默认 SQLite;CSAP_TEST_PG_DSN / CSAP_TEST_MYSQL_DSN 加测
scripts/sync-ui.sh --ref origin/main     # 从 Java 仓库同步两套前端的构建产物
```

## License

[Apache License 2.0](../LICENSE)
