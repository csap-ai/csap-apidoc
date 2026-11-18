# 文档核对清单(2026-11)

核对基准:`main` 的 `4b0f327`(Java 1.2.0、Go v0.1.1)。范围:`website/docs/**`、根目录 `README.md` / `README.en.md` / `CONTRIBUTING.md`、`csap-apidoc-go/README.md` 与 `docs/`、`skills/csap-apidoc/`,另外顺带核对了会同步到官网的 `docs/ROADMAP.md`。

约定:**行号是修正前的行号**。"状态"列:已修 = 本次已提交修正;待裁定 = 代码和文档谁对拿不准,没有改,交主会话裁定。严重程度:错误(文档说错了)、过时(曾经对,现在不对)、缺失(该写没写)、措辞(不算错但容易误解)。

## 一、已核对且一致的部分(无需改)

- 配置项:`@EnableApidoc` 全部 14 个属性及默认值、`csap.apidoc.*`(`CsapDocConfig`、`CsapApiInfo`、`ResultConfig`)、`csap.apidoc.devtools.proxy.*`、`csap.apidoc.runtime.filter.*`、`csap.validation.*`(含旧开关 `enabled` 的语义表)的名称、类型、默认值,和代码一致(`website/docs/guide/configuration.md`)。
- REST 接口:`/csap/apidoc`、`/csap/apidoc/{tableName}`、`/csap/apidoc/parent`、`/csap/apidoc/method`、`/csap/openapi/postman`、`/csap/openapi.json`、`/csap/yaml/*`、`/devtools/*`、代理接口的路径、参数、返回结构和 `CsapDocController` 一致(`website/docs/api/rest.md`)。
- 校验:异常处理器的返回码(411、413、400)、内置正则表(`PATTERN_00`～`PATTERN_44` 共 46 项)、模式与旧开关逻辑,和 `ValidationProperties`、`ValidateExceptionHandler`、`ValidatePatternType` 一致。
- 示例工程:Spring Boot 2.7.18 / 3.2.0、端口 8082 / 8083、`mainClass`、`csap.apidoc.version` 与文档一致。
- Go:模块路径、版本 v0.1.1、tag 格式 `csap-apidoc-go/vX.Y.Z`、Go 1.22、公开 API 签名、默认值、HTTP 路径与文档一致;`go build`、`go vet ./...`、`go test ./...` 通过,两个示例能编译。
- 中英文 README:结构、版本、坐标、链接一一对应(模块表漏 runtime 的问题见下,两边同样存在)。
- `npm ci && npm run build`(站点,含死链检查)通过。

## 二、核对发现(按页面)

### A. 版本号、坐标、模块

