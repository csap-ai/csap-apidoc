# 注解使用指南

本页按使用场景介绍如何用 CSAP Apidoc 的注解描述接口：Controller、方法、参数、模型字段、枚举、分组与版本、强制输出、请求头和鉴权提示。每个注解的完整属性表见 [注解参考](/api/annotations)。

## 文档是怎么被扫描出来的

一个接口要出现在文档里，需要同时满足：

1. 所在类被 `@EnableApidoc` 的 `apiPackages`（或 `value`）/ `apiPackageClasses` 扫描到，且类上（或其接口、父类上）有 `@Api`；
2. 类是 Spring 的 `@Controller`（`@RestController` 即可）；
3. 方法是 `public` 的，带 `@ApiOperation`，并且带 `@RequestMapping` 系列注解（`@GetMapping`、`@PostMapping` 等）。

`@EnableApidoc` 的用法见 [快速开始](/guide/quick-start)。下面是本页贯穿使用的完整示例：

```java
import org.springframework.web.bind.annotation.*;

import ai.csap.apidoc.annotation.Api;
import ai.csap.apidoc.annotation.ApiOperation;
import ai.csap.apidoc.annotation.ApiProperty;
import ai.csap.apidoc.core.ApidocResult;

@RestController
@RequestMapping("/users")
@Api(value = "用户管理", description = "用户的增删改查", tags = {"用户"})
public class UserController {

    @ApiOperation(value = "创建用户", description = "用户名不能重复")
    @PostMapping
    public ApidocResult<User> createUser(@RequestBody User user) {
        return ApidocResult.success(user);
    }

    @ApiOperation(value = "查询用户")
    @GetMapping("/{id}")
    public ApidocResult<User> getUser(@ApiProperty(value = "用户ID", required = true, example = "1001")
                                      @PathVariable Long id) {
        return ApidocResult.success(new User());
    }
}
```

```java
import jakarta.validation.constraints.NotBlank;

import ai.csap.apidoc.annotation.ApiModel;
import ai.csap.apidoc.annotation.ApiModelProperty;
import ai.csap.apidoc.annotation.Group;
import ai.csap.apidoc.annotation.Request;
import ai.csap.apidoc.annotation.Response;

@ApiModel(value = "用户", description = "用户信息")
public class User {

    @ApiModelProperty(value = "用户ID", example = "1001", groups = {
            @Group(value = "getUser", response = @Response)
    })
    private Long id;

    @NotBlank(message = "用户名不能为空")
    @ApiModelProperty(value = "用户名", example = "alice", groups = {
            @Group(value = "createUser", request = @Request(required = true)),
            @Group(value = "getUser", response = @Response)
    })
    private String username;

    @ApiModelProperty(value = "密码", ignoreRep = true, groups = {
            @Group(value = "createUser", request = @Request(required = true))
    })
    private String password;

    // getter / setter 省略
}
```

`ApidocResult` 是框架自带的统一返回体（`ai.csap.apidoc.core.ApidocResult`），它的 `code`、`message`、`data`、`language`、`time` 字段都标了 `forceRep = true`，所以在任何接口的返回参数里都会出现。你也可以用自己的返回体，做法相同。

:::tip 字段默认不会出现在文档里
`@ApiModelProperty` 只描述字段"是什么"；某个字段是否出现在某个接口的请求或返回参数里，由**字段规则**决定：注解方式下是 `groups` 里与方法名匹配的 `@Group`，YAML / SQLite 方式下是规则文件里的条目。没有匹配规则、也没有 `forceReq` / `forceRep` 的字段，不会出现。详见 [存储策略](/guide/storage-strategies)。
:::

## Controller：@Api

`@Api` 标在 Controller 类上，没有它的类不会被扫描。

```java
@Api(value = "订单管理",
     description = "订单相关接口",
     position = 1,
     tags = {"订单", "交易"},
     group = {"admin"},
     version = {"v1"},
     hiddenMethod = {"internalSync"},
     status = ApiStatus.FINISH,
     protocols = Protocols.HTTP)
```

- `value` / `description`：文档里 Controller 的名称和说明。
- `position`：排序位置。
- `hidden = true`：整个 Controller 不出现在文档里。
- `hiddenMethod`：按**方法名**隐藏若干方法。
- `showMethod`：白名单，设置后只展示列表里的方法。同一个方法同时出现在两个列表时，`hiddenMethod` 先生效（方法被隐藏）。
- `status`：接口状态，取值 `DEFAULT`（开发中）、`FINISH`（完成）、`DEPRECATED`（过时）。
- `protocols`：`HTTP`、`HTTPS`、`WS`、`WSS`。

