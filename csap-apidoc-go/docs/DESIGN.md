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

**2026-11-17 起（版本与分支统一后）**：唯一主线是 GitHub `csap-ai/csap-apidoc` 的 `main`，Go 版对齐它，在 `feat/apidoc-go` 分支（基于 `main`）开发。统一经过见 [版本与分支统一方案.md](版本与分支统一方案.md)。

`main` 包含线上生产代码（仓库首个提交 0fcb901 = 下文的 gitee `chore/apidoc-next-iteration`，只差两个 IDE 文件），以及之后 14 次提交的新功能。对文档协议的影响只有 `CsapDocMethod` 新增 `globalHeaderHints`、`authHint` 两个字段，Go 版待补（见统一方案 2.4）。

以下为统一前的记录：Go 版最初对齐的 Java 版本是 **gitee `csap-framework-apidoc` 的 `chore/apidoc-next-iteration` 分支（b4b9819，2026-04-11）**，即线上实际运行的代码（现存档为 gitee tag `archive/chore-apidoc-next-iteration`）。

2026-11-17 核对过：线上 jar（kyty-data-center-boot，依赖 `csap-apidoc-*-1.0.0`、`csap-validation-*-1.0.0`）里内嵌的 175 个 Java 源码文件，与该分支以及 `1.0.3` 分支逐字节一致，没有一处不同。`csap-apidoc-ui` 的构建产物（`csap-api.html`、js、css）也一致。`1.0.3` 与 `chore/apidoc-next-iteration` 的代码相同，后者只多了 CI 发布流程和英文 README。

`framework/old/csap-framework-apidoc` 是更早的本地副本（停在 2025-11-05），未提交的草稿已存档为 gitee tag `archive/old-local-drafts-2025-11`，**不作为基线**。

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

### 3.4 YAML 策略（M3 已实现，`strategy/yaml`）

- `yaml.Dir(dir)` 读写目录；`yaml.FS(fsys, dir)` 从 `go:embed` 等只读加载（对应 Java 从 classpath 读）。
- 格式与 Java `method-field.yaml` 完全相同，Java 项目的文件可以直接拿来用。kyty 的 77 份文件全部能解析（163 个接口，请求规则 457 条、返回规则 2658 条、校验规则 35 条），每个接口原样写回再加载，结果不变（`TestRealProjectFiles`，默认跳过）。
- 加载目录下所有 `.yaml/.yml`，文件名不要求与分组对应（kyty 就有 `CoachController-method-field.yaml` 里放 `OutputCoachController` 的情况）；同一分组出现在多个文件时按文件名排序取第一个，与 Java 合并规则一致。某个文件解析失败只跳过它，其他文件照常生效。
- 兼容 Java 写出的细节：`code: 1001`（数字）和 `code: "1001"` 都认；`INDENT_ARRAYS` 风格的 `-` 单独一行也认。Go 版新增的 `hidden`、`example` 字段，Java 读取时会忽略（`FAIL_ON_UNKNOWN_PROPERTIES=false`）。
- 写入（`Save`）：改包含该分组的那个文件，只替换该接口那一段，其他接口和注释保留；分组不存在时新建 `<简单名>-method-field.yaml`；空规则表示删除该接口。先写临时文件再改名，不会写出半个文件。字段按路径长度、再按字母排序，上级在前。
- 监听：每 `Poll`（默认 2 秒）比较文件名、大小、修改时间；本策略自己的写入立即通知。基准是**上次加载时的签名**，所以加载之后、开始监听之前发生的修改也不会漏（M3 修过这个 bug，有回归测试）。

### 3.5 SQL 策略（M3 已实现，`strategy/sql`）