| # | 页面:行号 | 文档怎么写 | 代码实际 | 改法 | 严重程度 | 状态 |
|---|---|---|---|---|---|---|
| A1 | `guide/installation.md:39,47` | Gradle(Groovy / Kotlin)示例版本 `1.1.1` | 当前版本 1.2.0(`pom.xml:7`),同页 Maven 示例已是 1.2.0 | 改为 1.2.0 | 错误 | 已修 |
| A2 | `guide/quick-start.md:25` | Gradle 示例版本 `1.1.1` | 同上 | 改为 1.2.0 | 错误 | 已修 |
| A3 | `rspress.config.ts:26` | 导航栏版本标签 `v1.1.1` | 1.2.0 | 改为 `v1.2.0` | 错误 | 已修 |
| A4 | `rspress.config.ts:8` | 站点 description"Java 已发布,Go 开发中" | Go v0.1.1 已发布 | 改为"Java、Go 已发布" | 过时 | 已修 |
| A5 | `guide/installation.md:55-66`(starter 内含模块表)、`guide/architecture.md:11-24`(模块表、依赖图)、`download/index.md:23-35`(全部构件表)、`README.md:81-89`、`README.en.md:78-86`(模块表) | 没有 `csap-apidoc-runtime` | starter 直接依赖它(`csap-apidoc-boot/csap-apidoc-boot-starter/pom.xml:43`);`csap-apidoc-runtime` 是 1.2.0 新增的返回字段过滤模块,依赖 core | 各表补一行;架构页 starter 的依赖列、依赖说明补 runtime | 缺失 | 已修 |
| A6 | `guide/quick-start.md:27` | starter"已经包含文档页面、YAML / SQLite 存储策略和 DevTools" | 还包含返回字段过滤 | 补 runtime | 缺失 | 已修 |
| A7 | `download/index.md` 全文、`:3`、`:47` | 只有 Java;"每个版本对应一个 `vX.Y.Z` 标签" | Go 版通过 `go get` 获取,标签是 `csap-apidoc-go/vX.Y.Z` | 新增"Go 版"一节(`go get`、Go 1.22、标签格式、pkg.go.dev) | 缺失 | 已修 |
| A8 | `download/index.md:83` | 构建命令 `mvn -B clean install`"与 CI 使用的命令一致" | CI 是 `mvn -B clean install -DskipTests=false`(`.github/workflows/ci.yml:33`) | 措辞改为"CI 同一条命令并加 `-DskipTests=false`" | 措辞 | 已修 |
| A9 | `README.md:84`、`README.en.md:80`、`download/index.md:21`、`guide/quick-start.md:29`、`guide/faq.md:9`、`guide/architecture.md:34`、`skills/.../SKILL.md:22` | 校验 starter"只在需要它的校验异常处理器或 `ValidationProperties` 时引入" | 1.2.0 起运行时参数校验也要它(`ValidationAutoConfiguration` 的 `AutoValidateConfig`) | 补"运行时参数校验" | 过时 | 已修 |

### B. 仍停留在 1.1.x 的描述

| # | 页面:行号 | 文档怎么写 | 代码实际 | 改法 | 严重程度 | 状态 |
|---|---|---|---|---|---|---|
| B1 | `guide/installation.md:84` | `csap.validation.enabled=true` 后才注册 `ValidateIntercept`,"1.1.x 中没有组件调用它,运行时参数校验不会执行" | 1.2.0:`mode` / `endpoints` / 旧开关任一使校验生效时注册(`OnValidationActiveCondition`、`ValidationAutoConfiguration.AutoValidateConfig`) | 按 1.2.0 重写,1.1.x 作历史说明 | 过时 | 已修 |
| B2 | `guide/installation.md`(异常处理提示框) | "以 HTTP 200 返回" | 状态码可用 `csap.validation.http-status` 改(`ValidationProperties.httpStatus`) | 补"默认" | 措辞 | 已修 |
| B3 | `guide/overview.md:56,78` | "运行时校验在 1.1.x 中还不执行(计划 1.2.0)" | 1.2.0 已提供 | 改为 1.2.0 已提供、默认关闭 | 过时 | 已修 |
| B4 | `guide/overview.md` "其他模块" | 没有返回字段过滤 | 1.2.0 新增 `csap-apidoc-runtime` | 补一条并链接 | 缺失 | 已修 |
| B5 | `guide/overview.md:24` | 字段上的"`jakarta.validation` 约束" | 1.2.0 起 `javax` / `jakarta` 都识别 | 两者都写 | 措辞 | 已修 |
| B6 | `guide/overview.md:70` | 需要 OpenAPI 时只能在页面导出 | 1.2.0 起有 `GET /csap/openapi.json` | 补充 | 缺失 | 已修 |
| B7 | `guide/architecture.md:113` | "运行时的参数校验和返回字段过滤都不执行(计划 1.2.0 提供)" | 1.2.0 已提供 | 改写并链接 | 过时 | 已修 |
| B8 | `guide/architecture.md:20` | core"Postman 转换" | core 同时含 `OpenApiConverterService` | 补 OpenAPI | 缺失 | 已修 |
| B9 | `guide/faq.md:3,9,21` | "使用 1.1.x 时的常见问题"、"版本 `1.1.1`"、"框架本身(1.1.1)" | 当前 1.2.0 | 更新版本说明 | 过时 | 已修 |
| B10 | `guide/faq.md:111` | 校验拦截器只有写了 `csap.validation.enabled: true` 才注册,1.1.x 中不执行,"计划 1.2.0 补上" | 1.2.0 默认关闭,用 `csap.validation.mode` 开启 | 按 1.2.0 重写 | 过时 | 已修 |
| B11 | `guide/ai-skill.md:5,35` | "当前版本对应 Java 1.1.x"、"1.1.x 的已知限制" | 技能已更新到 1.2.x | 同步 | 过时 | 已修 |
| B12 | `skills/csap-apidoc/SKILL.md:8,18,25,75-77` | 对应 1.1.x、依赖版本 1.1.1、"Go 版尚未发布"、"运行时过滤和校验在 1.1.x 不执行" | Java 1.2.0;Go v0.1.1 已发布;1.2.0 起可开启运行时过滤与校验 | 版本改 1.2.x;Go 指向官网 Go 页;第 7 节改为"运行时过滤与校验(1.2.0 起,默认关闭)";数据接口表补 `/csap/openapi.json` | 过时 | 已修 |
| B13 | `docs/ROADMAP.md:25,90` | Go"v0.1.0 已发布"、"2026-12-04 发布 v0.1.0" | tag 已有 `csap-apidoc-go/v0.1.1`;v0.1.0 已于 11-17 发布 | 改为 v0.1.1;日期行改"已于 11-17 发布" | 过时 | 已修 |

