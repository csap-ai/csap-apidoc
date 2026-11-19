# 配置参考

本页列出 CSAP Apidoc 1.4.0 中全部真实存在的配置：`@EnableApidoc` 注解属性，以及 `application.yml` 中 `csap.apidoc`、`csap.apidoc.devtools.proxy`、`csap.apidoc.runtime.filter`、`csap.validation` 四组配置项。配置项名称使用 Spring Boot 的 kebab-case 写法。

:::info 适用范围
本页的配置项都是 **Java 版**(Spring Boot 2.x / 3.x)的配置。Go 版用 `apidoc.Options` 配置(gin、net/http)的接入、结构体标签、过滤和校验见 [Go 版](/go/)；两种语言共用文档页面和 YAML 规则文件格式。
:::

:::warning 不存在的配置项
早期文档中出现过的 `csap.apidoc.enabled`、`csap.apidoc.title`、`csap.apidoc.version`、`csap.apidoc.description`、`csap.apidoc.base-package`、`csap.apidoc.strategy`、`csap.apidoc.storage-strategy`、`csap.apidoc.ui-path`、`csap.apidoc.security.*`、`csap.apidoc.devtools.port`、`csap.apidoc.path`、`csap.apidoc.filename` 在代码中都**不存在**，写了也不会生效。

- 扫描包、存储策略、文件路径通过 `@EnableApidoc` 的属性指定。
- 文档标题、描述、版本通过 `csap.apidoc.api-info.*` 配置。
:::

## @EnableApidoc 注解

`@EnableApidoc`（`ai.csap.apidoc.boot.autoconfigure.EnableApidoc`）是开启文档功能的前提，通常放在启动类上。文档相关的自动配置只有在它存在时才会加载。

| 属性 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `value` | `String[]` | 空 | `apiPackages` 的别名 |
| `apiPackages` | `String[]` | 空 | 要扫描的 Controller 包，包含子包。支持通配符 `*`、`?`、`**`（按 Spring 资源路径规则匹配）。只有带 `@Api` 的类会被收录 |
| `apiPackageClasses` | `Class<?>[]` | 空 | 直接指定 Controller 类，可与 `apiPackages` 同时使用 |
| `enumPackages` | `String[]` | 空 | 枚举扫描包，带 `@ApiResponseCode` 的枚举会作为响应码文档。与配置项 `csap.apidoc.enum-packages` 合并 |
| `enumPackageClasses` | `Class<?>[]` | 空 | 直接指定枚举类 |
| `modelPackages` | `String[]` | 空 | 模型扫描包。目前只被收集，文档生成没有使用 |
| `modelPackageClasses` | `Class<?>[]` | 空 | 直接指定模型类。目前只被收集，文档生成没有使用 |
| `type` | `ApiStrategyType` | `ANNOTATION` | 文档主体策略，一般保持默认。1.1.1 起 `YAML`、`JSON` 会注册对应策略；`SQL_LITE` 没有实现，启动即报错并提示改用 `paramType = SQL_LITE`。见 [存储策略](/guide/storage-strategies) |
| `paramType` | `ApiStrategyType` | `ANNOTATION` | 参数（字段规则）策略：`ANNOTATION`、`YAML`、`SQL_LITE`（1.4.0 起还有 `DATABASE`、`REDIS`，配置见 [数据库模式](/guide/database-mode)、[Redis 模式](/guide/redis-mode)），决定每个接口显示哪些请求 / 返回字段，见 [存储策略](/guide/storage-strategies) |
| `requestType` | `String` | `"default"` | 请求参数解析器名称，对应 `IMethodRequest` Bean 的 `name()`；内置只有 `default` |
| `responseType` | `String` | `"default"` | 返回参数解析器名称，对应 `IMethodResponse` Bean 的 `name()`；内置只有 `default` |
| `path` | `String` | `"csap-api-doc"` | YAML / SQLite 策略文件所在的目录名。支持 `${属性名:默认值}` 占位符 |
| `fileName` | `String` | `""` | SQLite 数据库文件名，只在 `SQL_LITE` 策略下生效；为空时使用 `apidoc`。支持占位符 |
| `prefixStrategy` | `FilePrefixStrategyType` | `DEFAULT` | YAML 文件的命名方式：`DEFAULT` 按类分别生成文件；`FIXED` 使用固定前缀 `api` 的文件 |
| `showChildPackageFlag` | `boolean` | `true` | 目前这个注解属性没有被读取。控制枚举扫描是否包含子包请使用配置项 `csap.apidoc.show-child-package-flag` |

