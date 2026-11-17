# 配置参考

本页列出 CSAP Apidoc 1.1.1 中全部真实存在的配置：`@EnableApidoc` 注解属性，以及 `application.yml` 中 `csap.apidoc`、`csap.apidoc.devtools.proxy`、`csap.validation` 三组配置项。配置项名称使用 Spring Boot 的 kebab-case 写法。

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
| `paramType` | `ApiStrategyType` | `ANNOTATION` | 参数（字段规则）策略：`ANNOTATION`、`YAML`、`SQL_LITE`，决定每个接口显示哪些请求 / 返回字段，见 [存储策略](/guide/storage-strategies) |
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

## csap.validation

对应 `ai.csap.validation.properties.ValidationProperties`，需要引入（可选的）`csap-validation-boot-starter`。详见 [参数校验](/guide/validation)。

| 配置项 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `csap.validation.enabled` | `Boolean` | 未配置时视为关闭 | 为 `true` 时注册请求拦截器 `ValidateIntercept`。见下方提示 |
| `csap.validation.tip-type` | `ValidationTipType` | `TIP_1` | 校验失败提示信息的格式，见下表 |
| `csap.validation.code` | `String` | `code` | 目前这个值没有被任何代码读取 |
| `csap.validation.message` | `String` | `message` | 目前这个值没有被任何代码读取 |

:::warning 运行时校验需要显式开启
`ValidationProperties.enabled` 的默认值虽然是 `true`，但注册请求拦截器的条件是 `@ConditionalOnProperty(prefix = "csap.validation", name = "enabled", havingValue = "true")`，没有写这个属性时条件不成立，所以必须在配置文件里**明确写上** `csap.validation.enabled: true` 才会注册拦截器（1.1.1 起 IDE 的配置提示也显示默认 `false`）。另外，1.1.x 中没有组件在请求处理时调用这个拦截器，开启后 CSAP 规则校验仍不会执行，见 [参数校验](/guide/validation)。
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

  # 参数校验（需要引入可选的 csap-validation-boot-starter）
  validation:
    enabled: true
    tip-type: TIP_1
```

## 相关页面

- [安装](/guide/installation)
- [存储策略](/guide/storage-strategies)
- [参数校验](/guide/validation)
- [DevTools](/guide/devtools)
- [网关聚合](/guide/gateway-aggregation)
