# 架构

本页说明 CSAP Apidoc 的模块划分与依赖关系、应用启动时如何扫描生成文档、字段规则从哪里来以及如何合并，还有文档缓存与刷新的实际行为。

:::info 适用范围
本页讲的是 **Java 版**(Spring Boot 2.x / 3.x)。Go 版的设计见仓库里的 `csap-apidoc-go/docs/DESIGN.md`，用法见 [Go 版](/go/)；两种语言共用文档页面和 YAML 规则文件格式。
:::

## 模块

仓库是一个 Maven 多模块工程，当前发布版本 `1.4.0`。除两个 starter 的 groupId 是 `ai.csap.apidoc.boot` 外，其余模块的 groupId 都是 `ai.csap.apidoc`。

| 模块 | 作用 | 直接依赖的本项目模块 |
| --- | --- | --- |
| `csap-apidoc-annotation` | 全部文档注解（`@Api`、`@ApiOperation`、`@ApiModelProperty`、`@DocAuth` 等） | 无（只依赖 `spring-web`、`jakarta.validation-api`） |
| `csap-apidoc-common` | 文档模型（`ai.csap.apidoc.model`）、`ApidocResult`、策略类型枚举、工具类 | annotation |
| `csap-validation-core` | 参数校验框架，`IValidateFactory` 等 | annotation、common |
| `csap-apidoc-ui` | 文档页面 `csap-api.html` 的静态资源（React 构建产物），没有 Java 代码 | 无 |
| `csap-apidoc-core` | 扫描引擎、`ApidocContext`、`CsapDocController`、各类 Provider、Postman / OpenAPI 转换 | annotation、common、validation-core、ui |
| `csap-apidoc-standard` | 文件型策略共用的 `StandardProperties`、`IStandardHandle` | core |
| `csap-apidoc-yaml` | YAML / JSON 文档策略和参数策略 | common、core、standard |
| `csap-apidoc-sqlite` | SQLite 文档策略和参数策略 | core、standard |
| `csap-apidoc-devtools` | DevTools 接口、DevTools 页面、可选的转发代理 | core、standard |
| `csap-apidoc-runtime` | 返回字段过滤（1.2.0 起），按文档规则裁剪接口实际返回的 JSON | core |
| `csap-apidoc-boot-starter` | 自动配置、`@EnableApidoc` | core、yaml、sqlite、annotation、devtools、runtime |
| `csap-validation-boot-starter` | 校验自动配置（可选），提供 `IValidateFactory` Bean、`ValidationProperties`、`ValidateExceptionHandler`，以及 1.2.0 起的运行时校验组件 | validation-core |

另外还有三个只含 POM 配置的模块：`csap-apidoc-dependency`（BOM，统一依赖版本）、`csap-apidoc-build`（共享构建配置，各模块的父 POM）、`csap-apidoc-parent`（给使用方继承的父 POM）。

依赖关系（箭头指向被依赖方）：

```
csap-apidoc-boot-starter ──┬─▶ csap-apidoc-devtools ──┬─▶ csap-apidoc-standard ──▶ csap-apidoc-core
                           ├─▶ csap-apidoc-yaml ──────┤                              │
                           ├─▶ csap-apidoc-sqlite ────┘                              ├─▶ csap-apidoc-ui
                           ├─▶ csap-apidoc-core                                      ├─▶ csap-validation-core ─┐
                           └─▶ csap-apidoc-annotation                                ├─▶ csap-apidoc-common ◀──┤
                                                                                     └─▶ csap-apidoc-annotation ◀┘
csap-validation-boot-starter ──▶ csap-validation-core
```

业务项目一般只引入 `csap-apidoc-boot-starter`；需要运行时参数校验、校验异常处理器或 `ValidationProperties` 时再加 `csap-validation-boot-starter`，见[安装](/guide/installation)。`csap-apidoc-runtime` 依赖 core，由 starter 引入，图中未画出。没有 `IValidateFactory` Bean 时，文档 starter 内部使用一个默认实现（1.1.1 起；1.1.0 中会空指针）。

## 启动流程

### 1. 注册阶段：@EnableApidoc

启动类上的 `@EnableApidoc` 通过 `ApidocImportBeanDefinitionRegistrar` 在 Bean 定义阶段完成：

- 注册 `EnableApidocConfig` Bean。`ApidocAutoConfiguration` 以这个 Bean 存在为条件，**没有 `@EnableApidoc` 就不会启用任何文档功能**。
- 扫描 `value` / `apiPackages` 指定的包。不含通配符的包名按普通包扫描；含 `*`、`**`、`?` 的按 Spring 资源模式 `classpath*:包路径/**/*.class` 扫描。`apiPackageClasses` 直接指定类。
- 只保留类本身、实现的接口、父类或元注解上带 `@Api` 的类。
- 把这些类连同注解上的 `type`、`paramType`、`requestType`、`responseType`、`path`、`fileName`、`prefixStrategy` 打包成一个 `StrategyModel`，后面按它生成文档。`path` 和 `fileName` 支持 `${属性:默认值}` 占位符。
- 记录 `enumPackages` / `enumPackageClasses`、`modelPackages` / `modelPackageClasses`。