### C. 其他页面

| # | 页面:行号 | 文档怎么写 | 代码实际 | 改法 | 严重程度 | 状态 |
|---|---|---|---|---|---|---|
| C1 | `guide/cors.md:155-169` | 试运行默认"没有"`withCredentials`,"等待后续在 Settings 加开关(M8.x 跟进项)" | 设置里已有"跨域请求附带 Cookie (withCredentials)"(`csap-apidoc-ui/src/stores/settingsStore.ts:27`,`guide/testing.md` 也已写) | 改为指向设置开关 | 错误 | 已修 |
| C2 | `guide/cors.md:11` | "试运行 v2 不走任何后端代理" | 1.1.1 起可填代理地址,也可走 devtools 转发接口(同页 3.2、3.4 自己就写了) | 改为"默认不走" | 措辞 | 已修 |
| C3 | `guide/cors.md:2` | 适用于 `csap-apidoc-ui` 的 `⚡ 试运行 v2` 面板 | 读者看不到 ui 模块名,页面叫"试运行 v2" | 改措辞 | 措辞 | 已修 |
| C4 | `api/rest.md` 总览表、DevTools 接口表 | `/devtools/hiddenMethod`"隐藏接口" | `ApidocDevtools.hiddenMethod` 直接 `return true`(`csap-apidoc-devtools/.../ApidocDevtools.java:1221`),没有作用;`guide/devtools.md` 已如实写 | 与 devtools 页一致 | 措辞 | 已修 |
| C5 | `website/README.md` 目录表 | 没有 `docs/roadmap.md`;"npm run dev 是 localhost:5173" | `sync.mjs` 同时生成 roadmap.md;端口由工具决定 | 补行、去掉写死的端口 | 缺失 | 已修 |
| C6 | `guide/export.md`(服务端导出一节) | 说 `PostmanConverterService` 和 `OpenApiConverterService` "都没有注册为 Spring Bean(`CsapDocController` 内部直接 new)" | `ApidocAutoConfiguration:218` 里 `new PostmanConverterService()` 传给 `CsapDocController` 构造器,`OpenApiConverterService` 是控制器内部 `new`;两者都不是 Bean,结论对,"内部直接 new"对 Postman 不准 | 不改(结论正确,细节不影响使用) | 措辞 | 不改 |

### D. CONTRIBUTING.md

