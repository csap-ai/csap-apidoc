# 安装

本页说明 CSAP Apidoc 的完整安装方式：Maven / Gradle 坐标、每个模块的作用和是否需要单独引入、Spring Boot 2 与 3 的差别，以及如何让 DevTools 只在开发环境启用。

## 环境要求

| 项目 | 要求 | 依据 |
| --- | --- | --- |
| JDK | 11 及以上 | 框架按 Java 11 编译，CI 在 JDK 11 / 17 / 21 上构建 |
| Spring Boot | 2.x 或 3.x | 仓库提供 Spring Boot 2.7.18 和 3.2.0 两个示例工程 |
| Spring Boot 3 的 JDK | 17 及以上 | Spring Boot 3 自身的要求 |
| Web 栈 | Spring MVC（`spring-boot-starter-web`） | 文档接口和页面以 Spring MVC Controller 与静态资源方式提供 |

## 添加依赖

所有构件都发布在 Maven Central，当前版本 `1.1.0`。一般项目只需要下面两个 starter。

### Maven

```xml
<properties>
    <csap.apidoc.version>1.1.0</csap.apidoc.version>
</properties>

<dependencies>
    <!-- 文档生成 + 文档页面 + 存储策略 + DevTools -->
    <dependency>
        <groupId>ai.csap.apidoc.boot</groupId>
        <artifactId>csap-apidoc-boot-starter</artifactId>
        <version>${csap.apidoc.version}</version>
    </dependency>
    <!-- 参数校验（1.1.0 中文档生成也依赖它，见下文） -->
    <dependency>
        <groupId>ai.csap.apidoc.boot</groupId>
        <artifactId>csap-validation-boot-starter</artifactId>
        <version>${csap.apidoc.version}</version>
    </dependency>
</dependencies>
```

### Gradle（Groovy DSL）

```groovy
dependencies {
    implementation 'ai.csap.apidoc.boot:csap-apidoc-boot-starter:1.1.0'
    implementation 'ai.csap.apidoc.boot:csap-validation-boot-starter:1.1.0'
}
```

### Gradle（Kotlin DSL）

```kotlin
dependencies {
    implementation("ai.csap.apidoc.boot:csap-apidoc-boot-starter:1.1.0")
    implementation("ai.csap.apidoc.boot:csap-validation-boot-starter:1.1.0")
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

### 校验 starter

`ai.csap.apidoc.boot:csap-validation-boot-starter` 提供校验模块的自动配置：

- 注册 `IValidateFactory`。1.1.0 中文档生成会用到这个 Bean，缺少它时接口文档无法生成，所以**目前请总是和文档 starter 一起引入**。
- 注册全局异常处理 `ValidateExceptionHandler`（见下方提示）。
- 设置 `csap.validation.enabled=true` 后，才会注册请求拦截 `ValidateIntercept`，在运行时按规则校验参数。

:::warning 全局异常处理
`csap-validation-boot-starter` 会注册一个 `@ControllerAdvice`（`ValidateExceptionHandler`，`@Order(1)`），处理 `BindException`、`MethodArgumentNotValidException`、`MethodArgumentTypeMismatchException`、`ConstraintViolationException` 和框架自己的 `ValidateException`，并以 HTTP 200 返回 `ApidocResult` 格式的 JSON。

如果你的项目已经有统一的异常处理，请确认它们的优先级和返回格式符合预期。这个 Bean 带有 `@ConditionalOnMissingBean`，你可以自己声明一个 `ValidateExceptionHandler` 类型的 Bean 来替换它。
:::

### 单独使用某个模块

一般不需要。唯一常见的情况是：把接口定义放在独立的 API 模块（只放 Controller 接口和 DTO）时，该模块只需要注解：

```xml
<dependency>
    <groupId>ai.csap.apidoc</groupId>
    <artifactId>csap-apidoc-annotation</artifactId>
    <version>1.1.0</version>
</dependency>
```

注意注解模块使用的是 `ai.csap.apidoc` groupId，而两个 starter 是 `ai.csap.apidoc.boot`。

## Spring Boot 2 与 3 的差别

两个版本使用**同一套构件、同一个版本号**，不需要选择不同的 artifact。差别在你自己的项目里：

| | Spring Boot 2.x | Spring Boot 3.x |
| --- | --- | --- |
| JDK | 11 及以上 | 17 及以上 |
| Servlet 包名 | `javax.servlet.*` | `jakarta.servlet.*` |
| 示例工程 | `example/example-spring-boot2`（2.7.18） | `example/example-spring-boot3`（3.2.0） |
| 示例端口 | 8082 | 8083 |

两个示例的依赖写法完全相同，都是 `csap-apidoc-boot-starter` + `csap-validation-boot-starter`。

:::warning DevTools 代理在 Spring Boot 3 上的注册方式
DevTools 的 HTTP 代理（`csap.apidoc.devtools.proxy.*`）的自动配置在 1.1.0 中只登记在 `META-INF/spring.factories` 里。Spring Boot 3 不再从 `spring.factories` 读取自动配置，所以在 Spring Boot 3 上这个代理不会被注册。文档和 DevTools 主体由 `ApidocAutoConfiguration` 加载，校验由 `ValidationAutoConfiguration` 加载，这两个都已登记在 `AutoConfiguration.imports` 中，不受影响。
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
