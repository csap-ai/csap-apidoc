---
name: csap-apidoc
description: 为 Java Spring Boot 项目接入和维护 CSAP Apidoc(运行时 API 文档框架,Maven 坐标 ai.csap.apidoc):添加依赖与 @EnableApidoc、写 @Api/@ApiOperation/@ApiModel/@ApiModelProperty 等注解、按接口选择显示字段(forceReq/forceRep、@Group、YAML 规则 *-method-field.yaml)、配置 csap.apidoc.* 与 DevTools、读取 /csap/apidoc 文档数据。当用户提到 CSAP Apidoc、csap-apidoc、csap-api.html、method-field.yaml、@EnableApidoc,或要给使用了这些的项目加接口文档、改字段显示、排查"文档里看不到字段/接口"时使用。Use for integrating or maintaining CSAP Apidoc in Spring Boot projects.
---

# CSAP Apidoc

CSAP Apidoc 在服务启动时扫描注解生成接口文档,页面地址 `/csap-api.html`。官网 <https://apidoc.csap.ai>,仓库 <https://github.com/csap-ai/csap-apidoc>。本技能对应 **Java 1.1.x**;Go 版尚未发布,不要给 Go 项目套用下面的 Java 写法。

动手前先看项目现状:`pom.xml` / `build.gradle` 里有没有 `ai.csap.apidoc`、启动类上有没有 `@EnableApidoc`、`src/main/resources/csap-api-doc/method-field/` 下有没有 YAML 规则。已有接入时沿用项目现有的策略和写法。

## 1. 依赖

```xml
<dependency>
    <groupId>ai.csap.apidoc.boot</groupId>
    <artifactId>csap-apidoc-boot-starter</artifactId>
    <version>1.1.1</version>
</dependency>
```

- 坐标只有 `ai.csap.apidoc(.boot)`。旧文档里的 `com.csap.framework.boot:csap-framework-apidoc-boot-starter` **不存在**,看到要改掉。
- starter 已包含文档页面、YAML/SQLite 策略、DevTools,不要另加。
- `ai.csap.apidoc.boot:csap-validation-boot-starter` 是可选的,只在需要它的校验异常处理器(`ValidateExceptionHandler`)或 `csap.validation.*` 配置(`ValidationProperties`)时引入。
- JDK 11+;Spring Boot 3 需要 JDK 17+。1.1.0 的 jar 实际是 Java 17 字节码,Java 11 上无法加载;项目还在用 1.1.0 时建议升级到 1.1.1。

## 2. 开启

```java
@SpringBootApplication
@EnableApidoc("com.example.demo")   // 必须写扫描包,否则页面为空
public class DemoApplication { ... }
```

`@EnableApidoc` 在 `ai.csap.apidoc.boot.autoconfigure` 包。扫描范围**只能**由它指定;`csap.apidoc.base-package`、`csap.apidoc.enabled`、`csap.apidoc.title` 这类配置项**不存在**,不要写。文档标题等用 `csap.apidoc.api-info.*`,完整配置见 <https://apidoc.csap.ai/guide/configuration>。

## 3. 注解(包 `ai.csap.apidoc.annotation`)

- Controller:`@RestController` + `@Api(value = "名称")`,缺一个都不会出现在文档里。
- 方法:请求映射注解 + `@ApiOperation("接口名称")`(`value` 必填)。
- 基础类型参数:`@ApiProperty(value = "说明", required = true)`,总会出现在请求参数里。
- 模型类:`@ApiModel(description = "...")`(只能用在类上,不能用在参数上)。
- 模型字段:`@ApiModelProperty(value = "...")`。**没有这个注解的字段永远不出现。**

## 4. 字段为什么"不显示"(最常见的问题)

同一个模型在不同接口里可以显示不同字段,所以字段**默认不出现在任何接口**,必须被"选中":

1. `forceReq = true` / `forceRep = true`:在所有用到该模型的接口的请求 / 返回中显示。最简单。
2. 按接口选择,取决于 `@EnableApidoc(paramType = ...)` 选的策略(三种策略**互不合并**):
   - `ANNOTATION`(默认):`@ApiModelProperty(groups = @Group(value = "方法名", request = @Request(required = true), response = @Response))`
   - `YAML`:规则写在 `src/main/resources/csap-api-doc/method-field/<Controller简单类名>-method-field.yaml`,格式见 [references/yaml-rules.md](references/yaml-rules.md)
   - `SQL_LITE`:规则存 SQLite,通常用 DevTools 维护
3. 返回字段在规则里要 `include: true` 才出现;请求字段只要规则里有就出现。
4. 对象 / List / Map 字段:它自己出现了,子字段才会被处理。

排查顺序:字段有没有 `@ApiModelProperty` → 是否 `hidden` / `ignoreReq` / `ignoreRep` → 有没有 force → 当前策略的规则里有没有它(YAML 的键名写对没有,见参考文件)。

## 5. 文档数据

| 地址 | 内容 |
|---|---|
| `GET /csap-api.html` | 文档页面 |
| `GET /csap/apidoc` | 完整文档 JSON,外层 `{code:"0", message, data, language, time, success}` |
| `GET /csap/apidoc/parent` | 文档树 |
| `GET /csap/apidoc/method?name=<Controller全名>&key=<Controller全名>.<方法名>` | 单个接口详情 |
| `GET /csap/openapi/postman` | Postman Collection |

需要了解一个服务有哪些接口、参数是什么时,优先读 `/csap/apidoc`,不要去猜。

## 6. DevTools

开发环境开启 `csap.apidoc.devtool.enabled: true`(默认关闭),访问 `/devtools-ui` 勾选字段、配置校验规则并写回 YAML。**不要在生产环境开启**。

## 7. 1.1.x 的限制(写代码和回答问题时要说清楚)

- 规则里的返回字段 `include` 和请求 `validate` **只影响文档**;运行时的返回字段过滤和参数校验在开源版 1.1.x 中不执行,计划 1.2.0 提供。不要向用户承诺"配了 include 接口就不会返回这个字段"。
- 1.1.0 jar 里的文档页面是旧版构建,没有环境切换、全局请求头、鉴权、历史与重放、命名快照、中英文;这些从 1.1.1 起可用。
- 手工改 YAML 后需要重启;DevTools 修改会即时生效。

## 8. 写完后的检查

1. 启动日志里有 `CSAP API Documentation Generated Successfully!` 和文档地址;出现"N 个文档加载失败"说明依赖或注解有问题。
2. `curl -s localhost:<port>/csap/apidoc | head` 能看到新接口。
3. 打开 `/csap-api.html` 确认字段显示符合预期。

详细说明以官网为准:<https://apidoc.csap.ai>。