### 2. 自动配置

`ApidocAutoConfiguration` 注册的主要 Bean：

| Bean | 作用 |
| --- | --- |
| `CsapDocConfig` | `csap.apidoc.*` 配置 |
| `ApiDocService` | 基于注解的文档策略（`ApidocStrategy`，名称 `annotation`） |
| `DefaultMethodRequest` / `DefaultMethodResponse` | 默认的请求 / 返回参数解析器，名称都是 `default` |
| `AnnotationParamGroupStrategy`、`YamlParamGroupStrategy`、`SQLiteParamGroupStrategy` | 三种字段规则来源（`ParamGroupStrategy`） |
| `ScannerPackageConfig`、`ApidocContext` | 扫描配置和文档上下文 |
| `CsapDocController` | 文档 HTTP 接口，见 [REST 接口](/api/rest) |
| `CsapResourcesProvider`、`CsapHeadersProvider`、`GlobalCsapRequestParamProvider`、`GlobalCsapResponseParamProvider`、`DevtoolsClass` | 可替换的扩展点，见 [Java 扩展点](/api/java) |
| `RunApplicationListener` | 启动后触发文档生成 |

`csap.apidoc.devtool.enabled=true` 时，`ApidocDevtoolsAutoConfiguration` 再注册 DevTools 相关的 Controller。

所有单例初始化完成后，`ApidocContext` 把容器里的 `ApidocStrategy`、`ParamGroupStrategy`、`IMethodRequest`、`IMethodResponse` 按各自的名称收集到静态表里，后续按名称查找。

### 3. 生成文档

收到 `ApplicationStartedEvent` 后，`RunApplicationListener` **异步**调用 `ApidocContext.cmd(true, false)`，生成完毕后在控制台打印文档地址 `http://localhost:{server.port}{context-path}/csap-api.html`。

`cmd` 对每个 `StrategyModel` 按它的 `type` 找到 `ApidocStrategy` 执行；单个策略失败只记录日志，不影响其他策略。默认的注解策略 `ApiDocService` 的处理过程：

1. **并发处理每个类**，整体最多等待 60 秒。类上必须同时有 `@Controller`（`@RestController` 也算）和 `@Api`，`@Api(hidden = true)` 的类跳过。
2. **收集方法**：遍历类的全部 public 方法（含继承的），只处理带 `@ApiOperation` 的；`@ApiOperation` 和 `@RequestMapping` 都支持从接口、父类继承。跳过桥接方法、`@Api` 的 `hiddenMethod` 里列出的方法；`showMethod` 不为空时只保留其中的方法。路径和 HTTP 方法都相同的重复映射只保留一个。
3. **HTTP 方法**取自 `@RequestMapping`（含 `@GetMapping` 等）。`@RequestMapping` 没有写 `method` 时记录为 GET、POST、PUT、DELETE（1.1.1 起）；有 `@ApiOperation` 但没有任何映射注解的方法不是接口，会被跳过。
4. **请求头**：`@ApiOperation(headers = …)` 声明的请求头，再追加 `CsapHeadersProvider` 提供的公共请求头。`headers` 默认含一个 `Content-Type`，所以一般方法都会追加；只有显式写成 `headers = {}` 的方法，公共请求头也不会追加。
5. **提示信息**：收集 `@DocGlobalHeader` 和 `@DocAuth`，写入 `globalHeaderHints`、`authHint`。
6. **请求参数**：交给名称为 `requestType` 的 `IMethodRequest` 解析。方法名包含 `csap.apidoc.result.request-page-method` 中任一项（默认 `Page`）时，再追加 `GlobalCsapRequestParamProvider` 提供的分页等公共参数。
7. **返回参数**：交给名称为 `responseType` 的 `IMethodResponse` 解析，返回类型为 `Optional<T>` 时取 `T`。
8. **枚举**：配置的枚举类中，标注 `@ApiResponseCode` 的枚举生成响应码文档。

最后 Controller 按 `@Api` 的 `position`、再按类名排序，填入 `apiInfo` 和 `resources`。

:::warning 文档策略 type
`@EnableApidoc` 的 `type` 可选 `ANNOTATION`、`YAML`、`JSON`、`SQL_LITE`。默认的 `ANNOTATION` 由 `ApiDocService` 处理。1.1.1 起，`type = YAML` / `JSON` 时由 `ApidocImportBeanDefinitionRegistrar` 注册 `YamlApidocStrategy` / `JsonApidocStrategy`（1.1.0 没有注册，文档静默为空）；`SqliteApidocStrategy` 无法正常工作，`type = SQL_LITE` 启动即报错，提示改用 `paramType = SQL_LITE`。常用的 YAML / SQLite 是指 `paramType`，即下一节的字段规则来源。详见[存储策略](/guide/storage-strategies)。
:::

## 字段规则从哪里来

一个字段最终出现在文档里的样子，由两部分信息合并而成：

