# csap-apidoc-go 设计

> 状态：草案（2026-11-17）。第 10 节是需要确认的事项，确认后按第 9 节的里程碑实现。

## 1. 定位

这是 CSAP Apidoc 的 Go 实现，开源，协议为 Apache-2.0（与 Java 版相同，沿用仓库根目录的 LICENSE）。它作为 `csap-framework-apidoc` 仓库里的一个子模块，前端源码（`csap-apidoc-ui`、`csap-apidoc-devtools/devtools`）在同一仓库内，构建产物直接内嵌。在 Go 的 Web 项目里提供以下能力：

1. **运行时生成接口文档**。启动时从路由、请求和返回的结构体、字段规则三处收集信息，在内存里生成文档。规则改了不用重启。
2. **控制文档里显示哪些字段**。每个接口的请求参数、返回字段，都可以单独设成在文档中显示或隐藏。
3. **控制实际返回哪些字段**。返回字段按白名单过滤：代码里返回了、但规则没放行的字段，序列化时直接去掉。和 Java 版 `include` 的语义一致。
4. **后台管理**。在线修改字段规则后实时生效，修改有记录，可以回滚，多实例之间自动同步。
5. **协议与 Java 版兼容**。`/csap/apidoc*`、`/csap/yaml/*`、`/devtools/*` 的请求和返回格式与 Java 版一致，现有的 `csap-apidoc-ui`（文档页面）和 `devtools`（管理页面）两套前端可以直接复用。
6. **导出**。支持 OpenAPI 3.1、Postman Collection v2.1，后续加 Markdown。

第一个使用方是 football-data-hub（kyty-data-center 的 Go 重写），它要替换掉现在写死在代码里的返回字段白名单。

## 1.1 Java 基线

Go 版对齐的 Java 版本是 **gitee `csap-framework-apidoc` 的 `chore/apidoc-next-iteration` 分支（b4b9819，2026-04-11）**，即线上实际运行的代码。Go 模块在该分支基础上开 `feat/apidoc-go` 分支开发，本地检出在 `framework/apidoc/`。

2026-11-17 核对过：线上 jar（kyty-data-center-boot，依赖 `csap-apidoc-*-1.0.0`、`csap-validation-*-1.0.0`）里内嵌的 175 个 Java 源码文件，与该分支以及 `1.0.3` 分支逐字节一致，没有一处不同。`csap-apidoc-ui` 的构建产物（`csap-api.html`、js、css）也一致。`1.0.3` 与 `chore/apidoc-next-iteration` 的代码相同，后者只多了 CI 发布流程和英文 README。

`framework/old/csap-framework-apidoc` 是更早的本地副本（停在 2025-11-05），里面有 110 个文件改动没提交，与线上代码有 6 到 21 个文件不同，**不作为基线**。

## 2. 与 Java 版的对应关系

| Java 模块 / 能力 | Go 包 | 说明 |
|---|---|---|
| `csap-apidoc-annotation`（`@Api`、`@ApiOperation`、`@ApiModelProperty`…） | `strategy/tag` | Go 没有注解，改用结构体标签：`doc:"说明"`、`doc_required`、`doc_example`、`doc_hidden`、`doc_force`（对应 `forceRep`） |
| `csap-apidoc-core`（扫描、生成文档模型） | 根包 `apidoc` + `model` | 路由来自框架适配器，类型靠反射，规则来自策略 |
| `csap-apidoc-yaml`（`method-field.yaml`） | `strategy/yaml` | 文件格式与 Java 完全一致，Java 项目的 YAML 可直接拿来用；监听文件变化自动加载 |
| `csap-apidoc-sqlite` | `strategy/sql` | 用 `database/sql`，支持 SQLite、PostgreSQL、MySQL；适合多实例部署和在线修改 |
| `csap-apidoc-standard` | 不另做 | 只用 `strategy/tag` 即可 |
| 返回字段过滤（`validateFactory.response` + `PropertyPreFilter`） | `filter` | 见第 5 节 |
| `csap-validation-core`（请求参数校验） | `validate` | 见第 5.5 节；默认关闭，与 Java 一致 |
| `CsapDocController`（`/csap/apidoc*`） | 根包 HTTP handler | 返回格式与 Java `ApidocResult` 一致 |
| `PostmanConverterService` | `postman` | |
| （Java 无） | `openapi` | 导出 OpenAPI 3.1，给 Swagger UI、Scalar、Redoc 和代码生成工具用 |
| `csap-apidoc-devtools`（`/csap/yaml/*`、`/devtools/*`、`/devtools-ui`） | `devtools` | 接口兼容；新增修改记录和回滚 |
| `csap-apidoc-ui`、`devtools` 前端 | `ui` | 用 `go:embed` 内嵌构建产物，源码仍在 Java 仓库里维护 |

