# 常见问题

本页收集使用 CSAP Apidoc 时的常见问题，当前版本 1.3.0；个别问题只出现在 1.1.0 或 1.1.1，会在问题里注明。每条答案都以当前代码为准。

:::info 适用范围
本页的问题主要针对 **Java 版**。Go 版的接入见 [Go 版](/go/)。
:::

## 安装与依赖

### 需要引入哪些依赖？

只需要 `ai.csap.apidoc.boot:csap-apidoc-boot-starter`，版本 `1.3.0`。`ai.csap.apidoc.boot:csap-validation-boot-starter` 是可选的，需要运行时参数校验、它的校验异常处理器或 `ValidationProperties` 时引入。文档页面、存储策略、DevTools 和返回字段过滤已经包含在文档 starter 中，不需要再单独引入 `csap-apidoc-ui`、`csap-apidoc-devtools` 或 `csap-apidoc-runtime`。见 [安装](/guide/installation)。

### 只引入文档 starter，为什么接口出不来？

这是 1.1.0 的问题：文档生成会调用 `IValidateFactory` Bean，而这个 Bean 由 `csap-validation-boot-starter` 注册，缺少它时生成文档空指针，启动日志中会出现“有 N 个文档加载失败”的警告。1.1.1 已修复，升级到 1.1.1 即可，不需要再加校验 starter。

### Java 11 上启动报 `UnsupportedClassVersionError`？

1.1.0 的 jar 实际是 Java 17 字节码（class 文件版本 61），Java 11 无法加载。升级到 1.1.1，它的 jar 是 Java 11 字节码。

### 支持 Spring Boot 3 吗？

支持。Spring Boot 2.x 和 3.x 使用同一套构件和版本号。仓库中有 `example/example-spring-boot2`（Spring Boot 2.7.18）和 `example/example-spring-boot3`（Spring Boot 3.2.0）两个示例。Spring Boot 3 需要 JDK 17 及以上，框架本身（1.1.1 起）需要 JDK 11 及以上。

1.1.0 在 Spring Boot 3 上不会注册 DevTools 的 HTTP 代理（`csap.apidoc.devtools.proxy.*`），1.1.1 已修复，见 [配置参考](/guide/configuration)。

### 坐标 `com.csap.framework.boot:csap-framework-apidoc-boot-starter` 找不到？

这是旧文档中的错误坐标，从未发布过。请使用 `ai.csap.apidoc.boot:csap-apidoc-boot-starter`。全部构件见 [获取方式](/download/)。

## 文档不显示

### 页面能打开，但接口列表是空的

按顺序检查：

1. `@EnableApidoc` 是否写了包名，例如 `@EnableApidoc("com.example.demo")`。不写 `apiPackages` / `apiPackageClasses` 时不会扫描任何 Controller。
2. Controller 是否在这个包（或其子包）下。
3. Controller 类上是否**同时**有 `@RestController`（或 `@Controller`）和 `ai.csap.apidoc.annotation.Api`。
4. `@Api` 是否设置了 `hidden = true`。
5. 如果还在用 1.1.0，依赖中是否有 `csap-validation-boot-starter`（见上一节）；1.1.1 不需要。
6. 启动日志中是否有 `apidoc 加载完成，但有 N 个文档加载失败` 的警告，以及它上方的异常。

### 某个接口方法没有出现

- 方法上必须有 `ai.csap.apidoc.annotation.ApiOperation` 和请求映射注解（`@GetMapping` 等）。
- `@ApiOperation(hidden = true)` 的方法不显示。
- 方法名出现在 `@Api(hiddenMethod = {...})` 中的方法不显示。
- 如果 `@Api` 设置了 `showMethod`，只有列在其中的方法会显示。

### 接口出来了，但请求体或返回数据里没有字段

字段要满足两个条件才会显示：

1. 字段上有 `@ApiModelProperty`，且没有设置 `hidden = true`，请求中没有设置 `ignoreReq = true`、返回中没有设置 `ignoreRep = true`。
2. 字段被当前接口“选中”。默认的 `ANNOTATION` 策略下，选中方式有：`forceReq = true`（请求）/ `forceRep = true`（返回），或者在 `groups` 中按方法名指定；使用 `YAML` / `SQL_LITE` 策略时，由对应文件中的规则决定，可以在 DevTools 中勾选。

最快的做法是在字段上加 `forceReq = true` 和 / 或 `forceRep = true`。原理见 [基础使用](/guide/basic-usage) 和 [存储策略](/guide/storage-strategies)。

### 规则过期怎么排查(改了方法名或返回结构后字段不见了)

使用 `YAML` / `SQL_LITE` 策略时,规则按"类名 + 方法名 + 字段路径"保存。下面两种改动不会自动同步到规则里:

- **方法改名**:旧方法名下的规则留在原处,新方法名下没有规则(或只剩零星几条)。
- **返回结构变了**(例如从 `Result<Item>` 改成分页 `Result<Page<Item>>`):旧路径 `data.id` 在新结构里变成了 `data.rows.id`,白名单里没有新路径,分页元素的字段全被排除。文档里 `data` 下面是空的,看起来像泛型没解析。

