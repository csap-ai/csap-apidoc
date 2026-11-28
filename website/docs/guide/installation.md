# 安装

本页说明 CSAP Apidoc 的完整安装方式：Maven / Gradle 坐标、每个模块的作用和是否需要单独引入、Spring Boot 2 与 3 的差别，以及如何让 DevTools 只在开发环境启用。

:::info 适用范围
本页讲的是 **Java 版**(Spring Boot 2.x / 3.x)。Go 版(gin、net/http)的接入、结构体标签、过滤和校验见 [Go 版](/go/)；两种语言共用文档页面和 YAML 规则文件格式。
:::

## 环境要求

| 项目 | 要求 | 依据 |
| --- | --- | --- |
| JDK | 11 及以上 | 1.1.1 起所有模块编译为 Java 11 字节码，CI 在 JDK 11 / 17 / 21 上构建。1.1.0 的 jar 实际是 Java 17 字节码，需要 JDK 17，请使用 1.1.1 及以上 |
| Spring Boot | 2.x 或 3.x | 仓库提供 Spring Boot 2.7.18 和 3.2.0 两个示例工程 |
| Spring Boot 3 的 JDK | 17 及以上 | Spring Boot 3 自身的要求 |
| Web 栈 | Spring MVC（`spring-boot-starter-web`） | 文档接口和页面以 Spring MVC Controller 与静态资源方式提供 |

## 添加依赖

所有构件都发布在 Maven Central，当前版本 `1.5.1`。一般项目只需要引入文档 starter。

### Maven

```xml
<properties>
    <csap.apidoc.version>1.5.1</csap.apidoc.version>
</properties>

<dependencies>
    <!-- 文档生成 + 文档页面 + 存储策略 + DevTools -->
    <dependency>
        <groupId>ai.csap.apidoc.boot</groupId>
        <artifactId>csap-apidoc-boot-starter</artifactId>
        <version>${csap.apidoc.version}</version>
    </dependency>
</dependencies>
```

### Gradle（Groovy DSL）

```groovy
dependencies {
    implementation 'ai.csap.apidoc.boot:csap-apidoc-boot-starter:1.5.1'
}
```

### Gradle（Kotlin DSL）

```kotlin
dependencies {
    implementation("ai.csap.apidoc.boot:csap-apidoc-boot-starter:1.5.1")
}
```

## 模块说明

### starter 里已经包含什么

`csap-apidoc-boot-starter` 直接依赖了以下模块，引入 starter 后它们都会在 classpath 上，**不需要再单独声明**：

| 模块 | 坐标 | 作用 |
| --- | --- | --- |
| 核心 | `ai.csap.apidoc:csap-apidoc-core` | 扫描 Controller、解析注解、生成文档数据；提供 `/csap/apidoc` 等数据接口 |
| 注解 | `ai.csap.apidoc:csap-apidoc-annotation` | `@Api`、`@ApiOperation`、`@ApiModel`、`@ApiModelProperty` 等注解 |
| 公共 | `ai.csap.apidoc:csap-apidoc-common` | 公共模型与工具类 |
| 文档页面 | `ai.csap.apidoc:csap-apidoc-ui` | `/csap-api.html` 文档页面（由 core 引入） |
| 校验核心 | `ai.csap.apidoc:csap-validation-core` | 校验规则的解析与执行（由 core 引入，但不含自动配置） |
| YAML 策略 | `ai.csap.apidoc:csap-apidoc-yaml` | 把字段规则存到 YAML 文件 |
| SQLite 策略 | `ai.csap.apidoc:csap-apidoc-sqlite` | 把字段规则存到 SQLite 文件 |
| 标准处理 | `ai.csap.apidoc:csap-apidoc-standard` | YAML / SQLite 策略共用的文件读写（由策略模块引入） |
| DevTools | `ai.csap.apidoc:csap-apidoc-devtools` | 开发阶段的可视化管理界面；默认不启用 |
| 运行时过滤 | `ai.csap.apidoc:csap-apidoc-runtime` | 返回字段过滤（1.2.0 起），默认关闭，见 [返回字段过滤](/guide/runtime-filter) |

### 校验 starter（可选）

`ai.csap.apidoc.boot:csap-validation-boot-starter` 是可选的。需要运行时参数校验（1.2.0 起），或者下面这些功能时引入：

```xml
<dependency>
    <groupId>ai.csap.apidoc.boot</groupId>
    <artifactId>csap-validation-boot-starter</artifactId>
    <version>${csap.apidoc.version}</version>
</dependency>
```

- 注册全局异常处理 `ValidateExceptionHandler`（见下方提示）。
- 注册 `ValidationProperties`，读取 `csap.validation.*` 配置。
- 注册 `IValidateFactory`。不引入时，文档 starter 会使用一个内置的默认实现，文档照常生成（1.1.0 中缺少它会空指针，这是 1.1.0 要求两个 starter 同时引入的原因，1.1.1 已修复）。
- `csap.validation.mode` 不是 `off`（或者 `endpoints` 里有接口不是 `off`，或者设置了旧开关 `csap.validation.enabled=true`）时，注册运行时校验组件 `ValidateIntercept`、`ValidationExecutor` 等，开始校验请求参数（1.2.0 起，默认关闭）。1.1.x 中没有组件调用 `ValidateIntercept`，不会校验。见 [参数校验](/guide/validation)。