**字段本身的描述，始终来自代码**：字段名、类型、`@ApiModelProperty` 的 `value`、`description`、`example`、长度、分组、版本，以及枚举字段的取值说明。

**字段在某个接口里的规则，来自 `paramType` 选定的 `ParamGroupStrategy`**：这个字段是否出现在该接口的请求 / 返回参数里、是否必填、参数位置（Query / Body 等）、校验规则。

| `paramType` | 实现类 | 规则来源 |
| --- | --- | --- |
| `ANNOTATION`（默认） | `AnnotationParamGroupStrategy` | 字段上 `@ApiModelProperty(groups = @Group(…))` 里与当前方法匹配的分组；方法参数上的 `@ApiProperty` |
| `YAML` | `YamlParamGroupStrategy` | `@EnableApidoc(path = …)` 目录下的 YAML 文件，通常由 [DevTools](/guide/devtools) 维护 |
| `SQL_LITE` | `SQLiteParamGroupStrategy` | SQLite 数据库文件 |
| `DATABASE`（1.4.0） | `DatabaseParamGroupStrategy`（模块 `csap-apidoc-database`） | 应用自己的关系数据库，见 [数据库模式](/guide/database-mode) |

注解策略里，`@Group` 的 `value` 写方法名时匹配所有同名方法，写成 `类简单名.方法名` 时只匹配该类的方法。

合并规则：

- **请求参数**：策略返回了该字段的规则，或者字段标注了 `forceReq = true`，字段才会出现。必填、参数位置、校验规则优先取策略里的；策略没有给出时，必填取 `@ApiModelProperty` 的 `required`，校验规则从字段上的校验注解解析。
- **返回参数**：策略返回的规则里 `include` 为 `true`，或者字段标注了 `forceRep = true`，字段才会出现。必填同样优先取策略里的。
- **方法上的简单参数**（如 `@RequestParam Long id`）：策略里有这个参数的规则时，必填以规则为准；没有规则时取 `@ApiProperty(required)`（1.3.0 起；1.2.x 取两者的或）。描述优先取策略里的，其次是 `@ApiProperty` 的。

同一份规则在生成文档时也会登记到 `IValidateFactory`。1.2.0 起，运行时可以用这些规则校验请求参数（`csap.validation.mode`，默认关闭），也可以按返回字段规则裁剪接口实际返回的 JSON（`csap.apidoc.runtime.filter.enabled`，默认关闭）；1.1.x 中没有组件使用这些登记结果。参见[参数校验](/guide/validation)、[返回字段过滤](/guide/runtime-filter)。

## 缓存与刷新

文档保存在 `ApidocContext` 的一个静态 `CsapDocResponse` 对象里，整个应用只有一份。

| 场景 | 实际行为 |
| --- | --- |
| 应用启动 | 异步生成一次。1.1.1 起扫描写入一个新对象，完成后才替换缓存；扫描完成前到来的请求会等待，拿到的是完整文档（1.1.0 中可能拿到尚未生成完的文档） |
| `GET /csap/apidoc?flush=true` | 重新全量扫描，完成后替换缓存（1.1.1 起；1.1.0 中缓存已存在时直接返回缓存，`flush` 不生效） |
| `GET /csap/apidoc/parent`、`/csap/openapi/postman`、`/csap/openapi.json` | 只读缓存 |
| `GET /csap/apidoc/method`、`/csap/apidoc/{tableName}`，且 `csap.apidoc.devtool.enabled=true` | 先重新扫描参数里指定的那个类，用新结果替换缓存里该 Controller 的接口列表，再返回 |
| 同上，但未开启 DevTools | 只读缓存 |

由此可以得出：

- 生产环境改了代码，要**重启应用**才能看到新文档（`flush=true` 只重新扫描已加载的类，不会加载新代码）。
- 开发时开启 `csap.apidoc.devtool.enabled`，在页面里点开某个接口就会重新扫描它所在的类，已有 Controller 里方法和参数的变化能及时反映出来。新增的 Controller 类不在启动时确定的扫描结果里，仍然需要重启。
- `csap.apidoc.devtool.cache` 这个配置项存在，但目前的代码没有读取它，设置与否没有区别。
- `YamlParamGroupStrategy` 读出的 YAML 规则另有一层按 `StrategyModel` 缓存，通过 DevTools 写入时会同步更新这层缓存。
- 1.1.0 中 `GET /csap/apidoc/{tableName}?key=` 会改坏缓存（之后全量文档永久缺方法），1.1.1 已修复，见 [REST 接口](/api/rest)。

## 文档页面如何取数

`csap-api.html` 是纯静态页面，打开后：

1. 请求 `GET /csap/apidoc/parent` 渲染左侧接口树，并用其中的 `resources` 填充「切换服务」下拉框（见[网关聚合](/guide/gateway-aggregation)）。
2. 点开接口时请求 `GET /csap/apidoc/method` 取详情。
3. 在线调试由浏览器直接请求业务接口，环境、请求头、鉴权等配置只保存在浏览器本地，见[在线调试](/guide/testing)。
4. 导出在浏览器里完成，见[导出](/guide/export)。
