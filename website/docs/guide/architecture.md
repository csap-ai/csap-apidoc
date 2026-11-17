# 架构

本页说明 CSAP Apidoc 的模块划分与依赖关系、应用启动时如何扫描生成文档、字段规则从哪里来以及如何合并，还有文档缓存与刷新的实际行为。

## 模块

仓库是一个 Maven 多模块工程，版本 `1.1.0`。除两个 starter 的 groupId 是 `ai.csap.apidoc.boot` 外，其余模块的 groupId 都是 `ai.csap.apidoc`。

| 模块 | 作用 | 直接依赖的本项目模块 |
| --- | --- | --- |
| `csap-apidoc-annotation` | 全部文档注解（`@Api`、`@ApiOperation`、`@ApiModelProperty`、`@DocAuth` 等） | 无（只依赖 `spring-web`、`jakarta.validation-api`） |
| `csap-apidoc-common` | 文档模型（`ai.csap.apidoc.model`）、`ApidocResult`、策略类型枚举、工具类 | annotation |
| `csap-validation-core` | 参数校验框架，`IValidateFactory` 等 | annotation、common |
| `csap-apidoc-ui` | 文档页面 `csap-api.html` 的静态资源（React 构建产物），没有 Java 代码 | 无 |
| `csap-apidoc-core` | 扫描引擎、`ApidocContext`、`CsapDocController`、各类 Provider、Postman 转换 | annotation、common、validation-core、ui |
| `csap-apidoc-standard` | 文件型策略共用的 `StandardProperties`、`IStandardHandle` | core |
| `csap-apidoc-yaml` | YAML / JSON 文档策略和参数策略 | common、core、standard |
| `csap-apidoc-sqlite` | SQLite 文档策略和参数策略 | core、standard |
| `csap-apidoc-devtools` | DevTools 接口、DevTools 页面、可选的转发代理 | core、standard |
| `csap-apidoc-boot-starter` | 自动配置、`@EnableApidoc` | core、yaml、sqlite、annotation、devtools |
| `csap-validation-boot-starter` | 校验自动配置，提供 `IValidateFactory` Bean | validation-core |

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

业务项目一般只引入两个 starter，见[安装](/guide/installation)。

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
3. **HTTP 方法**取自 `@RequestMapping`（含 `@GetMapping` 等）。没有声明 method 时，按代码记录为 POST、DELETE、GET。
4. **请求头**：`@ApiOperation(headers = …)` 声明的请求头，再追加 `CsapHeadersProvider` 提供的公共请求头。注意 1.1.0 中只有声明了 `headers` 的方法才会追加公共请求头。
5. **提示信息**：收集 `@DocGlobalHeader` 和 `@DocAuth`，写入 `globalHeaderHints`、`authHint`。
6. **请求参数**：交给名称为 `requestType` 的 `IMethodRequest` 解析。方法名包含 `csap.apidoc.result.request-page-method` 中任一项（默认 `Page`）时，再追加 `GlobalCsapRequestParamProvider` 提供的分页等公共参数。
7. **返回参数**：交给名称为 `responseType` 的 `IMethodResponse` 解析，返回类型为 `Optional<T>` 时取 `T`。
8. **枚举**：配置的枚举类中，标注 `@ApiResponseCode` 的枚举生成响应码文档。

最后 Controller 按 `@Api` 的 `position`、再按类名排序，填入 `apiInfo` 和 `resources`。

:::warning 文档策略只注册了注解一种
`@EnableApidoc` 的 `type` 可选 `ANNOTATION`、`YAML`、`JSON`、`SQL_LITE`。`csap-apidoc-yaml`、`csap-apidoc-sqlite` 里有对应的 `YamlApidocStrategy`、`JsonApidocStrategy`、`SqliteApidocStrategy` 类，但 1.1.0 的自动配置只把 `ApiDocService`（`ANNOTATION`）注册成了 Bean。`type` 设为其他值时找不到策略，文档为空（日志里会有加载失败的记录）。常用的 YAML / SQLite 是指 `paramType`，即下一节的字段规则来源，它们是注册了的。详见[存储策略](/guide/storage-strategies)。
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

注解策略里，`@Group` 的 `value` 写方法名时匹配所有同名方法，写成 `类简单名.方法名` 时只匹配该类的方法。

合并规则：

- **请求参数**：策略返回了该字段的规则，或者字段标注了 `forceReq = true`，字段才会出现。必填、参数位置、校验规则优先取策略里的；策略没有给出时，必填取 `@ApiModelProperty` 的 `required`，校验规则从字段上的校验注解解析。
- **返回参数**：策略返回的规则里 `include` 为 `true`，或者字段标注了 `forceRep = true`，字段才会出现。必填同样优先取策略里的。
- **方法上的简单参数**（如 `@RequestParam Long id`）：必填取「策略规则」与「`@ApiProperty(required)`」的或；描述优先取策略里的，其次是 `@ApiProperty` 的。

同一份规则在生成文档时也会登记到 `IValidateFactory`，供运行时的参数校验和返回字段过滤使用，所以文档展示的规则和实际生效的规则是一致的。参见[参数校验](/guide/validation)、[存储策略](/guide/storage-strategies)。

## 缓存与刷新

文档保存在 `ApidocContext` 的一个静态 `CsapDocResponse` 对象里，整个应用只有一份。

| 场景 | 实际行为 |
| --- | --- |
| 应用启动 | 异步生成一次。缓存对象在扫描开始时就已创建，启动后很短时间内的请求可能拿到尚未生成完的文档 |
| `GET /csap/apidoc?flush=true` | 缓存已存在时直接返回缓存，`flush` 不生效 |
| `GET /csap/apidoc/parent`、`/csap/openapi/postman` | 只读缓存 |
| `GET /csap/apidoc/method`、`/csap/apidoc/{tableName}`，且 `csap.apidoc.devtool.enabled=true` | 先重新扫描参数里指定的那个类，用新结果替换缓存里该 Controller 的接口列表，再返回 |
| 同上，但未开启 DevTools | 只读缓存 |

由此可以得出：

- 生产环境改了代码，要**重启应用**才能看到新文档。
- 开发时开启 `csap.apidoc.devtool.enabled`，在页面里点开某个接口就会重新扫描它所在的类，已有 Controller 里方法和参数的变化能及时反映出来。新增的 Controller 类不在启动时确定的扫描结果里，仍然需要重启。
- `csap.apidoc.devtool.cache` 这个配置项存在，但 1.1.0 的代码没有读取它，设置与否没有区别。
- `YamlParamGroupStrategy` 读出的 YAML 规则另有一层按 `StrategyModel` 缓存，通过 DevTools 写入时会同步更新这层缓存。
- `GET /csap/apidoc/{tableName}?key=` 会破坏缓存，见 [REST 接口](/api/rest) 中的已知问题。

## 文档页面如何取数

`csap-api.html` 是纯静态页面，打开后：

1. 请求 `GET /csap/apidoc/parent` 渲染左侧接口树，并用其中的 `resources` 填充「切换服务」下拉框（见[网关聚合](/guide/gateway-aggregation)）。
2. 点开接口时请求 `GET /csap/apidoc/method` 取详情。
3. 在线调试由浏览器直接请求业务接口，环境、请求头、鉴权等配置只保存在浏览器本地，见[在线调试](/guide/testing)。
4. 导出在浏览器里完成，见[导出](/guide/export)。
