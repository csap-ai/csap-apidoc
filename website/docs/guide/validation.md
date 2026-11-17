# 参数校验

本页介绍 `csap-validation-boot-starter` 提供的参数校验：如何开启、规则怎么写（JSR-303 注解和 YAML 两种）、内置正则、校验失败时的返回格式，以及 1.1.x 中需要注意的几处行为。

## 引入与开启

```xml
<dependency>
    <groupId>ai.csap.apidoc.boot</groupId>
    <artifactId>csap-validation-boot-starter</artifactId>
    <version>1.1.1</version>
</dependency>
```

```yaml
csap:
  validation:
    enabled: true
    tip-type: TIP_1
```

:::warning 必须显式写 `enabled: true`
`ValidationProperties.enabled` 字段的默认值是 `true`（1.1.0 的 IDE 配置提示也显示默认 `true`，1.1.1 起提示改为 `false`），但注册校验拦截器 `ValidateIntercept` 的条件是 `@ConditionalOnProperty(prefix = "csap.validation", name = "enabled", havingValue = "true")`，没有 `matchIfMissing`。不写这一项，拦截器不会注册。
:::

:::tip 文档生成不依赖这个 starter
1.1.1 起，只引入 `csap-apidoc-boot-starter` 也能生成文档：没有 `IValidateFactory` Bean 时，文档 starter 使用内置的默认实现。1.1.0 中缺少它会空指针，所以当时要求两个 starter 同时引入。
:::

配置项：