| # | 行号 | 文档怎么写 | 代码实际 | 改法 | 严重程度 | 状态 |
|---|---|---|---|---|---|---|
| D1 | 147-150 | JDK 8 or higher;Node.js 16+ | 所有模块编译为 Java 11(`pom.xml:77-80`);CI 前端用 Node 18(`ci.yml`) | JDK 11+(Spring Boot 3 示例 17);Node 18+ | 错误 | 已修 |
| D2 | 185 | 示例启动后打开 `localhost:8080` | 示例端口 8082 / 8083(`example/*/application.yml`) | 写明两个端口 | 错误 | 已修 |
| D3 | 300-312 | `mvn test`、`mvn test -Dtest=PostmanConverterServiceTest`、`cd csap-apidoc-devtools/devtools && npm test` | 根 pom `skipTests` 默认 true(`pom.xml:86`),需 `-DskipTests=false`;不存在这个测试类,现有 `PostmanConverterServiceUrlTest`;devtools 前端没有 `test` 脚本,有测试的是 `csap-apidoc-ui` | 逐一改正 | 错误 | 已修 |
| D4 | 33 / 365 | 行为准则邮箱 `support@csap.ai`;联系邮箱 `support@csap.com` | 其余文件(`CODE_OF_CONDUCT.md`、`SECURITY.md`)统一用 `support@csap.ai` | 改为 `.ai` | 错误 | 已修 |
| D5 | 8-24(目录) | 目录条目 `Backend Setup`、`Frontend Setup (DevTools)`、`Running the Example` | 正文标题是 `Backend Setup (Java)`、`Frontend Setup`、`Running an Example`,锚点失效;缺 `Website`、`Go` | 目录对齐正文 | 措辞 | 已修 |
| D6 | 示例代码 | `convertToPostmanCollection(CsapDocParentResponse)` | 1.2.0 的主入口接收 `CsapDocResponse`(`CsapDocParentResponse` 版本已过时) | 示例改为 `CsapDocResponse` | 过时 | 已修 |
| D7 | 364 | "Join our community discussions" | 仓库没有开启讨论区的任何说明 | 删除 | 措辞 | 已修 |
| D8 | Documentation 一节 | 只提 README 和 `/docs` | 官网文档在 `website/docs/`,中英文 README 要一起改 | 补充 | 缺失 | 已修 |

### E. Go 文档(官网 Go 页、`csap-apidoc-go/README.md`、`docs/DESIGN.md`、`CHANGELOG.md`)

| # | 页面:行号 | 问题 | 改法 | 严重程度 | 状态 |
|---|---|---|---|---|---|
| E1 | `go/index.md:74` | 请求字段位置推断写成"`form`→QUERY(GET)或 FORM、`json`→BODY";代码(`build.go:182-184,246-283`):POST/PUT/PATCH 上 `form`→FORM、`json`→BODY,其他方法都是 QUERY,与路径参数同名是 PATH | 按代码改写,指向 DESIGN 3.1.1 | 措辞 | 已修 |
| E2 | `go/index.md:81-90` | 标签表缺 `doc_position`;`doc_required` 没写 `validate:"required"`(`strategy/tag/tag.go:19,58-67`) | 补 | 缺失 | 已修 |
| E3 | `go/index.md:204`、`DESIGN.md:242` | 校验失败返回写成 `{code, message, success:false}`;`model/csapjson.go:57-71` 还有 `language`、`time` | 补全 | 措辞 | 已修 |
| E4 | `go/index.md:225` | SQL 修改记录表叫 `history`;实际默认 `csap_apidoc_history`(带 `TablePrefix`) | 改 | 措辞 | 已修 |
| E5 | `go/index.md:231-238`、`:28-70`、`README.md:77` | 片段缺 import / `ctx` / `doc :=` | 补 | 措辞 | 已修 |
| E6 | `go/index.md:130`、`README.md:85` | `go run ./examples/gin-basic` 没说要在 `csap-apidoc-go` 目录下(仓库根没有 go.mod) | 写明目录 | 措辞 | 已修 |
| E7 | `README.md:3` | "与 Java 版(1.1.x)协议兼容" | 过滤、校验行为已对齐 1.2.0 | 改 | 过时 | 已修 |
| E8 | `README.md:89` | 引用 DESIGN"第 3.1 节" | 位置推断在 3.1.1、标签表在 3.1.2 | 改 | 措辞 | 已修 |
| E9 | `DESIGN.md:3` | "状态:草案" | M1～M5 均已完成并发布 | 改为已实现 | 过时 | 已修 |
| E10 | `DESIGN.md:47-56` | 3.1 节 `Endpoint` 结构体与代码无关;公开的 `Endpoint` 是快照里的运行时信息,声明用 `Operation` / `Op()` | 改写为链式声明 | 过时 | 已修 |
| E11 | `DESIGN.md:99-110` | `FieldRule` 有 `Path`、`Pattern`,`Required`/`Hidden` 是 `bool`;代码(`strategy/strategy.go:25-33`)没有 `Path`/`Pattern`,三个布尔是 `*bool`,多 `Validate` | 按代码改 | 过时 | 已修 |
| E12 | `DESIGN.md:221-250`(5.5.1) | 描述 Java 1.1.x 的校验行为("只写 required 什么都不校验""只有 `enabled=true`") | 加"1.1.x 的行为"提示,差别清单改为只剩"非必填字段传值也校验" | 过时 | 已修 |
| E13 | `DESIGN.md:223` | "45 种内置正则" | Java、Go 都是 46 项(`PATTERN_00`、`PATTERN_01` + `PATTERN_1`～`44`) | 改 | 错误 | 已修 |
| E14 | `CHANGELOG.md:32` | "46 条内置正则",官网写 `PATTERN_1`～`PATTERN_44` | 口径统一:44 条正则 + 2 个类型占位 | 措辞 | 已修 |
| E15 | `DESIGN.md:273,276` | devtools `Options` 缺 `DisableUI`、`MaxBodyBytes`;`Options.History` 实际在 `yaml.Options` | 补全 | 缺失 | 已修 |
| E16 | `DESIGN.md:288-300` | 目录树:`filter/` 含"请求校验";没有 `validate/`、`integration/`、`scripts/`;只列一个示例 | 更新 | 过时 | 已修 |
| E17 | `DESIGN.md:302` | "目前是单个 go.mod" | 另有 `integration/go.mod` | 补充 | 措辞 | 已修 |
| E18 | `DESIGN.md:314` | M5"待打 tag" | v0.1.0、v0.1.1 已发布 | 改 | 过时 | 已修 |

