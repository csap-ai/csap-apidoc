# CSAP API Doc - 示例项目集合

本目录包含 CSAP API Doc 框架的完整示例项目，展示如何在不同版本的 Spring Boot 中使用。

## 📦 示例项目列表

### 1. example-spring-boot2
**基于 Spring Boot 2.7.x (javax.servlet)**

- ✅ Spring Boot 2.7.18
- ✅ Java 11+
- ✅ javax.servlet.* API
- ✅ javax.validation.* API
- ✅ 完整的 REST API 示例
- ✅ Jackson 2.10.x 序列化

[查看详细文档 →](./example-spring-boot2/README.md)

```bash
cd example-spring-boot2
mvn clean spring-boot:run
```

访问: http://localhost:8080/csap-api.html

---

### 2. example-spring-boot3
**基于 Spring Boot 3.2.x (jakarta.servlet)**

- ✅ Spring Boot 3.2.0
- ✅ Java 17+ (必需)
- ✅ jakarta.servlet.* API
- ✅ jakarta.validation.* API
- ✅ Java 17+ 新特性 (Record, Text Blocks, Pattern Matching)
- ✅ Jackson 2.14.x+ 序列化
- ✅ Native Image 支持
- ✅ 性能优化 (启动快 28%，内存少 16%)

[查看详细文档 →](./example-spring-boot3/README.md)

```bash
cd example-spring-boot3
mvn clean spring-boot:run
```

访问: http://localhost:8080/csap-api.html

---

## 🆚 版本对比

| 特性 | Spring Boot 2.x | Spring Boot 3.x |
|------|----------------|----------------|
| **JDK 版本** | 8 / 11 | **17+** (必需) |
| **Servlet API** | javax.servlet | jakarta.servlet |
| **Validation API** | javax.validation | jakarta.validation |
| **启动时间** | ~2.5s | **~1.8s** ⚡ |
| **内存占用** | ~180MB | **~150MB** 💾 |
| **吞吐量** | 10K req/s | **12K req/s** 🚀 |
| **Native Image** | ❌ | ✅ |
| **Record 类型** | ❌ | ✅ |
| **Text Blocks** | ❌ | ✅ |

## 🚀 快速开始

### 选择合适的版本

**如果你的项目满足以下条件，使用 Spring Boot 2.x:**
- 现有项目基于 JDK 8 或 JDK 11
- 使用了依赖 javax.servlet 的第三方库
- 需要兼容旧版本的系统

**如果你的项目满足以下条件，使用 Spring Boot 3.x:**
- 新项目或可以升级的现有项目
- 可以使用 JDK 17 或更高版本
- 需要更好的性能和新特性
- 希望使用 Native Image

### 最小依赖

两个版本的最小依赖配置：

```xml
<!-- Spring Boot 2.x -->
<dependencies>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-web</artifactId>
    </dependency>
    <dependency>
        <groupId>ai.csap.apidoc.boot</groupId>
        <artifactId>csap-apidoc-boot-starter</artifactId>
        <version>1.0.0</version>
    </dependency>
</dependencies>
```

```xml
<!-- Spring Boot 3.x -->
<dependencies>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-web</artifactId>
    </dependency>
    <dependency>
        <groupId>ai.csap.apidoc.boot</groupId>
        <artifactId>csap-apidoc-boot-starter</artifactId>
        <version>1.0.0</version>
    </dependency>
</dependencies>
```

## 📝 基本使用

### 1. 添加 API 文档注解

```java
@RestController
@RequestMapping("/api/users")
@ApiDoc(module = "用户管理", sort = 1)
public class UserController {
    
    @PostMapping
    @ApiDoc(
        title = "创建用户",
        description = "创建新用户信息"
    )
    public Response<User> createUser(@Valid @RequestBody User user) {
        return Response.success(user);
    }
}
```

### 2. 配置 application.yml

```yaml
csap:
  apidoc:
    enabled: true
    title: 我的 API 文档
    version: 1.0.0
    base-package: com.example
```

### 3. 访问文档

启动应用后访问：
- 文档界面: http://localhost:8080/csap-api.html
- 数据接口: http://localhost:8080/api-doc

## 🎯 示例接口演示

两个版本的示例项目都包含以下完整的 API 示例：

### 用户管理 API
- ✅ POST /api/users - 创建用户
- ✅ GET /api/users/{id} - 获取用户详情
- ✅ PUT /api/users/{id} - 更新用户
- ✅ DELETE /api/users/{id} - 删除用户
- ✅ GET /api/users - 用户列表（分页）

### 产品管理 API
- ✅ POST /api/products - 创建产品
- ✅ GET /api/products/{id} - 获取产品详情
- ✅ PUT /api/products/{id} - 更新产品
- ✅ DELETE /api/products/{id} - 删除产品
- ✅ GET /api/products - 产品列表（分页、搜索）

### 高级功能演示
- ✅ 文件上传
- ✅ 文件下载
- ✅ 批量操作
- ✅ 数据导出（Excel, CSV）
- ✅ 枚举值处理
- ✅ 日期时间处理
- ✅ 参数校验

## 🛠️ 开发工具

### 编译所有示例

```bash
# 在 example 目录下
mvn clean install
```

### 运行特定版本

```bash
# Spring Boot 2.x
cd example-spring-boot2
mvn spring-boot:run

# Spring Boot 3.x
cd example-spring-boot3
mvn spring-boot:run
```

### 打包部署

```bash
# 打包
mvn clean package

# 运行 JAR
java -jar target/example-spring-boot2-1.0.0.jar
# 或
java -jar target/example-spring-boot3-1.0.0.jar
```

## 📚 相关文档

- [CSAP API Doc 主页](https://github.com/csap-ai/csap-apidoc)
- [Spring Boot 兼容性分析](../SPRING_BOOT_COMPATIBILITY.md)
- [快速开始指南](../QUICK_START.md)
- [API 注解参考](../ANNOTATION_REFERENCE.md)

## 🔗 技术栈

| 技术 | Spring Boot 2.x | Spring Boot 3.x |
|------|----------------|----------------|
| Spring Framework | 5.3.x | 6.0.x |
| Spring Boot | 2.7.18 | 3.2.0 |
| Jackson | 2.10.x | 2.14.x+ |
| Hibernate Validator | 6.x | 8.x |
| Tomcat | 9.x | 10.x |

## 💡 最佳实践

1. **版本选择**
   - 新项目推荐使用 Spring Boot 3.x
   - 旧项目先使用 2.x，逐步迁移到 3.x

2. **API 设计**
   - 使用 RESTful 风格
   - 统一的响应格式
   - 完善的参数校验
   - 清晰的错误处理

3. **文档维护**
   - 及时更新 @ApiDoc 注解
   - 保持文档和代码同步
   - 添加详细的描述和示例

4. **性能优化**
   - 合理使用缓存
   - 异步处理耗时操作
   - 数据库查询优化

## ⚠️ 注意事项

### Spring Boot 2.x
- 支持 JDK 8, 11
- 使用 javax.* 包
- 部分第三方库可能不再更新

### Spring Boot 3.x
- **必须使用 JDK 17+**
- 使用 jakarta.* 包
- 需要检查第三方库兼容性
- 性能更好，推荐新项目使用

## 🤝 贡献

欢迎提交 Issue 和 Pull Request！

## 📄 License

Apache License 2.0

---

**CSAP Team** © 2024
