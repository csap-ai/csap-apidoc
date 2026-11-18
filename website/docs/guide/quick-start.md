# 快速开始

本页用一个最小的 Spring Boot 项目，5 分钟内跑起 CSAP Apidoc 文档页面：加依赖、加 `@EnableApidoc`、给 Controller 和模型加注解、启动后访问页面。

:::info 适用范围
本页是 **Java 版**(Spring Boot)的快速开始。Go 版(gin、net/http)的接入步骤见 [Go 版](/go/)。
:::

## 准备

- JDK 11 及以上；Spring Boot 3 项目需要 JDK 17 及以上。
- 一个引入了 `spring-boot-starter-web` 的 Spring Boot 2.x 或 3.x 项目。

## 第 1 步：添加依赖

在 `pom.xml` 中加入文档 starter：

```xml
<dependency>
    <groupId>ai.csap.apidoc.boot</groupId>
    <artifactId>csap-apidoc-boot-starter</artifactId>
    <version>1.4.0</version>
</dependency>
```

Gradle 写法：

```groovy
implementation 'ai.csap.apidoc.boot:csap-apidoc-boot-starter:1.4.0'
```

`csap-apidoc-boot-starter` 已经包含文档页面（`csap-apidoc-ui`）、YAML / SQLite 存储策略、DevTools 和返回字段过滤（`csap-apidoc-runtime`），不需要再单独引入。参数校验 starter `csap-validation-boot-starter` 是可选的，需要运行时参数校验（1.2.0 起）、它的校验异常处理器或 `csap.validation.*` 配置时引入。各模块的说明见 [安装](/guide/installation)。

## 第 2 步：开启文档

在启动类上加 `@EnableApidoc`，并写上要扫描的包：

```java
package com.example.demo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import ai.csap.apidoc.boot.autoconfigure.EnableApidoc;

@SpringBootApplication
@EnableApidoc("com.example.demo")
public class DemoApplication {
    public static void main(String[] args) {
        SpringApplication.run(DemoApplication.class, args);
    }
}
```

`@EnableApidoc("com.example.demo")` 等价于 `@EnableApidoc(apiPackages = "com.example.demo")`，会扫描该包及其子包。

:::tip
包名必须写。`@EnableApidoc` 没有指定 `apiPackages`（或 `apiPackageClasses`）时，不会扫描任何 Controller，页面上是空的。框架没有 `csap.apidoc.base-package` 之类的配置项，扫描范围只能通过 `@EnableApidoc` 指定。
:::

## 第 3 步：给 Controller 加注解

```java
package com.example.demo.user;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import ai.csap.apidoc.annotation.Api;
import ai.csap.apidoc.annotation.ApiOperation;
import ai.csap.apidoc.annotation.ApiProperty;

@RestController
@RequestMapping("/api/users")
@Api(value = "用户管理", description = "用户的查询与创建")
public class UserController {

    @GetMapping("/{id}")
    @ApiOperation(value = "查询用户", description = "根据 ID 查询用户")
    public User get(@ApiProperty(value = "用户 ID", required = true, example = "1")
                    @PathVariable Long id) {
        User user = new User();
        user.setId(id);
        user.setName("张三");
        return user;
    }

    @PostMapping
    @ApiOperation("创建用户")
    public User create(@RequestBody User user) {
        user.setId(1L);
        return user;
    }
}
```

规则只有三条：

- 类上要有 `@RestController`（或 `@Controller`）和 `@Api`，缺一个都不会出现在文档里。
- 方法上要有 `@ApiOperation` 和请求映射注解（`@GetMapping`、`@PostMapping` 等）。
- `@ApiOperation` 的 `value` 是接口名称，必填。

## 第 4 步：给模型加注解

```java
package com.example.demo.user;

import ai.csap.apidoc.annotation.ApiModel;
import ai.csap.apidoc.annotation.ApiModelProperty;

@ApiModel(description = "用户")
public class User {

    @ApiModelProperty(value = "用户 ID", example = "1", forceRep = true)
    private Long id;

    @ApiModelProperty(value = "用户名", required = true, example = "张三",
            forceReq = true, forceRep = true)
    private String name;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
}
```

:::tip 为什么要写 forceReq / forceRep
CSAP Apidoc 允许同一个模型在不同接口里显示不同字段，所以一个字段默认不会出现在任何接口里，需要“被选中”。最简单的方式是：

- `forceReq = true`：在所有用到这个模型的接口的请求参数中显示；
- `forceRep = true`：在所有用到这个模型的接口的返回数据中显示。

上例中 `id` 只出现在返回数据里，`name` 在请求和返回中都出现。想按接口精确选择字段，可以用 `@ApiModelProperty` 的 `groups`，或者用 YAML / SQLite 策略配合 DevTools 勾选，见 [注解](/guide/annotations) 和 [存储策略](/guide/storage-strategies)。

没有 `@ApiModelProperty` 的字段永远不会出现在文档中。
:::

## 第 5 步：启动并访问

启动应用。文档生成完成后，控制台会打印一个横幅，里面是文档地址（端口和 `server.servlet.context-path` 取自你的配置）：

```text
CSAP API Documentation Generated Successfully!
API 文档地址：
   http://localhost:8080/csap-api.html
```

在浏览器打开 `http://localhost:8080/csap-api.html`，左侧可以看到「用户管理」下的两个接口，点开即可查看参数、返回结构，并在线发送请求。

文档数据本身也可以直接访问：

| 地址 | 内容 |
| --- | --- |
| `GET /csap/apidoc` | 完整文档数据（JSON） |
| `GET /csap/apidoc/parent` | 文档树（页面左侧列表使用的数据） |
| `GET /csap/openapi/postman` | Postman Collection 格式的 JSON |
| `GET /csap/openapi.json` | OpenAPI 3.0 格式的 JSON（1.2.0 起） |

全部接口见 [REST 接口](/api/rest)。

## 下一步

- [基础使用](/guide/basic-usage)：一个更完整的示例，以及页面上每部分的含义。
- [配置参考](/guide/configuration)：`application.yml` 中可以配置的全部内容，比如文档标题。
- [DevTools](/guide/devtools)：在开发环境用可视化界面管理字段。