### F. 注解、扩展点、存储策略、返回过滤(第二批,另一路核对,注解属性表 24 个全部与源码一致)

| # | 页面:行号 | 问题 | 改法 | 严重程度 | 状态 |
|---|---|---|---|---|---|
| F1 | `guide/annotations.md:133` | `@RequestMapping` 没写 `method` 时列"POST、DELETE、GET 三种";代码(`ApiDocService.java:603-605`)是 GET、POST、PUT、DELETE 四种 | 改 | 错误 | 已修 |
| F2 | `api/annotations.md:3` | 首句"1.1.1 全部注解" | 改 1.2.0(1.2.0 没改注解,内容有效) | 过时 | 已修 |
| F3 | `api/java.md:5-16` | 总览缺 1.2.0 新增的 `ApiMethodHandle`、`ViolationRecorder`、`ApidocRulesRefreshedEvent` | 补三行并链接到校验页 | 缺失 | 已修 |
| F4 | `api/java.md:16` | "这些类型都在 csap-apidoc-core 中";`IMethodRequest`、`ApiMethodHandle` 等在 common | 改口径 | 措辞 | 已修 |
| F5 | `skills/.../yaml-rules.md:7-10`、`guide/storage-strategies.md:96-101` | 没说 YAML 文件必须以 `-method-field.yaml` 结尾、只认 `.yaml`(`.yml` 不读取) | 补 | 缺失 | 已修 |
| F6 | `guide/runtime-filter.md:3-15` | 没说只支持 Servlet 栈(`RuntimeFilterAutoConfiguration` 带 `@ConditionalOnWebApplication(SERVLET)`) | 补 | 缺失 | 已修 |
| F7 | `api/annotations.md:212-236`、`guide/annotations.md:348-388` | `@DocGlobalHeader` / `@DocAuth` 不继承、包级只读本包 `package-info.java`(`DocHintsCollector`) | 两页各补一句 | 缺失 | 已修 |
| F8 | `skills/.../SKILL.md` 第 3 节 | 没提 `@DocGlobalHeader`、`@DocAuth`、`@ApiResponseCode`、`@EnumValue/@EnumMessage` | 补一行 | 缺失 | 已修 |
| F9 | `api/annotations.md:109` | 基础类型参数 `required`"YAML / SQLite 策略下以规则中的为准";代码(`DefaultAbstractMethod.java:224,232`):文档显示取规则与 `@ApiProperty` 的"或",运行时校验登记只看规则 | 按代码改写 | 措辞 | 已修,**待裁定**取"或"是否有意 |
| F10 | `skills/.../yaml-rules.md:61-78` | "上级字段也要写 `include: true`";`storage-strategies.md` 的同类示例没写 `data`(因为 `ApidocResult.data` 是 `forceRep`) | 写清两种情形 | 措辞 | 已修,统一口径待裁定 |
| F11 | `api/annotations.md:258` | `@DataTypeValidate` 由"boot-starter 中的异常处理器"读取;类实际在 `csap-validation-core` | 改 | 措辞 | 已修 |
| F12 | `guide/runtime-filter.md:108` | 放行全部字段"多 25%～35%";CHANGELOG 是 24%～32% | 统一 | 措辞 | 已修 |