## 3. 核心模型

### 3.1 接口描述

```go
type Endpoint struct {
    Group    string        // 对应 Java Controller(全名作为 key,Title 作为显示名)
    Name     string        // 对应 Java 方法名;Group+"."+Name 即规则的 key(与 YAML 一级/二级键一致)
    Method   string
    Path     string
    Summary  string
    Request  reflect.Type  // 可为 nil;query/path/body 由字段标签 in:"query|path|body|header" 区分
    Response reflect.Type  // 业务返回类型(不含统一外层包装,包装另外注册)
}
```

Go 的 handler 签名都一样（如 `gin.HandlerFunc`），看不出请求和返回用的是哪个结构体。所以每条路由要用下面任一种方式声明一次：

- **注册时声明**（推荐，M1 已实现）：`g.GET("/users/:id", apidoc.Op("UserController", "get").Req(GetUserReq{}).Resp(User{}), h)`，`g` 是 `ginadapter.Mount` 返回的路由包装。`Op` 的分组名、接口名可以留空，留空时按 handler 的函数名推断：`pkg.(*UserController).Get` → 分组 `pkg.UserController`、接口 `Get`。
- **泛型 handler**：`apidoc.Handle[GetUserReq, User](fn)`，从类型参数自动拿到类型。（未实现，排在 M2 之后）
- **只靠配置**：YAML 里写 `requestType` / `responseType`，类型名提前用 `apidoc.RegisterType` 登记。迁移老项目时可以不改路由代码。（未实现，随 M3 YAML 策略做）

没有声明的路由也会出现在文档里，只列出方法和路径，分组和接口名按函数名推断。

### 3.1.1 请求参数怎么映射成 Java 的结构（M1 实现）

Java 的请求是"方法参数列表"，Go 是一个请求结构体，映射规则如下：

- `Req(v)`：逐个字段决定位置，优先级 `in:"..."` > `uri`（PATH）> `header`（HEADER）> 与路径参数同名（PATH）> `form`（GET 为 QUERY，其他方法为 FORM）> 有 `json` 且方法为 POST/PUT/PATCH（BODY）；都没有时 GET/DELETE 为 QUERY，其余为 BODY。匿名嵌入的结构体会展开。
- 非 BODY 字段各自生成一个 `BASE_DATA` 参数（对应 Java 的 `@RequestParam`），`methodParamName` 为参数名，规则路径为 `<名>.<名>`，如 `id.id`，与 kyty YAML 一致。
- BODY 字段合并成一个 `OBJECT` 参数，参数名默认 `request`（`BodyName` 可改），规则路径为 `request.<字段>`。
- `Body(v)`：整个结构体作为请求体（对应 `@RequestBody`），切片时 `modelType` 为 `ARRAY`。

字段类型显示名沿用 Java 写法：`string`→`String`，`int`/`int32`→`Integer`，`int64`/`uint64`→`Long`，`float64`→`Double`，`bool`→`Boolean`，`time.Time`→`Date`，切片→`List<X>`，map→`Map<K, V>`，泛型 `Page[pkg.Item]`→`Page<Item>`；可用 `doc_type` 标签覆盖。

