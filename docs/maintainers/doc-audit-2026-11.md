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

## 三、待主会话裁定

1. **非必填字段上的校验规则,Go 与 Java 行为不同**(`csap-apidoc-go/validate/validate.go:352-358` 与 `website/docs/guide/validation.md` "只有必填字段的规则会执行")。Go 在非必填字段传了值时也按规则校验;Java 1.2.0 只在 `required: true` 时登记并执行。同一份 YAML 在两边结果不同,Go 更严格。`DESIGN.md` 已把它写成"有意为之",本次只在 DESIGN 里如实记录,**官网 Go 页和 Go 的 `CHANGELOG.md` "与 Java 版的差别"清单没有改**:需要裁定这是有意保留的差别(那就补进这两处),还是要在代码里对齐 Java。
2. **`/devtools/hiddenMethod` 目前什么也不做**(`ApidocDevtools.java:1221` 直接返回 `true`)。文档已如实写成"没有实际作用",但如果这是未完成的功能而不是有意保留,可能要在 1.3.0 里处理或从文档中去掉该接口。

## 四、顺带发现,未处理(超出"只改文档"的范围)

- `.github/workflows/docs.yml` 的 `paths` 只监听 `website/**` 和 `CHANGELOG.md`,没有 `docs/ROADMAP.md`;路线图页由 `sync.mjs` 从它生成,改了路线图不会触发站点发布。
- `csap-apidoc-devtools/devtools` 前端没有 `test` 脚本(CONTRIBUTING 已改为指向 `csap-apidoc-ui`)。