### G. GitHub 上读者会看到的文档(第二批)

| # | 文件:行号 | 文档怎么写 | 实际 | 改法 | 严重程度 | 状态 |
|---|---|---|---|---|---|---|
| G1 | `SECURITY.md` 支持版本表 | 只有 1.1.x ✅ | 当前 1.2.0,"安全修复以最新次版本线发布" | 加 1.2.x ✅,1.1.x 改 ❌(已被取代) | 过时 | 已修,**支持策略待裁定** |
| G2 | `.github/ISSUE_TEMPLATE/bug_report.md:32`、`question.md:40` | 版本示例 `1.1.0` | 1.1.0 是 Java 17 字节码,不应再引导用户用 | 改 `1.2.0` | 过时 | 已修 |
| G3 | `CHANGELOG.md` "Upcoming Features"(v2.0 / v3.0 / v4.0) | 计划里的"环境管理、请求历史、暗黑模式、i18n、TypeScript 生成"早已发布;版本规划与路线图(1.3.0 / 1.4.0 / 团队版)矛盾 | 官网"发布说明"页由它同步生成,会直接展示 | 换成指向路线图的一段 | 过时 | 已修 |
| G4 | `CHANGELOG.md` "Migration Guides" 只有"Installing 1.1.0" | 1.2.0 是最新 | 改为"Upgrading to 1.2.0"(校验 enforce、过滤默认关闭、别用 1.1.0) | 过时 | 已修(历史版本条目 `[1.2.0]`～`[1.0.0]` 未改) |
| G5 | `docs/maintainers/PUBLISHING.md` 发布清单 | 引用不存在的 `docs/guides/QUICK_START.md`、`docs/index.md`;例子版本 1.1.0 | 版本号分布在 `website/docs/**`、`rspress.config.ts`、`SKILL.md`、`ROADMAP.md`、`example/*/pom.xml` | 清单改为真实位置 | 过时 | 已修 |
| G6 | `docs/design/1.2.0-plan.md` 标题"2026-12-04 发布" | 已于 2026-11-17 发布 | 标题去日期,加"已完成"说明 | 过时 | 已修 |
| G7 | `docs/design/environment-auth-headers.md` 状态 "APPROVED — ready for M1" | 已在 1.1.1 实现 | 状态改 IMPLEMENTED,指向官网在线调试页 | 过时 | 已修 |
| G8 | `docs/design/m93-mock-server.md` | 状态"DESIGN — not yet implemented" | 路线图里是 1.4.0,未实现,准确 | 不改 | — | — |
| G9 | `CODE_OF_CONDUCT.md`、`.github/pull_request_template.md`、`.gitee/*.zh-CN.md`、`ISSUE_TEMPLATE/feature_request.md` | 核对事实与链接 | 无问题 | — | — | — |
| G10 | GitHub 仓库描述(`gh repo view`) | "Runtime API documentation for Spring Boot: annotation / YAML / SQLite field rules, response field filtering, request validation and in-browser try-it-out." | README 定位是"多语言的运行时 API 文档框架",Go v0.1.1 已发布;描述只写 Spring Boot,topics 没有 `go` / `golang` | 建议描述改为多语言口径,topics 增加 `go`、`golang`、`gin` | 过时 | **待主会话裁定**(只读,未改) |
| G11 | GitHub Release `v1.2.0` 发布说明 | 正文是英文 CHANGELOG 摘录,事实(坐标、Go 与其同时发布 v0.1.0、已知问题表)与代码一致;末尾写"Go: released together as csap-apidoc-go/v0.1.0" | 事实无误;Go 现在是 v0.1.1 | 可不改。如要改,加一句"Go 后续补丁见 csap-apidoc-go/v0.1.1" | 措辞 | **待主会话裁定**(只读,未改) |
| G12 | GitHub Release `csap-apidoc-go/v0.1.1` | 中文发布说明,内容与 `csap-apidoc-go/CHANGELOG.md` 一致,安装命令正确 | 无问题 | — | — | — |