| 配置 | 默认值 | 说明 |
|------|--------|------|
| `csap.validation.enabled` | 未配置时视为关闭 | 为 `true` 时注册 `ValidateIntercept` |
| `csap.validation.tip-type` | `TIP_1` | 校验失败提示的格式，见 [错误返回格式](#错误返回格式) |
| `csap.validation.code` | `code` | 目前未被读取 |
| `csap.validation.message` | `message` | 目前未被读取 |

引入 starter 后，不论 `enabled` 是否开启，下列组件都会注册：校验规则工厂 `IValidateFactory`、统一异常处理器 `ValidateExceptionHandler`（`@ControllerAdvice`）、NotNull / NotEmpty / Pattern / 注解四种校验策略。`enabled` 只控制 `ValidateIntercept`。

`ValidateExceptionHandler` 是全局生效的（`@Order(1)`）：引入 starter 后，应用中下文列出的几类 Spring 校验异常都会被它以 HTTP 200 + `ApidocResult` 的形式返回，而不是 Spring Boot 默认的 400 错误页。

## 两条校验路径

项目里实际会遇到两种校验，它们互不依赖：

| | Spring 标准校验 | CSAP 规则校验 |
|---|---|---|
| 规则来源 | 参数上的 `@Valid` / `@Validated` + JSR-303 注解 | `@ApiModelProperty` 字段上的 JSR-303 注解，以及 YAML / SQLite 中的 `validate` 规则 |
| 谁执行 | Spring MVC + Hibernate Validator（需要 `spring-boot-starter-validation`） | `ValidateIntercept` |
| 失败时 | 抛出 Spring 的校验异常，由 `ValidateExceptionHandler` 转成统一返回 | 抛出 `ValidateException`，由 `ValidateExceptionHandler` 转成统一返回 |
| 规则是否显示在文档里 | 字段上的 JSR-303 注解会显示 | 会显示 |

### Spring 标准校验

这是 Spring Boot 本身的能力，CSAP 只负责把异常转成统一格式。例如：

```java
@ApiOperation(value = "创建订单")
@PostMapping("/orders")
public ApidocResult<Order> createOrder(@Valid @RequestBody Order order) {
    return ApidocResult.success(order);
}
```

`ValidateExceptionHandler` 对各类异常的处理：

| 异常 | 返回的 `code` | 返回的 `message` |
|------|---------------|------------------|
| `MethodArgumentNotValidException`（`@Valid @RequestBody` 失败） | `"413"` | 第一个字段错误的 `defaultMessage` |
| `BindException`（表单 / 查询参数绑定失败） | `"411"` | `参数类型错误[字段:xxx,值为:xxx]` |
| `ConstraintViolationException`（`@Validated` 方法参数校验失败） | `"400"` | 从异常信息中截取的第一条提示 |
| `MethodArgumentTypeMismatchException`（参数类型转换失败） | 参数有 `@DataTypeValidate` 时取其 `code`，否则 `"400"` | 参数有 `@DataTypeValidate` 时取其 `message`，否则 `数据格式错误` |
| `ValidateException`（CSAP 规则校验失败，或业务代码主动抛出） | 异常的 `code` | 异常的 `message` |

### CSAP 规则校验

CSAP 在**生成文档时**把每个接口的校验规则登记到 `IValidateFactory`，请求到达时由 `ValidateIntercept.resolve(MethodParameter, Object)` 按参数取出规则执行。好处是规则可以写在 YAML / SQLite 里、由 [DevTools](/guide/devtools) 可视化维护，并且与文档展示的规则完全一致。

:::warning 1.1.x 没有自动调用 ValidateIntercept
`ValidateIntercept` 实现了 `ai.csap.apidoc.core.ApiMethodHandle` 接口，但 1.1.x 的各个模块中没有任何组件在请求处理时调用它（早期版本由一个参数解析器调用，该模块已移除）。也就是说，只开启 `csap.validation.enabled` 时，CSAP 规则校验**不会执行**。

在官方补上调用方之前，可以自行在参数解析后调用它。下面的示例对 `@RequestBody` 参数生效：

```java
import java.lang.reflect.Type;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.RequestBodyAdviceAdapter;

import ai.csap.validation.ValidateIntercept;

@ControllerAdvice
public class CsapRequestBodyValidationAdvice extends RequestBodyAdviceAdapter {

    private final ObjectProvider<ValidateIntercept> validateIntercept;

    public CsapRequestBodyValidationAdvice(ObjectProvider<ValidateIntercept> validateIntercept) {
        this.validateIntercept = validateIntercept;
    }

    @Override
    public boolean supports(MethodParameter methodParameter, Type targetType,
                            Class<? extends HttpMessageConverter<?>> converterType) {
        return true;
    }

    @Override
    public Object afterBodyRead(Object body, HttpInputMessage inputMessage, MethodParameter parameter,
                                Type targetType, Class<? extends HttpMessageConverter<?>> converterType) {
        validateIntercept.ifAvailable(intercept -> intercept.resolve(parameter, body));
        return body;
    }
}
```

`ValidateIntercept` 只对实体类带 `@ApiModel` 的对象参数、基础类型参数和它们的 `List` 做校验。
:::

## 规则写法

### 方式一：JSR-303 注解

在实体字段上同时写 `@ApiModelProperty` 和 JSR-303 注解。CSAP 用 Hibernate Validator 内置的校验器执行这些注解：

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

- 注解里的 `message` 会原样作为提示内容，不做 `{...}` 模板插值，请写成明文。
- JSR-303 注解没有 `code` 属性，失败时返回的 `code` 固定为 `"400"`。
- 注解策略下，规则只来自 JSR-303 注解；YAML / SQLite 策略下，字段上的 JSR-303 注解会与规则文件中的 `validate` 合并。

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
| `Pattern` | 值不为 `null`，且 `toString()` 后不能**整串**匹配 `pattern`。值为 `null` 时不判定失败，需要非空请再加一条 `NotNull` |

### 只有必填字段的规则会执行

:::warning required 决定规则是否生效
生成文档时，框架只在字段**必填**时才把它的校验规则登记到 `IValidateFactory`：

- 实体字段：注解策略下取匹配 `@Group` 的 `@Request.required`（没有匹配的 `@Group` 而靠 `forceReq` 出现时取 `@ApiModelProperty.required`）；YAML / SQLite 策略下取规则中的 `required`；
- 基础类型的方法参数：注解策略下取 `@ApiProperty.required`；YAML / SQLite 策略下取规则中的 `required`。

非必填字段上的规则**仍会显示在文档里**，但运行时不会执行。比如一个可选的邮箱字段写了格式校验，只要它不是 `required`，填错格式也不会被拦截。
:::

### 执行顺序

同一字段的多条规则（包括合并进来的 JSR-303 注解）按 `level` 从小到大排列，逐条执行，**第一条失败即停止**并返回该条的 `code` 和提示。JSR-303 注解生成的规则 `level` 为 `1`。

## 内置正则

`ai.csap.validation.type.ValidatePatternType` 定义了一组常用正则，DevTools 配置校验规则时可以直接选用（接口 `GET /csap/yaml/validatePatternTypes`）。下表按 1.1.1 源码列出；「分类」列是枚举中的类型值：`1` 常规、`2` 数字、`3` 字符、`4` 特殊需求。

:::warning 使用前请核对
1.1.1 修正了 1.1.0 中写错的几条：`PATTERN_12`、`PATTERN_14`、`PATTERN_17`、`PATTERN_21`、`PATTERN_27`、`PATTERN_30`、`PATTERN_31`、`PATTERN_33`～`PATTERN_36`、`PATTERN_41`、`PATTERN_42`（括号与锚点、混入的中文、末尾空白和换行、`https`、手机号段等）。仍需注意：`PATTERN_2`～`PATTERN_4` 写死了位数（13 位、13 位以上、10～20 位）。校验时按整串匹配（`Matcher.matches()`），没写 `^`、`$` 的条目同样要求整个值匹配。`PATTERN_00`、`PATTERN_01` 不是正则，分别对应 `NotNull`、`NotEmpty` 两种规则类型。
:::

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

## 错误返回格式

CSAP 规则校验失败时抛出 `ValidateException`，`ValidateExceptionHandler` 返回 **HTTP 200**，响应体是 `ApidocResult`：

```json
{
  "code": "1001",
  "message": "字段username,不能为空",
  "data": null,
  "language": "ZH",
  "time": 1767225600000
}
```

`data` 为 `null` 时是否输出取决于你的 Jackson 配置（例如 `spring.jackson.default-property-inclusion: non_null` 会省略它）。

`message` 的格式由 `csap.validation.tip-type` 决定，单条规则可用 `tipType` 覆盖。其中"字段名称"是 `@ApiModelProperty.name`（未填时为 Java 字段名），"字段描述"是 `@ApiModelProperty.value`（为空时退回字段名称），"message"是规则的提示：

| `tipType` | 格式 | 示例 |
|-----------|------|------|
| `TIP_1`（默认） | `字段` + 字段名称 + `,` + message | `字段graphId,只能是0-9的数字` |
| `TIP_11` | `字段` + 字段名称 + message | `字段graphId只能是0-9的数字` |
| `TIP_2` | 字段名称 + `,` + message | `graphId,只能是0-9的数字` |
| `TIP_22` | 字段名称 + message | `graphId只能是0-9的数字` |
| `TIP_3` | 字段描述 + `,` + message | `用户名,只能是0-9的数字` |
| `TIP_33` | 字段描述 + message | `用户名只能是0-9的数字` |
| `TIP_3_1` | 只有 message | `只能是0-9的数字` |

`code` 取自失败规则的 `code`；JSR-303 注解生成的规则为 `"400"`。

## 内置的自定义注解

校验模块还带了两个注解，位于 `ai.csap.validation.annotation`：

- `@Phone`：手机号校验，属性 `code`（默认 `""`）、`required`（默认 `true`）、`message`（默认 `"手机号码格式错误"`）、`payload`。`required = true` 时空值判定为失败。注意它没有 JSR-303 规范要求的 `groups` 属性，与 Spring 的 `@Valid` 一起使用时会被 Hibernate Validator 拒绝。
- `@AssertBoolean`：没有关联任何校验器，标注后不产生效果。

## 相关页面

- [存储策略](/guide/storage-strategies)：YAML / SQLite 规则文件格式
- [DevTools](/guide/devtools)：可视化配置校验规则
- [注解使用指南](/guide/annotations)：`@ApiModelProperty`、`@Group`、`@ApiProperty`
