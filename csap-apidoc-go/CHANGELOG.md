# 更新日志

csap-apidoc-go 的版本号独立于 Java 版,tag 格式为 `csap-apidoc-go/vX.Y.Z`。格式参考 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.0.0/),版本号遵循 [语义化版本](https://semver.org/lang/zh-CN/)。

## [未发布]

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

[未发布]: https://github.com/csap-ai/csap-apidoc/compare/csap-apidoc-go/v0.1.0...HEAD
[0.1.0]: https://github.com/csap-ai/csap-apidoc/releases/tag/csap-apidoc-go/v0.1.0