- `database/sql`，方言 `SQLite` / `Postgres` / `MySQL`（只影响占位符和建表语句）。主模块**不引入任何驱动**，由使用方自己 import。
- 三张表（前缀默认 `csap_apidoc_`）：`rule`（规则 JSON、版本、修改人、时间）、`history`（每次保存的修改前后内容）、`meta`（全局版本号）。`EnsureSchema` 建表，`Schema()` 返回语句供自己执行。与 Java SQLite 策略的表结构不同（Java 存的是整份文档），不共用。
- `Save` 在一个事务里：全局版本号加 1（PostgreSQL / MySQL 锁住 meta 行，并发保存串行化）、写规则、写修改记录。`History(key, limit)` 查记录，`Rollback(id, by)` 恢复成该次修改前的样子，回滚本身也留记录。M4 的后台管理直接用这两个接口。
- 多实例：每 `Poll`（默认 5 秒）查一次全局版本号，变了就重新加载；可选 `Notifier`（如 Redis Pub/Sub）保存后立即通知其他实例。基准同样是上次加载时的版本号。
- SQLite 注意：多个连接并发写时，连接串要加 `_txlock=immediate`（事务开始就拿写锁）和 `busy_timeout`，否则会偶发 `database is locked`。
- 实测：SQLite（纯 Go 驱动）和 PostgreSQL 16（临时容器）各跑 15 遍、开竞态检测，全部通过，包括两个实例共用一个库时的轮询同步、只靠通知的同步。**MySQL 只写了方言，没有实测**（本机没有镜像）。
- 集成测试在独立模块 `integration/`（`cd integration && go test ./...`；设置 `CSAP_TEST_PG_DSN` / `CSAP_TEST_MYSQL_DSN` 加测），驱动依赖只在那里。

Redis 通知的实现示例（go-redis v9，放在使用方代码里）：

```go
type redisNotifier struct{ rdb *redis.Client }

func (n redisNotifier) Publish(ctx context.Context, v int64) error {
    return n.rdb.Publish(ctx, "csap:apidoc:rules", v).Err()
}

func (n redisNotifier) Subscribe(ctx context.Context, fn func(int64)) error {
    sub := n.rdb.Subscribe(ctx, "csap:apidoc:rules")
    defer sub.Close()
    for {
        select {
        case <-ctx.Done():
            return ctx.Err()
        case msg := <-sub.Channel():
            v, _ := strconv.ParseInt(msg.Payload, 10, 64)
            fn(v)
        }
    }
}
```

## 4. 运行时流程

1. **启动**：适配器收集路由，与第 3.1 节的声明合并，再按 3.2 合并规则，生成文档模型和过滤计划，原子替换 `atomic.Pointer[snapshot]`。
2. **规则变更**：Watch 回调触发，重新生成快照，再原子替换。正在处理的请求继续用旧快照，不加锁。
3. **多实例**：SQL 策略在保存时把版本号加 1，并发 Redis 通知（可选）；其他实例收到通知后重新加载。没有 Redis 时按配置的间隔轮询版本号（默认 5 秒）。
4. **启动监听**：`go doc.Watch(ctx)`，会监听所有实现了 `strategy.Watcher` 的策略（YAML、SQL、Memory）。

## 5. 返回字段过滤（M2 已实现）

### 5.1 语义

两种模式（`Options.FilterMode`）：

- **路径白名单 `filter.ModePath`（默认）**：接口配置了任意返回规则后，只输出 `include: true` 的路径、`doc_force` 字段，以及有下级被放行的上级字段，其余去掉。某个被放行的字段如果没有任何下级规则，它的值整段输出。没有返回规则的接口不过滤，也不缓冲。文档页面显示的返回字段和实际输出完全一致（规则 `hidden: true` 只在文档里隐藏，不影响输出；`doc_hidden` 标签的字段既不显示也不输出，与 Java `hidden` 一致）。
- **csap 兼容模式 `filter.ModeCsap`**：完全复刻 Java 的 `FilterClassParam` + fastjson `SimplePropertyPreFilter`：
  - 沿返回类型链遍历字段，放行的字段（`doc_force` 或 `include: true`）进入所在类型的白名单；没放行的，只要当时还不在白名单里，就进入黑名单。只有放行的字段才继续展开。
  - 同一类型出现在多个路径时，规则合并到同一个过滤器。
  - 输出时**先查黑名单**再查白名单：白名单非空只输出白名单；白名单为空则输出黑名单之外的全部。
  - 由此产生的 Java 怪异行为也一并复刻，例如同一类型先在 A 路径没放行 `name`、后在 B 路径放行 `name`，结果两处都不输出 `name`（见 `mask_test.go`）。
  - 不会自动放行上级：上级没写 `include: true`，下级规则无效。
