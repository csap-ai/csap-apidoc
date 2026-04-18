# CSAP Framework API Doc - 注解参考手册

## 📋 注解总览

| 注解 | 使用位置 | 作用 | 必需 |
|------|---------|------|------|
| `@EnableApidoc` | 启动类 | 启用 API 文档 | ✅ 是 |
| `@Api` | Controller 类 | 定义 API 分组 | ✅ 是 |
| `@ApiOperation` | Controller 方法 | 定义 API 操作 | ✅ 是 |
| `@ApiModel` | 实体类 | 定义数据模型 | ✅ 是（实体类） |
| `@ApiModelProperty` | 实体字段 | 定义字段属性 | ⚠️ 建议 |
| `@ApiProperty` | Controller 方法 | 定义单个参数文档 | ⚠️ 可选 |
| `@ApiPropertys` | Controller 方法 | 定义多个参数文档 | ⚠️ 可选 |
| `@EnumValue` | 枚举类字段 | 标注数据库值字段 | ✅ 是（枚举） |
| `@EnumMessage` | 枚举类字段 | 标注显示说明字段 | ✅ 是（枚举） |

## 1. @EnableApidoc

**位置**: Spring Boot 启动类上

**作用**: 启用 CSAP API 文档功能，配置扫描包路径和策略

### 基础用法

```java
@SpringBootApplication
@EnableApidoc("com.yourcompany.yourproject")
public class Application {
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
```

### 完整配置

```java
@EnableApidoc(
    apiPackages = {"com.yourcompany.controller"},    // API 控制器包
    enumPackages = {"com.yourcompany.enums"},        // 枚举类包
    modelPackages = {"com.yourcompany.model"},       // 实体类包
    paramType = ApiStrategyType.ANNOTATION,          // 参数策略
    type = ApiStrategyType.ANNOTATION,               // 文档策略
    path = "${csap.apidoc.path:csap-data}",              // 存储路径（支持环境变量）
    fileName = "${csap.apidoc.file:apidoc-db}",          // 文件名（支持环境变量）
    showChildPackageFlag = true                      // 扫描子包
)
```

### 参数说明

- `value` / `apiPackages`: API 控制器包路径
- `enumPackages`: 枚举类包路径
- `modelPackages`: 实体类包路径
- `paramType`: 参数策略（ANNOTATION/SQL_LITE/YAML）
- `type`: 文档策略（ANNOTATION/SQL_LITE/YAML）
- `path`: 存储路径
- `fileName`: 文件名（SQLite 模式）
- `showChildPackageFlag`: 是否扫描子包

## 2. @Api

**位置**: Controller 类上

**作用**: 定义 API 分组信息

```java
@RestController
@RequestMapping("/api/users")
@Api(tags = "用户管理", description = "用户增删改查相关接口")
public class UserController {
    // ...
}
```

### 参数

- `tags`: 标签/分组名称（数组）
- `description`: 详细描述
- `value`: 简短描述

## 3. @ApiOperation

**位置**: Controller 方法上

**作用**: 定义 API 操作信息

```java
@GetMapping("/{id}")
@ApiOperation(value = "获取用户", description = "根据用户ID获取详细信息")
public Response<User> getUser(@PathVariable Long id) {
    // ...
}
```

### 参数

- `value`: 操作名称
- `description`: 详细描述
- `notes`: 额外说明
- `tags`: 标签（数组）

## 4. @ApiModel

**位置**: 实体类上

**作用**: 定义数据模型文档

```java
@Data
@ApiModel(description = "用户实体")
public class User {
    // 字段定义
}
```

### 参数

- `value`: 模型名称
- `description`: 模型描述
- `tags`: 标签（数组）
- `required`: 是否必传

## 5. @ApiModelProperty

**位置**: 实体类字段上

**作用**: 定义字段属性文档

```java
@ApiModelProperty(value = "用户ID", required = true, example = "1001")
private Long id;

@ApiModelProperty(value = "用户名", required = true, example = "john_doe")
@NotBlank(message = "用户名不能为空")
@Size(min = 3, max = 50)
private String username;
```

### 参数

