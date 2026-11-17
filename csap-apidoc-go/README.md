# csap-apidoc-go

CSAP Apidoc 的 Go 实现，协议为 Apache-2.0。

- **运行时生成接口文档**：启动时从路由、结构体和字段规则生成文档，规则改了不用重启。
- **控制文档里显示哪些字段**：每个接口的请求参数和返回字段都可以单独设成显示或隐藏。
- **控制实际返回哪些字段**：返回字段按白名单过滤，代码里返回了、但规则没放行的字段不会输出。
- **后台管理**：在线修改字段规则，实时生效，保留修改记录，可以回滚，多实例自动同步。
- **与 Java 版协议兼容**：可直接复用 `csap-apidoc-ui` 文档页面和 `devtools` 管理页面。
- **导出**：支持 OpenAPI 3.1 和 Postman。

> 状态：开发中。M1（文档生成、文档页面、OpenAPI 导出、gin 适配）已完成，返回过滤、请求校验、YAML/SQL 规则、后台管理在后续里程碑。设计见 [docs/DESIGN.md](docs/DESIGN.md)，接手请先读 [docs/HANDOFF.md](docs/HANDOFF.md)。

## 用法（gin）

```go
import (
    apidoc "github.com/csap-ai/csap-apidoc/csap-apidoc-go"
    ginadapter "github.com/csap-ai/csap-apidoc/csap-apidoc-go/adapter/gin"
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
})
doc.Group(apidoc.GroupInfo{Name: "example.UserController", Title: "用户管理", Path: "/api/v1/users"})

r := gin.New()
g := ginadapter.Mount(r, doc) // 挂 /csap/apidoc*、/csap/openapi.json、/csap-api.html
g.GET("/api/v1/users/:id",
    apidoc.Op("example.UserController", "get").Summary("查询用户").Req(GetUserReq{}).Resp(User{}),
    getUser)
```

启动后打开 `/csap-api.html` 查看文档，`/csap/openapi.json` 是 OpenAPI 3.1。完整示例见 [examples/gin-basic](examples/gin-basic/main.go)：

```bash
go run ./examples/gin-basic
```

标签说明、请求参数位置的推断规则见 [docs/DESIGN.md](docs/DESIGN.md) 第 3.1 节。

## 与 Java 版的关系

| Java | Go |
|---|---|
| `@ApiModelProperty` 等注解 | 结构体标签 `doc:"..."` |
| `method-field.yaml`（YAML 策略） | 同格式，可直接使用 |
| SQLite 策略 | SQL 策略（SQLite / PostgreSQL / MySQL） |
| devtools | 接口兼容，新增修改记录和回滚 |

## License

[Apache License 2.0](../LICENSE)