1.3.0 起,文档生成完成后会对照已加载的规则检查一遍,每个有问题的方法打一条 WARN(只打日志,不改变文档和过滤结果):

```text
WARN  csap-apidoc 字段规则可能已过期: com.example.ItemController.listItems —— 类中已没有这个方法(规则来源 yaml: csap-api-doc);相似的现有方法: pageItems。方法改名后请把规则移到新方法名下,否则新方法没有规则
WARN  csap-apidoc 字段规则可能已过期: com.example.ItemController.pageItems —— 以下路径在当前的参数 / 返回结构中找不到(规则来源 yaml: csap-api-doc);返回: data.animationUrl。返回结构改过(例如改成分页)时,白名单会把新结构的字段排除,文档看起来像泛型没解析
```

排查步骤:

1. 启动日志里搜 `字段规则可能已过期`,确认是哪个类、哪个方法、哪些路径。
2. 方法改名:在规则文件里把旧方法名下的规则改到新方法名下(YAML 在 `{path}/method-field/{类简名}-method-field.yaml`;SQLite 改 `api_method` 等表,或直接在 DevTools 里重新勾选)。
3. 结构改了:在 DevTools 里打开这个接口,按新结构重新勾选返回字段并保存;或在 YAML 里把旧路径换成新路径(如 `data.id` → `data.rows.id`,并加上 `data.rows`)。
4. 重启或调用 `/csap/apidoc?flush=true`,确认 WARN 消失。

只有"上级路径在当前结构里存在、但这一级不存在"的路径才会被报告,未展开的子树不会误报。不需要这个检查时设置 `csap.apidoc.rule-check.enabled=false`。

### 能直接用 Swagger 的注解吗？

不能。框架只识别 `ai.csap.apidoc.annotation` 包下的注解，例如扫描 Controller 时查找的是 `ai.csap.apidoc.annotation.Api`。Swagger 的 `io.swagger.annotations.Api` 等不会被识别。注解同名但属性不同，迁移时需要逐个核对。见 [注解](/guide/annotations)。

### 修改了注解，页面没变化

未开启 DevTools 时，文档在应用启动时生成一次并缓存，修改代码后需要重启应用。

## 配置

### 配置了 csap.apidoc.enabled / title / base-package 没有效果

这些配置项在代码中不存在。对应关系如下：

| 旧写法 | 实际做法 |
| --- | --- |
| `csap.apidoc.enabled` | 没有总开关。是否启用取决于有没有 `@EnableApidoc`，可以配合 `@Profile` 控制，见下一个问题 |
| `csap.apidoc.base-package` | `@EnableApidoc(apiPackages = ...)` |
| `csap.apidoc.title` / `version` / `description` | `csap.apidoc.api-info.title` / `version` / `description` |
| `csap.apidoc.strategy` / `storage-strategy` | `@EnableApidoc(paramType = ApiStrategyType.YAML)` 等 |
| `csap.apidoc.path` / `filename` | `@EnableApidoc(path = ..., fileName = ...)`，可以在其中写 `${...}` 占位符引用你自己定义的属性 |

全部配置见 [配置参考](/guide/configuration)。

### 如何在生产环境关闭文档？

把 `@EnableApidoc` 放到一个带 `@Profile("!prod")` 的配置类上。文档的自动配置以 `@EnableApidoc` 为前提，在 `prod` 环境中就不会注册文档数据接口和 DevTools。`/csap-api.html` 是静态文件，仍然能访问但没有数据；如需完全屏蔽，在网关或 Spring Security 中拦截该路径。示例见 [安装](/guide/installation)。

### 如何只在开发环境启用 DevTools？

DevTools 默认关闭。只在 `application-dev.yml` 中写：

```yaml
csap:
  apidoc:
    devtool:
      enabled: true
```

注意是 `devtool`（单数）。见 [DevTools](/guide/devtools)。

### 用 java -jar 运行时，DevTools 保存规则失败

打包后的 jar 里 classpath 不能写，YAML / JSON 规则文件无法保存。1.3.0 起接口返回 `code = 1007` 的失败结果，DevTools 页面显示原因和可行的做法，同时打一条 WARN（1.2.0 及以前是 HTTP 500 + `NullPointerException`）。

可以这样做：

- 在 IDE 或 `mvn spring-boot:run` 中运行，规则写回 `src/main/resources`；
- 改用 `@EnableApidoc(paramType = ApiStrategyType.SQL_LITE)`：打包运行时规则保存在 jar 外、启动目录下的 `./csap-api-doc/apidoc.db`（随 `path`、`fileName` 变化），重启后仍在；
- 数据库模式（MySQL / PostgreSQL / 达梦 / 人大金仓）计划在 1.4.0 提供，当前版本没有；Redis 不存放规则。