`ApiStrategyType` 的取值为 `JSON`、`ANNOTATION`、`SQL_LITE`、`YAML`，位于 `ai.csap.apidoc.ApiStrategyType`；`FilePrefixStrategyType` 位于 `ai.csap.apidoc.FilePrefixStrategyType`。

示例：

```java
import ai.csap.apidoc.ApiStrategyType;
import ai.csap.apidoc.boot.autoconfigure.EnableApidoc;

@SpringBootApplication
@EnableApidoc(
        apiPackages = {"com.example.demo.controller", "com.example.**.api"},
        enumPackages = {"com.example.demo.enums"},
        paramType = ApiStrategyType.SQL_LITE,
        path = "${apidoc.path:csap-api-doc}",
        fileName = "${apidoc.db:${spring.application.name}}"
)
public class DemoApplication {
    // ...
}
```

`path` 和 `fileName` 的占位符由 Spring `Environment` 解析，所以它们引用的属性名可以自己定义（上例中的 `apidoc.path`、`apidoc.db` 就是自定义的），不需要是 `csap.apidoc.*` 下的配置项。

## csap.apidoc

对应配置类 `ai.csap.apidoc.properties.CsapDocConfig`。

### 文档基本信息 api-info

对应 `ai.csap.apidoc.properties.CsapApiInfo`，随文档数据一起返回；`/csap/openapi/postman` 导出的 Postman Collection 使用其中的 `title` 和 `description`；1.2.0 起 `/csap/openapi.json` 的 `info` 也取自这里（`title`、`description`、`version`）。

| 配置项 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `csap.apidoc.api-info.title` | `String` | `Api Documentation`* | 文档标题 |
| `csap.apidoc.api-info.description` | `String` | `Api Documentation`* | 文档描述 |
| `csap.apidoc.api-info.version` | `String` | `1.0`* | 文档版本 |
| `csap.apidoc.api-info.service-url` | `String` | `urn:tos`* | 服务地址 |
| `csap.apidoc.api-info.license` | `String` | `Apache 2.0` | 许可证名称 |
| `csap.apidoc.api-info.license-url` | `String` | `http://www.apache.org/licenses/LICENSE-2.0` | 许可证地址 |
| `csap.apidoc.api-info.contact.name` | `String` | 空 | 联系人 |
| `csap.apidoc.api-info.contact.url` | `String` | 空 | 联系人地址 |
| `csap.apidoc.api-info.contact.email` | `String` | 空 | 联系邮箱 |
| `csap.apidoc.api-info.authorization-type` | `ApiAuthorization` | `OAUTH_2` | 认证类型：`BEARER_TOKEN`、`BASIC_AUTH`、`OAUTH_2`、`OTHER` |

\* 带星号的默认值只在**完全没有配置** `api-info` 时使用（此时整体使用内置的默认信息）。只要配置了 `api-info` 下的任意一项，没有配置的 `title`、`description`、`version`、`service-url` 就是空值，建议一起写上。

### 扫描与字段处理