- `value`: 字段说明
- `name`: 字段名称
- `description`: 详细描述
- `required`: 是否必需
- `example`: 示例值
- `defaultValue`: 默认值
- `hidden`: 是否隐藏
- `dataTypeClass`: 数据类型
- `paramType`: 参数类型
- `position`: 位置排序
- `length`: 长度
- `decimals`: 小数位数

## 6. @ApiProperty

**位置**: Controller 方法上（不是参数上！）

**作用**: 为方法的单个参数补充详细文档

### 基础用法

```java
@GetMapping("/search")
@ApiOperation(value = "搜索用户")
@ApiProperty(
    value = "搜索关键词",
    name = "keyword",              // 👈 匹配方法参数名
    description = "用户名或邮箱关键词",
    required = true,
    example = "john",
    paramType = ParamType.QUERY,
    dataTypeClass = String.class
)
public Response<List<User>> search(String keyword) {
    // 方法有实际参数 keyword
    // @ApiProperty 通过 name="keyword" 匹配这个参数
    return Response.success(users);
}
```

### 参数

- `value`: 参数说明
- `name`: 参数名称（必须匹配方法参数名）
- `description`: 详细描述
- `required`: 是否必需
- `example`: 示例值
- `defaultValue`: 默认值
- `paramType`: 参数类型（QUERY/PATH/BODY/HEADER/FORM_DATA/DEFAULT）
- `dataTypeClass`: 数据类型
- `position`: 位置
- `length`: 长度
- `decimals`: 小数位数
- `hidden`: 是否隐藏

### ParamType 枚举值

- `ParamType.DEFAULT` - 默认参数，等同于 QUERY
- `ParamType.QUERY` - 查询参数（URL 参数）
- `ParamType.PATH` - 路径参数
- `ParamType.BODY` - 请求体参数（JSON）
- `ParamType.HEADER` - 请求头参数
- `ParamType.FORM_DATA` - 表单数据参数（multipart/form-data）

## 7. @ApiPropertys

**位置**: Controller 方法上

**作用**: 为方法的多个参数补充文档

```java
@GetMapping("/filter")
@ApiOperation(value = "过滤用户")
@ApiPropertys({
    @ApiProperty(
        value = "最小年龄",
        name = "minAge",           // 匹配参数 minAge
        required = false,
        example = "18",
        paramType = ParamType.QUERY,
        dataTypeClass = Integer.class
    ),
    @ApiProperty(
        value = "最大年龄",
        name = "maxAge",           // 匹配参数 maxAge
        required = false,
        example = "60",
        paramType = ParamType.QUERY,
        dataTypeClass = Integer.class
    ),
    @ApiProperty(
        value = "城市",
        name = "city",             // 匹配参数 city
        required = false,
        example = "北京",
        paramType = ParamType.QUERY,
        dataTypeClass = String.class
    )
})
public Response<List<User>> filter(Integer minAge, Integer maxAge, String city) {
    // 方法有3个实际参数
    // @ApiPropertys 为这些参数补充文档
    return Response.success(users);
}
```

## 8. @EnumValue

**位置**: 枚举类的字段上

**作用**: 标注存储在数据库的值字段（通常是 code）

```java
@AllArgsConstructor
@Getter
public enum UserStatus {
    
    ACTIVE(1, "激活"),
    DISABLED(2, "禁用"),
    LOCKED(3, "锁定");

    @EnumValue                      // 👈 用在字段上
    private final Integer code;      // 这个值存数据库

    @EnumMessage
    private final String message;
}
```

**重要**: 
- ✅ 用在字段上，不是枚举值上
- ✅ 标注的字段是存数据库的值（通常是 Integer 或 String）
- ✅ 可以是 Integer, Long, String 等类型

## 9. @EnumMessage

**位置**: 枚举类的字段上

**作用**: 标注显示说明字段（通常是 message 或 description）

```java
@AllArgsConstructor
@Getter
public enum ProductStatus {
    
    PENDING_REVIEW(1, "待审核"),
    APPROVED(2, "审核通过"),
    REJECTED(3, "审核不通过");

    @EnumValue
    private final Integer code;

    @EnumMessage                    // 👈 用在字段上
    private final String description;  // 这个值用于显示
}
```

**生成的文档：**

```json
[
  {"code": "1", "description": "待审核"},
  {"code": "2", "description": "审核通过"},
  {"code": "3", "description": "审核不通过"}
]
```

