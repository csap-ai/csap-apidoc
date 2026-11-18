# CSAP API Doc - Spring Boot 3.x 示例

基于 Spring Boot 3.2.x(jakarta.servlet)的示例项目。

## 项目信息

- Spring Boot: 3.2.0
- Java: 17+(必需)
- Servlet / Validation API: jakarta.*
- CSAP API Doc: 1.2.0(由 `pom.xml` 中的 `csap.apidoc.version` 控制)
- 端口: 8083(`application.yml` 中的 `server.port`)

## 快速开始

需要 JDK 17+ 与 Maven 3.6+。先在仓库根目录执行 `mvn clean install -DskipTests` 安装本地构建产物,然后:

```bash
cd example/example-spring-boot3
mvn spring-boot:run        # 或 ./run.sh(先打包再运行)
```

启动后访问:

- 文档界面: http://localhost:8083/csap-api.html
- 文档数据: http://localhost:8083/csap/apidoc
- OpenAPI JSON: http://localhost:8083/csap/openapi.json
- Health: http://localhost:8083/actuator/health

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
@EnableApidoc(
        value = "ai.csap.example",          // 扫描 ai.csap.example 下的全部 Controller
        paramType = ApiStrategyType.YAML)   // 也可用 apiPackageClasses = {...} 只列出指定的 Controller 类
public class ExampleApplication { }
```

`application.yml` 中与 CSAP API Doc 相关的部分:

```yaml
server:
  port: 8083

csap:
  apidoc:
    devtool:
      enabled: true   # 仅开发环境开启
      cache: true
  # 运行时请求参数校验(1.2.0 起):off(默认)/ observe(记录并放行)/ enforce(拦截)
  validation:
    mode: enforce
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
| `AdvancedController` | `/api/advanced` | 在参数上标注 `@ApiProperty` 的用法 |
| `AuthController` | `/auth` | `POST /login`、`GET /whoami`(JWT,见 [SECURITY.md](./SECURITY.md)) |
| `UserCrudController` | `/api/user-crud` | 继承泛型基类 `BaseCrudController<User, Long>`,演示继承方法的泛型解析 |
| `GenericTestController` | `/api/generic-test` | 泛型请求体(`BaseRequest<T>` 子类)的字段解析 |

`ProductController` 实现了 `ProductApi` 接口,接口上的 Spring MVC 映射与文档注解会被一并识别。

## 目录结构

```
example-spring-boot3/
├── src/main/java/ai/csap/example/
│   ├── ExampleApplication.java
│   ├── controller/   # User/Product/Advanced/Auth/UserCrud/GenericTest 等 Controller,ProductApi,BaseCrudController
│   ├── model/        # User, UserStatus, Product, ProductCategory, Response, BaseRequest, UserRequest
│   └── security/     # 可选的 JWT 示例(默认关闭)
├── src/main/resources/
│   ├── application.yml
│   ├── application-sqlite.yml
│   ├── application-yaml.yml
│   └── application-gateway.yml
├── src/test/         # 校验、泛型字段、字段过滤的测试
├── api-test.http     # HTTP Client 请求集合
├── test-api.sh       # curl 脚本
├── run.sh
└── pom.xml
```

`api-test.http` 与 `test-api.sh` 默认请求 `http://localhost:8083`;`test-api.sh` 可用环境变量 `BASE_URL` 覆盖。`application-sqlite.yml`、`application-yaml.yml`、`application-gateway.yml` 是配置样例,端口沿用 8083,不切换存储策略(见 [STRATEGIES.md](./STRATEGIES.md))。

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

- 必须使用 JDK 17 或更高版本
- 使用 jakarta.servlet / jakarta.validation,不兼容 javax.*(需要 javax 请看 `example-spring-boot2`)
- 第三方依赖需要支持 Spring Boot 3.x

## 更多

- 注解用法: [ANNOTATIONS.md](./ANNOTATIONS.md)
- 存储策略: [STRATEGIES.md](./STRATEGIES.md)
- 文件说明: [FILES.md](./FILES.md)
- 示例 JWT 安全层: [SECURITY.md](./SECURITY.md)
- 官网文档: https://apidoc.csap.ai

## License

Apache License 2.0