- 两种模式下，规则路径都同时认驼峰和下划线写法（Java 规则用 Java 字段名 `data.rows.leagueId`，返回 JSON 是 `league_id`），对应 Java 的 `response-type: toUnderlineCase` 别名。
- 两种模式都只对**配置了返回规则的接口**生效。Java 对扫描到的接口一律挂过滤器（没规则时只剩 `forceRep` 字段），Go 版不这样做，避免误伤没配规则的接口。

**黄金样本验证（2026-11-17）**：用 football-data-hub 的比对环境启动 Java 线上 jar，抓了 79 个 kaiyun 接口的真实返回。Java 序列化开了 `WriteMapNullValue`，白名单字段即使为 null 也会输出，所以每个对象的键集合就是它的白名单。`filter/golden_test.go` 逐个对象比较 Go 掩码放行的键和 Java 实际输出的键，**79 个接口全部一致**；另外验证了 Prune 不改动 Java 返回，并能去掉额外注入的字段。数据属于使用方，不进仓库，测试默认跳过，运行方法见测试文件注释。

### 5.2 实现

- 构建快照时，每个接口编译一棵**字段掩码树**（`filter.Node`），放在 `Snapshot.Endpoint(...).ResponseMask`。
- 运行时由中间件（`ginadapter.ResponseFilter(doc)`）缓冲业务输出，按掩码在字节流上裁剪（`filter.Prune`）：只遍历一次，保持原有键顺序和格式，不生成中间 map，不依赖编码器，换成 fastjson 风格的编码器也能用。不是合法 JSON 时原样输出并记日志。
- **性能**（`filter/bench_test.go`，100 行、约 18KB 的返回，Apple M 系列）：`encoding/json` 编码 67µs；裁剪 37µs，只有 1 次内存分配。即额外开销约为编码耗时的 **50%**，没达到原定的 30%。剩下的开销分散在逐字节扫描上；要再降，需要改成序列化前按结构体过滤（只编码放行的字段，反而比不过滤还快），列为后续优化。
- 流式输出（SSE 等）的接口不要配置返回规则：缓冲期间 `Flush` 不生效。

### 5.3 统一外层包装

`Options.Wrapper` 注册一次（如 `{code,message,data}`），`DataField`（默认 `data`）在文档和掩码里替换成各接口的 `Resp` 类型，规则路径以 `data.` 开头，与 Java 的写法保持一致。外层包装里需要始终输出的字段打 `doc_force:"true"`（对应 Java `forceRep`），没打的（如 `success`）在接口有规则时会被去掉，与 Java 一致。

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

### 5.5.2 Go 版方案（M2 已实现）

