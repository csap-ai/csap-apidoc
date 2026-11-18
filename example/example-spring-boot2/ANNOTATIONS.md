# 注解使用指南

本页对应示例项目里的实际写法,注解均来自 `csap-apidoc-annotation`(包 `ai.csap.apidoc.annotation`)。
完整属性说明以官网 https://apidoc.csap.ai 为准。

## 各注解的作用位置

| 注解 | 位置 | 说明 |
|---|---|---|
| `@Api` | 类 | Controller 的名称、描述、分组等 |
| `@ApiOperation` | 方法 | 接口名称、描述 |
| `@ApiProperty` | 方法 或 参数 | 补充单个请求参数的文档 |
| `@ApiPropertys` | 方法 | 一次补充多个参数,内含多个 `@ApiProperty` |
| `@ApiModel` | 类 | 实体类说明 |
| `@ApiModelProperty` | 字段 | 实体字段说明 |
| `@EnumValue` / `@EnumMessage` | 枚举类的字段 | 标记枚举的取值字段与说明字段 |

## Controller 层

### @Api(类)

```java
@RestController
@RequestMapping("/api/users")
@Api(value = "用户管理", description = "用户增删改查相关接口")
public class UserController { }
```

### @ApiOperation(方法)

`value` 是必填项。

```java
@GetMapping("/{id}")
@ApiOperation(value = "获取用户详情", description = "根据用户ID获取用户详细信息")
public Response<User> getUser(@PathVariable Long id) { ... }
```

### @ApiProperty 与 @ApiPropertys

`@ApiProperty` 可以写在方法上(通过 `name` 匹配方法的参数名),也可以直接写在参数上。`value` 必填。

写在方法上(`AdvancedController`):

```java
@GetMapping("/search")
@ApiOperation(value = "搜索用户", description = "根据关键词搜索用户")
@ApiProperty(
    value = "搜索关键词",
    name = "keyword",              // 对应方法参数名 keyword
    description = "用户名或邮箱关键词",
    required = true,
    example = "john",
    paramType = ParamType.QUERY
)
public Response<List<User>> searchByKeyword(String keyword) { ... }
```

多个参数用 `@ApiPropertys`:

```java
@GetMapping("/filter")
@ApiOperation(value = "过滤用户")
@ApiPropertys({
    @ApiProperty(value = "最小年龄", name = "minAge", example = "18",
                 paramType = ParamType.QUERY, dataTypeClass = Integer.class),
    @ApiProperty(value = "最大年龄", name = "maxAge", example = "60",
                 paramType = ParamType.QUERY, dataTypeClass = Integer.class)
})
public Response<List<User>> filterUsers(Integer minAge, Integer maxAge) { ... }
```

写在参数上(boot3 示例的 `ProductApi`):

```java
Response<List<Product>> listProducts(
    @ApiProperty(value = "页码", example = "1", defaultValue = "1")
    @RequestParam(defaultValue = "1") Integer page,
    @ApiProperty(value = "每页数量", example = "10", defaultValue = "10")
    @RequestParam(defaultValue = "10") Integer pageSize);
```

## Model 层

### @ApiModel(类)

```java
@Data
@ApiModel(description = "用户实体")
public class User { }
```

`@ApiModel` 只能用在类上,不能写在方法参数上。参数是实体时,框架会读取实体类上的 `@ApiModel`。

### @ApiModelProperty(字段)

```java
@ApiModelProperty(value = "用户名", required = true, example = "john_doe")
@NotBlank(message = "用户名不能为空")
@Size(min = 3, max = 50, message = "用户名长度必须在3-50之间")
private String username;
```

## 枚举

`@EnumValue` 与 `@EnumMessage` 写在枚举类的**字段**上(不是枚举常量上)。

```java
@AllArgsConstructor
@Getter
public enum UserStatus {
    ACTIVE(1, "激活"),
    DISABLED(2, "禁用"),
    LOCKED(3, "锁定"),
    PENDING(4, "待审核");

    @EnumValue          // 取值字段(如存数据库的 code)
    private final Integer code;

    @EnumMessage        // 说明字段(展示用)
    private final String message;
}
```

文档中会得到 code / message 对应的选项列表。

## 框架自动识别的内容

无需额外注解即可识别:

- Spring MVC 参数注解: `@PathVariable`、`@RequestParam`、`@RequestBody`、`@RequestHeader`
- 参数名、类型、`required`、`defaultValue`
- JSR-303 校验注解(`@NotNull`、`@NotBlank`、`@Size`、`@Min` / `@Max`、`@Email`、`@Pattern` 等),会显示在文档中

## 要点

1. `@Api`、`@ApiModel` 只能写在类上
2. `@ApiOperation` 只能写在方法上
3. `@ApiModelProperty` 只能写在字段上
4. `@ApiProperty` 可写在方法或参数上,`@ApiPropertys` 只能写在方法上
5. 没有 `@ApiProperty` 时,参数仍会被识别,只是缺少说明与示例

## 示例代码

- [UserController.java](src/main/java/ai/csap/example/controller/UserController.java)
- [AdvancedController.java](src/main/java/ai/csap/example/controller/AdvancedController.java)
- [User.java](src/main/java/ai/csap/example/model/User.java)
- [UserStatus.java](src/main/java/ai/csap/example/model/UserStatus.java)