## 🎯 常见场景

### 场景1: 基础 CRUD API

```java
@RestController
@RequestMapping("/api/users")
@Api(tags = "用户管理")
public class UserController {

    @GetMapping
    @ApiOperation(value = "用户列表")
    public Response<List<User>> list(
        @RequestParam(defaultValue = "1") Integer page,
        @RequestParam(defaultValue = "10") Integer size
    ) {
        // 框架自动识别 @RequestParam
        return Response.success(users);
    }

    @GetMapping("/{id}")
    @ApiOperation(value = "用户详情")
    public Response<User> get(@PathVariable Long id) {
        // 框架自动识别 @PathVariable
        return Response.success(user);
    }

    @PostMapping
    @ApiOperation(value = "创建用户")
    public Response<User> create(@Valid @RequestBody User user) {
        // 框架自动识别 @RequestBody
        // User 类需要 @ApiModel 注解
        return Response.success(user);
    }
}
```

### 场景2: 使用 @ApiProperty 补充参数文档

```java
@GetMapping("/search")
@ApiOperation(value = "高级搜索")
@ApiPropertys({
    @ApiProperty(
        name = "keyword",
        value = "搜索关键词",
        description = "支持用户名、邮箱、手机号模糊搜索",
        required = false,
        example = "john",
        paramType = ParamType.QUERY
    ),
    @ApiProperty(
        name = "status",
        value = "用户状态",
        description = "筛选指定状态的用户",
        required = false,
        example = "1",
        paramType = ParamType.QUERY,
        dataTypeClass = Integer.class
    )
})
public Response<List<User>> search(String keyword, Integer status) {
    // 为已有参数补充详细文档
    return Response.success(users);
}
```

### 场景3: 枚举类型

```java
@AllArgsConstructor
@Getter
public enum OrderStatus {
    
    PENDING_PAYMENT(10, "待支付"),
    PAID(20, "已支付"),
    SHIPPING(30, "配送中"),
    COMPLETED(40, "已完成"),
    CANCELLED(99, "已取消");

    @EnumValue
    private final Integer code;      // 存数据库

    @EnumMessage
    private final String description; // 给用户看
}
```

## ⚠️ 重要提示

### 框架自动识别的内容

**无需额外注解，框架会自动识别：**

1. **Spring MVC 参数注解**
   - `@PathVariable` - 路径参数
   - `@RequestParam` - 查询参数
   - `@RequestBody` - 请求体
   - `@RequestHeader` - 请求头

2. **JSR-303 验证注解**
   - `@NotNull`
   - `@NotBlank`
   - `@NotEmpty`
   - `@Size`
   - `@Min` / `@Max`
   - `@Email`
   - `@Pattern`
   - `@DecimalMin` / `@DecimalMax`
   - `@Digits`
   - 等等...

3. **参数信息**
   - 参数名称（通过反射）
   - 参数类型
   - 是否必需（required 属性）
   - 默认值（defaultValue 属性）

### 何时使用 @ApiProperty？

**不需要使用的场景（90%的情况）：**
- ✅ 使用 `@PathVariable` - 自动识别
- ✅ 使用 `@RequestParam` - 自动识别
- ✅ 使用 `@RequestBody` - 自动识别（配合 @ApiModel）

**需要使用的场景（10%的情况）：**
- ⚠️ 参数需要非常详细的说明
- ⚠️ 需要指定特殊的 paramType
- ⚠️ 需要自定义示例值
- ⚠️ 使用了 SQLite 或 YAML 策略需要手动定义参数

## 🔑 最佳实践

### 1. 最简配置（推荐）

```java
// 启动类
@SpringBootApplication
@EnableApidoc("com.yourcompany")
public class App { }

// Controller
@RestController
@Api(tags = "用户管理")
public class UserController {
    
    @GetMapping("/{id}")
    @ApiOperation(value = "获取用户")
    public User get(@PathVariable Long id) {
        // 够用了！框架会自动处理
    }
}

// Model
@Data
@ApiModel(description = "用户")
public class User {
    
    @ApiModelProperty(value = "用户ID", example = "1001")
    private Long id;
    
    @ApiModelProperty(value = "用户名", required = true)
    @NotBlank
    private String username;
}
```