| 配置项 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `csap.apidoc.enum-packages` | `List<String>` | 空 | 枚举扫描包，带 `@ApiResponseCode` 的枚举会作为响应码文档。与 `@EnableApidoc(enumPackages)` 合并 |
| `csap.apidoc.show-child-package-flag` | `Boolean` | `true` | 扫描 `enum-packages` 时是否包含子包 |
| `csap.apidoc.model-packages` | `List<String>` | 空 | 模型扫描包。目前只被收集，文档生成没有使用 |
| `csap.apidoc.api-packages` | `List<String>` | 空 | 已废弃（`@Deprecated`），目前不会被读取。Controller 扫描包请用 `@EnableApidoc(apiPackages)` |
| `csap.apidoc.response-type` | `CamelCaseType` | `none` | 返回字段命名转换，用于返回字段过滤：`none` 不处理；`toCamelCase` 对下划线命名的字段，同时放行其驼峰形式；`toUnderlineCase` 对驼峰命名的字段，同时放行其下划线形式。适用于实际返回的 JSON 命名与 Java 字段命名不一致的情况 |

### 分页约定 result

对应 `ai.csap.apidoc.properties.ResultConfig`，用于识别「分页查询」方法，并让分页相关字段自动显示，无需逐个设置 `forceReq` / `forceRep`。匹配方式是“方法名包含其中某个字符串”，区分大小写。

| 配置项 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `csap.apidoc.result.request-page-method` | `List<String>` | `[Page]` | 方法名包含其中任一项时，视为分页请求方法。这类方法还会追加 `GlobalCsapRequestParamProvider` 提供的全局请求参数 |
| `csap.apidoc.result.request-field` | `List<String>` | `[size, current, column, asc]` | 分页请求方法中，请求模型里这些名称的字段自动显示 |
| `csap.apidoc.result.response-page-method` | `List<String>` | `[Page]` | 方法名包含其中任一项时，视为分页返回方法 |
| `csap.apidoc.result.response-field` | `List<String>` | `[rows, total, size, current, records, total, totalPage, currentPage, pages, pageSize]` | 分页返回方法中，返回模型里这些名称的字段自动显示 |
| `csap.apidoc.result.response-excludes` | `List<String>` | `[pages, records, searchCount, total, orders]` | 即使在 `response-field` 中，也不自动显示的字段 |
| `csap.apidoc.result.response-class-name` | `List<String>` | `[com.csap.framework.core.Page, com.csap.mybatisplus.page.Page, ai.csap.apidoc.core.ApidocResult]` | 只对这些类（全限定名）的返回模型应用上面的规则；配置为空列表时不限制类 |

### 网关聚合 resources

对应 `ai.csap.apidoc.model.CsapDocResource` 列表。配置后，文档页面顶部出现「切换服务」下拉框，用于在多个服务的文档之间切换。详见 [网关聚合](/guide/gateway-aggregation)。

| 配置项 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `csap.apidoc.resources[n].name` | `String` | 无 | 显示名称 |
| `csap.apidoc.resources[n].url` | `String` | 无 | 该服务文档树接口的地址，一般是 `/服务前缀/csap/apidoc/parent` |
| `csap.apidoc.resources[n].version` | `String` | 无 | 版本标识 |

### 规则过期检查 rule-check

| 配置项 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `csap.apidoc.rule-check.enabled` | `Boolean` | `true` | 文档生成后检查 `YAML` / `SQL_LITE` 字段规则:方法已不存在、字段路径在当前结构中找不到时打 WARN。只打日志,不改变任何行为(1.3.0 起) |

### DevTools devtool

对应 `CsapDocConfig.ApidocDevtoolsProperties`。注意前缀是 `devtool`（单数）。

| 配置项 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `csap.apidoc.devtool.enabled` | `Boolean` | `false` | 是否启用 DevTools。为 `true` 时注册 `/devtools-ui` 页面和 `/devtools/**`、`/csap/yaml/**` 接口，并在启动日志中打印 DevTools 地址 |
| `csap.apidoc.devtool.cache` | `Boolean` | `true` | 目前这个值没有被任何代码读取，设置后没有效果 |

## csap.apidoc.devtools.proxy

对应 `ai.csap.apidoc.devtools.proxy.DevtoolsProxyProperties`。这是 DevTools 提供的一个 HTTP 反向代理（`POST /csap/apidoc/devtools/proxy`），用于让独立部署的文档页面绕过浏览器跨域限制发起在线测试请求。注意前缀是 `devtools`（复数）。