参数节点的 `key` 不再随机生成，而是由接口 key、请求/返回、`keyName` 哈希得到，重启后不变，前端照样能用作节点 ID。

### 3.1.2 结构体标签（M1 实现）

| 标签 | 对应 Java | 说明 |
|---|---|---|
| `doc:"标题"` | `value` | 字段的简短说明 |
| `doc_desc:"..."` | `description` | 不写时与 `doc` 相同（与 Java `getDescription()` 一致） |
| `doc_example`、`doc_default`、`doc_position` | `example`、`defaultValue`、`position` | |
| `doc_required:"true"` | `required` | 也识别 gin 的 `binding:"required"` 和 `validate:"required"` |
| `doc_hidden:"true"` | `hidden` | 文档中不显示 |
| `doc_force:"true"` | `forceReq` / `forceRep` | 不受规则限制，始终显示和输出 |
| `doc_type:"Long"` | — | 覆盖类型显示名 |
| `in:"query\|path\|header\|body\|form"` | `paramType` | 显式指定请求参数位置 |

`json:"-"` 和未导出字段不出现在文档里。

### 3.2 字段规则

规则用路径定位字段，写法与 Java 一致，例如 `data.rows.leagueId`：

```go
type FieldRule struct {
    Path        string
    Description string
    Required    bool
    Include     *bool   // 返回字段:是否输出;nil = 未配置
    Hidden      bool    // 文档中隐藏
    ParamType   string  // 请求参数:QUERY / PATH / BODY / HEADER
    Example     string
    Pattern     string  // 请求校验(可选)
}
```

规则按 **结构体标签 < YAML < SQL** 的顺序合并，后面的覆盖前面的。

### 3.3 Strategy 接口

M1 实现时拆成了一个必选接口和两个可选接口，只读的来源不用实现空的 Save：

```go
type Strategy interface {
    Name() string
    Load(ctx) (Rules, error)   // Rules 的键 = strategy.Key(group, name) = group + "." + name
}
type Saver interface {        // 可写:YAML、SQL、Memory
    Save(ctx, key string, rules MethodRules, by string) (version int64, error)
}
type Watcher interface {      // 能感知外部变更:文件变更 / 轮询版本号 / Redis 通知
    Watch(ctx, onChange func()) error
}
```

`FieldRule` 里的 `Required`、`Include`、`Hidden` 用指针，`nil` 表示这个来源没配，合并时不覆盖。另有 `strategy.Memory`（内存可写策略，带 Watch），用于测试、示例和代码里直接下发规则。

**文档里的显示规则**（与 Java 一致）：某个接口配置了任意返回规则后，返回字段只显示 `include: true` 的和 `doc_force` 的，上级没放行时下级也不显示；配置了任意请求规则后，请求体字段只显示规则里有的和 `doc_force` 的。没有任何规则的接口显示全部字段，这一点比 Java 宽松（Java 没有规则时只显示 `forceRep` 字段），对 Go 项目更友好。

## 4. 运行时流程

1. **启动**：适配器收集路由，与第 3.1 节的声明合并，再按 3.2 合并规则，生成文档模型和过滤计划，原子替换 `atomic.Pointer[snapshot]`。
2. **规则变更**：Watch 回调触发，重新生成快照，再原子替换。正在处理的请求继续用旧快照，不加锁。
3. **多实例**：SQL 策略在保存时把版本号加 1，并发 Redis 通知（可选）；其他实例收到通知后重新加载。没有 Redis 时按配置的间隔轮询版本号（默认 5 秒）。

## 5. 返回字段过滤

### 5.1 语义

两种模式：

- **路径白名单（默认）**：某个接口配置了任意 `include` 规则后，返回 JSON 里只保留 `include: true` 的路径及其上级节点，其余全部去掉。没有配置的接口不过滤。
- **csap 兼容模式**：完全复刻 Java 的行为。按"类"挂过滤器，类型链上每个类各自有自己的 includes；强制输出的字段（如 `forceRep`）始终放行；配置 `response-type=toUnderlineCase` 时，蛇形字段名也自动放行。从 Java 迁移过来时用这个模式，返回结果可以逐字节对齐。