`@Api` 可以写在 Controller 实现的接口上，框架会通过接口找到它。

## 方法：@ApiOperation

```java
@ApiOperation(value = "分页查询订单",
              description = "按创建时间倒序",
              tags = {"查询"},
              status = ApiStatus.FINISH,
              paramType = ParamType.QUERY)
@GetMapping("/page")
public ApidocResult<List<Order>> listOrders(OrderQuery query) { ... }
```

- `value` 必填，是接口标题。
- `hidden = true`：不出现在文档里。
- `paramType`：整个方法所有字段的参数位置。默认 `ParamType.DEFAULT`，此时如果方法有 `@RequestBody` 参数，参数位置按 `BODY` 处理；否则按各字段自己的 `paramType`。设置为其他值时，覆盖方法内所有模型字段的 `paramType`。
- `headers`：接口的请求头说明，见下文 [请求头](#请求头headers-与-docglobalheader)。
- `group` / `version`：见 [分组与版本](#分组与版本)。

`@ApiOperation` 也可以写在接口或父类的方法上，实现类会继承。

:::warning
`@ApiOperation` 的 `auth` 和 `authType` 属性目前没有被读取，设置它们不会影响文档。要给调试界面提示鉴权方式，请用 [@DocAuth](#鉴权提示docauth)。
:::

:::warning
带 `@ApiOperation` 的方法必须同时带 `@RequestMapping` 系列注解。`@RequestMapping` 没有指定 `method` 时，文档里会列出 `POST`、`DELETE`、`GET` 三种方法。
:::

## 方法参数：@ApiProperty

基础类型参数（`String`、数字、日期、枚举等）总会出现在请求参数中。用 `@ApiProperty` 直接标在参数上，补充说明、示例和必填：

```java
@ApiOperation(value = "按状态查询")
@GetMapping("/search")
public ApidocResult<List<Order>> search(
        @ApiProperty(value = "订单状态", required = true, example = "PAID") @RequestParam String status,
        @ApiProperty(value = "关键字", description = "按订单号模糊匹配") @RequestParam(required = false) String keyword) {
    ...
}
```

- 参数位置：带 `@ApiProperty` 的参数，如果同时带 `@PathVariable` 则为 `PATH`，否则为 `@ApiProperty.paramType`（默认 `QUERY`）。没有 `@ApiProperty` 的参数使用方法的参数位置（见上文 `@ApiOperation.paramType`）。使用 YAML / SQLite 规则时，带 `@PathVariable` 的参数总是 `PATH`。
- `name` 可以覆盖文档中显示的参数名，不填时使用 Java 参数名。
- `@ApiProperty` 写在接口或父类方法的参数上时，实现类同样能读到。

:::warning 方法级写法目前不生效
`@ApiProperty` 和 `@ApiPropertys` 的注解定义允许标在方法上（通过 `name` 匹配参数）。但目前生成文档时只读取**参数上**的 `@ApiProperty`，方法级写法不会被读取。请把 `@ApiProperty` 直接标在参数上。
:::

对象类型参数（如 `@RequestBody User user`）按模型字段处理，见下一节。

## 模型与字段：@ApiModel、@ApiModelProperty

`@ApiModel` 标在实体类上，`@ApiModelProperty` 标在字段上。**没有 `@ApiModelProperty` 的字段一律不会出现在文档里。**

```java
@ApiModel(value = "订单", description = "订单信息")
public class Order {

    @ApiModelProperty(value = "订单号", example = "SO20260101001", position = 1)
    private String orderNo;

    @ApiModelProperty(value = "金额", description = "单位：元", length = 10, decimals = 2, example = "99.90")
    private BigDecimal amount;

    @ApiModelProperty(value = "备注", hidden = true)
    private String remark;
}
```

常用属性：

- `value` 必填，字段的简短说明；`description` 是补充说明。
- `name`：文档中显示的字段名，不填时使用 Java 字段名。字段规则（`@Group`、YAML 条目）也按这个名字匹配。
- `example`、`defaultValue`、`position`、`length`、`decimals`：文档展示用。
- `required`：字段必填的默认值。注解方式下，匹配到的 `@Group` 会用 `@Request.required` / `@Response.required` 覆盖它。
- `paramType`：字段的参数位置，默认 `QUERY`；会被 `@ApiOperation.paramType` 覆盖（见上文）。
- `hidden = true`：字段在所有接口中都不出现。

字段类型是另一个带 `@ApiModelProperty` 字段的对象、`List` 或 `Map` 时，框架会继续向下解析子字段。

## 按接口区分字段：groups

同一个实体类往往在不同接口里需要不同的字段：创建时需要 `password`，查询时不能返回它。注解方式下，用 `@ApiModelProperty` 的 `groups` 为每个接口单独声明：

```java
@ApiModelProperty(value = "用户名", groups = {
        @Group(value = "createUser", request = @Request(required = true, paramType = ParamType.BODY)),
        @Group(value = "getUser", response = @Response)
})
private String username;
```

- `@Group.value` 是**方法名**。不同 Controller 有同名方法时，写成 `"UserController.createUser"`（Controller 简单类名 + `.` + 方法名）精确匹配。
- 只要有匹配的 `@Group`，字段就会出现在该方法的**请求参数**里，`@Request.required` 和 `@Request.paramType` 决定必填和参数位置。
- 字段出现在**返回参数**里需要 `@Response.include` 为 `true`。注意 `@Group.response` 的默认值是 `@Response(include = false)`，所以要返回某个字段时必须显式写 `response = @Response`（`@Response` 自身的 `include` 默认是 `true`）。

:::warning
目前不读取 `@Request.value`。即使写 `request = @Request(value = false)`，匹配到的字段仍会进入该方法的请求参数。
:::

`groups` 只在注解策略下生效；改用 YAML 或 SQLite 规则时，框架不读 `groups`，改从规则文件判断。见 [存储策略](/guide/storage-strategies)。

## 强制输出与强制忽略

不想为每个方法写 `@Group` 的通用字段，可以用四个开关：

| 属性 | 作用 |
|------|------|
| `forceReq = true` | 只要该类型被用作请求参数，字段就出现 |
| `forceRep = true` | 只要该类型被用作返回参数，字段就出现 |
| `ignoreReq = true` | 在任何接口的请求参数中都不出现，优先于 `forceReq` |
| `ignoreRep = true` | 在任何接口的返回参数中都不出现，优先于 `forceRep` |

典型用法是统一返回体的公共字段：

```java
@ApiModel(value = "统一返回")
public class Result<T> {
    @ApiModelProperty(value = "状态码", example = "0", forceRep = true)
    private String code;

    @ApiModelProperty(value = "提示信息", forceRep = true)
    private String message;

    @ApiModelProperty(value = "数据", forceRep = true)
    private T data;
}
```

`forceReq` / `forceRep` 在所有存储策略下都有效。

## 枚举

### 字段是枚举类型

字段类型是枚举时，文档会列出所有枚举常量。用 `@EnumValue` 和 `@EnumMessage` 标出枚举里表示"值"和"说明"的字段，文档会一起展示：

```java
public enum OrderStatus {
    CREATED(1, "已创建"),
    PAID(2, "已支付");

    @EnumValue
    private final int code;

    @EnumMessage
    private final String desc;

    OrderStatus(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public int getCode() { return code; }

    public String getDesc() { return desc; }
}
```

每个常量在文档中显示为 `name`（常量名）、`code`（`@EnumValue` 字段值）、`message`（`@EnumMessage` 字段值）。框架优先通过 getter 读取字段值，没有 getter 时直接读字段。

### 全局返回码表：@ApiResponseCode

把业务返回码定义成枚举、标上 `@ApiResponseCode`，并通过 `@EnableApidoc(enumPackages = ...)` 或 `enumPackageClasses` 扫描到，文档会生成一张返回码表：

```java
public interface BizCode extends IValidate<String, Object> {
}

@ApiResponseCode("业务返回码")
public enum OrderCode implements BizCode {
    ORDER_NOT_FOUND("2001", "订单不存在"),
    ORDER_PAID("2002", "订单已支付");

    private final String code;
    private final String message;

    OrderCode(String code, String message) {
        this.code = code;
        this.message = message;
    }

    @Override
    public String getCode() { return code; }

    @Override
    public String getMessage() { return message; }

    @Override
    public String getName() { return name(); }
}
```

```java
@EnableApidoc(apiPackages = "com.example.controller", enumPackageClasses = OrderCode.class)
```

:::warning
读取返回码的方式比较特殊：枚举必须实现一个**继承自** `ai.csap.apidoc.util.IValidate` 的接口（如上面的 `BizCode`）。直接 `implements IValidate` 的枚举只会登记名称，不会列出码值；实现了与 `IValidate` 无关的接口则会在读取时出错。`@ApiResponseCode` 的 `group`、`version` 属性目前没有被读取。
:::

## 分组与版本

`@Api`、`@ApiOperation`、`@ApiModel`、`@ApiModelProperty`、`@ApiProperty`、`@ApiResponseCode`、`@Headers` 都有 `group` 和 `version` 属性（`String[]`，默认 `"default"`）。扫描到的所有分组、版本会汇总到文档数据的 `groups`、`versions` 列表里，供文档页面切换。

取值规则：

- `@Api` 用自己的值。
- `@ApiOperation` 用自己的值；保持默认时就是 `default`，**不会**继承 `@Api` 的值。方法上声明的分组会被加到所属 Controller 的分组集合中。
- `@ApiModel` 保持默认时继承所在方法的值；`@ApiModelProperty`、`@ApiProperty` 保持默认时继承上一级（模型或方法）的值。

```java
@Api(value = "订单管理", group = {"admin", "app"})
public class OrderController {

    @ApiOperation(value = "后台查询订单", group = "admin", version = "v2")
    @GetMapping("/admin/list")
    public ApidocResult<List<Order>> adminList() { ... }
}
```

`@Headers` 和 `@ApiResponseCode` 上的 `group`、`version` 目前没有被读取。

## 请求头：headers 与 @DocGlobalHeader

### 接口文档里的请求头

`@ApiOperation.headers` 描述接口需要的请求头。它的默认值是一个必填的 `Content-Type: application/json`；你自己设置 `headers` 时会替换掉这个默认值：

```java
@ApiOperation(value = "上传头像", headers = {
        @Headers(key = "Content-Type", value = "multipart/form-data", required = true, description = "上传类型"),
        @Headers(key = "X-Request-Id", value = "", description = "请求追踪 ID", example = "X-Request-Id=abc123")
})
```

需要给所有接口追加统一请求头时，可以注册一个 `ai.csap.apidoc.CsapHeadersProvider` 类型的 Bean（框架默认实现返回空列表），它返回的请求头会追加到每个声明了 `headers` 的接口上。`headers = {}` 的接口不会追加。

### 调试界面的全局请求头提示：@DocGlobalHeader

`@DocGlobalHeader` 只为文档 UI 的 Try-it-out 调试提供**预填建议**，不影响后端处理，也不强制调用方携带。可以标在方法、Controller 类或包（`package-info.java`）上，可重复：

```java
@DocGlobalHeader(name = "X-Tenant-Id", description = "租户标识", example = "demo", required = true)
@DocGlobalHeader(name = "X-Trace-Id", description = "可选的链路追踪 ID")
@RestController
@RequestMapping("/tenant")
@Api(value = "租户接口")
public class TenantController { ... }
```

三处的声明会合并，同名时按 方法 > 类 > 包 的优先级覆盖。合并结果输出在每个接口的 `globalHeaderHints` 字段中。

## 鉴权提示：@DocAuth

`@DocAuth` 同样只是给 Try-it-out 调试界面选择鉴权预设用的提示，**不会**影响后端实际的鉴权：

```java
@DocAuth(scheme = "bearer", description = "由 /auth/login 颁发的 JWT")
@RestController
@RequestMapping("/orders")
@Api(value = "订单管理")
public class OrderController {

    @DocAuth(scheme = "apikey", in = "header", name = "X-API-Key")
    @ApiOperation(value = "开放接口")
    @GetMapping("/open")
    public ApidocResult<String> open() { ... }

    @DocAuth(scheme = "none")
    @ApiOperation(value = "公开接口")
    @GetMapping("/public")
    public ApidocResult<String> publicInfo() { ... }
}
```

- `scheme` 取值：`bearer`、`basic`、`apikey`、`oauth2_client`、`none`。其他值会记录一条 WARN 日志，但仍原样输出。
- `in`、`name` 只对 `apikey` 有意义；`in` 取 `header`（默认）或 `query`。
- 方法、类、包上各最多一个，取第一个找到的：方法 > 类 > 包。结果输出在接口的 `authHint` 字段中。

## 其他注解

| 注解 | 用途 |
|------|------|
| `@DataTypeValidate` | 标在 Controller 方法参数上。参数类型转换失败（`MethodArgumentTypeMismatchException`）时，由校验模块的异常处理器返回注解里的 `code` 和 `message`。需要引入 `csap-validation-boot-starter`，见 [参数校验](/guide/validation) |
| `@JsonFieldFilter` / `@JsonFieldFilters` | 目前没有任何代码读取，标注无效果 |
| `@Description` | 目前没有任何代码读取 |
| `@RequestParamBody` | 目前没有任何代码读取 |

## 相关页面

- [注解参考](/api/annotations)：所有注解的完整属性表
- [存储策略](/guide/storage-strategies)：字段规则的三种来源与合并方式
- [参数校验](/guide/validation)：JSR-303 注解与 YAML 校验规则