| 配置项 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `csap.apidoc.devtools.proxy.enabled` | `boolean` | `false` | 是否注册代理接口。为 `false` 时该接口返回 404 |
| `csap.apidoc.devtools.proxy.allowed-hosts` | `List<String>` | 空 | 允许代理访问的主机名。支持精确主机名和前缀通配（`*.example.com` 匹配 `api.example.com`，但不匹配 `example.com`），不区分大小写、忽略端口。启用代理时**必须**配置，为空时所有请求都被拒绝 |
| `csap.apidoc.devtools.proxy.max-body-bytes` | `long` | `5242880`（5 MiB） | 请求体和响应体的最大字节数。请求体超限返回 415；响应体超限会被截断，并带上响应头 `X-Csap-Apidoc-Proxy-Truncated: 1` |
| `csap.apidoc.devtools.proxy.timeout-ms` | `int` | `30000` | 访问上游的连接和读取超时（毫秒） |
| `csap.apidoc.devtools.proxy.strip-headers` | `List<String>` | `[Cookie, X-Forwarded-For, X-Real-IP]` | 永远不会转发给上游的请求头，不区分大小写 |
| `csap.apidoc.devtools.proxy.log-bodies` | `boolean` | `false` | 是否在 INFO 日志中记录（截断后的）请求和响应体。请求体可能包含令牌，仅在排查问题时临时打开 |

:::warning 安全
这个代理按设计会让应用向任意允许的地址发起请求，属于 SSRF 能力。只在开发环境启用，务必配置 `allowed-hosts`，不要暴露在公网，必要时用 Spring Security 保护该接口。
:::

:::tip Spring Boot 3
1.1.0 中代理的自动配置 `DevtoolsProxyAutoConfiguration` 只登记在 `META-INF/spring.factories` 中，Spring Boot 3 不读取这个文件，这组配置在 Spring Boot 3 上不生效。1.1.1 起它也登记在 `AutoConfiguration.imports` 中，Spring Boot 2 和 3 都生效。
:::

## csap.apidoc.runtime.filter

对应 `ai.csap.apidoc.runtime.autoconfigure.RuntimeFilterProperties`（模块 `csap-apidoc-runtime`，`csap-apidoc-boot-starter` 已引入）。运行时返回字段过滤（1.2.0 起），默认关闭。说明见 [返回字段过滤](/guide/runtime-filter)。

| 配置项 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `csap.apidoc.runtime.filter.enabled` | `boolean` | `false` | 是否开启。只有写 `true` 时才注册过滤组件（仅 Servlet Web 应用）；关闭时没有运行时开销 |
| `csap.apidoc.runtime.filter.mode` | `FilterMode` | `path` | 全局模式。`path`：按字段路径放行，与文档一致；`csap`：按类型白名单 / 黑名单，兼容 csap-framework；`off`：不过滤（可配合 `endpoints` 只对个别接口开启） |
| `csap.apidoc.runtime.filter.engine` | `FilterEngine` | `auto` | `auto` 按接口实际使用的消息转换器选择；`jackson`、`fastjson`、`fastjson2` 只过滤用该库输出的响应 |
| `csap.apidoc.runtime.filter.endpoints` | `Map<String, FilterMode>` | 空 | 按接口覆盖模式，键为 Controller 全限定类名 + `.` + 方法名（YAML 中写成 `"[com.example.UserController.get]"`），值为 `off`、`path`、`csap`。同名重载方法共用一个键 |

## csap.apidoc.mcp

对应 `ai.csap.apidoc.mcp.autoconfigure.McpProperties`(1.3.0 起,模块 `csap-apidoc-mcp`,starter 已引入)。内嵌 MCP 端点,默认关闭。用法见 [MCP](/guide/mcp)。