### 5.2 实现

- 每个接口按快照预先编译一棵 **字段掩码树**（`map[string]*node`）。
- 序列化时，由适配器提供的 `Render(c, v)` 或过滤中间件先用 `encoding/json` 编码，然后按掩码树做 token 级裁剪，只遍历一次，不生成中间 map。
- 允许使用方换成自己的编码器（如 fastjson 风格），过滤引擎只处理 JSON 字节流。
- 性能目标：裁剪带来的额外开销不超过编码本身耗时的 30%。在 `filter` 包里写基准测试。

### 5.3 统一外层包装

外层包装（如 `{code,message,data}`）注册一次即可，规则路径以 `data.` 开头，与 Java 的写法保持一致。

## 5.5 请求参数校验（对应 csap-validation）

### 5.5.1 Java 版怎么做的

- **规则**：每个接口的每个参数可以设 `required`，以及若干条 `validate`。每条 `validate` 包含类型（`NotNull` / `NotEmpty` / `Pattern`）、正则、错误码、错误信息和优先级 `level`。规则来源有三种：JSR-303 注解、YAML（写在 `method-field.yaml` 的 `request.<参数>.validate`）、SQLite。
- **后台配置**：devtools 提供以下接口：
  - `/csap/yaml/getMethodValidateFields`：查看某个接口的校验规则。
  - `/csap/yaml/validatePatternTypes`：列出 45 种内置正则，如手机号、身份证、邮箱。
  - `/csap/yaml/writeSelect`：勾选参数和规则后写回 YAML。
- **执行**：`ValidateIntercept` 是一个 `ApiMethodHandle`，在 Spring 解析完方法参数后逐个参数校验。嵌套对象和 List 会递归校验。校验失败抛出 `ValidateException`，`ValidateExceptionHandler` 把它转成 **HTTP 200** 的 `ApidocResult`，`code` 和 `message` 取规则里配置的值。错误信息格式由 `tipType` 决定（`TIP_1` 为 `字段%s,%s` 等 7 种）。参数类型错误另有固定错误码：411、413、400。
- **开关**：只有显式配置 `csap.validation.enabled=true`，`ValidateIntercept` 才会注册（`@ConditionalOnProperty(havingValue="true")`，没有 `matchIfMissing`）。
- **实时性**：规则加载后缓存在内存里。devtools 只在开发环境开启，写的是本地 YAML 文件，所以线上要改规则只能重新发版。

**实测情况**：kyty-data-center 的 YAML 里写了 35 段 `validate` 规则（如 `OutputCoachCareerController.getCoachCareer` 的 `coachId` 必填），但项目没有配置 `csap.validation.enabled`，所以这些规则在线上**从来没有生效过**。

### 5.5.2 Go 版方案

Go 可以完整支持，而且线上也能实时生效。

| 项 | 设计 |
|---|---|
| 执行位置 | **HTTP 中间件，在 handler 之前对原始请求校验**。Go 没有 Spring 那种"参数解析后"的钩子，在原始请求上校验，可以不依赖 handler 的写法，适用于任何框架，规则也能运行时替换。也可以选择在泛型 handler（`apidoc.Handle[Req,Resp]`）绑定之后再校验结构体 |
| 参数位置 | 按规则里的 `paramType` 取值：`QUERY` / `PATH` / `HEADER` / `FORM`，`BODY` 用 JSON 路径（如 `items[].name`）。读请求体时有大小上限，读完放回 `r.Body`，handler 照常能读到 |
| 内置规则 | `NotNull`、`NotEmpty`、`Pattern`，兼容 Java 的 YAML 格式与 45 种内置正则。新增 `min` / `max`、`length`、`enum`、类型校验（int / long / bool / date / datetime，对应 Java 411 的类型错误） |
| 扩展 | `apidoc.RegisterValidator(name, func(v any, rule Rule) error)` 注册自定义规则，如业务字典校验 |
| 失败返回 | 默认与 Java 一致：HTTP 200，`{code, message}` 取规则配置，错误信息格式支持 7 种 `tipType`。外层包装和 HTTP 状态码（如 400）可配置。按 `level` 从小到大校验，遇到第一条失败就返回 |
| 实时 | 校验规则和文档、返回过滤共用同一份快照（第 4 节）。在后台改"哪些参数必填"，几秒内生效，多实例同步，留修改记录可回滚 |
| 文档联动 | `required` 和各条校验规则会显示在文档页面和 OpenAPI 导出里（`required`、`pattern`、`minimum` 等） |
| 开关 | 全局开关、接口级开关，外加**观察模式**：只记录校验失败的请求，不拦截。像 kyty 这种规则写了但从没生效过的项目，先用观察模式跑一段时间，确认不会误拦截正常调用方，再切成拦截模式 |

