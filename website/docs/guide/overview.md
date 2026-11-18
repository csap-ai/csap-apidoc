# 介绍

本页说明 CSAP Apidoc 是什么、能做什么、适合哪些项目，以及它和 Swagger / Knife4j 的区别。

## CSAP Apidoc 是什么

CSAP Apidoc 是一个多语言的运行时 API 文档框架,以 Apache-2.0 协议开源(仓库:[csap-ai/csap-apidoc](https://github.com/csap-ai/csap-apidoc))。

服务启动时从代码生成文档:Java 版扫描注解,Go 版读取路由、结构体和标签。把接口、请求参数、返回字段、校验规则整理成文档数据,并在服务内提供文档页面 `/csap-api.html`。不需要单独写文档文件,也不需要单独部署文档服务。

## 语言支持

文档协议(`/csap/apidoc` 接口的 JSON 结构)、规则文件格式(`method-field.yaml`)和文档页面在各语言之间共用,所以不同语言写的服务用的是同一个文档页面、同一种规则文件。

| 语言 | 状态 | 支持的框架 |
| --- | --- | --- |
| Java | 已发布,当前版本 **1.3.0**(1.1.0 实际需要 Java 17,请使用 1.1.1 及以上) | Spring Boot 2.x / 3.x |
| Go | 已发布,当前版本 **v0.1.1**,见 [Go 版](/go/) | gin、net/http |
| Rust | 规划中 | axum、actix-web |
| 更多语言 | 陆续支持 | — |

本站的指南(快速开始、安装、配置、注解等)以 **Java 版**为准,每页开头会注明适用范围;Go 版的接入、标签、过滤和校验集中在 [Go 版](/go/) 一页。文档页面、在线调试、导出和 YAML 规则文件格式两种语言通用。

## 核心能力

以下「从代码生成文档」一节讲 Java 版的写法;Go 版没有注解,用结构体标签和 `apidoc.Op` 声明接口,见 [Go 版](/go/)。

### 从代码生成文档(Java 版)

- 在启动类上加 `@EnableApidoc` 并指定要扫描的包，框架会找出同时带有 `@Controller`（或 `@RestController`）和 `@Api` 的类。
- 类中带有 `@ApiOperation` 和请求映射注解的方法会成为一个接口文档。
- 模型字段用 `@ApiModelProperty` 描述；字段上的 `javax.validation` / `jakarta.validation` 约束（如 `@NotBlank`、`@Min`）会作为校验规则显示在文档中。
- 枚举可以用 `@EnumValue` / `@EnumMessage` 标出取值和说明，文档里会列出枚举值。

### 同一个模型，在不同接口里显示不同字段

这是 CSAP Apidoc 和常见文档工具最大的不同：一个模型类被多个接口共用时，每个接口可以只显示自己用到的字段。字段是否出现在某个接口里，可以由三种方式决定：

- 注解：`@ApiModelProperty` 的 `forceReq` / `forceRep`（在所有接口中都显示）、`ignoreReq` / `ignoreRep`（在所有接口中都隐藏），以及按方法名指定的 `groups`。
- YAML 文件：字段规则写在项目资源目录下的 YAML 文件里。
- SQLite 数据库：字段规则存在一个 SQLite 文件中。

后两种方式可以配合 DevTools 页面可视化编辑。详见 [存储策略](/guide/storage-strategies) 和 [注解](/guide/annotations)。

### 文档页面

引入 starter 后，应用会提供 `/csap-api.html` 文档页面，主要功能：

- 左侧接口列表，支持搜索；按分组、版本筛选。
- 每个接口的参数文档：参数名、类型、是否必填、默认值、示例、描述、枚举值、校验规则，以及返回数据结构。
- 在线测试：填写参数后直接发送请求。
- 把参数结构复制为 TypeScript 类型定义。
- 导出为 OpenAPI 3.0、Postman Collection 或 Markdown。
- 配置了多个文档源时，可以在页面上切换服务（用于网关聚合）。

### 其他模块

- **参数校验**：可选的 `csap-validation-boot-starter` 提供统一的校验异常处理和 `csap.validation.*` 配置。1.2.0 起可以按文档规则在运行时校验请求参数，支持观察和拦截两种模式，默认关闭；1.1.x 中不执行，见 [参数校验](/guide/validation)。
- **返回字段过滤**：1.2.0 起，`csap.apidoc.runtime.filter.enabled=true` 后，接口实际返回的 JSON 只保留文档里显示的字段，默认关闭，见 [返回字段过滤](/guide/runtime-filter)。
- **DevTools**：开发阶段的可视化工具，用来扫描接口、勾选每个接口的请求 / 返回字段并写回 YAML 或 SQLite。只有显式开启 `csap.apidoc.devtool.enabled=true` 时才会注册。见 [DevTools](/guide/devtools)。
- **网关聚合**：通过 `csap.apidoc.resources` 把多个服务的文档汇总到一个页面。见 [网关聚合](/guide/gateway-aggregation)。

## 适合哪些项目

- Java:基于 Spring Boot 2.x 或 3.x、使用 Spring MVC 编写 REST 接口的项目。仓库中分别提供了 Spring Boot 2.7 和 3.2 的示例工程。
- Go:使用 gin 或标准库 `net/http`(Go 1.22+)的项目,见 [Go 版](/go/)。
- 一个模型类被多个接口复用、但每个接口实际只用到其中一部分字段的项目，例如新增、修改、详情共用一个实体类。
- 希望文档页面直接集成在应用里、不想另外部署文档服务的团队。
- 多个微服务需要在网关处统一查看文档的场景。

Java 版的运行环境要求 JDK 11 及以上（1.1.1 起；1.1.0 的 jar 实际需要 JDK 17）；Spring Boot 3 项目需要 JDK 17 及以上（这是 Spring Boot 3 本身的要求）。

## 和 Swagger / Knife4j 的区别

Swagger 系列工具（如 springdoc-openapi、springfox）以 OpenAPI 规范为核心，从代码生成 OpenAPI 文档；Knife4j 是基于 Swagger / OpenAPI 的增强文档界面。CSAP Apidoc 的做法不同，主要区别如下：

| 方面 | CSAP Apidoc |
| --- | --- |
| 注解（Java 版） | 使用自己的注解（包名 `ai.csap.apidoc.annotation`）。`@Api`、`@ApiOperation`、`@ApiModel`、`@ApiModelProperty` 与 Swagger 2 注解同名，但包名和属性不同，Swagger 注解不会被识别。 |
| 文档数据 | 通过自己的接口（如 `/csap/apidoc`）提供文档数据，格式不是 OpenAPI。需要 OpenAPI 时，在文档页面导出 OpenAPI 3.0 文件，或者访问 `GET /csap/openapi.json`（1.2.0 起）。 |
| 字段粒度 | 字段可以按接口选择显示，规则可以放在注解、YAML 或 SQLite 中。 |
| 校验 | 文档展示的校验规则会登记到校验模块，1.2.0 起可以在运行时按这些规则校验请求参数（默认关闭，先观察后拦截）。 |
| 界面 | 文档页面由 `csap-apidoc-ui` 提供，随 starter 一起引入，访问 `/csap-api.html`。 |

如果你的项目已经在用 Swagger 注解，迁移时需要把 import 换成 `ai.csap.apidoc.annotation.*`，并逐个核对属性名；不能直接复用。

## 下一步

- [快速开始](/guide/quick-start)：5 分钟跑起第一个文档页面。
- [安装](/guide/installation)：依赖、模块说明和 Spring Boot 2 / 3 的差别。
- [配置参考](/guide/configuration)：全部配置项。