| 配置项 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `csap.apidoc.mcp.enabled` | `boolean` | `false` | 是否开启 MCP 端点;关闭时端点不注册(404) |
| `csap.apidoc.mcp.path` | `String` | `/csap/mcp` | 端点路径 |
| `csap.apidoc.mcp.base-url` | `String` | 空 | 工具调用的回环地址,默认 `http://127.0.0.1:{端口}{context-path}{servlet-path}`;HTTPS 或端口不能直连时配置 |
| `csap.apidoc.mcp.call-timeout` | `Duration` | `30s` | 单次工具调用超时 |
| `csap.apidoc.mcp.max-result-chars` | `int` | `50000` | 工具结果文本最大长度,超出截断 |
| `csap.apidoc.mcp.expose-get-by-default` | `boolean` | `true` | 没有单独设置的 GET 接口是否暴露;写操作始终需要逐个打开 |
| `csap.apidoc.mcp.allowed-origins` | `List<String>` | 空 | 允许的浏览器来源;请求带 `Origin` 且不在列表中返回 403 |
| `csap.apidoc.mcp.instructions` | `String` | 空 | 发给 AI 的服务说明,默认用文档标题和描述 |
| `csap.apidoc.mcp.store` | `Store` | `auto` | 暴露范围等设置的存储:`auto`(`SQL_LITE` 字段规则存 SQLite,其余存 `{path}/mcp.yaml`)、`yaml`、`sqlite`、`memory` |
| `csap.apidoc.mcp.config-file` | `String` | 空 | 设置文件的显式路径(`.yaml` / `.db`);打包运行时需要持久化 DevTools 修改时配置 |
| `csap.apidoc.mcp.auth.mode` | `AuthMode` | `token` | `token`:需要 `Authorization: Bearer` 令牌;`passthrough`:不校验,转发请求头由接口自己鉴权。规则文件中的设置优先 |
| `csap.apidoc.mcp.auth.tokens[n].name` | `String` | 空 | 令牌名称,出现在审计日志中 |
| `csap.apidoc.mcp.auth.tokens[n].value` | `String` | 空 | 令牌值,建议 `${环境变量}`;token 模式下一个都没有时所有请求 401 |
| `csap.apidoc.mcp.auth.forward-headers` | `List<String>` | `[Authorization, Cookie]` | 转发给接口的请求头(token 模式不转发 `Authorization`) |
| `csap.apidoc.mcp.auth.upstream-headers` | `Map<String,String>` | 空 | 每次工具调用附加的请求头,例如服务账号凭据 |
| `csap.apidoc.mcp.audit.enabled` | `boolean` | `true` | 是否记录审计日志 |
| `csap.apidoc.mcp.audit.capacity` | `int` | `500` | 内存中保留的条数 |
| `csap.apidoc.mcp.audit.store` | `AuditStore` | `memory` | 审计存储(1.5.0 起):`memory` 只在内存、`sqlite` 同时写 SQLite 文件、`database` 同时写入数据库模式的库(表 `{前缀}mcp_audit`,需要 `paramType = DATABASE`,否则 warn 并退回内存)。不设置时由 `persist` 决定 |
| `csap.apidoc.mcp.audit.persist` | `boolean` | `false` | 同时写入 SQLite,等同 `store: sqlite`(兼容) |
| `csap.apidoc.mcp.audit.sqlite-file` | `String` | `csap-mcp-audit.db` | 审计 SQLite 文件,相对路径相对工作目录 |
| `csap.apidoc.mcp.audit.max-rows` | `int` | `10000` | SQLite / 数据库中最多保留的条数(数据库按 `service` 区分,每 10 分钟清理一次) |
| `csap.apidoc.mcp.audit.max-summary-chars` | `int` | `512` | 参数 / 结果摘要的最大长度 |
| `csap.apidoc.mcp.audit.mask-fields` | `List<String>` | 空 | 在内置敏感字段(password、token、secret 等)之外追加打码的字段名 |

## csap.apidoc.team