### 2. 带验证规则（推荐）

```java
@Data
@ApiModel(description = "用户注册请求")
public class UserRegisterRequest {
    
    @ApiModelProperty(value = "用户名", required = true, example = "john_doe")
    @NotBlank(message = "用户名不能为空")
    @Size(min = 3, max = 50, message = "用户名长度3-50")
    private String username;
    
    @ApiModelProperty(value = "密码", required = true, example = "Pass@123")
    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 20, message = "密码长度6-20")
    private String password;
    
    @ApiModelProperty(value = "邮箱", required = true, example = "john@example.com")
    @Email(message = "邮箱格式不正确")
    @NotBlank(message = "邮箱不能为空")
    private String email;
    
    @ApiModelProperty(value = "手机号", example = "13800138000")
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
    private String phone;
}
```

### 3. 枚举类型（推荐）

```java
@AllArgsConstructor
@Getter
public enum UserStatus {
    
    ACTIVE(1, "激活"),
    DISABLED(2, "禁用"),
    LOCKED(3, "锁定"),
    PENDING(4, "待审核");

    @EnumValue
    private final Integer code;      // 存数据库：1, 2, 3, 4

    @EnumMessage
    private final String message;    // 显示：激活, 禁用, 锁定, 待审核
}

// 在实体中使用
@Data
@ApiModel(description = "用户")
public class User {
    
    @ApiModelProperty(value = "状态", example = "ACTIVE")
    private UserStatus status;       // 使用枚举类型
}
```

### 4. 高级参数文档（可选）

只在需要非常详细的参数说明时使用：

```java
@GetMapping("/advanced-search")
@ApiOperation(value = "高级搜索")
@ApiPropertys({
    @ApiProperty(
        name = "keyword",
        value = "搜索关键词",
        description = "支持模糊匹配用户名、邮箱、手机号",
        required = false,
        example = "john",
        paramType = ParamType.QUERY
    ),
    @ApiProperty(
        name = "status",
        value = "用户状态",
        description = "状态筛选，1-激活 2-禁用 3-锁定",
        required = false,
        example = "1",
        paramType = ParamType.QUERY,
        dataTypeClass = Integer.class
    ),
    @ApiProperty(
        name = "X-Request-Id",
        value = "请求ID",
        description = "用于追踪请求的唯一标识",
        required = false,
        example = "uuid-123",
        paramType = ParamType.HEADER
    )
})
public Response<List<User>> advancedSearch(
    String keyword,
    Integer status,
    @RequestHeader(value = "X-Request-Id", required = false) String requestId
) {
    return Response.success(users);
}
```

## 📚 完整示例

查看示例项目：
- [UserController.java](example/example-apidoc-spring-boot/src/main/java/com/csap/framework/example/controller/UserController.java) - 基础示例
- [AdvancedController.java](example/example-apidoc-spring-boot/src/main/java/com/csap/framework/example/controller/AdvancedController.java) - @ApiProperty 高级示例
- [User.java](example/example-apidoc-spring-boot/src/main/java/com/csap/framework/example/model/User.java) - 实体类示例
- [UserStatus.java](example/example-apidoc-spring-boot/src/main/java/com/csap/framework/example/model/UserStatus.java) - 枚举类示例

## ❓ 常见问题

### Q: 参数上为什么不能用 @ApiProperty？

A: 因为 `@ApiProperty` 的 `@Target` 是 `ElementType.METHOD`，只能用在方法上。通过 `name` 属性匹配方法参数。

### Q: 什么时候需要用 @ApiProperty？

A: 大部分情况不需要！框架会自动识别 `@PathVariable`、`@RequestParam`、`@RequestBody`。只有需要非常详细的参数说明时才用。

### Q: 枚举为什么要用 code 和 message？

A: 
- code 存数据库（1, 2, 3）- 节省空间，不受语言影响
- message 给用户看（"激活", "禁用"）- 可读性好
- 枚举名用于代码（ACTIVE, DISABLED）- 类型安全

### Q: 验证注解会显示在文档中吗？

A: 会！框架会自动提取 JSR-303 验证注解的规则并显示在文档中。

---

**更多信息**: 查看 [快速开始指南](QUICK_START.md) 和 [示例项目](example/example-apidoc-spring-boot)