| 项 | 实现 |
|---|---|
| 执行位置 | 中间件，在业务 handler 之前对原始请求校验：`ginadapter.RequestValidator(validate.New(doc, opts))`；其他框架用 `Validator.Handle` / `Validator.Middleware` |
| 校验项 | 构建快照时从结构体标签和请求规则算出每个接口的 `FieldCheck` 列表（位置、名字或 JSON 路径、必填、规则、类型），放在 `Endpoint.Checks`。规则里 `hidden: true` 的字段只在文档里隐藏，照常校验 |
| 取值 | `PATH` 由适配器传入（gin 用 `c.Param`），`QUERY`、`HEADER`、`FORM` 按名字取，`BODY` 按 JSON 路径取，经过数组时对每个元素校验。上级对象为 null 时不校验下级（与 Java 一致）。请求体最多读 `MaxBodyBytes`（默认 1MB），读完放回 `r.Body`；超限、不是 JSON、multipart 时跳过请求体字段，交给 handler |
| 规则类型 | 与 Java 相同的 `NotNull`、`NotEmpty`、`Pattern`；新增 `NotBlank`、`Min`、`Max`、`Length`/`Size`（`"2,20"`、`"2,"`）、`Enum`（`"a,b,c"`）；`pattern` 可以写内置规则名（如 `PATTERN_35`）；`validate.Register(name, fn)` 注册自定义规则 |
| 正则 | 与 Java `matches()` 一样整串匹配，自动加 `^(?:…)$`；编译结果缓存 |
| 类型校验 | 单值参数和请求体里的基本类型字段，按 `Integer`/`Long`/`Double`/`Float`/`Boolean` 检查格式，失败返回 `400`、`<说明>,数据格式错误`（对应 Java `MethodArgumentTypeMismatch` 和 fastjson `DeserializerValidate`） |
| 顺序 | 按字段顺序，每个字段内按 `level` 从小到大，第一条失败即返回 |
| 失败返回 | 默认与 Java 一致：HTTP 200，`ApidocResult{code, message}`（Java 路径 B 的格式：tab 缩进、`success:false`、无 `data`），`code`、`message` 取规则配置，信息格式按 `TipType`（7 种，默认 `TIP_1`：`字段username,用户名不能为空`）。`Options.Respond` 可以换成自己的外层包装和 HTTP 状态码 |
| 模式 | `Off`（默认，与 Java 一致）、`Observe`（只调用 `OnViolation` 记录，默认 slog Warn，放行请求）、`Enforce`（拦截）；`Endpoints` 按接口覆盖 |
| 实时 | 校验项在快照里，规则变更后下一个请求即生效（测试 `TestRulesHotReload`） |

**与 Java 不同的地方（有意为之）**：

- Java 只在 `required: true` 时才执行 `validate` 规则，而只写 `required: true`、不写 `validate` 时**什么都不校验**（`addRequestValidate` 在规则列表为空时直接返回）。Go 版：`required: true` 没写规则时按"不能为空"校验（错误码 `RequiredCode` 默认 `400`）；非必填字段传了值也执行规则，没传则跳过。
- Java 的 `Pattern` 对 null 放行，Go 版相同；`NotEmpty` 对空数组、空对象也判失败（与 Hutool `ObjectUtil.isEmpty` 一致）。

### 5.5.3 需要注意的地方

1. **正则引擎不同**：Go 的 `regexp` 是 RE2，不支持零宽断言。内置正则里 `PATTERN_43`（邮编）用了 `(?!\d)`，需要改写。
2. **Java 内置正则本身有错**，Go 版已修正（`validate/patterns.go` 里每条都在 `Fixed` 字段注明了原版问题，`TestBuiltinPatterns` 覆盖）：`PATTERN_12`、`14`、`17`、`21`、`27`、`30`、`31`、`33`、`34`、`35`、`36`、`41`、`42`、`43`。
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

## 6.1 后台管理(M4 实现,`devtools` 包)

- `devtools.New(doc, Options{Auth, Realm, Store, User})`:`Auth` 必填;`Store` 默认是 Strategies 里最后一个可写的(只读的 `yaml.FS` 跳过)。挂载:`ginadapter.MountDevtools`、`nethttpadapter.MountDevtools`,或按 `Routes()` / `Patterns()` 自己挂。
- 修改的语义按 Java `ApidocDevtools.addMethodParam`:请求参数按提交的参数名整体替换该参数下的规则,校验规则只有前端点开过校验弹窗(`clickValidate`)才覆盖;返回字段整体替换为提交的字段(全部 `include: true`)。改进:旧规则按 `<参数名>.` 删除(Java 用 startsWith 会误删同前缀参数),保留字段原有的说明、示例、hidden。
- 读写基于 Store 自己的规则(不含其他来源合并进来的),保存后立即 `Doc.Refresh`。
- 修改记录统一为 `strategy.Change` / `strategy.Historian`:SQL 存 `history` 表;YAML 默认追加写 `<Dir>/.csap-apidoc-history.jsonl`(`Options.History` 可改,`"-"` 只记内存);Memory 记在内存。
- 正则:页面用 JS 测试正则,所以 `validatePatternTypes` 输出 `\uXXXX` 写法;保存时转成 RE2 的 `\x{XXXX}`;`validate` 编译前也会把 Java 写法转换,两种写法都能用。

