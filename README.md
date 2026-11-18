<div align="center">

# CSAP Apidoc

**多语言的运行时 API 文档框架**

服务启动即生成接口文档 · 按接口控制显示哪些字段 · 文档页内在线调试

[![Maven Central](https://img.shields.io/maven-central/v/ai.csap.apidoc.boot/csap-apidoc-boot-starter?label=Maven%20Central)](https://central.sonatype.com/namespace/ai.csap.apidoc)
[![CI](https://github.com/csap-ai/csap-apidoc/actions/workflows/ci.yml/badge.svg)](https://github.com/csap-ai/csap-apidoc/actions/workflows/ci.yml)
[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](LICENSE)
[![Website](https://img.shields.io/badge/docs-apidoc.csap.ai-4f46e5)](https://apidoc.csap.ai)

简体中文 | [English](README.en.md)

</div>

## 是什么

CSAP Apidoc 在服务运行时直接从代码生成 API 文档,不需要单独写文档文件,也不需要额外的文档服务。你在 Controller 和模型上加注解(或者写 YAML 规则),启动后打开 `/csap-api.html` 就是最新的文档,并且可以在页面里直接调试接口。

文档协议、规则文件格式和文档页面在各语言之间共用:不同语言写的服务,用同一个文档页面、同一种规则文件。

## 语言支持

| 语言 | 状态 | 支持的框架 |
|---|---|---|
| **Java** | ✅ 已发布 [1.2.0](https://central.sonatype.com/namespace/ai.csap.apidoc) | Spring Boot 2.x / 3.x |
| **Go** | ✅ 已发布 [v0.1.1](https://pkg.go.dev/github.com/csap-ai/csap-apidoc/csap-apidoc-go)(`go get github.com/csap-ai/csap-apidoc/csap-apidoc-go@v0.1.1`) | gin、net/http |
| **Rust** | 📋 规划中 | axum、actix-web |
| 更多语言 | 📋 陆续支持 | 见 [路线图](docs/ROADMAP.md) |

## 已有能力

以下能力 Java 和 Go 都有(Go 的返回过滤和校验见 [Go 版](https://apidoc.csap.ai/go/));标注「Java」的目前只有 Java 版。

- **运行时生成文档**:启动时从代码生成文档(Java 扫描注解,Go 读取路由和结构体标签),访问 `/csap-api.html` 查看。
- **字段规则**:用注解(Java)、结构体标签(Go)、YAML 文件或 SQLite / SQL 描述每个接口显示哪些请求参数和返回字段;同一个模型在不同接口里可以显示不同字段。
- **在线调试**:在文档页直接发请求,支持环境切换、全局请求头、鉴权、请求历史与重放、命名快照导入导出。
- **导出**:在文档页导出 OpenAPI 3.0、Postman v2.1、Markdown,或复制为 TypeScript 类型。
- **DevTools**:开发环境下用可视化界面勾选字段、配置校验规则,写回 YAML。
- **网关聚合**(Java):一个页面切换查看多个服务的文档。
- **返回字段过滤**(Java 1.2.0 起,默认关闭;Go 配了返回规则的接口默认过滤):按接口规则只返回文档里显示的字段,支持 Jackson、fastjson、fastjson2,见 [返回字段过滤](https://apidoc.csap.ai/guide/runtime-filter)。
- **请求参数校验**(Java 1.2.0 起,默认关闭;Go 默认关闭):关闭 / 观察 / 拦截三种模式,可按接口覆盖,见 [参数校验](https://apidoc.csap.ai/guide/validation)。

## 快速开始(Java)

**1. 添加依赖**:

```xml
<dependency>
    <groupId>ai.csap.apidoc.boot</groupId>
    <artifactId>csap-apidoc-boot-starter</artifactId>
    <version>1.2.0</version>
</dependency>
```

**2. 开启文档**,写上要扫描的包:

```java
@SpringBootApplication
@EnableApidoc("com.example.demo")
public class DemoApplication {
    public static void main(String[] args) {
        SpringApplication.run(DemoApplication.class, args);
    }
}
```

**3. 加注解**:

```java
@RestController
@RequestMapping("/api/users")
@Api(value = "用户管理")
public class UserController {

    @GetMapping("/{id}")
    @ApiOperation("查询用户")
    public User get(@ApiProperty(value = "用户 ID", required = true) @PathVariable Long id) { ... }
}

@ApiModel(description = "用户")
public class User {
    @ApiModelProperty(value = "用户 ID", forceRep = true)
    private Long id;
    @ApiModelProperty(value = "用户名", forceReq = true, forceRep = true)
    private String name;
}
```

**4. 启动**,打开 <http://localhost:8080/csap-api.html>。

字段为什么要写 `forceReq` / `forceRep`、怎样按接口选择字段,见 [快速开始](https://apidoc.csap.ai/guide/quick-start)。

## 快速开始(Go)

```bash
go get github.com/csap-ai/csap-apidoc/csap-apidoc-go@v0.1.1
```

```go
doc := apidoc.New(apidoc.Options{})
r := gin.New()
g := ginadapter.Mount(r, doc) // 挂 /csap/apidoc*、/csap/openapi*、/csap-api.html
g.GET("/api/v1/users/:id",
    apidoc.Op("example.UserController", "get").Summary("查询用户").Req(GetUserReq{}).Resp(User{}),
    getUser)
r.Run(":8080")
```

启动后打开 <http://localhost:8080/csap-api.html>。标准库 `net/http`、结构体标签、返回过滤、校验和后台管理见 [Go 版](https://apidoc.csap.ai/go/) 和 [csap-apidoc-go/README.md](csap-apidoc-go/README.md)。

## 文档

完整文档在 **<https://apidoc.csap.ai>**:

- [介绍](https://apidoc.csap.ai/guide/overview) · [快速开始](https://apidoc.csap.ai/guide/quick-start) · [安装](https://apidoc.csap.ai/guide/installation) · [配置参考](https://apidoc.csap.ai/guide/configuration)
- [注解](https://apidoc.csap.ai/guide/annotations) · [存储策略](https://apidoc.csap.ai/guide/storage-strategies) · [DevTools](https://apidoc.csap.ai/guide/devtools) · [在线调试](https://apidoc.csap.ai/guide/testing) · [导出](https://apidoc.csap.ai/guide/export)
- [REST 接口](https://apidoc.csap.ai/api/rest) · [注解参考](https://apidoc.csap.ai/api/annotations) · [Java 扩展点](https://apidoc.csap.ai/api/java)
- [发布说明](CHANGELOG.md) · [路线图](docs/ROADMAP.md)

## 模块(Java)

Go 版是单个 Go 模块 `github.com/csap-ai/csap-apidoc/csap-apidoc-go`,见上文。

| 坐标 | 说明 |
|---|---|
| `ai.csap.apidoc.boot:csap-apidoc-boot-starter` | Spring Boot starter,包含核心、文档页面、YAML/SQLite 策略、DevTools、返回字段过滤 |
| `ai.csap.apidoc.boot:csap-validation-boot-starter` | 参数校验 starter,可选:需要运行时参数校验、它的校验异常处理器或 `ValidationProperties` 时引入 |
| `ai.csap.apidoc:csap-apidoc-annotation` | 注解 |
| `ai.csap.apidoc:csap-apidoc-core` | 扫描与文档生成 |
| `ai.csap.apidoc:csap-apidoc-ui` | 文档页面 |
| `ai.csap.apidoc:csap-apidoc-devtools` | DevTools 后台 |
| `ai.csap.apidoc:csap-apidoc-runtime` | 返回字段过滤(1.2.0 起) |
| `ai.csap.apidoc:csap-apidoc-yaml` / `csap-apidoc-sqlite` / `csap-apidoc-standard` | 规则存储策略 |
| `ai.csap.apidoc:csap-validation-core` | 校验引擎 |

源码目录:Java 各模块在仓库根目录;Go 实现在 `csap-apidoc-go/`;官网在 `website/`。

## AI 编码助手

仓库提供 [`csap-apidoc` 技能](skills/csap-apidoc/SKILL.md),装进 Claude Code 等支持 Agent Skills 的助手后,它就知道如何给 Java 或 Go 项目接入和维护 CSAP Apidoc,见 [安装说明](https://apidoc.csap.ai/guide/ai-skill)。

## 参与贡献

欢迎提 Issue 和 Pull Request,请先阅读 [贡献指南](CONTRIBUTING.md)。安全问题请按 [安全策略](SECURITY.md) 私下报告,不要公开提 Issue。

## 协议

[Apache License 2.0](LICENSE)
