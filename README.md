# CSAP Framework API Doc

<div align="center">

[中文](README.md) | [English](README.en.md)

[![Maven Central](https://img.shields.io/badge/maven--central-v1.0.3-blue)](https://search.maven.org/)
[![License](https://img.shields.io/badge/license-Apache%202.0-green.svg)](https://www.apache.org/licenses/LICENSE-2.0)
[![Java](https://img.shields.io/badge/java-8%2B-orange.svg)](https://www.oracle.com/java/technologies/javase-downloads.html)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-2.x%2F3.x-brightgreen.svg)](https://spring.io/projects/spring-boot)

一个强大的 Spring Boot API 文档管理框架，提供自动化文档生成、在线测试、多格式导出等功能

</div>

## ✨ 特性

- 🚀 **零侵入集成** - 基于注解，无需修改现有代码
- 📝 **自动生成文档** - 扫描 Spring MVC 注解自动生成 API 文档
- 🎨 **现代化界面** - 基于 React 18 + TypeScript + Ant Design 5 的美观 UI
- 🧪 **在线测试** - 内置 API 测试工具，无需 Postman
- 📦 **多格式导出** - 支持 OpenAPI/Swagger、Postman、Markdown、JSON Schema
- 🔍 **参数验证** - 集成 JSR-303 验证规则展示
- 🌲 **多存储策略** - 支持 SQLite、YAML、标准输出等多种持久化方式
- 🔧 **灵活配置** - 丰富的配置选项，满足各种场景需求
- 🎯 **开发工具** - 提供 DevTools 实时预览和配置管理
- 📐 **列宽调整** - DevTools 参数表格支持拖动调整列宽

## 🎯 快速开始

### 环境要求

- JDK 8+（推荐 JDK 8 或 JDK 11）
- Maven 3.x 或 Gradle
- Spring Boot 2.x

### Maven 依赖

**1. API 文档核心依赖（必需）**

```xml
<dependency>
    <groupId>com.csap.framework.boot</groupId>
    <artifactId>csap-framework-apidoc-boot-starter</artifactId>
    <version>1.0.3</version>
</dependency>
```

**2. 数据验证依赖（必需）**

如果需要使用 JSR-303 验证规则展示、参数验证等功能，必须添加：

```xml
<dependency>
    <groupId>com.csap.framework.boot</groupId>
    <artifactId>csap-framework-validation-boot-starter</artifactId>
    <version>1.0.3</version>
</dependency>
```

此依赖提供：

- ✅ JSR-303 验证注解支持（`@NotNull`、`@NotEmpty`、`@Size` 等）
- ✅ 自定义验证注解（`@Phone`、`@AssertBoolean` 等）
- ✅ 验证错误统一异常处理
- ✅ API 文档中展示验证规则

**3. 可选依赖**

根据需要选择添加：

```xml
<!-- DevTools 开发工具（开发环境推荐） -->
<dependency>
    <groupId>com.csap.framework</groupId>
    <artifactId>csap-framework-apidoc-devtools</artifactId>
    <version>1.0.3</version>
    <scope>provided</scope>  <!-- 生产环境可排除 -->
</dependency>

<!-- 或者使用独立 Web 界面 -->
<dependency>
    <groupId>com.csap.framework</groupId>
    <artifactId>csap-framework-apidoc-web</artifactId>
    <version>1.0.3</version>
    <scope>provided</scope>  <!-- 生产环境可排除 -->
</dependency>
```

### 基本配置（推荐使用注解方式）

在你的 Spring Boot 启动类上添加 `@EnableApidoc` 注解：

```java
@SpringBootApplication
@EnableApidoc("com.yourcompany.yourproject")
public class Application {
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
```

### 参数策略类型

CSAP Apidoc 支持多种参数管理策略，通过 `paramType` 配置：

**1. ANNOTATION 策略（默认 - 推荐）**

```java
@EnableApidoc(
    value = "com.yourcompany.yourproject",
    paramType = ApiStrategyType.ANNOTATION  // 使用注解定义参数
)
```

- 使用 `@ApiProperty`、`@ApiModelProperty` 等注解定义参数
- 类型安全，IDE 支持，易于维护
- **这是默认且推荐的方式**

**2. SQLite 策略**

```java
@EnableApidoc(
    value = "com.yourcompany.yourproject",
    paramType = ApiStrategyType.SQL_LITE,  // 参数配置存储在 SQLite
    path = "csap-example",                  // 存储路径
    fileName = "example-db"                 // SQLite 数据库文件名
)
```

- 参数配置存储在 SQLite 数据库
- 支持通过 DevTools 可视化配置参数
- 适合需要动态参数管理的场景

**使用环境变量（推荐用于 path 和 fileName）：**

```java
@EnableApidoc(
    value = "com.yourcompany.yourproject",
    paramType = ApiStrategyType.SQL_LITE,
    path = "${csap.apidoc.path:csap-example}",       // 使用环境变量，带默认值
    fileName = "${csap.apidoc.filename:example-db}"  // 使用环境变量，带默认值
)
```

然后在 `application.yml` 中配置：

```yaml
csap:
  apidoc:
    path: csap-production    # 覆盖路径
    filename: prod-db        # 覆盖数据库名
```

或使用系统环境变量：

```bash
export CSAP_APIDOC_PATH=/var/data/apidoc
export CSAP_APIDOC_FILENAME=prod-db
```

**3. YAML 策略**

```java
@EnableApidoc(
    value = "com.yourcompany.yourproject",
    paramType = ApiStrategyType.YAML,  // 参数配置存储在 YAML 文件
    path = "csap-docs"                  // YAML 文件存储路径
)
```

- 参数配置存储在 YAML 文件
- 人类可读，便于版本控制
- 易于手动审查和编辑

**4. 多包路径高级配置**

```java
@EnableApidoc(
    apiPackages = {"com.yourcompany.yourproject.controller"},  // API 控制器包
    enumPackages = {"com.yourcompany.yourproject.enums"},      // 枚举类包
    modelPackages = {"com.yourcompany.yourproject.model"},     // 模型类包
    paramType = ApiStrategyType.ANNOTATION,                     // 策略类型
    showChildPackageFlag = true                                 // 扫描子包
)
```

**策略对比：**


| 策略                  | 适用场景       | 优点                       | 缺点             |
| --------------------- | -------------- | -------------------------- | ---------------- |
| **ANNOTATION** (默认) | 标准 API 文档  | 类型安全、IDE 支持、易维护 | 需要代码注解     |
| **SQL_LITE**          | 动态参数管理   | 可视化配置、无需改代码     | 需要数据库文件   |
| **YAML**              | 版本控制的文档 | 人类可读、易于审查         | 需要手动编辑文件 |

### 使用注解

在你的 Controller 中添加文档注解：

```java
@RestController
@RequestMapping("/api/users")
@Api(tags = "用户管理", description = "用户相关接口")
public class UserController {

    @GetMapping("/{id}")
    @ApiOperation(value = "获取用户信息", description = "根据用户ID获取用户详细信息")
    public Response<User> getUser(@PathVariable Long id) {
        // 业务逻辑
        // 框架自动识别 @PathVariable、@RequestParam、@RequestBody 参数
        return Response.success(user);
    }

    @PostMapping
    @ApiOperation(value = "创建用户", description = "创建新用户")
    public Response<User> createUser(@Valid @RequestBody User user) {
        // 业务逻辑
        // User 类需要用 @ApiModel 标注
        // @Valid 会触发验证，验证规则会展示在 API 文档中
        return Response.success(createdUser);
    }
}
```

### 使用验证注解

在你的实体类中添加验证注解，这些验证规则会自动展示在 API 文档中：

```java
@ApiModel(description = "用户实体")
public class User {
  
    @ApiModelProperty(value = "用户ID", required = true, example = "1001")
    private Long id;
  
    @ApiModelProperty(value = "用户名", required = true, example = "张三")
    @NotEmpty(message = "用户名不能为空")
    @Size(min = 2, max = 20, message = "用户名长度必须在2-20之间")
    private String username;
  
    @ApiModelProperty(value = "邮箱", example = "user@example.com")
    @Email(message = "邮箱格式不正确")
    private String email;
  
    @ApiModelProperty(value = "手机号", example = "13800138000")
    @Phone(message = "手机号格式不正确")  // 自定义验证注解
    private String mobile;
  
    @ApiModelProperty(value = "年龄", example = "25")
    @Min(value = 18, message = "年龄必须大于18岁")
    @Max(value = 120, message = "年龄必须小于120岁")
    private Integer age;
}
```

**支持的验证注解：**


| 注解             | 说明                        | 示例                                   |
| ---------------- | --------------------------- | -------------------------------------- |
| `@NotNull`       | 不能为 null                 | `@NotNull(message = "不能为空")`       |
| `@NotEmpty`      | 不能为 null 或空字符串/集合 | `@NotEmpty(message = "不能为空")`      |
| `@NotBlank`      | 不能为 null 或空白字符串    | `@NotBlank(message = "不能为空")`      |
| `@Size`          | 字符串/集合/数组长度限制    | `@Size(min = 2, max = 20)`             |
| `@Min` / `@Max`  | 数值最小/最大值             | `@Min(18)` `@Max(120)`                 |
| `@Email`         | 邮箱格式验证                | `@Email(message = "邮箱格式不正确")`   |
| `@Pattern`       | 正则表达式验证              | `@Pattern(regexp = "^[a-zA-Z0-9]+$")`  |
| `@Phone`         | 手机号验证（自定义）        | `@Phone(message = "手机号格式不正确")` |
| `@AssertBoolean` | 布尔值断言（自定义）        | `@AssertBoolean(value = true)`         |

### 启动访问

启动应用后，访问以下地址：

- **API 文档界面**: `http://localhost:8080/csap-api.html`
- **API 数据接口**: `http://localhost:8080/csap/apidoc/parent` （树形结构）
- **完整文档接口**: `http://localhost:8080/csap/apidoc` （所有文档）
- **Postman 导出**: `http://localhost:8080/csap/openapi/postman`

## 📦 模块介绍

### 核心模块


| 模块                                       | 说明          | 主要功能                                            |
| ------------------------------------------ | ------------- | --------------------------------------------------- |
| **csap-framework-apidoc-annotation**       | 注解模块      | 提供`@Api`、`@ApiOperation`、`@ApiModel` 等文档注解 |
| **csap-framework-apidoc-common**           | 公共模块      | 通用工具类、常量定义、基础模型                      |
| **csap-framework-apidoc-core**             | 核心模块      | 文档扫描、解析、生成核心逻辑                        |
| **csap-framework-apidoc-boot-starter**     | API文档启动器 | API 文档自动配置、快速集成                          |
| **csap-framework-validation-boot-starter** | 验证启动器    | JSR-303 验证、自定义验证注解、统一异常处理          |

### 功能模块


| 模块                               | 说明        | 主要功能                 |
| ---------------------------------- | ----------- | ------------------------ |
| **csap-framework-apidoc-strategy** | 存储策略    | 支持多种文档持久化方式   |
| **csap-framework-apidoc-sqlite**   | SQLite 存储 | 基于 SQLite 的文档存储   |
| **csap-framework-apidoc-yaml**     | YAML 存储   | 基于 YAML 文件的文档存储 |
| **csap-framework-apidoc-standard** | 标准输出    | 标准格式文档输出         |
| **csap-framework-apidoc-devtools** | 开发工具    | 提供可视化文档管理界面   |
| **csap-framework-apidoc-web**      | Web 前端    | React 实现的文档展示界面 |
| **csap-framework-validation-core** | 验证核心    | JSR-303 验证规则集成     |

## 🔧 高级配置

### 环境变量支持

`path` 和 `fileName` 支持 Spring Boot 占位符语法：

**语法：**

- `${property.name}` - 使用属性值，不存在则报错
- `${property.name:defaultValue}` - 使用属性值或默认值

**示例：**

```java
@EnableApidoc(
    value = "com.yourcompany.yourproject",
    paramType = ApiStrategyType.SQL_LITE,
    path = "${csap.apidoc.data.path:/tmp/apidoc}",              // 未配置则使用 /tmp/apidoc
    fileName = "${csap.apidoc.db.file:${spring.application.name}}"  // 默认使用应用名
)
```

在 `application.yml` 或 `application.properties` 中配置：

```yaml
# application.yml
csap:
  apidoc:
    data:
      path: /var/apidoc/data
    db:
      file: myapp-docs
```

```properties
# application.properties
csap.apidoc.data.path=/var/apidoc/data
csap.apidoc.db.file=myapp-docs
```

或使用系统环境变量：

```bash
span
```

**优势：**

- ✅ 不同环境（开发/测试/生产）使用不同配置
- ✅ 集中化配置管理
- ✅ 易于通过环境变量覆盖
- ✅ 支持 Docker 和 Kubernetes 部署

### 完整配置示例

**主启动类（必需）：**

```java
@SpringBootApplication
@EnableApidoc(
    apiPackages = {"com.yourcompany.yourproject.controller"},
    enumPackages = {"com.yourcompany.yourproject.enums"},
    modelPackages = {"com.yourcompany.yourproject.model"},
    type = ApiStrategyType.ANNOTATION,
    paramType = ApiStrategyType.ANNOTATION,
    fileName = "apidoc.db",  // SQLite 文件名
    showChildPackageFlag = true
)
public class Application {
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
```

**application.yml（可选，用于 DevTools）：**

```yaml
csap:
  apidoc:
    # DevTools 配置
    devtool:
      enabled: true    # 启用开发工具界面
      cache: true      # 启用缓存（默认：true）
```

## 📚 文档导出

### 支持的导出格式

- **OpenAPI 3.0 / Swagger** - JSON 和 YAML 格式
- **Postman Collection** - 可直接导入 Postman
- **Markdown** - 美观的文档格式
- **JSON Schema** - 标准的 JSON Schema 格式
- **TypeScript** - 自动生成 TypeScript 类型定义

### 导出示例

```java
@Autowired
private PostmanConverterService postmanService;

public void exportToPostman() {
    CsapDocParentResponse apiDoc = apidocService.getApiDoc();
    String postmanJson = postmanService.convertToPostmanCollection(apiDoc);
    // 保存或返回 JSON
}
```

## 🎨 DevTools 开发工具

### 功能特点

- 🔍 **实时预览** - 实时查看 API 文档变化
- ⚙️ **在线配置** - 可视化配置参数、返回值
- 🧪 **在线测试** - 发送测试请求，查看响应结果
- 📝 **文档编辑** - 可视化编辑 API 文档信息
- 🎯 **字段管理** - 管理可选字段、复用字段配置

### 技术栈

- React 18.2
- TypeScript 5.3
- Vite 5.x
- Ant Design 5.x
- Zustand (状态管理)
- Axios (HTTP 客户端)

### 如何启用 DevTools

**方式一：添加 DevTools 依赖（推荐）**

在你的 `pom.xml` 中添加：

```xml
<dependency>
    <groupId>com.csap.framework</groupId>
    <artifactId>csap-framework-apidoc-devtools</artifactId>
    <version>1.0.3</version>
</dependency>
```

启动应用后访问：

- **DevTools 界面**: `http://localhost:8080/csap-api-devtools.html`

**方式二：使用独立 Web 项目**

如果不想在业务项目中引入 DevTools 依赖，可以使用独立的 Web 界面：

```xml
<dependency>
    <groupId>com.csap.framework</groupId>
    <artifactId>csap-framework-apidoc-web</artifactId>
    <version>1.0.3</version>
</dependency>
```

启动应用后访问：

- **Web 界面**: `http://localhost:8080/csap-api.html`

### DevTools 配置选项

在 `application.yml` 中配置：

```yaml
csap:
  apidoc:
    # DevTools 基本配置
    devtool:
      enabled: true        # 是否启用 DevTools（默认：true）
      cache: true          # 是否启用缓存（默认：true）
    
    # 接口数据配置（用于网关聚合或远程数据源）
    resources:
      - name: 本地API              # 资源名称
        url: /csap/apidoc/parent   # API 文档接口地址
        version: v1.0              # 版本号（可选）
```

**配置说明：**

- `devtool.enabled`: 控制是否启用 DevTools 功能

  - `true`: 启用（开发/测试环境推荐）
  - `false`: 禁用（生产环境推荐）
- `devtool.cache`: 控制是否缓存 API 文档数据

  - `true`: 启用缓存，提升性能（默认）
  - `false`: 禁用缓存，实时刷新数据
- `resources`: 配置多个 API 文档数据源（用于网关聚合场景）

  - `name`: 显示名称
  - `url`: API 文档接口地址
  - `version`: 版本标识

### 生产环境建议

在生产环境中，建议：

1. **禁用 DevTools**（如果引入了 devtools 依赖）：

```yaml
csap:
  apidoc:
    devtool:
      enabled: false
```

2. **或者使用 Maven Profile** 在生产环境排除 DevTools 依赖：

```xml
<profiles>
  <profile>
    <id>dev</id>
    <dependencies>
      <dependency>
        <groupId>com.csap.framework</groupId>
        <artifactId>csap-framework-apidoc-devtools</artifactId>
        <version>1.0.3</version>
      </dependency>
    </dependencies>
  </profile>
</profiles>
```

3. **仅保留文档查看界面**（使用 apidoc-web 模块），不包含编辑功能。

## 📊 架构设计

```
┌─────────────────────────────────────────────────────────┐
│                   Spring Boot Application                │
├─────────────────────────────────────────────────────────┤
│                                                           │
│  ┌────────────┐    ┌──────────────┐   ┌──────────────┐ │
│  │ Controller │───▶│  Apidoc Core │───│  Storage     │ │
│  │   Layer    │    │  Scanner     │   │  Strategy    │ │
│  └────────────┘    └──────────────┘   └──────────────┘ │
│         │                  │                   │         │
│         │                  ▼                   ▼         │
│         │          ┌──────────────┐   ┌──────────────┐ │
│         │          │  Annotation  │   │   SQLite/    │ │
│         │          │  Processor   │   │   YAML/...   │ │
│         │          └──────────────┘   └──────────────┘ │
│         │                                               │
│         ▼                                               │
│  ┌────────────┐    ┌──────────────┐   ┌──────────────┐ │
│  │   REST     │───▶│   DevTools   │───│   Web UI     │ │
│  │   API      │    │   Service    │   │   (React)    │ │
│  └────────────┘    └──────────────┘   └──────────────┘ │
│                                                           │
└─────────────────────────────────────────────────────────┘
```

## 🌟 核心注解

### @Api

标注在 Controller 类上，定义 API 分组信息

```java
@Api(tags = "用户管理", description = "用户相关接口")
```

### @ApiOperation

标注在方法上，描述 API 操作

```java
@ApiOperation(value = "获取用户", description = "根据ID获取用户信息")
```

### @ApiModel

标注在实体类上，描述数据模型

```java
@ApiModel(description = "用户实体")
public class User {
    // 字段定义
}
```

### @ApiModelProperty

标注在字段上，描述字段属性

```java
@ApiModelProperty(value = "用户ID", required = true, example = "1001")
private Long id;
```

### @EnumValue 和 @EnumMessage

标注在枚举类的字段上

```java
@AllArgsConstructor
@Getter
public enum UserStatus {
    ACTIVE(1, "激活"),
    DISABLED(2, "禁用");

    @EnumValue              // 标注 code 字段（存数据库）
    private final Integer code;

    @EnumMessage            // 标注 message 字段（显示说明）
    private final String message;
}
```

### @ApiProperty

标注在方法上，为方法参数补充详细文档

```java
@GetMapping("/search")
@ApiOperation(value = "搜索用户")
@ApiProperty(
    value = "搜索关键词",
    name = "keyword",           // 匹配方法参数名
    description = "用户名或邮箱关键词",
    required = true,
    example = "john",
    paramType = ParamType.QUERY
)
public Response<List<User>> search(String keyword) {
    // 方法有实际参数 keyword
    // @ApiProperty 通过 name="keyword" 匹配这个参数
}
```

### @ApiPropertys

在方法上定义多个参数的文档

```java
@GetMapping("/filter")
@ApiPropertys({
    @ApiProperty(name = "minAge", value = "最小年龄", example = "18"),
    @ApiProperty(name = "maxAge", value = "最大年龄", example = "60")
})
public Response<List<User>> filter(Integer minAge, Integer maxAge) {
    // 为多个参数补充文档
}
```

> **注意**:
>
> - 对于常规的 `@PathVariable` 和 `@RequestParam`，框架会自动识别
> - `@ApiProperty` 用于补充更详细的文档说明
> - 通过 `name` 属性匹配实际参数名

## 🎯 使用场景

### 场景一：团队协作开发

- **前后端分离** - 前端根据文档开发，无需等待后端完成
- **接口变更通知** - 文档自动更新，减少沟通成本
- **测试支持** - 测试人员直接使用在线测试功能

### 场景二：API 对接

- **第三方集成** - 导出 Postman Collection 给合作方
- **文档交付** - 导出精美的 Markdown 文档
- **类型定义** - 自动生成 TypeScript 类型，前端直接使用

### 场景三：项目维护

- **版本管理** - 记录 API 变更历史
- **快速查询** - 快速定位接口和参数信息
- **代码生成** - 基于文档生成客户端代码

### 场景四：网关聚合文档

在微服务架构或网关场景下，统一管理多个服务的 API 文档：

- **多服务聚合** - 在网关层聚合所有微服务的 API 文档
- **统一入口** - 一个界面查看所有服务的接口文档
- **快速切换** - 通过下拉框快速切换不同服务的文档

#### 配置示例

在网关项目的 `application.yml` 中配置：

```yaml
csap:
  apidoc:
    resources:
      - name: 总后台API
        url: /example-admin-api/csap/apidoc/parent
        version: v1.0
      - name: 门店API
        url: /example-store-api/csap/apidoc/parent
        version: v1.0
      - name: 其他API
        url: /example-other-api/csap/apidoc/parent
        version: v1.0
```

**工作原理：**

1. 网关层配置所有微服务的文档接口地址
2. 前端界面提供服务选择下拉框
3. 用户选择不同服务时，动态加载对应的 API 文档
4. 无需在每个服务中单独查看文档

**适用场景：**

- Spring Cloud Gateway 微服务架构
- Nginx 网关统一入口
- Kong、APISIX 等 API 网关
- 多租户 SaaS 系统

## 🔨 开发计划

### 已完成 ✅

- [X]  核心文档生成功能
- [X]  多种注解支持
- [X]  DevTools 开发工具
- [X]  React 前端界面
- [X]  Postman 格式转换
- [X]  多存储策略支持
- [X]  参数验证集成

### 进行中 🚧

- [ ]  API 在线测试增强
- [ ]  Mock 数据生成
- [ ]  JSON Schema 导入导出
- [ ]  字段库和模板功能
- [ ]  国际化支持
- [ ]  暗黑模式

### 规划中 📋

- [ ]  版本管理和变更追踪
- [ ]  团队协作功能
- [ ]  接口上下线管理
- [ ]  API 监控和分析
- [ ]  AI 辅助测试
- [ ]  细粒度权限控制

查看完整路线图: [产品路线图](csap-framework-apidoc-devtools/APIDOC开发工具产品路线图.md)

## 📖 文档

- [快速开始指南](QUICK_START.md) - 5分钟快速集成
- [注解参考手册](ANNOTATION_REFERENCE.md) - 完整的注解使用说明
- [网关聚合指南](GATEWAY-AGGREGATION.md) - 微服务网关聚合文档配置
- [常见问题 FAQ](FAQ.md) - 60+ 常见问题解答
- [架构设计](ARCHITECTURE.md) - 系统架构和设计原则
- [Postman 转换器](csap-framework-apidoc-core/README-Postman.md) - Postman 集成
- [产品路线图](csap-framework-apidoc-devtools/APIDOC开发工具产品路线图.md) - 未来规划
- [贡献指南](CONTRIBUTING.md) - 如何参与贡献
- [安全策略](SECURITY.md) - 安全相关说明

## 💡 示例项目

提供两个版本的示例项目，分别支持 Spring Boot 2.x 和 3.x：

- [示例项目集合](example/README.md) - 快速开始和版本对比
- [Spring Boot 2.x 示例](example/example-spring-boot2) - 基于 javax.servlet，适合 JDK 8/11
- [Spring Boot 3.x 示例](example/example-spring-boot3) - 基于 jakarta.servlet，适合 JDK 17+
- [注解使用指南](example/example-spring-boot3/ANNOTATIONS.md) - 正确的注解使用方式
- [策略对比](example/example-spring-boot3/STRATEGIES.md) - ANNOTATION vs SQLite vs YAML
- [高级示例](example/example-spring-boot3/src/main/java/ai/csap/example/controller/AdvancedController.java) - @ApiProperty 用法

## 🤝 贡献

我们欢迎所有形式的贡献！

### 如何贡献

1. Fork 本仓库
2. 创建你的特性分支 (`git checkout -b feature/AmazingFeature`)
3. 提交你的更改 (`git commit -m 'Add some AmazingFeature'`)
4. 推送到分支 (`git push origin feature/AmazingFeature`)
5. 开启一个 Pull Request

### 贡献指南

- 遵循现有代码风格（详见 [代码风格指南](docs/development/code-style/CODE_STYLE_README.md)）
- 提交前运行 `mvn checkstyle:check` 检查代码风格
- 添加必要的测试用例
- 更新相关文档
- 确保所有测试通过

## 🌟 致谢

感谢所有为这个项目做出贡献的开发者！

## 📄 许可证

本项目采用 [Apache License 2.0](LICENSE) 开源协议。

## 📮 联系我们

- **问题反馈**: [GitHub Issues](https://github.com/csap-ai/csap-framework-apidoc/issues)
- **功能建议**: [GitHub Discussions](https://github.com/csap-ai/csap-framework-apidoc/discussions)
- **邮件**: support@csap.ai

## 🔗 相关链接

- [官方网站](https://csap.ai)
- [在线文档](https://docs.csap.ai)
- [📚 文档中心](docs/README.md) ⭐ 完整文档索引
- [更新日志](CHANGELOG.md)
- [常见问题](docs/guides/FAQ.md)
- [快速入门](docs/guides/QUICK_START.md)
- [代码风格指南](docs/development/code-style/CODE_STYLE_README.md) - 开发必读

---

<div align="center">

**如果这个项目对你有帮助，请给一个 ⭐️ Star 支持一下！**

Made with ❤️ by CSAP Team

</div>