### I. 示例与各模块 README(第三批,约 90 处,逐条明细见主会话笔记目录的 module-readmes-report.md)

已重写或修正:`example/README.md`、`example-spring-boot2|3/{README,ANNOTATIONS,FILES,STRATEGIES}.md`、`example-spring-boot3/SECURITY.md`、`csap-apidoc-core/README-Postman.md`、`csap-apidoc-devtools/{PROXY,EXAMPLE-APPLICATION}.md`、`csap-apidoc-devtools/devtools/README*.md`、`csap-apidoc-ui/README.md`、`csap-validation-core/VALIDATION_FRAMEWORK.md`(1676 → 1129 行,头部加"以官网为准")。主要问题类型:

- **使用了不存在的东西**(错误):`@ApiDoc` 注解、`csap.apidoc.enabled/title/base-package` 配置、`/api-doc` 接口、`PostmanConverterServiceTest` 等测试类、不存在的文档链接(`SPRING_BOOT_COMPATIBILITY.md`、`QUICK_START.md`、`TYPESCRIPT-MIGRATION.md`、`DEVTOOLS-DEPLOY.md`)、不存在的 Record DTO / HTTP Interface / Native Image / 文件上传下载示例。
- **编造内容**(错误):性能对比数字(启动快 28%、吞吐 10K→12K 等)、伪造的启动日志、"已验证"状态。已删除。
- **事实错误**:示例端口 8080(实际 8082 / 8083)、依赖 1.1.0、JDK 8、"`@ApiProperty` 不能用在参数上"(实际 `@Target` 含 `PARAMETER`)、profile 能切换存储策略(实际由 `@EnableApidoc` 固定)、`README-zh.md` 是 vue-admin-template 残留。

**未逐行核对**:`VALIDATION_FRAMEWORK.md` 的 84-334 行、使用示例、自定义注解、FAQ 其余条目只抽查;示例技术栈表里 Jackson / Hibernate Validator / Tomcat 版本按 Spring Boot BOM 填写,没有解析依赖树验证。

### H. 官网导航与链接

- 侧边栏、顶部导航:`guide/*` 17 页、`api/*` 3 页、`go/`、`download/`、路线图、发布说明全部已挂,无漏挂;`npm run build` 的死链检查通过。
- 侧边栏标签"存储策略与返回过滤"与该页 H1 一致(该页内含"返回字段过滤"一节,另有独立的"返回字段过滤"页,不冲突)。
- 使用文档按读者操作顺序(快速开始 → 安装 → 基础使用 → 配置)走查:依赖坐标、`@EnableApidoc`、注解属性(`@Api`、`@ApiOperation`、`@ApiProperty`、`@ApiModel`、`@ApiModelProperty` 的 `forceReq` / `forceRep` / `required` / `example`)均存在,片段可照抄;已改的是 Gradle 版本号和 starter 内容说明。

## 三、裁定结果与后续(用户已答复的项)

