# Annotation Usage Guide

## ✅ 正确的注解使用方式

### Controller 层注解

#### @Api - 用在类上

```java
@RestController
@RequestMapping("/api/users")
@Api(tags = "用户管理", description = "用户相关接口")
public class UserController {
    // ...
}
```

**作用**: 定义 Controller 的分组和描述

#### @ApiOperation - 用在方法上

```java
@GetMapping("/{id}")
@ApiOperation(value = "获取用户", description = "根据ID获取用户详细信息")
public Response<User> getUser(@PathVariable Long id) {
    // ...
}
```

**作用**: 定义 API 方法的名称和描述

#### @ApiProperty - 用在方法上，定义单个参数的文档

用在**方法上**，通过 `name` 属性匹配方法里的实际参数：

```java
@GetMapping("/search")
@ApiOperation(value = "搜索用户")
@ApiProperty(
    value = "搜索关键词",        // 参数说明
    name = "keyword",           // 对应方法参数名 keyword
    description = "用户名或邮箱",
    required = true,
    example = "john",
    paramType = ParamType.QUERY
)
public Response<List<User>> searchByKeyword(String keyword) {
    // 方法里必须有名为 keyword 的实际参数
    // @ApiProperty 通过 name="keyword" 来匹配并补充文档
}
```

**重点**: 
- ✅ 用在方法上，不是参数上
- ✅ 通过 `name` 属性匹配实际参数名
- ✅ 方法里必须有对应的参数

#### @ApiPropertys - 用在方法上，定义多个参数的文档

当需要为多个参数补充文档时使用：

```java
@GetMapping("/filter")
@ApiOperation(value = "过滤用户")
@ApiPropertys({
    @ApiProperty(
        value = "最小年龄",
        name = "minAge",        // 对应参数 minAge
        required = false,
        example = "18",
        paramType = ParamType.QUERY,
        dataTypeClass = Integer.class
    ),
    @ApiProperty(
        value = "最大年龄",
        name = "maxAge",        // 对应参数 maxAge
        required = false,
        example = "60",
        paramType = ParamType.QUERY,
        dataTypeClass = Integer.class
    )
})
public Response<List<User>> filterUsers(Integer minAge, Integer maxAge) {
    // 方法有2个实际参数：minAge 和 maxAge
    // @ApiPropertys 为这2个参数补充了文档说明
}
```

### Model 层注解

#### @ApiModel - 用在类上

```java
@Data
@ApiModel(description = "用户实体")
public class User {
    // ...
}
```

**作用**: 定义实体类的文档信息

#### @ApiModelProperty - 用在字段上

```java
@ApiModelProperty(value = "用户ID", required = true, example = "1001")
private Long id;

@ApiModelProperty(value = "用户名", required = true, example = "john_doe")
@NotBlank(message = "用户名不能为空")
private String username;
```

**作用**: 定义字段的文档信息

### Enum 注解

#### @EnumValue 和 @EnumMessage - 用在枚举类的字段上

**重要**: 这两个注解用在枚举类的**字段**上，不是枚举值上！

```java
@AllArgsConstructor
@Getter
public enum UserStatus {
    
    ACTIVE(1, "激活"),
    DISABLED(2, "禁用"),
    LOCKED(3, "锁定");

    @EnumValue              // 标注 code 字段（存数据库）
    private final Integer code;

    @EnumMessage            // 标注 message 字段（中文说明）
    private final String message;
}
```

**生成的文档数据：**
```json
[
  {"code": "1", "message": "激活"},
  {"code": "2", "message": "禁用"},
  {"code": "3", "message": "锁定"}
]
```

**为什么需要 code？**
- ✅ code 是存储在数据库的值（1, 2, 3）
- ✅ message 是给用户看的中文说明
- ✅ 不能直接用中文存数据库！

## ❌ 错误的枚举用法

### ❌ 不要在枚举值上使用 @EnumValue

```java
// ❌ 错误！@EnumValue 不是用在枚举值上的
public enum UserStatus {
    @EnumValue("激活")      // 错误！
    ACTIVE,
    
    @EnumValue("禁用")      // 错误！
    DISABLED
}
```

### ✅ 正确的枚举用法

```java
// ✅ 正确！@EnumValue 和 @EnumMessage 用在枚举类的字段上
@AllArgsConstructor
@Getter
public enum UserStatus {
    ACTIVE(1, "激活"),      // 枚举值带参数
    DISABLED(2, "禁用");
    
    @EnumValue              // 用在字段上
    private final Integer code;
    
    @EnumMessage            // 用在字段上
    private final String message;
}
```

**为什么这样设计？**
- ✅ `code` 存储在数据库（1, 2, 3...）
- ✅ `message` 用于前端显示（"激活", "禁用"）
- ✅ 枚举名称用于代码（ACTIVE, DISABLED）

