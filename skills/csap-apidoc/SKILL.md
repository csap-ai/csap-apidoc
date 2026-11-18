---
name: csap-apidoc
description: 为项目接入和维护 CSAP Apidoc(多语言的运行时 API 文档框架,服务启动即生成接口文档,页面 /csap-api.html)。当前覆盖 Java(Spring Boot 2/3,Maven 坐标 ai.csap.apidoc / ai.csap.apidoc.boot)和 Go(gin、net/http,模块 github.com/csap-ai/csap-apidoc/csap-apidoc-go),后续语言陆续加入。能做:添加依赖并开启(Java 的 @EnableApidoc;Go 的 go get、apidoc.New、apidoc.Op)、写注解(Java 的 @Api/@ApiOperation/@ApiModel/@ApiModelProperty)或结构体标签(Go 的 doc、doc_required、doc_force 等)、按接口选择显示字段(forceReq/forceRep、YAML 规则 *-method-field.yaml,两种语言共用同一种规则文件)、运行时返回字段过滤与请求参数校验、DevTools 后台、读取 /csap/apidoc 文档数据。当用户提到 CSAP Apidoc、csap-apidoc、csap-apidoc-go、csap-api.html、method-field.yaml、@EnableApidoc、apidoc.Op、apidoc.New,或要给使用这些的 Java / Go 项目加接口文档、改字段显示、排查"文档里看不到字段/接口"时使用。Use for integrating or maintaining CSAP Apidoc (a multi-language runtime API documentation framework; Java / Spring Boot and Go / gin, net/http today) in a project.
---

# CSAP Apidoc

CSAP Apidoc 是**多语言的运行时 API 文档框架**:服务启动时从代码生成接口文档,不需要单独写文档文件,也不需要另外部署文档服务。官网 <https://apidoc.csap.ai>,仓库 <https://github.com/csap-ai/csap-apidoc>。

| 语言 | 当前版本 | 框架 | 详细写法 |
|---|---|---|---|
| Java | 1.2.0 | Spring Boot 2.x / 3.x | [references/java.md](references/java.md) |
| Go | v0.1.1 | gin、net/http | [references/go.md](references/go.md) |
| 其他语言 | 规划中 | | 见官网路线图,目前没有可用实现 |

两种语言**共用**:文档数据协议(`/csap/apidoc*` 的 JSON)、文档页面 `/csap-api.html`、DevTools 前端、YAML 字段规则文件格式([references/yaml-rules.md](references/yaml-rules.md))。**接入方式、注解 / 标签、配置各不相同**,不要把一种语言的写法套到另一种上。

## 第一步:先判断项目是什么语言

| 看到什么 | 语言 |
|---|---|
| `pom.xml` / `build.gradle`、Spring Boot、`@RestController` | Java → 读 [references/java.md](references/java.md) |
| `go.mod`、gin 或 `http.ServeMux`、`gin.Engine` | Go → 读 [references/go.md](references/go.md) |
| 其他语言 | 目前没有实现,如实告知,不要编造接入方式;指向官网路线图 |

再看是否已经接入:Java 找 `ai.csap.apidoc` 依赖和 `@EnableApidoc`;Go 找 `csap-apidoc-go` 依赖和 `apidoc.New`。已有接入时沿用项目现有的策略和写法。一个仓库里同时有 Java 和 Go 服务时,各自按各自的语言处理,规则文件格式相同。

## 共通概念

- **运行时生成文档**:启动时扫描代码(Java 扫注解,Go 读路由、结构体和标签)生成文档,访问 `/csap-api.html` 查看,页面内可以直接调试接口(环境、全局请求头、鉴权、历史、快照)。
- **字段规则**:同一个模型在不同接口里可以显示不同字段,所以字段默认不会出现在每个接口里,要被"选中"。选中的来源:代码里的标记(Java 的 `forceReq` / `forceRep`,Go 的 `doc_force`)、YAML 规则文件、SQL / SQLite(Java 的 SQLite;Go 的 SQLite / PostgreSQL / MySQL)。Java 另有注解策略下的 `@Group`。
- **返回字段过滤**:让接口实际返回的 JSON 只含文档里显示的字段。Java 1.2.0 起可用,**默认关闭**(`csap.apidoc.runtime.filter.enabled`);Go 需要注册 `ResponseFilter` 中间件,注册后配了返回规则的接口才会过滤(默认路径白名单模式,`apidoc.Options.FilterMode` 可改)。
- **请求参数校验**:按规则校验必填、正则、长度等。Java 1.2.0 起可用,**默认关闭**(`csap.validation.mode`);Go 的 `validate.Options.Mode` 默认也是 `Off`。都有 `observe`(只记录不拦截)和 `enforce`(拦截)两种开启方式,建议先观察。
- **DevTools**:开发阶段的后台页面 `/devtools-ui`,勾选字段、配置校验规则并写回规则文件。**两种语言默认都不开启,不要在生产环境开启**;Go 必须配置鉴权才能创建。
- **文档数据接口**(两种语言路径一致):