### 5.5.3 需要注意的地方

1. **正则引擎不同**：Go 的 `regexp` 是 RE2，不支持零宽断言。内置正则里 `PATTERN_43`（邮编）用了 `(?!\d)`，需要改写。
2. **Java 内置正则本身有错**，Go 版修正后补测试：
   - `PATTERN_12`：`[]0-9"*`
   - `PATTERN_21`：正则里带着中文"或"
   - `PATTERN_36`：`{3.4}`
   - `PATTERN_41`：末尾多了 `\n`
   - `PATTERN_27`、`PATTERN_30`、`PATTERN_33`：末尾多了空格
   - `PATTERN_35`：手机号号段过时
3. **已有项目开启校验，行为会改变**：原来能调通的请求，可能开启后被拦截。所以默认关闭，并提供观察模式。
4. **性能**：每个接口的规则预先编译，正则做缓存。只有接口有 BODY 规则时才读请求体。

## 6. HTTP 接口

| 路径 | 说明 | 兼容性 |
|---|---|---|
| `GET /csap/apidoc[?flush=]` | 全量文档 | 与 Java 相同 |
| `GET /csap/apidoc/{group}[?key=]` | 按分组或接口筛选 | 与 Java 相同；**不修改共享缓存**（Java 版有这个缺陷，见附录 A） |
| `GET /csap/apidoc/parent` | 树形菜单 | 与 Java 相同 |
| `GET /csap/apidoc/method?name=&key=` | 单个接口详情 | 与 Java 相同 |
| `GET /csap/openapi/postman` | Postman 导出 | 与 Java 相同（M5） |
| `GET /csap/openapi.json` | OpenAPI 3.1 | 新增 |
| `GET /csap-api.html` | 文档页面 | 复用 csap-apidoc-ui。静态资源从 Java 的 `/assets/*` 改到 `/csap-ui/assets/*`（`Options.UIPrefix`），输出时改写 html/js 里的地址，避免和业务的 `/assets/` 冲突 |
| `/csap/yaml/*`、`/devtools/*`、`/devtools-ui` | 后台管理 | 复用 devtools 前端；新增 `GET /csap/yaml/history`、`POST /csap/yaml/rollback` |

## 7. 安全

- devtools **默认关闭**，需要显式 `EnableDevtools(...)` 才开启，并且必须传入鉴权回调；不传则拒绝启动。
- 文档接口默认对外开放（与 Java 一致），可以通过 `DocAuth` 回调加鉴权。
- 每次修改规则都记录修改人、时间、变更前后内容和版本号。回滚本身也算一次修改，同样留记录。
- 文档内容只来自已登记的路由和规则，不会暴露未声明的内部结构体。

## 8. 目录结构