## ❌ 错误的注解用法

### ❌ 不要在参数上使用 @ApiProperty

```java
// ❌ 错误！@ApiProperty 不能用在参数上
public User getUser(
    @ApiProperty(value = "用户ID")  // 错误！
    @PathVariable Long id
) { }
```

### ❌ 不要在参数上使用 @ApiModel

```java
// ❌ 错误！@ApiModel 不能用在参数上
public Response<User> createUser(
    @ApiModel(description = "用户")  // 错误！
    @RequestBody User user
) { }
```

### ✅ 正确的做法

```java
// ✅ 正确！直接使用 Spring MVC 注解
@PostMapping
@ApiOperation(value = "创建用户", description = "创建新用户")
public Response<User> createUser(@Valid @RequestBody User user) {
    // 框架会自动识别 @RequestBody 并使用 User 类上的 @ApiModel 注解
}

@GetMapping("/{id}")
@ApiOperation(value = "获取用户", description = "获取用户详情")
public Response<User> getUser(@PathVariable Long id) {
    // 框架会自动识别 @PathVariable 参数
}

@GetMapping
@ApiOperation(value = "查询用户", description = "分页查询用户列表")
public Response<List<User>> listUsers(
    @RequestParam(defaultValue = "1") Integer page,
    @RequestParam(defaultValue = "10") Integer pageSize
) {
    // 框架会自动识别 @RequestParam 参数
}
```

## 🎯 框架自动识别的内容

框架会自动识别以下内容，**无需额外注解**：

### 1. 参数识别

- `@PathVariable` - 路径参数
- `@RequestParam` - 查询参数
- `@RequestBody` - 请求体
- `@RequestHeader` - 请求头

### 2. 参数信息

- 参数名称（通过反射获取）
- 参数类型（String, Integer, Long 等）
- 是否必需（required 属性）
- 默认值（defaultValue 属性）

### 3. 验证规则

框架会自动识别 JSR-303 验证注解：

- `@NotNull`
- `@NotBlank`
- `@Size`
- `@Min` / `@Max`
- `@Email`
- `@Pattern`
- 等等...

这些验证规则会自动显示在文档中！

## 📋 完整示例

### Controller 示例

```java
@RestController
@RequestMapping("/api/users")
@Api(tags = "用户管理", description = "用户CRUD操作")
public class UserController {

    @GetMapping
    @ApiOperation(value = "用户列表", description = "分页查询用户")
    public Response<List<User>> list(
        @RequestParam(defaultValue = "1") Integer page,
        @RequestParam(defaultValue = "10") Integer size
    ) {
        // 无需额外注解，框架自动识别参数
        return Response.success(users);
    }

    @GetMapping("/{id}")
    @ApiOperation(value = "用户详情", description = "获取单个用户")
    public Response<User> get(@PathVariable Long id) {
        // 无需额外注解
        return Response.success(user);
    }

    @PostMapping
    @ApiOperation(value = "创建用户", description = "创建新用户")
    public Response<User> create(@Valid @RequestBody User user) {
        // User 类上的 @ApiModel 会被自动使用
        return Response.success(user);
    }
}
```

### Model 示例

```java
@Data
@ApiModel(description = "用户实体")
public class User {
    
    @ApiModelProperty(value = "用户ID", example = "1001")
    private Long id;
    
    @ApiModelProperty(value = "用户名", required = true, example = "john")
    @NotBlank(message = "用户名不能为空")
    @Size(min = 3, max = 50)
    private String username;
    
    @ApiModelProperty(value = "邮箱", required = true, example = "john@example.com")
    @Email
    @NotBlank
    private String email;
}
```

## 🔑 关键要点

1. **@Api** → 只能用在 **Controller 类**上
2. **@ApiOperation** → 只能用在 **方法**上
3. **@ApiModel** → 只能用在 **实体类**上
4. **@ApiModelProperty** → 只能用在 **字段**上
5. **@ApiProperty** → 用在 **方法**上（不是参数！）
6. **参数文档** → 框架**自动识别** Spring MVC 注解

## 💡 记住

> **框架会自动识别 Spring MVC 的标准注解！**
> 
> 不需要在每个参数上都加 @ApiProperty 或 @ApiModel！
> 
> 只需要：
> - Controller 上加 @Api
> - 方法上加 @ApiOperation  
> - 实体类上加 @ApiModel
> - 字段上加 @ApiModelProperty
> 
> 其他的框架会自动处理！

---

查看完整示例代码：
- [UserController.java](src/main/java/com/csap/framework/example/controller/UserController.java)
- [ProductController.java](src/main/java/com/csap/framework/example/controller/ProductController.java)
- [User.java](src/main/java/com/csap/framework/example/model/User.java)

