# 更新日志

csap-apidoc-go 的版本号独立于 Java 版,tag 格式为 `csap-apidoc-go/vX.Y.Z`。格式参考 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.0.0/),版本号遵循 [语义化版本](https://semver.org/lang/zh-CN/)。

## [0.2.0] - 未发布 (Unreleased)

### 变更(不兼容)

- **SQL 规则来源的表结构与 Java 1.4.0 数据库模式统一**:三张表都加了 `service` 列(`rule` 主键改为 `(service, rule_key)`,`history` 索引改为 `(service, rule_key, id)`,`meta` 由 `id=1` 单行改为每个服务一行);`updated_by` 去掉 `DEFAULT ''`,为空时写 `unknown`;大文本 MySQL 改用 `LONGTEXT`(原 `MEDIUMTEXT`)。旧表(v0.1.x)不能直接用:`EnsureSchema` 发现旧表会返回 `sql.ErrLegacySchema`,不会自动改动。升级步骤见下方"升级"和 [docs/SQL.md](docs/SQL.md)。

### 新增

- `sql.Options.Service`:服务名,默认 `default`,所有读写按它过滤;同一个库可以存多个服务(包括 Java 服务)的规则。
- 方言 `sql.DM`(达梦 DM8)和 `sql.Kingbase`(人大金仓,按 PostgreSQL 兼容模式)。
- `Strategy.Migrate(ctx)` 与 `Strategy.MigrationSQL()`:v0.1.x → v0.2.0 的迁移,旧数据归到 `Options.Service`。
- `sql.ServiceNotifier`:带服务名的通知(可选,实现它就优先使用)。原 `Notifier` 接口不变。
- **Redis 规则来源 `strategy/redis`**:与 Java 1.4.0 Redis 模式的数据约定一致(`docs/design/redis-mode.md`),Lua 保存脚本在 `strategy/redis/save.lua`。实现 Strategy / Saver / Historian / Watcher;订阅通知 + 轮询兜底;修改记录按 `HistoryLimit` 裁剪。主模块不依赖 Redis 客户端,使用方实现最小接口 `redis.Client`,go-redis v9 适配器见 [docs/REDIS.md](docs/REDIS.md)。
- **规则导入 `strategy.Import`**:把 YAML 等来源导入 SQL / Redis,默认不覆盖已有的不同规则并返回冲突列表,支持 dry-run,修改人 `migrate`。见 [docs/IMPORT.md](docs/IMPORT.md)。

### 升级

1. 停掉所有 v0.1.x 实例,备份数据库。
2. 在一次性的迁移程序或启动代码里调用 `sqlstrategy.New(Options{..., Service: "你的服务名"}).Migrate(ctx)`,或者执行 `MigrationSQL()` 输出的语句(各方言的完整语句见 docs/SQL.md)。旧数据归到你指定的服务名。
3. 升级到 v0.2.0。没有迁移时 `EnsureSchema` 会提示先迁移。
4. 如果之前依赖 `Name()`:现在形如 `sql:csap_apidoc_[default]`。

## [0.1.1] - 2026-11-17

### 修复

- **有外层包装但没声明 `Resp` 时 `data` 被整段裁掉**:`Options.Wrapper` 已设、接口有返回规则、但 `Op` 没有 `Resp(...)` 时,csap 模式和路径模式都会把 `data` 去掉。现在 `data` 类型未知时整段放行,外层包装上的规则照常生效。
- **返回过滤作用于错误响应**:`ResponseFilter`(gin、net/http)原来对所有响应体都裁剪,包括 4xx/5xx 错误体和异常处理中间件写的失败体。现在默认只裁剪 2xx,与 Java 只过滤控制器正常返回的对象一致。
- **规则路径与返回类型对不上时字段被全部裁掉**:例如接口返回改成 `ApiResult<Page<Item>>`,规则还是 `data.xxx`(少了 `rows` 一层)时,csap 模式把列表元素的字段全部裁掉。现在与 Java 1.2.0 一致:只有至少一条返回规则命中实际返回字段路径的接口才过滤,否则原样输出(路径模式同样适用)。
- **校验失败的字段名**:路径、查询、请求头、表单参数校验失败时,`validate.Violation.Field` 原来是 `updated_at.updated_at` 这种规则路径写法,现在是参数名 `updated_at`,与 Java 一致;请求体字段仍为规则路径。

### 新增

- `Options.ShouldFilter func(r *http.Request, status int) bool`:自定义哪些响应要裁剪(默认只裁剪 2xx);`Doc.ShouldFilter` 供自定义适配器调用。
- `Options.FilterEndpoints map[string]filter.Mode` 与 `filter.ModeOff`:按接口(规则的键 `分组.接口名`)覆盖过滤模式或关闭过滤,对应 Java `csap.apidoc.runtime.filter.endpoints`;`FilterMode: filter.ModeOff` 全局关闭。
- 模型显示名覆盖(对应 Java `@ApiModel.value`):可选接口 `apidoc.ModelNamer`(`ApidocName() string`)和 `Options.TypeNames map[reflect.Type]string`,优先级 `TypeNames` > `ApidocName()` > 类型名。字段的 `dataType` 不受影响。

## [0.1.0] - 2026-11-17

首个版本。模块路径 `github.com/csap-ai/csap-apidoc/csap-apidoc-go`,最低 Go 1.22。协议与 Java 版 1.1.x 兼容。

### 新增

- **运行时生成文档**:从路由、结构体标签(`doc`、`doc_required`、`doc_force` 等)和字段规则生成文档;`/csap/apidoc`、`/csap/apidoc/parent`、`/csap/apidoc/method`、`/csap/apidoc/{分组}` 的返回格式与 Java 一致(含 fastjson 风格的格式化和 `$ref`)。
- **内嵌前端**:文档页面 `/csap-api.html` 与后台管理页面 `/devtools-ui` 直接使用 Java 仓库 main 上的构建产物(文档页 1.1.1);静态资源放在 `/csap-ui/` 前缀下,避免与业务的 `/assets/` 冲突。`scripts/sync-ui.sh --ref <提交>` 同步新版前端。
- **在线调试建议**:`globalHeaderHints`、`authHint`(对应 Java 1.1.0 的 `@DocGlobalHeader`、`@DocAuth`),支持全局、分组、接口三级。
- **返回字段过滤**:路径白名单模式(默认)和完整复刻 Java 行为的 csap 兼容模式;在 JSON 字节流上裁剪,不依赖编码器。
- **请求参数校验**:关闭 / 观察 / 拦截三种模式,可按接口覆盖;Java 的 `NotNull`、`NotEmpty`、`Pattern` 加上 `NotBlank`、`Min`、`Max`、`Length`、`Enum` 和自定义规则;46 条内置正则(修正了 Java 1.0.x 里写错的十余条),Java 写法的 `\uXXXX` 自动转换;失败返回格式与 Java 一致。
- **规则来源**:YAML(与 Java `method-field.yaml` 同格式,热加载,写回时保留注释和其他接口)、SQL(SQLite / PostgreSQL / MySQL,版本号、轮询与可选通知实现多实例同步)、内存;按 标签 < YAML < SQL 合并。
- **后台管理**(`devtools` 包):与 Java DevTools 前端兼容的 `/csap/yaml/*`、`/devtools/*` 接口;保存后立即生效;必须配置鉴权(`BasicAuth`、`Token`、`AllowLocal` 等);修改记录与回滚(`/csap/yaml/history`、`/csap/yaml/rollback`),YAML、SQL、内存三种来源都支持。
- **框架适配**:gin(`adapter/gin`)、标准库 net/http 的 Go 1.22 ServeMux(`adapter/nethttp`),各自提供文档挂载、后台挂载、返回过滤和请求校验中间件。
- **导出**:OpenAPI 3.1(`/csap/openapi.json`)、Postman Collection v2.1(`/csap/openapi/postman`,与 Java 输出一致)。
- 示例 `examples/gin-basic`、`examples/nethttp-basic`。

### 与 Java 版的差别

- 没有任何规则的接口,文档显示全部字段、返回不过滤(Java 只显示 `forceRep` 字段)。
- `GET /csap/apidoc/{分组}?key=` 不会改坏文档缓存(Java 1.1.0 之前的缺陷)。
- 后台保存请求参数时,按 `<参数名>.` 精确删除旧规则,字段原有的说明、示例保留。
- 只写 `required: true`、不写校验规则的字段,按"不能为空"校验(Java 1.1.x 不校验;Java 1.2.0 起同样校验,默认 code、提示与 Go 一致)。

### 已知限制

- 只有 gin 和 net/http 适配器。
- 返回过滤的额外开销约为 JSON 编码耗时的 50%。
- 文档页面在线调试的跨域代理(Java `/csap/apidoc/devtools/proxy`)未提供。
- SQL 规则来源的 MySQL 方言未实测。

[未发布]: https://github.com/csap-ai/csap-apidoc/compare/csap-apidoc-go/v0.1.1...HEAD
[0.1.1]: https://github.com/csap-ai/csap-apidoc/compare/csap-apidoc-go/v0.1.0...csap-apidoc-go/v0.1.1
[0.1.0]: https://github.com/csap-ai/csap-apidoc/releases/tag/csap-apidoc-go/v0.1.0
