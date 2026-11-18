# 参数校验

本页介绍 `csap-validation-boot-starter` 的运行时请求参数校验（1.2.0）：怎么开启、三种模式、配置项、规则写法、失败时的返回格式，以及目前的限制。

:::info 适用范围
本页讲的是 **Java 版**的运行时参数校验。Go 版有对应功能(`validate` 包和 `RequestValidator` 中间件,默认关闭),见 [Go 版](/go/)。两者有一处行为差别:非必填字段上的规则,Java 不执行,Go 在请求传了该字段时会执行,两边是否对齐计划在 1.4.0 前确定。
:::

:::tip 1.2.0 之前
1.1.x 会登记校验规则，但请求处理时没有组件执行它们，开启 `csap.validation.enabled` 也不会拦截任何请求；并且在 Spring Boot 2 应用中引入这个 starter 会因为缺少 `jakarta.validation` 类而启动失败。1.2.0 补上了执行环节，同时支持 Spring Boot 2（`javax.validation`）和 Spring Boot 3（`jakarta.validation`）。
:::

## 引入与开启

```xml
<dependency>
    <groupId>ai.csap.apidoc.boot</groupId>
    <artifactId>csap-validation-boot-starter</artifactId>
    <version>1.2.0</version>
</dependency>
```

**默认关闭**。只引入 starter 不会校验任何请求。建议先用观察模式跑一段时间，确认不会误拦截正常调用方，再切到拦截模式：

```yaml
csap:
  validation:
    mode: observe        # off（默认）/ observe / enforce
```

同一个 jar 支持 Spring Boot 2.x 和 3.x，不依赖 Servlet API 的具体包名。

## 三种模式

| 模式 | 行为 |
|------|------|
| `off`（默认） | 不校验，不包装参数解析器，没有运行时开销 |
| `observe` | 校验并记录（warn 日志 + Micrometer 计数），请求照常进入接口方法 |
| `enforce` | 校验失败直接返回错误，不进入接口方法；同样计数 |

可以按接口覆盖全局模式，例如全局观察、个别接口拦截：

```yaml
csap:
  validation:
    mode: observe
    endpoints:
      "[com.example.controller.UserController.createUser]": enforce
      "[com.example.controller.UserController.importUsers]": off
```

接口的键是 **Controller 全限定类名 + `.` + 方法名**。键里有 `.`，YAML 中要写成 `"[...]"`，properties 文件中写成 `csap.validation.endpoints[com.example.controller.UserController.createUser]=enforce`。同名的重载方法共用一个键。

## 配置项