| 项 | 结论 | 文档状态 |
|---|---|---|
| 1. Go / Java 非必填字段校验不一致 | 讨论中,倾向 Java 向 Go 对齐(非必填但传了值也校验,`Pattern` 本就对 null 放行),属行为变更,只能放 1.3.0 之后并先用 `observe`;未定方向 | 官网 Go 页已写明差别;对齐方向列入 1.4.0 前确定(见路线图) |
| 2. `/devtools/hiddenMethod` 没作用 | 文档备注"目前没用" | 已写在 `api/rest.md`、`guide/devtools.md` |
| 3. `AdvancedController` 演示方法级 `@ApiProperty` | 方法级写法按弃用处理,1.3.0 中示例改为参数级 | 文档保持"方法级不生效" |
| 4. 示例配置与注释不符、示例里没有 `@Group` | 用户倾向**直接去掉 `@Group` 注解**(不再使用) | **未改文档**:`@Group` 目前仍在代码里并且是注解策略下"按接口选字段"的主要写法(`guide/annotations.md`、`storage-strategies.md`、`api/annotations.md`、SKILL 都有介绍),必须代码先删除或标记弃用,文档才能同步;影响面见下 |
| 5. 基础类型参数必填 | 用户:**以实际配置(规则)为准**,即规则里的 `required` 优先 | 代码现为"规则与 `@ApiProperty` 取或",文档按代码写;改代码后文档改回"以规则为准" |
| 6. YAML 上级字段 `include: true` | 已向用户解释(返回规则里,`data` 这样的外层字段本身要被选中,下级字段才会输出;`ApidocResult.data` 已是 `forceRep` 所以不用写) | 已按两种情形写在 `yaml-rules.md` |
| 7. `SECURITY.md` 只支持最新次版本线 | 同意 | 已改 |
| 8. GitHub 仓库描述 / topics / v1.2.0 发布说明要写清 Go | 同意,**已在 GitHub 上修改** | 描述改为多语言口径(Java / Spring Boot 2 & 3、Go / gin & net/http);topics 增加 `go`、`golang`、`gin`;v1.2.0 发布说明在 Go 一行后补"Go 单独版本、当前 v0.1.1、go get 命令、官网 Go 页" |
| 9. 示例工程代码/配置问题 | 用户:需要权衡后解决 | 属代码改动,文档已如实说明;待安排 |
| 10. `devtools/README-zh.md`(vue-admin-template 残留) | 无用,**已删除** | 已删 |

`@Group` 若删除的影响:注解策略下"同一模型按接口显示不同字段"只剩 `forceReq`/`forceRep`(全接口生效)和 YAML / SQLite 策略;`@ApiModelProperty.groups`、`@Group`、`@Request`、`@Response` 四个注解/属性会一起失效;`AnnotationParamGroupStrategy`、示例、官网"同一个模型,在不同接口里显示不同字段"这条核心卖点的"注解"方式都要改。

## 四、顺带发现,未处理(超出"只改文档"的范围)

- `.github/workflows/docs.yml` 的 `paths` 只监听 `website/**` 和 `CHANGELOG.md`,没有 `docs/ROADMAP.md`;路线图页由 `sync.mjs` 从它生成,改了路线图不会触发站点发布。
- `csap-apidoc-devtools/devtools` 前端没有 `test` 脚本(CONTRIBUTING 已改为指向 `csap-apidoc-ui`)。

## 五、主会话执行记录(2026-11-18)

1. 非必填字段的校验差别:暂不改代码,在官网 Go 页写明差别;是否对齐、以哪边为准,1.4.0 前确定。
2. 方法级 `@ApiProperty` / `@ApiPropertys`:按已弃用处理。1.3.0 中示例改为参数级写法,`DefaultAbstractMethod` 的 javadoc 同步改正。
3. `SECURITY.md`:只支持 1.2.x,1.1.x 标为已被取代,保持本次修改。
4. GitHub 仓库描述与 topics:改为多语言口径,topics 增加 `go`、`golang`、`gin`(已在仓库设置中修改)。
5. F9、F10 按本次写法保留;v1.2.0 发布说明按第 8 项补了 Go 的说明。