:::warning 全局异常处理
`csap-validation-boot-starter` 会注册一个 `@ControllerAdvice`（`ValidateExceptionHandler`，`@Order(1)`），处理 `BindException`、`MethodArgumentNotValidException`、`MethodArgumentTypeMismatchException`、`ConstraintViolationException` 和框架自己的 `ValidateException`，并默认以 HTTP 200 返回 `ApidocResult` 格式的 JSON（状态码可用 `csap.validation.http-status` 修改）。

如果你的项目已经有统一的异常处理，请确认它们的优先级和返回格式符合预期。这个 Bean 带有 `@ConditionalOnMissingBean`，你可以自己声明一个 `ValidateExceptionHandler` 类型的 Bean 来替换它。
:::

### 单独使用某个模块

一般不需要。唯一常见的情况是：把接口定义放在独立的 API 模块（只放 Controller 接口和 DTO）时，该模块只需要注解：

```xml
<dependency>
    <groupId>ai.csap.apidoc</groupId>
    <artifactId>csap-apidoc-annotation</artifactId>
    <version>1.5.1</version>
</dependency>
```

注意注解模块使用的是 `ai.csap.apidoc` groupId，而 starter 是 `ai.csap.apidoc.boot`。

## Spring Boot 2 与 3 的差别

两个版本使用**同一套构件、同一个版本号**，不需要选择不同的 artifact。差别在你自己的项目里：

| | Spring Boot 2.x | Spring Boot 3.x |
| --- | --- | --- |
| JDK | 11 及以上 | 17 及以上 |
| Servlet 包名 | `javax.servlet.*` | `jakarta.servlet.*` |
| 示例工程 | `example/example-spring-boot2`（2.7.18） | `example/example-spring-boot3`（3.2.0） |
| 示例端口 | 8082 | 8083 |

两个示例的依赖写法完全相同，都引入了 `csap-apidoc-boot-starter` 和 `csap-validation-boot-starter`（示例用到了校验 starter；你的项目只需要文档 starter）。

:::tip DevTools 代理在 Spring Boot 3 上的注册方式
DevTools 的 HTTP 代理（`csap.apidoc.devtools.proxy.*`）的自动配置在 1.1.0 中只登记在 `META-INF/spring.factories` 里，Spring Boot 3 不读取这个文件，所以 1.1.0 在 Spring Boot 3 上不会注册这个代理。1.1.1 起它同时登记在 `AutoConfiguration.imports` 中，Spring Boot 2 和 3 都生效。文档和 DevTools 主体（`ApidocAutoConfiguration`）、校验（`ValidationAutoConfiguration`）一直两处都有登记。
:::

## DevTools 只在开发环境启用

DevTools 已经随 starter 引入，但它的接口（`/devtools-ui`、`/devtools/**`、`/csap/yaml/**`）只有在 `csap.apidoc.devtool.enabled=true` 时才会注册，默认是关闭的。因此推荐的做法是：**只在开发环境的 profile 里打开它**。

`application.yml`（所有环境共用，不开 DevTools）：

```yaml
spring:
  profiles:
    active: dev
```

`application-dev.yml`（仅开发环境）：

```yaml
csap:
  apidoc:
    devtool:
      enabled: true
```

生产环境不设置这个属性即可（默认 `false`），也可以在 `application-prod.yml` 中显式写 `enabled: false`。

开启后，启动日志会打印 DevTools 的地址：`http://localhost:端口/devtools-ui`。更多用法见 [DevTools](/guide/devtools)。

:::tip 注意拼写
DevTools 的开关是 `csap.apidoc.devtool.enabled`（`devtool`，单数）；DevTools 代理的前缀是 `csap.apidoc.devtools.proxy`（`devtools`，复数）。两者是不同的配置类。
:::

## 在某些环境关闭整个文档

框架没有 `csap.apidoc.enabled` 这样的总开关。文档功能的自动配置以 `@EnableApidoc` 为前提（`ApidocAutoConfiguration` 上有 `@ConditionalOnBean(EnableApidocConfig.class)`），所以可以把 `@EnableApidoc` 放到一个带 `@Profile` 的配置类上，在不需要的环境里不加载它：

```java
package com.example.demo.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import ai.csap.apidoc.boot.autoconfigure.EnableApidoc;

@Configuration
@Profile("!prod")
@EnableApidoc("com.example.demo")
public class ApidocConfig {
}
```

这样在 `prod` 环境中不会注册文档接口（`/csap/apidoc` 等）和 DevTools。`/csap-api.html` 是 jar 包里的静态文件，仍然可以访问，但没有数据可显示；如需彻底屏蔽，请在网关或 Spring Security 中拦截该路径。

## 验证安装

启动应用后：

1. 控制台出现 `CSAP API Documentation Generated Successfully!` 横幅，并给出文档地址。
2. 浏览器打开 `http://localhost:8080/csap-api.html` 能看到接口列表。
3. `GET http://localhost:8080/csap/apidoc` 返回 JSON 文档数据。

如果页面上没有接口，请看 [常见问题](/guide/faq)。

## 下一步

- [快速开始](/guide/quick-start)
- [配置参考](/guide/configuration)
- [参数校验](/guide/validation)