对应 `ai.csap.apidoc.team.autoconfigure.TeamProperties`(1.5.0 起,模块 `csap-apidoc-team-client`,starter 已引入)。把本服务注册到团队版的文档中心,默认关闭,不配置时没有任何线程和网络请求。用法见 [与团队版对接](/guide/team-integration)。

| 配置项 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `csap.apidoc.team.enabled` | `boolean` | `false` | 是否向团队版注册 |
| `csap.apidoc.team.url` | `String` | 空 | 团队版地址,例如 `http://team.example.com:8090`;为空时不注册 |
| `csap.apidoc.team.token` | `String` | 空 | 团队版控制台创建的 REGISTER 令牌,建议用环境变量;不会出现在日志里 |
| `csap.apidoc.team.service` | `String` | 空 | 服务名。依次取本项、`csap.apidoc.database.service`、`spring.application.name`,都没有时为 `default` |
| `csap.apidoc.team.base-url` | `String` | 空 | 团队版能访问到的本服务地址(含 context-path,不带末尾 `/`);为空时推断为 `http://{本机首个非回环 IPv4}:{端口}{context-path}`,推断不出时 warn 并不注册 |
| `csap.apidoc.team.instance-id` | `String` | 空 | 实例标识,同一服务内唯一;默认 `base-url` 的 `host:port` |
| `csap.apidoc.team.doc-path` | `String` | `/csap/apidoc` | 文档接口路径 |

## csap.validation

对应 `ai.csap.validation.properties.ValidationProperties`，需要引入（可选的）`csap-validation-boot-starter`。1.2.0 起运行时校验真正执行，默认关闭。详见 [参数校验](/guide/validation)。

| 配置项 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `csap.validation.mode` | `ValidationMode` | `off` | 全局校验模式：`off` 不校验；`observe` 校验并记录日志 / 指标，请求放行；`enforce` 校验失败直接返回错误 |
| `csap.validation.endpoints` | `Map<String, ValidationMode>` | 空 | 按接口覆盖模式，键为 Controller 全限定类名 + `.` + 方法名（YAML 中写成 `"[com.example.UserController.create]"`），值为 `off`、`observe`、`enforce`。全局 `off` 时只要有一个接口不是 `off`，运行时校验也会注册 |
| `csap.validation.http-status` | `int` | `200` | 校验失败时响应的 HTTP 状态码；响应体始终是 `ApidocResult`（`code`、`message`）。同样用于 Spring 标准校验异常的统一返回 |
| `csap.validation.tip-type` | `ValidationTipType` | `TIP_1` | 校验失败提示信息的格式，见下表 |
| `csap.validation.required-code` | `String` | `"400"` | 字段只配了必填、没配任何校验规则时，值为空（`null`、空字符串、空集合）返回的 `code`（1.2.0 起） |
| `csap.validation.required-message` | `String` | `不能为空` | 同上，返回的提示，按 `tip-type` 拼上字段名，如 `字段nickname,不能为空`（1.2.0 起） |
| `csap.validation.enabled` | `Boolean` | 未配置 | 1.1.x 的旧开关，兼容语义见下表。新项目请用 `mode` |
| `csap.validation.code` | `String` | `code` | 目前这个值没有被任何代码读取 |
| `csap.validation.message` | `String` | `message` | 目前这个值没有被任何代码读取 |

旧开关 `enabled` 与 `mode` 的关系：

| 配置 | 生效的全局模式 |
| --- | --- |
| 只写 `enabled: true` | `enforce` |
| `enabled: true` 且写了 `mode` | 以 `mode` 为准 |
| `enabled: false` | 全部关闭，包括 `endpoints` 中的覆盖 |
| 都不写 | `off` |

:::warning 从 1.1.x 升级
1.1.x 中写了 `csap.validation.enabled: true` 的项目，升级到 1.2.0 后会**真正开始拦截**校验失败的请求（1.1.x 中没有组件执行规则）。如果不确定规则是否都正确，先改成 `mode: observe`。
:::

