# CSAP API Doc - 示例项目

本目录包含两个可直接运行的示例,分别对应 Spring Boot 2.x(javax)和 Spring Boot 3.x(jakarta)。

| | example-spring-boot2 | example-spring-boot3 |
|---|---|---|
| Spring Boot | 2.7.18 | 3.2.0 |
| JDK | 11+ | 17+ |
| Servlet / Validation | javax.* | jakarta.* |
| 默认端口 | 8082 | 8083 |
| 额外内容 | - | 可选的 JWT 安全层、泛型基类 Controller 示例 |

两个示例都使用 `csap-apidoc-boot-starter` 与 `csap-validation-boot-starter`,版本跟随仓库(当前 1.2.0)。

- [example-spring-boot2 说明](./example-spring-boot2/README.md)
- [example-spring-boot3 说明](./example-spring-boot3/README.md)

另有 `compat-spring-boot23`:只有测试,在支持范围下限 Spring Boot 2.3 / Spring 5.2 上跑 MCP 与返回字段过滤的端到端测试(复用 example-spring-boot2 的测试源码),`mvn -f example/compat-spring-boot23/pom.xml test`。

## 快速运行

在仓库根目录先安装本地构建产物,再进入示例目录运行:

```bash
mvn clean install -DskipTests
cd example/example-spring-boot2   # 或 example/example-spring-boot3
mvn spring-boot:run
```

启动后访问(以 boot2 为例,boot3 把端口换成 8083):

- 文档界面: http://localhost:8082/csap-api.html
- 文档数据: http://localhost:8082/csap/apidoc
- OpenAPI JSON: http://localhost:8082/csap/openapi.json
- Postman 导出: http://localhost:8082/csap/openapi/postman

## 最小接入

```xml
<dependency>
    <groupId>ai.csap.apidoc.boot</groupId>
    <artifactId>csap-apidoc-boot-starter</artifactId>
    <version>1.2.0</version>
</dependency>
```

启动类上用 `@EnableApidoc` 指定扫描包与存储策略(示例里用的是 YAML 策略):

```java
@SpringBootApplication
@EnableApidoc(value = "ai.csap.example", paramType = ApiStrategyType.YAML)
public class ExampleApplication { }
```

Controller 与模型上的注解来自 `csap-apidoc-annotation`:

```java
@RestController
@RequestMapping("/api/users")
@Api(value = "用户管理", description = "用户增删改查相关接口")
public class UserController {

    @PostMapping
    @ApiOperation(value = "创建用户", description = "创建新用户账户")
    public Response<User> createUser(@Valid @RequestBody User user) { ... }
}
```

配置项写在 `application.yml` 的 `csap.apidoc.*` 下,例如:

```yaml
csap:
  apidoc:
    api-info:
      title: 我的 API 文档
      version: 1.0.0
    devtool:
      enabled: true   # 仅开发环境开启
```

注解用法见 [ANNOTATIONS.md](./example-spring-boot2/ANNOTATIONS.md),存储策略见 [STRATEGIES.md](./example-spring-boot2/STRATEGIES.md),完整参考以官网 https://apidoc.csap.ai 为准。

## 示例接口

两个示例都包含:

- 用户管理 `/api/users`: 列表(分页)、详情、创建、更新、删除、`PATCH /{id}/status`
- 产品管理 `/api/products`: 列表、详情、创建、更新、删除、库存更新、批量删除
- 高级用法 `/api/advanced`: `@ApiProperty` / `@ApiPropertys` 补充方法参数文档
- 枚举(`@EnumValue` / `@EnumMessage`)、日期时间、JSR-303 参数校验

boot3 额外包含 `/auth/login`、`/auth/whoami`(JWT,见 [SECURITY.md](./example-spring-boot3/SECURITY.md))、
`/api/user-crud`(继承泛型基类)和 `/api/generic-test`(泛型请求体)。

## 打包

```bash
cd example/example-spring-boot2   # 或 example-spring-boot3
mvn clean package
java -jar target/example-spring-boot2-1.0.0.jar   # boot3 为 example-spring-boot3-1.0.0.jar
```

## 技术栈

| 技术 | Spring Boot 2.x 示例 | Spring Boot 3.x 示例 |
|------|----------------|----------------|
| Spring Framework | 5.3.x | 6.1.x |
| Spring Boot | 2.7.18 | 3.2.0 |
| Jackson | 2.13.x | 2.15.x |
| Hibernate Validator | 6.2.x | 8.0.x |
| Tomcat | 9.0.x | 10.1.x |

## License

Apache License 2.0