## 7. 安全

- devtools **默认关闭**，需要显式 `devtools.New(...)` 并挂载才开启，并且必须传入鉴权回调；不传返回 `ErrNoAuth`。POST 接口只接受 `application/json`,防止跨站表单提交。
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
| M2 过滤与校验（**已完成** 2026-11-17） | 返回字段过滤（路径白名单、csap 兼容模式、外层包装）、请求参数校验（中间件、内置与自定义规则、观察模式）、基准测试 | 用 Java 的真实返回做黄金样本：79 个 kaiyun 接口的返回字段集合与 Java 完全一致；`/csap/apidoc` 编码器对 2.5MB 的 Java 实时返回逐字节还原。性能目标 30% 未达到，实测约 50%（见 5.2） |
| M3 配置（**已完成** 2026-11-17） | YAML 策略（读写、热加载）、SQL 策略（版本号、轮询、Redis 通知） | 改 YAML 或数据库后几秒内生效，不重启：示例项目实测改 YAML 3 秒内返回字段变化；SQL 两实例共用一个库，A 保存后 B 在轮询间隔内生效（SQLite、PostgreSQL 实测，MySQL 未实测） |
| M4 管理(**已完成** 2026-11-17) | devtools 接口与前端（含必填和校验规则配置）、修改记录、回滚、鉴权;`globalHeaderHints`、`authHint` | 用 devtools 页面勾选字段后，返回结果实时变化，并能回滚(浏览器实测,见 HANDOFF 2.4) |
| M5 发布(**已完成** 2026-11-17,待打 tag) | net/http 适配、Postman 导出、README、CHANGELOG、CI、官网接入文档 | 总覆盖率 80%;tag `csap-apidoc-go/v0.1.0` 由用户发布时打 |

## 10. 已确认事项

1. **仓库和模块路径**（已定，2026-11-17）：发布到 **GitHub**，组织 `csap-ai`（`csap-website` 已在这个组织下）。模块路径 `github.com/csap-ai/csap-apidoc/csap-apidoc-go`。GitHub 仓库是 `https://github.com/csap-ai/csap-apidoc`（用户 2026-11-17 给出），模块路径据此定为 `github.com/csap-ai/csap-apidoc/csap-apidoc-go`。**注意**：GitHub `main`（2026-05-02 bee9d83）和 gitee 不是同一条提交历史，GitHub 上没有 b4b9819；它比线上基线多了 2026-04 之后的开发，如文档页请求历史和请求快照（M9.1/M9.2）、i18n、`csap-apidoc-mock` 设计、只发 Maven Central 等。Go 代码怎么合到 GitHub 仓库、内嵌哪一版前端，推送前要和用户定。开发期间代码仍放在 gitee `csap-framework-apidoc` 仓库的 `feat/apidoc-go` 分支。发版 tag 格式为 `csap-apidoc-go/vX.Y.Z`。
2. **名称**：目录 `csap-apidoc-go`（与 `csap-apidoc-*` 其他模块保持一致），包名 `apidoc`。
3. **最低 Go 版本**：1.22，需要泛型和新版 ServeMux。
4. **前端**（已定）：内嵌。复用本仓库的 `csap-apidoc-ui` 和 `csap-apidoc-devtools/devtools` 源码，构建产物用 `go:embed` 打进 `ui` 包，不拆成独立仓库。
5. **football-data-hub 何时接入**（已定）：本框架开发完成后，通知 football-data-hub 会话「Java到Go迁移可行性分析 (fork)」，由那边接入：把写死的 `json:"-"` 白名单换成 YAML 配置加过滤中间件，用现有比对样本回归。本框架这边不改 football-data-hub 的代码。

## 附录 A：Java 版已知缺陷（Go 版不复刻）

- `GET /csap/apidoc/{tableName}?key=` 会浅拷贝后直接修改缓存里的 `Controller.methodList`。之后全量文档、树形菜单、方法详情都会永久缺少方法，`flush=true` 也恢复不了，只能重启。实测在 kyty-data-center 上，调用几十次后方法数从 88 降到 46。