`tip-type` 的取值（以字段 `graphId`、描述「用户名」、提示「只能是0-9的数字」为例）：

| 取值 | 格式 | 示例 |
| --- | --- | --- |
| `TIP_1` | 字段 + 字段名 + 逗号 + 提示 | 字段graphId,只能是0-9的数字 |
| `TIP_11` | 字段 + 字段名 + 提示 | 字段graphId只能是0-9的数字 |
| `TIP_2` | 字段名 + 逗号 + 提示 | graphId,只能是0-9的数字 |
| `TIP_22` | 字段名 + 提示 | graphId只能是0-9的数字 |
| `TIP_3` | 字段描述 + 逗号 + 提示 | 用户名,只能是0-9的数字 |
| `TIP_33` | 字段描述 + 提示 | 用户名只能是0-9的数字 |
| `TIP_3_1` | 只有提示 | 只能是0-9的数字 |

## 完整 application.yml 示例

下面的示例覆盖了所有会生效的配置项，取值多为默认值，按需删减。DevTools 建议放在 `application-dev.yml` 中，见 [安装](/guide/installation)。

```yaml
csap:
  apidoc:
    # 文档基本信息
    api-info:
      title: 订单服务 API
      description: 订单服务的接口文档
      version: 1.0.0
      service-url: https://api.example.com
      license: Apache 2.0
      license-url: http://www.apache.org/licenses/LICENSE-2.0
      authorization-type: OAUTH_2
      contact:
        name: 后端组
        url: https://example.com
        email: dev@example.com

    # 返回字段命名转换：none / toCamelCase / toUnderlineCase
    response-type: none

    # 响应码枚举扫描
    enum-packages:
      - com.example.demo.enums
    show-child-package-flag: true

    # 分页约定
    result:
      request-page-method: [Page]
      request-field: [size, current, column, asc]
      response-page-method: [Page]
      response-field: [rows, total, size, current, records, totalPage, currentPage, pages, pageSize]
      response-excludes: [pages, records, searchCount, total, orders]
      response-class-name:
        - ai.csap.apidoc.core.ApidocResult

    # 网关聚合（只在网关 / 聚合服务中配置）
    resources:
      - name: 订单服务
        url: /order-api/csap/apidoc/parent
        version: v1
      - name: 用户服务
        url: /user-api/csap/apidoc/parent
        version: v1

    # DevTools（建议只在开发环境开启）
    devtool:
      enabled: false

    # DevTools 代理（默认关闭）
    devtools:
      proxy:
        enabled: false
        allowed-hosts:
          - localhost
          - 127.0.0.1
        max-body-bytes: 5242880
        timeout-ms: 30000
        strip-headers: [Cookie, X-Forwarded-For, X-Real-IP]
        log-bodies: false

    # 返回字段过滤（1.2.0 起，默认关闭）
    runtime:
      filter:
        enabled: false
        mode: path            # path / csap / off
        engine: auto          # auto / jackson / fastjson / fastjson2
        endpoints:
          "[com.example.demo.controller.OrderController.detail]": off

    # 内嵌 MCP 端点（1.3.0 起，默认关闭）
    mcp:
      enabled: false
      auth:
        mode: token           # token / passthrough
        tokens:
          - name: my-ide
            value: ${CSAP_MCP_TOKEN:}
      audit:
        persist: false

  # 参数校验（需要引入可选的 csap-validation-boot-starter）
  validation:
    mode: observe             # off（默认）/ observe / enforce
    http-status: 200
    tip-type: TIP_1
    required-code: "400"
    required-message: 不能为空
    endpoints:
      "[com.example.demo.controller.OrderController.create]": enforce
```

## 相关页面

- [安装](/guide/installation)
- [存储策略](/guide/storage-strategies)
- [参数校验](/guide/validation)
- [返回字段过滤](/guide/runtime-filter)
- [MCP](/guide/mcp)
- [DevTools](/guide/devtools)
- [网关聚合](/guide/gateway-aggregation)
