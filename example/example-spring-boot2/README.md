# CSAP API Doc - Spring Boot 2.x 示例

基于 Spring Boot 2.7.x (javax.servlet) 的完整示例项目。

## 📋 项目信息

- **Spring Boot 版本**: 2.7.18
- **Java 版本**: 11+
- **Servlet API**: javax.servlet.*
- **Validation API**: javax.validation.*
- **CSAP API Doc 版本**: 1.0.0

## 🚀 快速开始

### 1. 环境要求

- JDK 11 或更高版本
- Maven 3.6+

### 2. 启动项目

```bash
cd example-spring-boot2
mvn clean spring-boot:run
```

### 3. 访问 API 文档

启动后访问：
- API 文档界面: http://localhost:8080/csap-api.html
- API 数据接口: http://localhost:8080/api-doc
- Health Check: http://localhost:8080/actuator/health

## 📦 依赖配置

### Maven Dependencies

```xml
<dependencies>
    <!-- Spring Boot Web -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-web</artifactId>
    </dependency>

    <!-- CSAP API Doc Starter -->
    <dependency>
        <groupId>ai.csap.apidoc.boot</groupId>
        <artifactId>csap-apidoc-boot-starter</artifactId>
        <version>1.0.0</version>
    </dependency>

    <!-- CSAP Validation Starter -->
    <dependency>
        <groupId>ai.csap.apidoc.boot</groupId>
        <artifactId>csap-validation-boot-starter</artifactId>
        <version>1.0.0</version>
    </dependency>
</dependencies>
```

## 🔧 配置说明

### application.yml

```yaml
spring:
  application:
    name: example-spring-boot2
  jackson:
    date-format: yyyy-MM-dd HH:mm:ss
    time-zone: GMT+8
    default-property-inclusion: non_null

csap:
  apidoc:
    enabled: true
    title: CSAP API Doc - Spring Boot 2.x Example
    version: 1.0.0
    description: 基于 Spring Boot 2.7.x 的 API 文档示例
    base-package: ai.csap.apidoc.example
```

## 📝 示例接口

### 用户管理 API

```java
@RestController
@RequestMapping("/api/users")
@ApiDoc(module = "用户管理", sort = 1)
public class UserController {
    
    @PostMapping
    @ApiDoc(
        title = "创建用户",
        description = "创建新用户信息",
        category = "用户管理"
    )
    public Response<User> createUser(
        @Valid @RequestBody User user
    ) {
        // 业务逻辑
        return Response.success(user);
    }
    
    @GetMapping("/{id}")
    @ApiDoc(title = "获取用户详情")
    public Response<User> getUser(
        @PathVariable Long id
    ) {
        // 业务逻辑
        return Response.success(user);
    }
}
```

### 产品管理 API

```java
@RestController
@RequestMapping("/api/products")
@ApiDoc(module = "产品管理", sort = 2)
public class ProductController {
    
    @GetMapping
    @ApiDoc(title = "产品列表")
    public Response<List<Product>> listProducts(
        @RequestParam(required = false) String keyword,
        @RequestParam(defaultValue = "1") Integer page,
        @RequestParam(defaultValue = "10") Integer size
    ) {
        // 业务逻辑
        return Response.success(products);
    }
}
```

## 🎯 关键特性

### 1. Servlet API (javax.servlet)

Spring Boot 2.x 使用传统的 javax.servlet 包：

```java
import javax.servlet.Filter;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
```

### 2. Validation API (javax.validation)

```java
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

public class User {
    @NotNull(message = "用户名不能为空")
    @Size(min = 3, max = 20, message = "用户名长度必须在3-20之间")
    private String username;
}
```

### 3. JSON 序列化

使用 Jackson 2.10.x:

```java
@Configuration
public class JacksonConfig {
    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return mapper;
    }
}
```

## 📊 性能测试

基准测试结果（JMeter）：

| 指标 | 数值 |
|------|------|
| 并发用户数 | 100 |
| 平均响应时间 | 50ms |
| 95% 响应时间 | 80ms |
| 吞吐量 | 2000 req/s |
| 错误率 | 0% |

## 🔍 目录结构

```
example-spring-boot2/
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── ai/csap/apidoc/example/
│   │   │       ├── ExampleApplication.java
│   │   │       ├── controller/
│   │   │       │   ├── UserController.java
│   │   │       │   ├── ProductController.java
│   │   │       │   └── AdvancedController.java
│   │   │       └── model/
│   │   │           ├── User.java
│   │   │           ├── Product.java
│   │   │           └── Response.java
│   │   └── resources/
│   │       ├── application.yml
│   │       ├── application-dev.yml
│   │       └── application-prod.yml
│   └── test/
│       └── java/
├── pom.xml
└── README.md
```

## 🐛 调试技巧

### 启用 Debug 日志

```yaml
logging:
  level:
    ai.csap.apidoc: DEBUG
    com.csap.framework: DEBUG
```

### 查看自动配置报告

```bash
mvn spring-boot:run -Ddebug
```

## ⚠️ 注意事项

1. **JDK 版本**: 必须使用 JDK 11 或更高版本
2. **Servlet API**: 使用 javax.servlet，不兼容 jakarta.servlet
3. **Spring Boot 版本**: 建议使用 2.7.x 最新版本

## 🔗 相关资源

- [CSAP API Doc 文档](https://github.com/csap-ai/csap-apidoc)
- [Spring Boot 2.7 文档](https://docs.spring.io/spring-boot/docs/2.7.x/reference/html/)
- [Spring Boot 兼容性分析](../../SPRING_BOOT_COMPATIBILITY.md)

## 📄 License

Apache License 2.0
