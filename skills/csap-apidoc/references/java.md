# Java 版(Spring Boot 2.x / 3.x)

当前版本 1.3.0。本文件只适用于 Java 项目;Go 项目见 [go.md](go.md)。共用的 YAML 规则格式见 [yaml-rules.md](yaml-rules.md)。

动手前先看项目现状:`pom.xml` / `build.gradle` 里有没有 `ai.csap.apidoc`、启动类上有没有 `@EnableApidoc`、`src/main/resources/csap-api-doc/method-field/` 下有没有 YAML 规则。

## 1. 依赖

```xml
<dependency>
    <groupId>ai.csap.apidoc.boot</groupId>
    <artifactId>csap-apidoc-boot-starter</artifactId>
    <version>1.3.0</version>
</dependency>
```

- 坐标只有 `ai.csap.apidoc(.boot)`。
- starter 已包含文档页面、YAML/SQLite 策略、DevTools、返回字段过滤(`csap-apidoc-runtime`),不要另加。
- `ai.csap.apidoc.boot:csap-validation-boot-starter` 是可选的,需要运行时参数校验(1.2.0 起)、它的校验异常处理器(`ValidateExceptionHandler`)或 `csap.validation.*` 配置(`ValidationProperties`)时引入。
- JDK 11+;Spring Boot 3 需要 JDK 17+。1.1.0 的 jar 实际是 Java 17 字节码,Java 11 上无法加载;项目还在用 1.1.0 时建议升级到 1.1.1 及以上。

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
- 基础类型参数:`@ApiProperty(value = "说明", required = true)` 直接标在**参数**上,总会出现在请求参数里。标在方法上的 `@ApiProperty` / `@ApiPropertys` 不生效(1.3.0 起弃用)。是否必填:规则(YAML / SQLite)里有这个参数时以规则的 `required` 为准(1.3.0 起),没有规则时才看注解。
- 模型类:`@ApiModel(description = "...")`(只能用在类上,不能用在参数上)。
- 模型字段:`@ApiModelProperty(value = "...")`。**没有这个注解的字段永远不出现。**
- 其他:`@DocGlobalHeader` / `@DocAuth`(调试界面预填请求头和鉴权建议,不继承)、`@ApiResponseCode`(返回码表)、`@EnumValue` / `@EnumMessage`(枚举字段说明),详见 <https://apidoc.csap.ai/guide/annotations>。

## 4. 字段为什么"不显示"(最常见的问题)

同一个模型在不同接口里可以显示不同字段,所以字段**默认不出现在任何接口**,必须被"选中":

1. `forceReq = true` / `forceRep = true`:在所有用到该模型的接口的请求 / 返回中显示。最简单。
2. 按接口选择,取决于 `@EnableApidoc(paramType = ...)` 选的策略(三种策略**互不合并**):
   - `ANNOTATION`(默认):`@ApiModelProperty(groups = @Group(value = "方法名", request = @Request(required = true), response = @Response))`。**1.3.0 起弃用,2.0 删除**;新代码请改用 YAML / SQLite 规则。
   - `YAML`:规则写在 `src/main/resources/csap-api-doc/method-field/<Controller简单类名>-method-field.yaml`,格式见 [yaml-rules.md](yaml-rules.md)
   - `SQL_LITE`:规则存 SQLite,通常用 DevTools 维护
   - 数据库模式(MySQL / PostgreSQL / 达梦 / 人大金仓)和 Redis 模式:1.4.0 计划,当前版本没有,不要写 `paramType = DATABASE`。多实例共享规则目前 Java 做不到(YAML / SQLite 都是各实例一份)
3. 返回字段在规则里要 `include: true` 才出现;请求字段只要规则里有就出现。
4. 对象 / List / Map 字段:它自己出现了,子字段才会被处理。

排查顺序:字段有没有 `@ApiModelProperty` → 是否 `hidden` / `ignoreReq` / `ignoreRep` → 有没有 force → 当前策略的规则里有没有它(YAML 的键名写对没有)。

## 5. DevTools

开发环境开启 `csap.apidoc.devtool.enabled: true`(默认关闭,注意是 `devtool` 单数),访问 `/devtools-ui` 勾选字段、配置校验规则并写回 YAML / SQLite。**不要在生产环境开启**。`@EnableApidoc(paramType = ANNOTATION)` 下不能用 DevTools 保存规则。

## 6. 运行时过滤与校验(1.2.0 起,默认关闭)

- 规则里的返回字段 `include` 和请求 `validate` 默认**只影响文档**。要让接口实际返回的 JSON 只含文档里的字段,配置 `csap.apidoc.runtime.filter.enabled: true`(`mode`:`path` 默认 / `csap` / `off`;`endpoints` 按接口覆盖);要在运行时校验请求参数,引入 `csap-validation-boot-starter` 并配置 `csap.validation.mode`(`observe` 只记录,`enforce` 拦截)。两者都建议先在少数接口上试用。说明见 <https://apidoc.csap.ai/guide/runtime-filter> 和 <https://apidoc.csap.ai/guide/validation>。
- 只有必填字段(`required: true`)的校验规则会执行;校验规则在文档扫描完成后才生效。必填但没写任何规则的字段,开启校验后按"不能为空"检查。
- 只支持 Spring MVC(Servlet),不支持 WebFlux。
- 项目还在用 1.1.x 时,这两项都不会执行。
- 1.1.0 jar 里的文档页面是旧版构建,没有环境切换、全局请求头、鉴权、历史与重放、命名快照、中英文;这些从 1.1.1 起可用。
- 手工改 YAML 后需要重启;DevTools 修改会即时生效。

## 7. 写完后的检查

1. 启动日志里有 `CSAP API Documentation Generated Successfully!` 和文档地址;出现"N 个文档加载失败"说明依赖或注解有问题。
2. `curl -s localhost:<port>/csap/apidoc | head` 能看到新接口。
3. 打开 `/csap-api.html` 确认字段显示符合预期。