| 配置 | 默认值 | 说明 |
|------|--------|------|
| `csap.validation.mode` | `off` | 全局模式：`off` / `observe` / `enforce` |
| `csap.validation.endpoints` | 空 | 按接口覆盖模式，键见上文 |
| `csap.validation.http-status` | `200` | 校验失败时的 HTTP 状态码，响应体格式不变 |
| `csap.validation.tip-type` | `TIP_1` | 提示信息格式，见 [返回格式](#返回格式) |
| `csap.validation.required-code` | `"400"` | 字段只配了必填、没配任何规则时，值为空返回的 `code`，见 [只配了必填的字段](#只配了必填的字段) |
| `csap.validation.required-message` | `不能为空` | 同上，返回的提示（按 `tip-type` 拼上字段名） |
| `csap.validation.enabled` | 未配置 | 1.1.x 的旧开关，见下文 |
| `csap.validation.code` / `message` | `code` / `message` | 目前未被读取 |

旧开关 `csap.validation.enabled` 仍然有效：

| 配置 | 生效的全局模式 |
|------|----------------|
| 只写 `enabled: true` | `enforce` |
| `enabled: true` 且写了 `mode` | 以 `mode` 为准 |
| `enabled: false` | 全部关闭，包括 `endpoints` 中的覆盖 |
| 都不写 | `off` |

:::warning 从 1.1.x 升级
1.1.x 中写了 `csap.validation.enabled: true` 的项目，升级后会**真正开始拦截**校验失败的请求。如果不确定规则是否都正确，先改成 `mode: observe`。
:::

## 工作方式

- 开启后（全局模式或任一接口覆盖不是 `off`），starter 把 Spring MVC `RequestMappingHandlerAdapter` 的每个参数解析器包装一层：先由原解析器完成解析，再对解析出的参数值执行校验。原解析器的顺序不变。
- 只对带 `@ApiOperation` 的接口方法生效（注解写在 Controller 实现的接口方法上也可以）。每个方法的模式只计算一次并缓存。
- 默认的参数处理器是 `ValidateIntercept`，它处理三类参数：基础类型（含 `String`、日期等）参数、实体类带 `@ApiModel` 的对象参数、以及它们的 `List`。其他类型的参数不校验。
- 应用里所有 `ai.csap.apidoc.core.ApiMethodHandle` Bean 都会按 `@Order` / `getOrder()` 依次调用，可以用它挂自定义的参数检查。
- 只支持 Spring MVC（Servlet），不支持 WebFlux。

### 规则从哪来、什么时候生效

规则在**生成文档时**登记到 `IValidateFactory`，请求到达时每次从这里读取：

- 应用启动后，文档在后台异步扫描，扫描完成后规则才生效；扫描完成前到达的请求不会被校验。
- 通过 [DevTools](/guide/devtools) 修改规则后，文档重新生成，**下一个请求**就按新规则校验，不需要重启。重新生成的瞬间（先清空再登记）到达的请求可能不被校验。

## 规则写法

### 方式一：约束注解

在实体字段上同时写 `@ApiModelProperty` 和约束注解。Spring Boot 2 项目用 `javax.validation.constraints.*`，Spring Boot 3 项目用 `jakarta.validation.constraints.*`，两套都能识别：

```java
@ApiModel(value = "订单")
public class Order {

    @NotBlank(message = "订单号不能为空")
    @Size(max = 32, message = "订单号最长32位")
    @ApiModelProperty(value = "订单号", groups = {
            @Group(value = "createOrder", request = @Request(required = true))
    })
    private String orderNo;
}
```

- 内置约束（`@NotNull`、`@Size`、`@Pattern`、`@Email`、`@Min` 等）用当前 Hibernate Validator 自带的校验器执行；自定义约束用注解上 `@Constraint(validatedBy = ...)` 指定的校验器执行。
- Spring Boot 3 项目里残留的 `javax.validation.constraints.*` 注解（或反过来）会换成当前 Hibernate Validator 认识的同名注解执行。
- 注解写了明文 `message` 时原样使用；没写时按 `ValidationMessages.properties` 和 Hibernate Validator 自带的消息包（JVM 默认语言）取文案，并替换 `{min}`、`{max}` 等属性占位符。`${...}` 表达式不求值，会被去掉。
- 约束注解没有 `code` 属性时，失败返回的 `code` 为 `"400"`；自定义注解可以加一个 `code` 属性指定。
- 校验器拿到的 `ConstraintValidatorContext` 是一个只读的简化实现：可以取时钟（`@Past`、`@Future` 可用），构建自定义违规信息的调用会被忽略。校验器自身抛出异常时按通过处理，并记一次 warn 日志。
- 注解策略下，规则只来自约束注解；YAML / SQLite 策略下，字段上的约束注解会与规则文件中的 `validate` 合并。

### 方式二：YAML / SQLite 规则

在规则文件中为请求字段写 `validate` 列表，`type` 取 `NotNull`、`NotEmpty` 或 `Pattern`：

```yaml
com.example.controller.OrderController:
  createOrder:
    request:
      order.orderNo:
        required: true
        validate:
          - type: NotEmpty
            level: 1
            code: "1001"
            message: 不能为空
          - type: Pattern
            level: 2
            pattern: '^SO\d{10,20}$'
            code: "1002"
            message: 格式不正确
            tipType: TIP_3
```

完整的文件格式和字段键写法见 [存储策略 - YAML 策略](/guide/storage-strategies#yaml-策略)。三种规则类型的含义：

| `type` | 失败条件 |
|--------|----------|
| `NotNull` | 值为 `null` |
| `NotEmpty` | 值为 `null`、空字符串、空集合、空 Map 或空数组 |
| `Pattern` | 值不为 `null`，且 `toString()` 后不能**整串**匹配 `pattern`。值为 `null` 时不判定失败，需要非空请再加一条 `NotNull`。`pattern` 写错（无法编译）时这条规则被跳过并记 warn 日志 |

:::warning YAML / SQLite 策略只校验规则文件里列出的字段
这两种策略下，请求字段要在规则文件中出现（至少写 `required: true`）才会登记，字段上的约束注解也只在这时合并进来。
:::

### 只有必填字段的规则会执行

:::warning required 决定规则是否生效
生成文档时，框架只在字段**必填**时才把它的校验规则登记到 `IValidateFactory`：

- 实体字段：注解策略下取匹配 `@Group` 的 `@Request.required`（没有匹配的 `@Group` 而靠 `forceReq` 出现时取 `@ApiModelProperty.required`）；YAML / SQLite 策略下取规则中的 `required`；
- 基础类型的方法参数：注解策略下取 `@ApiProperty.required`；YAML / SQLite 策略下，规则里有这个参数时取规则中的 `required`（规则说不必填时，`@ApiProperty(required = true)` 不再生效），没有这个参数的规则时取 `@ApiProperty.required`（1.3.0 起；1.2.x 中没有规则时不登记）。文档里显示的必填与此一致。

非必填字段上的规则**仍会显示在文档里**，但运行时不会执行。
:::

### 只配了必填的字段

1.2.0 起，字段**必填但没有任何校验规则**（没写 `validate`，也没有约束注解）时，运行时校验开启后会检查它是否为空：值为 `null`、空字符串、空集合、空 Map 或空数组即失败（只含空格的字符串不算空）。失败返回：

- `code`：`csap.validation.required-code`，默认 `"400"`；
- `message`：`csap.validation.required-message`，默认 `不能为空`，按 `tip-type` 拼上字段名，例如 `字段nickname,不能为空`；
- 日志和指标中的规则名为 `Required`。

与 Go 版的 `RequiredCode` / `RequiredMessage` 默认值一致。字段只要有一条规则（包括合并进来的约束注解），就只按规则校验，不再额外检查非空。这项检查同样只在 `observe` / `enforce` 模式下执行，`off` 时不生效。

:::warning 从 1.1.x 升级
1.1.x 中只写了 `required: true`（或 `@Request(required = true)`）而没有规则的字段不会被检查。升级到 1.2.0 并开启校验后，这些字段为空的请求会被判定失败。建议先用 `observe` 模式观察一段时间。
:::

### 执行顺序

同一字段的多条规则（包括合并进来的约束注解）按 `level` 从小到大排列，逐条执行，**第一条失败即停止**并返回该条的 `code` 和提示。约束注解生成的规则 `level` 为 `1`。

## 返回格式

`enforce` 模式下校验失败时抛出 `ValidationViolationException`（`ValidateException` 的子类），由 `ValidateExceptionHandler` 返回 `ApidocResult`：

```json
{
  "code": "1001",
  "message": "字段username,不能为空",
  "data": null,
  "language": "ZH",
  "time": 1767225600000
}
```

HTTP 状态码默认 **200**，可用 `csap.validation.http-status` 修改（例如 `400`），响应体不变。`data` 为 `null` 时是否输出取决于你的 Jackson 配置。

`message` 的格式由 `csap.validation.tip-type` 决定，单条规则可用 `tipType` 覆盖。"字段名称"是规则登记时的字段名（`@ApiModelProperty.name`，未填时为 Java 字段名），"字段描述"是 `@ApiModelProperty.value`（为空时退回字段名称），"message"是规则的提示：

| `tipType` | 格式 | 示例 |
|-----------|------|------|
| `TIP_1`（默认） | `字段` + 字段名称 + `,` + message | `字段graphId,只能是0-9的数字` |
| `TIP_11` | `字段` + 字段名称 + message | `字段graphId只能是0-9的数字` |
| `TIP_2` | 字段名称 + `,` + message | `graphId,只能是0-9的数字` |
| `TIP_22` | 字段名称 + message | `graphId只能是0-9的数字` |
| `TIP_3` | 字段描述 + `,` + message | `用户名,只能是0-9的数字` |
| `TIP_33` | 字段描述 + message | `用户名只能是0-9的数字` |
| `TIP_3_1` | 只有 message | `只能是0-9的数字` |

## 观察模式的日志与指标

每次校验失败（两种模式都会）交给 `ViolationRecorder` 记录。内置两个：

- **日志**：logger 名为 `ai.csap.validation.violations`。`observe` 模式记 warn，`enforce` 模式记 debug（请求已经被拦截并返回）。内容只有接口、字段、规则、`code`、`message`，**不包含参数值**：

  ```text
  csap validation violation (observe, request passed): endpoint=com.example.controller.UserController.createUser field=username rule=Size code=400 message=字段username,长度3-20
  ```

- **指标**：classpath 中有 Micrometer 且存在 `MeterRegistry` 时，计数器 `csap.validation.violations`，标签 `endpoint`、`rule`（`NotNull` / `NotEmpty` / `Pattern` / `Required` / 约束注解简单类名）、`mode`（`observe` / `enforce`）。接入 Actuator 后可在 `/actuator/metrics/csap.validation.violations` 查看。

需要上报到别处时，声明一个 `ai.csap.validation.runtime.ViolationRecorder` Bean 即可，与内置记录器并存。

## Spring 标准校验的统一返回

参数上写 `@Valid` / `@Validated` 时，Spring MVC 会先用 Hibernate Validator 校验，失败时抛出 Spring 的异常，**不会**再走到 CSAP 规则校验。starter 注册的 `ValidateExceptionHandler`（`@ControllerAdvice`，`@Order(1)`，不论模式都注册）把这些异常转成同样的返回格式，状态码同样取 `csap.validation.http-status`：

| 异常 | 返回的 `code` | 返回的 `message` |
|------|---------------|------------------|
| `MethodArgumentNotValidException`（`@Valid @RequestBody` 失败） | `"413"` | 第一个字段错误的 `defaultMessage` |
| `BindException`（表单 / 查询参数绑定失败） | `"411"` | `参数类型错误[字段:xxx,值为:xxx]` |
| `ConstraintViolationException`（`@Validated` 方法参数校验失败，`javax` / `jakarta` 均可） | `"400"` | 第一条违规的提示 |
| `MethodArgumentTypeMismatchException`（参数类型转换失败） | 参数有 `@DataTypeValidate` 时取其 `code`，否则 `"400"` | 参数有 `@DataTypeValidate` 时取其 `message`，否则 `数据格式错误` |
| `ValidateException`（CSAP 规则校验失败，或业务代码主动抛出） | 异常的 `code` | 异常的 `message` |

## 内置正则

`ai.csap.validation.type.ValidatePatternType` 定义了一组常用正则，DevTools 配置校验规则时可以直接选用（接口 `GET /csap/yaml/validatePatternTypes`）。下表按源码列出；「分类」列是枚举中的类型值：`1` 常规、`2` 数字、`3` 字符、`4` 特殊需求。

:::warning 使用前请核对
1.1.1 修正了 1.1.0 中写错的几条：`PATTERN_12`、`PATTERN_14`、`PATTERN_17`、`PATTERN_21`、`PATTERN_27`、`PATTERN_30`、`PATTERN_31`、`PATTERN_33`～`PATTERN_36`、`PATTERN_41`、`PATTERN_42`（括号与锚点、混入的中文、末尾空白和换行、`https`、手机号段等）。仍需注意：`PATTERN_2`～`PATTERN_4` 写死了位数（13 位、13 位以上、10～20 位）。校验时按整串匹配（`Matcher.matches()`），没写 `^`、`$` 的条目同样要求整个值匹配。`PATTERN_00`、`PATTERN_01` 不是正则，分别对应 `NotNull`、`NotEmpty` 两种规则类型。
:::

1.2.0 起，`type: Pattern` 规则的 `pattern` 可以直接写枚举名（如 `PATTERN_35`），运行时取对应的正则。

| 枚举名 | 分类 | 含义 | 正则 | 备注 |
|--------|------|------|------|------|
| `PATTERN_00` | 1 | 不能为空 | `NotNull` |  |
| `PATTERN_01` | 1 | 不能为空字符串 | `NotEmpty` |  |
| `PATTERN_1` | 2 | 数字 | `^[0-9]*$` |  |
| `PATTERN_2` | 2 | n位的数字 | `^\d{13}$` |  |
| `PATTERN_3` | 2 | 至少n位的数字 | `^\d{13,}$` |  |
| `PATTERN_4` | 2 | m-n位的数字 | `^\d{10,20}$` |  |
| `PATTERN_5` | 2 | 零和非零开头的数字 | `^(0\|[1-9][0-9]*)$` |  |
| `PATTERN_6` | 2 | 非零开头的最多带两位小数的数字 | `^([1-9][0-9]*)+(\.[0-9]{1,2})?$` |  |
| `PATTERN_7` | 2 | 带1-2位小数的正数或负数 | `^(\-)?\d+(\.\d{1,2})$` |  |
| `PATTERN_8` | 2 | 正数、负数、和小数 | `^(\-\|\+)?\d+(\.\d+)?$` |  |
| `PATTERN_9` | 2 | 有两位小数的正实数 | `^[0-9]+(\.[0-9]{2})?$` |  |
| `PATTERN_10` | 2 | 有1~3位小数的正实数 | `^[0-9]+(\.[0-9]{1,3})?$` |  |
| `PATTERN_11` | 2 | 非零的正整数 | `^[1-9]\d*$` |  |
| `PATTERN_12` | 2 | 非零的负整数 | `^-[1-9][0-9]*$` |  |
| `PATTERN_13` | 2 | 非负整数 | `^\d+$` |  |
| `PATTERN_14` | 2 | 非正整数 | `^(-[1-9]\d*\|0)$` |  |
| `PATTERN_15` | 2 | 非负浮点数 | `^\d+(\.\d+)?$` |  |
| `PATTERN_16` | 2 | 非正浮点数 | `^((-\d+(\.\d+)?)\|(0+(\.0+)?))$` |  |
| `PATTERN_17` | 2 | 正浮点数 | `^([1-9]\d*\.\d*\|0\.\d*[1-9]\d*)$` |  |
| `PATTERN_18` | 2 | 负浮点数 | `^-([1-9]\d*\.\d*\|0\.\d*[1-9]\d*)$` |  |
| `PATTERN_19` | 2 | 浮点数 | `^(-?\d+)(\.\d+)?$` |  |
| `PATTERN_20` | 3 | 汉字 | `^[\u4e00-\u9fa5]{0,}$` |  |
| `PATTERN_21` | 3 | 英文和数字 | `^[A-Za-z0-9]+$` |  |
| `PATTERN_22` | 3 | 长度为3-20的所有字符 | `^.{3,20}$` |  |
| `PATTERN_23` | 3 | 由26个英文字母组成的字符串 | `^[A-Za-z]+$` |  |
| `PATTERN_24` | 3 | 由26个大写英文字母组成的字符串 | `^[A-Z]+$` |  |
| `PATTERN_25` | 3 | 由26个小写英文字母组成的字符串 | `^[a-z]+$` |  |
| `PATTERN_26` | 3 | 由数字和26个英文字母组成的字符串 | `^[A-Za-z0-9]+$` |  |
| `PATTERN_27` | 3 | 由数字、26个英文字母或者下划线组成的字符串 | `^\w+$` |  |
| `PATTERN_28` | 3 | 中文、英文、数字包括下划线 | `^[\u4E00-\u9FA5A-Za-z0-9_]+$` |  |
| `PATTERN_29` | 3 | 由中文、英文、数字但不包括下划线等符号 | `^[\u4E00-\u9FA5A-Za-z0-9]+$` |  |
| `PATTERN_30` | 3 | 不能含有 ^%&',;=?$" 等字符 | `^[^%&',;=?$\x22]+$` |  |
| `PATTERN_31` | 3 | 禁止输入含有~的字符 | `^[^~\x22]+$` |  |
| `PATTERN_32` | 4 | Email地址 | `^\w+([-+.]\w+)*@\w+([-.]\w+)*\.\w+([-.]\w+)*$` |  |
| `PATTERN_33` | 4 | 域名 | `[a-zA-Z0-9][-a-zA-Z0-9]{0,62}(\.[a-zA-Z0-9][-a-zA-Z0-9]{0,62})+\.?` |  |
| `PATTERN_34` | 4 | InternetURL | `^https?://([\w-]+\.)+[\w-]+(/[\w\-./?%&=]*)?$` |  |
| `PATTERN_35` | 4 | 手机号码 | `^1[3-9]\d{9}$` |  |
| `PATTERN_36` | 4 | 电话号码 | `^(\(\d{3,4}\)\|\d{3,4}-)?\d{7,8}$` |  |
| `PATTERN_37` | 4 | 国内电话号码 | `\d{3}-\d{8}\|\d{4}-\d{7}` |  |
| `PATTERN_38` | 4 | 身份证号(15位、18位数字)，最后一位是校验位，可能为数字或字符X | `(^\d{15}$)\|(^\d{18}$)\|(^\d{17}(\d\|X\|x)$)` |  |
| `PATTERN_39` | 4 | 日期格式 | `^\d{4}-\d{1,2}-\d{1,2}` |  |
| `PATTERN_40` | 4 | 一年的12个月(01～09和1～12) | `^(0?[1-9]\|1[0-2])$` |  |
| `PATTERN_41` | 4 | 一个月的31天(01～09和1～31) | `^((0?[1-9])\|((1\|2)[0-9])\|30\|31)$` |  |
| `PATTERN_42` | 4 | xml文件 | `^([a-zA-Z]+-?)+[a-zA-Z0-9]+\.[xX][mM][lL]$` |  |
| `PATTERN_43` | 4 | 中国邮政编码 | `[1-9]\d{5}(?!\d)` |  |
| `PATTERN_44` | 3 | IPv4地址 | `((2(5[0-5]\|[0-4]\d))\|[0-1]?\d{1,2})(\.((2(5[0-5]\|[0-4]\d))\|[0-1]?\d{1,2})){3}` |  |

## 内置的自定义注解

校验模块还带了两个注解，位于 `ai.csap.validation.annotation`（基于 `jakarta.validation`，只能在 Spring Boot 3 项目中使用）：

- `@Phone`：手机号校验，属性 `code`（默认 `""`，此时返回 `"400"`）、`required`（默认 `true`）、`message`（默认 `"手机号码格式错误"`）、`payload`。`required = true` 时空值判定为失败。它没有 JSR-303 规范要求的 `groups` 属性，与 Spring 的 `@Valid` 一起使用时会被 Hibernate Validator 拒绝，只适合 CSAP 规则校验。
- `@AssertBoolean`：没有关联任何校验器，标注后不产生效果。

## 限制

- 只支持 Spring MVC（Servlet），不支持 WebFlux；只校验带 `@ApiOperation` 的方法。
- 规则在文档扫描完成后才生效；DevTools 重新生成规则的瞬间可能有请求未被校验。
- 只有必填字段的规则会执行；必填但没有规则的字段只检查非空。
- 实体字段按字段名（`@ApiModelProperty.name`，未填时为 Java 字段名）找 getter 取值；`name` 与 Java 字段名不一致时取不到值（按 `null` 校验）。上线前请先用 `observe` 模式确认。
- 约束注解的 `groups` 不参与判断；`ConstraintValidatorContext` 是简化实现，依赖完整上下文的自定义校验器可能被跳过（会记 warn 日志）。
- 校验组件自身出错（非校验失败）时请求放行，并对每个接口记一次 warn 日志。

## 相关页面

- [存储策略](/guide/storage-strategies)：YAML / SQLite 规则文件格式
- [DevTools](/guide/devtools)：可视化配置校验规则
- [注解使用指南](/guide/annotations)：`@ApiModelProperty`、`@Group`、`@ApiProperty`