各策略的实测结果见 [存储策略 - 打包运行时能否在 DevTools 保存](/guide/storage-strategies#打包运行时能否在-devtools-保存)。

### 访问 /devtools-ui 返回 404

DevTools 没有开启。确认当前生效的配置中有 `csap.apidoc.devtool.enabled: true`，并且写的是 `devtool` 而不是 `devtools`。开启后启动日志会打印 DevTools 地址。

### 如何修改文档页面的访问路径？

没有对应的配置项。文档页面是 `csap-apidoc-ui` jar 中的静态文件 `static/csap-api.html`，由 Spring Boot 的静态资源处理提供，路径固定为 `/csap-api.html`。

## 校验

### 加了校验 starter，请求参数没有被校验

1.2.0 起运行时校验默认关闭，需要在配置里开启：`csap.validation.mode: observe`（只记录不拦截）或 `enforce`（校验失败直接返回错误）；也可以只给个别接口在 `csap.validation.endpoints` 里写非 `off` 的模式。旧开关 `csap.validation.enabled: true` 仍然有效，等价于 `enforce`。另外，规则在文档扫描完成后才生效，并且只有必填字段的规则会执行，见 [参数校验](/guide/validation)。1.1.x 中没有组件在请求处理时执行规则，开启也没有效果。Spring 标准的 `@Valid` / `@Validated` 校验不受影响。

### 引入校验 starter 后，项目原有的参数错误返回格式变了

校验 starter 会注册全局异常处理 `ValidateExceptionHandler`（`@ControllerAdvice`，`@Order(1)`），处理 `BindException`、`MethodArgumentNotValidException`、`MethodArgumentTypeMismatchException`、`ConstraintViolationException` 和 `ValidateException`，默认以 HTTP 200 返回 `ApidocResult` 格式（状态码可用 `csap.validation.http-status` 修改）。如果需要保留原有格式，声明一个你自己的 `ValidateExceptionHandler` 类型的 Bean 即可替换它（它带有 `@ConditionalOnMissingBean`）。

## 访问与安全

### 项目用了 Spring Security，页面打不开或没有数据

需要放行文档页面、静态资源和文档数据接口。文档相关的路径有：

| 路径 | 用途 |
| --- | --- |
| `/csap-api.html`、`/assets/**`、`/favicon.ico` | 文档页面及其静态资源 |
| `/csap/apidoc/**` | 文档数据 |
| `/csap/openapi/postman` | Postman Collection 导出 |
| `/csap/openapi.json` | OpenAPI 3.0 导出（1.2.0 起） |
| `/devtools-ui/**`、`/csap-api-devtools.html`、`/assets2/**` | DevTools 页面及其静态资源（开启 DevTools 时） |
| `/devtools/**`、`/csap/yaml/**` | DevTools 接口（开启 DevTools 时） |

`example/example-spring-boot3` 中的 `SecurityConfig` 是一个可以参考的写法。生产环境是否对外开放这些路径，请按你的安全要求决定。

### 文档数据有哪些接口？

| 接口 | 说明 |
| --- | --- |
| `GET /csap/apidoc` | 完整文档数据 |
| `GET /csap/apidoc/parent` | 文档树，网关聚合时 `resources[n].url` 指向它 |
| `GET /csap/apidoc/method?name=类全名&key=方法全名` | 单个方法的详细信息 |
| `GET /csap/openapi/postman` | Postman Collection JSON |
| `GET /csap/openapi.json` | OpenAPI 3.0 JSON（1.2.0 起） |

详见 [REST 接口](/api/rest)。

## 导出与聚合

### 如何导出为 Postman、OpenAPI 或 Markdown？

在文档页面顶部点击「导出文档」，可以选择 OpenAPI 3.0、Postman Collection 或 Markdown。Postman Collection 也可以直接从 `GET /csap/openapi/postman` 获取，1.2.0 起 OpenAPI 3.0 可以从 `GET /csap/openapi.json` 获取。见 [导出](/guide/export)。

### 多个微服务的文档能放在一起看吗？

可以。在网关（或任意一个聚合服务）中配置 `csap.apidoc.resources`，每一项指向一个服务的 `/csap/apidoc/parent`，页面上会出现「切换服务」下拉框。见 [网关聚合](/guide/gateway-aggregation)。

## 项目

### 可以用于商业项目吗？

CSAP Apidoc 以 Apache License 2.0 开源，许可证允许商业使用，具体条款以 [LICENSE](https://github.com/csap-ai/csap-apidoc/blob/main/LICENSE) 为准。项目只有开源版。

### 在哪里反馈问题？

- 问题和建议：[GitHub Issues](https://github.com/csap-ai/csap-apidoc/issues)
- 安全漏洞：按 [SECURITY.md](https://github.com/csap-ai/csap-apidoc/blob/main/SECURITY.md) 中的方式私下报告，不要公开提 Issue
- 参与贡献：见 [CONTRIBUTING.md](https://github.com/csap-ai/csap-apidoc/blob/main/CONTRIBUTING.md)