| 地址 | 内容 |
|---|---|
| `GET /csap-api.html` | 文档页面 |
| `GET /csap/apidoc` | 完整文档 JSON,外层 `{code:"0", message, data, language, time, success}` |
| `GET /csap/apidoc/parent` | 文档树 |
| `GET /csap/apidoc/method?name=<分组全名>&key=<分组全名>.<接口名>` | 单个接口详情 |
| `GET /csap/openapi.json` | OpenAPI(Java 导出 3.0.3,Go 导出 3.1) |
| `GET /csap/openapi/postman` | Postman Collection |

需要了解一个服务有哪些接口、参数是什么时,优先读 `/csap/apidoc`,不要去猜。

## 两种语言的主要差别

| 方面 | Java | Go |
|---|---|---|
| 声明接口 | `@Api` + `@ApiOperation` + Spring 映射注解 | 注册路由时用 `apidoc.Op(分组, 接口名)...` 声明请求 / 返回结构体 |
| 描述字段 | `@ApiModelProperty` | 结构体标签 `doc`、`doc_desc`、`doc_example`、`doc_required`、`doc_force` 等 |
| 字段"选中" | `forceReq` / `forceRep`、`@Group`、YAML、SQLite | `doc_force`、YAML、SQL(没有 `@Group`) |
| 规则文件命名 | 文件名必须以 `-method-field.yaml` 结尾,`.yml` 不读取 | 目录下所有 `.yaml` / `.yml` 都加载,文件名不限 |
| 规则修改生效 | 手工改 YAML 需重启;DevTools 修改即时生效 | `doc.Watch` 监听,文件改了自动重新加载 |
| 返回过滤 | `csap.apidoc.runtime.filter.*`,默认关闭;按 `mode: path / csap / off` | `Options.FilterMode`(`ModePath` 默认 / `ModeCsap` / `ModeOff`)、`FilterEndpoints`、`ShouldFilter`,只裁剪 2xx |
| 校验 | `csap.validation.mode`,需引入 `csap-validation-boot-starter` | `validate.New(doc, Options{Mode})` + 适配器的 `RequestValidator` 中间件 |
| 非必填字段上的规则 | **不执行**:只有 `required: true` 的字段才登记并执行规则 | **执行**:请求里传了这个字段就按规则校验,没传才跳过 |
| 模型显示名 | `@ApiModel(value)` | `Options.TypeNames` 或类型实现 `ApidocName()` |
| 内置正则 | `PATTERN_00`～`PATTERN_44` | 同一份名字,Go 版已修正 Java 里写错的几条 |
| DevTools | `csap.apidoc.devtool.enabled=true` | `devtools.New(doc, Options{Auth: ...})` 并挂载,`Auth` 必填 |

两个要特别说清楚的差别:

- **非必填字段的校验**:同一份 YAML 规则在 Go 上可能比 Java 更严格。两边是否对齐、以哪边为准,计划在 1.4.0 前确定;在此之前不要向用户承诺两边行为完全相同。
- **YAML 文件名**:给 Java 项目写规则文件务必以 `-method-field.yaml` 结尾;只在 Go 项目里使用时 `.yml` 也行,但两边共用同一份文件时按 Java 的要求命名。

## 不要承诺的事

- 不要向用户承诺"配了 include 接口就不会返回这个字段":Java 要开启 `csap.apidoc.runtime.filter.enabled`(1.2.0 起),项目还在用 1.1.x 时不会过滤;Go 只裁剪 2xx 响应,规则路径和返回类型对不上时原样输出。
- 不要承诺校验已生效:Java 默认 `off`,Go 默认 `Off`;校验规则在文档生成之后才生效。
- 旧文档里的 `com.csap.framework.boot:csap-framework-apidoc-boot-starter` 坐标、`csap.apidoc.base-package` / `csap.apidoc.enabled` / `csap.apidoc.title` 这类配置项**不存在**,看到要改掉。

## 写完后的检查

1. 启动并访问 `/csap-api.html`,确认接口和字段显示符合预期。
2. `curl -s localhost:<端口>/csap/apidoc | head` 能看到新接口。
3. 各语言专属的检查(启动日志、规则文件位置等)见各自的 references 文件。

详细说明以官网为准:Java 见 <https://apidoc.csap.ai/guide/overview>,Go 见 <https://apidoc.csap.ai/go/>。
