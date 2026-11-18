# CSAP API Doc - Spring Boot 2.x 示例

基于 Spring Boot 2.7.x(javax.servlet)的示例项目。

## 项目信息

- Spring Boot: 2.7.18
- Java: 11+
- Servlet / Validation API: javax.*
- CSAP API Doc: 1.2.0(由 `pom.xml` 中的 `csap.apidoc.version` 控制)
- 端口: 8082(`application.yml` 中的 `server.port`)

## 快速开始

需要 JDK 11+ 与 Maven 3.6+。先在仓库根目录执行 `mvn clean install -DskipTests` 安装本地构建产物,然后:

```bash
cd example/example-spring-boot2
mvn spring-boot:run        # 或 ./run.sh(先打包再运行)
```

启动后访问:

- 文档界面: http://localhost:8082/csap-api.html
- 文档数据: http://localhost:8082/csap/apidoc
- OpenAPI JSON: http://localhost:8082/csap/openapi.json
- Health: http://localhost:8082/actuator/health

## 依赖

```xml
<dependency>
    <groupId>ai.csap.apidoc.boot</groupId>
    <artifactId>csap-apidoc-boot-starter</artifactId>
    <version>1.2.0</version>
</dependency>
<!-- 运行时请求参数校验(可选) -->
<dependency>
    <groupId>ai.csap.apidoc.boot</groupId>
    <artifactId>csap-validation-boot-starter</artifactId>
    <version>1.2.0</version>
</dependency>
```

## 启动类与配置

扫描包与存储策略由启动类上的 `@EnableApidoc` 指定:

```java
@SpringBootApplication
@EnableApidoc(value = "ai.csap.example", paramType = ApiStrategyType.YAML)
public class ExampleApplication { }
```

`application.yml` 中与 CSAP API Doc 相关的部分:

```yaml
server:
  port: 8082

csap:
  apidoc:
    api-info:
      title: CSAP API Doc - Spring Boot 2.x Example
      version: 1.0.0
      description: 基于 Spring Boot 2.7.x (javax.servlet) 的 API 文档示例
    devtool:
      enabled: true   # 仅开发环境开启
      cache: true
  # 运行时请求参数校验(1.2.0 起):off(默认)/ observe(记录并放行)/ enforce(拦截)
  # validation:
  #   mode: observe
```

## 示例接口

Controller 使用 `@Api` / `@ApiOperation`,实体使用 `@ApiModel` / `@ApiModelProperty`:

```java
@RestController
@RequestMapping("/api/users")
@Api(value = "用户管理", description = "用户增删改查相关接口")
public class UserController {

    @PostMapping
    @ApiOperation(value = "创建用户", description = "创建新用户账户")
    public Response<User> createUser(@Valid @RequestBody User user) { ... }

    @GetMapping("/{id}")
    @ApiOperation(value = "获取用户详情", description = "根据用户ID获取用户详细信息")
    public Response<User> getUser(@PathVariable Long id) { ... }
}
```

| Controller | 路径前缀 | 内容 |
|---|---|---|
| `UserController` | `/api/users` | 列表、详情、创建、更新、删除、`PATCH /{id}/status` |
| `ProductController` | `/api/products` | `listProducts`、`getProduct/{id}`、`createProduct`、`updateProduct/{id}`、`deleteProduct/{id}`、`updateStock/{id}/stock`、`DELETE /batch` |
| `AdvancedController` | `/api/advanced` | `@ApiProperty` / `@ApiPropertys` 的用法 |

## 目录结构

```
example-spring-boot2/
├── src/main/java/ai/csap/example/
│   ├── ExampleApplication.java
│   ├── controller/   # UserController, ProductController, AdvancedController
│   └── model/        # User, UserStatus, Product, ProductCategory, Response
├── src/main/resources/
│   ├── application.yml
│   ├── application-sqlite.yml
│   ├── application-yaml.yml
│   └── application-gateway.yml
├── src/test/         # 校验与字段过滤的单元 / E2E 测试
├── api-test.http     # HTTP Client 请求集合
├── test-api.sh       # curl 脚本
├── run.sh
└── pom.xml
```

`api-test.http` 与 `test-api.sh` 默认请求 `http://localhost:8082`;`test-api.sh` 可用环境变量 `BASE_URL` 覆盖。`application-sqlite.yml`、`application-yaml.yml`、`application-gateway.yml` 是配置样例,端口沿用 8082,不切换存储策略(见 [STRATEGIES.md](./STRATEGIES.md))。

## 调试

```yaml
logging:
  level:
    ai.csap.apidoc: DEBUG
```

```bash
mvn spring-boot:run -Ddebug     # 输出自动配置报告
```

## 注意事项

- JDK 11 或更高版本
- 使用 javax.servlet,不兼容 jakarta.servlet(需要 jakarta 请看 `example-spring-boot3`)

## 更多

- 注解用法: [ANNOTATIONS.md](./ANNOTATIONS.md)
- 存储策略: [STRATEGIES.md](./STRATEGIES.md)
- 文件说明: [FILES.md](./FILES.md)
- 官网文档: https://apidoc.csap.ai

## License

Apache License 2.0