```
csap-apidoc-go/
├── doc.go, apidoc.go        # 入口:New(opts) / Register / Snapshot
├── model/                   # 文档模型(兼容 Java JSON 字段)
├── strategy/                # Strategy 接口 + tag / yaml / sql 三种实现
├── filter/                  # 返回字段过滤(路径白名单 / csap 兼容)、请求校验
├── openapi/  postman/       # 导出
├── devtools/                # 后台管理接口、修改记录、回滚
├── ui/                      # 内嵌前端构建产物(go:embed)
├── adapter/gin, adapter/nethttp   # 首批适配;echo / chi / fiber 后续
├── examples/gin-basic/
└── docs/
```

依赖原则：根包只依赖标准库。YAML、SQL 驱动、gin 都放在各自的子包里，用户用到哪个才引入哪个。目前是单个 go.mod，gin 写在 require 里（固定 v1.10.1，它要求的 Go 版本不超过 1.22）；只用根包的项目编译时不会编译 gin，但 go.sum 里会有它。如果以后有人在意，再把适配器拆成嵌套模块。

`ui/static` 是 `csap-apidoc-ui` 构建产物的副本（`go:embed` 不能引用模块目录外的文件），用 `scripts/sync-ui.sh` 同步，`--build` 会先重新构建前端。

## 9. 里程碑

| 阶段 | 内容 | 验收 |
|---|---|---|
| M1 核心（**已完成** 2026-11-17） | model、tag 策略、gin 适配、`/csap/apidoc*`、文档页面、OpenAPI 导出 | 示例项目能打开 `/csap-api.html`，`/csap/apidoc` 能被 Java 版前端正常解析 |
| M2 过滤与校验 | 返回字段过滤（路径白名单、csap 兼容模式、外层包装）、请求参数校验（中间件、内置与自定义规则、观察模式）、基准测试 | 用 football-data-hub 的 77 份 YAML 和 Java 的真实返回做黄金样本，逐字节一致 |
| M3 配置 | YAML 策略（读写、热加载）、SQL 策略（版本号、轮询、Redis 通知） | 改 YAML 或数据库后几秒内生效，不重启 |
| M4 管理 | devtools 接口与前端（含必填和校验规则配置）、修改记录、回滚、鉴权 | 用 devtools 页面勾选字段后，返回结果实时变化，并能回滚 |
| M5 发布 | net/http 适配、Postman 导出、中英文 README、CI、v0.1.0 tag | `go get` 可用，测试覆盖率不低于 80% |

## 10. 已确认事项

1. **仓库和模块路径**（已定，2026-11-17）：发布到 **GitHub**，组织 `csap-ai`（`csap-website` 已在这个组织下）。模块路径 `github.com/csap-ai/csap-framework-apidoc/csap-apidoc-go`，第一次推送前如果仓库名有变化，需要同步修改 `go.mod` 和文档。开发期间代码仍放在 gitee `csap-framework-apidoc` 仓库的 `feat/apidoc-go` 分支。发版 tag 格式为 `csap-apidoc-go/vX.Y.Z`。
2. **名称**：目录 `csap-apidoc-go`（与 `csap-apidoc-*` 其他模块保持一致），包名 `apidoc`。
3. **最低 Go 版本**：1.22，需要泛型和新版 ServeMux。
4. **前端**（已定）：内嵌。复用本仓库的 `csap-apidoc-ui` 和 `csap-apidoc-devtools/devtools` 源码，构建产物用 `go:embed` 打进 `ui` 包，不拆成独立仓库。
5. **football-data-hub 何时接入**（已定）：本框架开发完成后，通知 football-data-hub 会话「Java到Go迁移可行性分析 (fork)」，由那边接入：把写死的 `json:"-"` 白名单换成 YAML 配置加过滤中间件，用现有比对样本回归。本框架这边不改 football-data-hub 的代码。

## 附录 A：Java 版已知缺陷（Go 版不复刻）

- `GET /csap/apidoc/{tableName}?key=` 会浅拷贝后直接修改缓存里的 `Controller.methodList`。之后全量文档、树形菜单、方法详情都会永久缺少方法，`flush=true` 也恢复不了，只能重启。实测在 kyty-data-center 上，调用几十次后方法数从 88 降到 46。
