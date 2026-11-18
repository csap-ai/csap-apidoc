# CSAP Framework Validation Core - 技术文档

> 本文是验证模块内部结构的说明,部分章节写于早期版本,代码片段只用于说明思路;使用与配置以官网 https://apidoc.csap.ai 为准,类与方法签名以源码为准。


## 📚 目录
- [项目概述](#项目概述)
- [核心架构](#核心架构)
- [ValidateData 接口体系](#validatedata-接口体系)
- [ValidateIntercept 验证拦截器](#validateintercept-验证拦截器)
- [验证策略模式](#验证策略模式)
- [使用示例](#使用示例)

---

## 项目概述

`csap-validation-core` 是 CSAP 框架的核心验证模块，提供了一套完整的、可扩展的参数验证解决方案。它不仅支持标准的 JSR-303/JSR-380 Bean Validation，还扩展了级联验证、自定义验证策略等高级特性。

### 主要特性

- ✅ **统一验证入口**：通过 `ValidateIntercept` 拦截器统一处理所有验证逻辑
- ✅ **多层级验证**：支持基础类型、复杂对象、集合类型的级联验证
- ✅ **策略模式**：可插拔的验证策略，易于扩展
- ✅ **响应过滤**：支持根据验证规则过滤返回参数
- ✅ **验证缓存**：缓存解析后的验证规则

---

## 核心架构

### 架构图

```
┌─────────────────────────────────────────────────────────┐
│                   ValidateIntercept                      │
│                   (验证拦截器入口)                        │
└───────────────────┬─────────────────────────────────────┘
                    │
                    ▼
┌─────────────────────────────────────────────────────────┐
│              IValidateFactory 接口层                     │
│  ┌──────────────────────────────────────────────────┐  │
│  │ ValidateData (基础验证接口)                       │  │
│  │   ├─ isPropertieValidate() 属性验证判断           │  │
│  │   └─ getClObj() 参数过滤                         │  │
│  └──────────────────────────────────────────────────┘  │
│  ┌──────────────────────────────────────────────────┐  │
│  │ ValidateFilter (验证过滤接口)                     │  │
│  │   ├─ getAllFieldConstraintValidator() 获取验证器  │  │
│  │   └─ validator() 创建验证器实例                   │  │
│  └──────────────────────────────────────────────────┘  │
│  ┌──────────────────────────────────────────────────┐  │
│  │ IValidateBaseFactory (基础工厂接口)              │  │
│  │   ├─ method() 获取方法验证信息                    │  │
│  │   ├─ request() 获取请求验证规则                   │  │
│  │   ├─ response() 获取响应过滤规则                  │  │
│  │   └─ validateMap (验证规则缓存)                   │  │
│  └──────────────────────────────────────────────────┘  │
│  ┌──────────────────────────────────────────────────┐  │
│  │ IValidateFactory (标准验证接口)                  │  │
│  │   ├─ validation() 执行验证                        │  │
│  │   └─ validatorsChildren() 级联验证               │  │
│  └──────────────────────────────────────────────────┘  │
└───────────────────┬─────────────────────────────────────┘
                    │
                    ▼
┌─────────────────────────────────────────────────────────┐
│         DefaultValidateFactoryImpl (默认实现)           │
└───────────────────┬─────────────────────────────────────┘
                    │
                    ▼
┌─────────────────────────────────────────────────────────┐
│            ValidationStrategyContext                     │
│                  (策略上下文)                            │
│  ┌────────────────────────────────────────────────┐    │
│  │ AnnotationValidationStrategy (注解验证)        │    │
│  │ NotEmptyValidationStrategy (非空验证)          │    │
│  │ NotNullValidationStrategy (非null验证)         │    │
│  │ PatternValidationStrategy (正则验证)           │    │
│  └────────────────────────────────────────────────┘    │
└─────────────────────────────────────────────────────────┘
```

---

## ValidateData 接口体系

### 1. ValidateData 接口（基础验证接口）

`ValidateData` 是整个验证体系的**基础接口**，定义了验证的入口方法和参数过滤逻辑。

#### 📍 位置
```
ai.csap.validation.factory.ValidateData
```

#### 🎯 核心职责

1. **属性验证判断**：判断某个属性是否需要验证
2. **参数过滤**：过滤不需要验证的系统类型（如 ServletRequest、ServletResponse 等）

#### 📝 核心方法

```java
public interface ValidateData {
    
    /**
     * 单参数使用 - 判断是否验证属性
     * 
     * 应用场景：当方法参数是基础类型（String、Integer等）时，
     * 通过 @ApiProperty 或 @ApiPropertys 注解标记哪些参数需要验证
     * 
     * @param method    当前方法参数对象
     * @param fieldName 属性名称
     * @return true-需要验证，false-不需要验证
     */
    default boolean isPropertieValidate(MethodParameter method, final String fieldName) {
        if (method.hasMethodAnnotation(ApiProperty.class)) {
            return Stream.of(method.getMethodAnnotation(ApiProperty.class))
                .filter(Objects::nonNull)
                .anyMatch(i -> i.name().equals(fieldName) && i.required());
        } else if (method.hasMethodAnnotation(ApiPropertys.class)) {
            return Stream.of(Objects.requireNonNull(method.getMethodAnnotation(ApiPropertys.class)).value())
                .anyMatch(i -> i.name().equals(fieldName) && i.required());
        }
        return false;
    }

    /**
     * 过滤掉不需要验证的参数
     * 
     * 应用场景：排除框架内置类型，如：
     * - ServletRequest、ServletResponse（Servlet相关）
     * - MultipartFile（文件上传）
     * - HttpSession、HttpServletRequest 等
     * 
     * @param name 类型名称（全限定类名）
     * @return true-需要验证，false-不需要验证
     */
    default boolean getClObj(String name) {
        boolean validate = true;
        if (OBJ.contains(name)) { // OBJ 是预定义的排除类型集合
            validate = false;
        }
        return validate;
    }
}
```

#### 💡 作用说明

1. **验证入口控制**：作为所有验证子接口的基础，控制哪些参数需要进入验证流程
2. **参数过滤**：自动过滤框架类型，避免对系统内置参数进行不必要的验证
3. **注解驱动**：通过 `@ApiProperty` 注解灵活控制验证行为

---

### 2. ValidateFilter 接口（验证过滤器接口）

`ValidateFilter` 继承自 `ValidateData`，负责**解析验证注解**并**构建验证器实例**。

#### 📍 位置
```
ai.csap.validation.factory.ValidateFilter
```

#### 🎯 核心职责

1. **验证器构建**：根据字段上的验证注解，创建对应的 `ConstraintValidator` 实例
2. **注解解析**：利用 Hibernate Validator 的 `ConstraintHelper` 解析 JSR-303 注解
3. **类型匹配**：确保验证器支持当前字段类型

#### 📝 核心方法

```java
public interface ValidateFilter extends ValidateData {
    
    /**
     * 获取当前字段所有验证逻辑
     * 
     * 工作流程：
     * 1. 遍历字段上的所有注解
     * 2. 过滤出带 @Constraint 元注解的验证注解
     * 3. 通过 ConstraintHelper 获取验证器实现类
     * 4. 匹配字段类型，创建验证器实例
     * 
     * @param fieldClass  字段class类型
     * @param annotations 字段上的所有注解
     * @return 验证器列表
     */
    default List<Validate.ConstraintValidatorField> getAllFieldConstraintValidator(
            Class<?> fieldClass, Annotation[] annotations) {
        return Stream.of(annotations)
            .filter(i -> i.annotationType().isAnnotationPresent(Constraint.class))
            .map(i -> getAllValidatorDescriptors(i.annotationType())
                .stream()
                .map(ConstraintValidatorDescriptor::getValidatorClass)
                .filter(i2 -> containsClass(fieldClass, (Class<?>) getClass(i2)))
                .map(i3 -> validator(i3, i))
                .collect(Collectors.toList()))
            .flatMap(Collection::stream)
            .filter(i -> containsClass(fieldClass, (Class<?>) getClass(i.getValidator().getClass())))
            .collect(Collectors.toList());
    }

    /**
     * 创建验证器实例
     * 
     * @param cl 验证器Class
     * @param i  注解实例
     * @return 验证字段信息（包含验证器、注解、错误码、错误消息等）
     */
    default Validate.ConstraintValidatorField validator(
            Class<? extends ConstraintValidator<? extends Annotation, ?>> cl, Annotation i) {
        ConstraintValidator validator = cl.getDeclaredConstructor().newInstance();
        validator.initialize(i); // 初始化验证器，传入注解配置
        return new Validate.ConstraintValidatorField()
            .setValidator(validator)
            .setAnnotation(i)
            .setCode(code(i))        // 提取错误码
            .setType(ValidationStrategyType.Annotation)
            .setMessage(message(i)); // 提取错误消息
    }
}
```

#### 💡 作用说明

1. **解析验证注解**：自动识别字段上的 `@NotNull`、`@Size`、`@Pattern` 等标准验证注解
2. **构建验证器**：为每个验证注解创建对应的验证器实例
3. **类型安全**：确保验证器支持当前字段类型，避免类型不匹配错误

---

### 3. IValidateBaseFactory 接口（验证基础工厂）

`IValidateBaseFactory` 继承自 `ValidateFilter`，提供**验证规则缓存**和**响应参数过滤**功能。

#### 📍 位置
```
ai.csap.validation.factory.IValidateBaseFactory
```

#### 🎯 核心职责

1. **验证规则缓存**：缓存每个方法的验证规则，避免重复解析
2. **响应过滤管理**：管理方法返回值的字段过滤规则（includes/excludes）
3. **验证数据获取**：提供便捷的 API 获取验证信息

#### 📝 核心数据结构

```java
public interface IValidateBaseFactory extends ValidateFilter {
    
    /**
     * 验证缓存对象
     * 
     * 缓存结构：
     * key: "com.example.UserController.createUser"（类名.方法名）
     * value: Validate 对象（包含所有验证规则和过滤规则）
     */
    Map<String, Validate> validateMap = new ConcurrentHashMap<>(16);
}
```

#### 📝 核心方法

```java
/**
 * 获取方法的验证信息
 * 
 * @param controller Controller 类
 * @param method     方法对象
 * @return Validate 对象（包含验证规则）
 */
default Validate method(Class<?> controller, Method method) {
    return method(controller.getName() + DOT + method.getName());
}

/**
 * 获取请求参数的验证规则
 * 
 * @param controller Controller 类
 * @param method     方法对象
 * @param field      字段名称（如 "User" 或 "User.name"）
 * @return 字段验证信息
 */
default Validate.ValidateField request(Class<?> controller, Method method, String field) {
    return method(controller, method).field(field);
}

/**
 * 获取响应参数的过滤规则
 * 
 * 应用场景：根据不同的业务场景，过滤返回给前端的字段
 * 例如：用户列表不返回密码字段，详情页返回完整信息
 * 
 * @param controller Controller 类
 * @param method     方法对象
 * @return 过滤规则列表
 */
default List<FilterClassParam> response(Class<?> controller, Method method) {
    return method(controller, method).getFilterClassParams();
}

/**
 * 添加包含字段（白名单模式）
 * 
 * @param classNameAndMethodName 方法标识
 * @param field                  字段名
 * @param modelClass             模型类
 */
default FilterClassParam addIncludes(String classNameAndMethodName, String field, Class<?> modelClass) {
    return addResponse(classNameAndMethodName, modelClass).addIncludes(field);
}

/**
 * 添加排除字段（黑名单模式）
 * 
 * @param classNameAndMethodName 方法标识
 * @param field                  字段名
 * @param modelClass             模型类
 */
default FilterClassParam addExcludes(String classNameAndMethodName, String field, Class<?> modelClass) {
    return addResponse(classNameAndMethodName, modelClass).addExcludes(field);
}
```

#### 💡 作用说明

1. **缓存**:通过缓存避免重复解析验证规则
2. **响应过滤**：支持动态控制返回字段，保护敏感信息
3. **真实数据提供**：为 API 文档生成提供真实的验证规则和数据结构

---

### 4. IValidateFactory 接口（标准验证接口）

`IValidateFactory` 继承自 `IValidateBaseFactory`，定义了**实际执行验证**的标准接口。

#### 📍 位置
```
ai.csap.validation.factory.IValidateFactory
```

#### 🎯 核心职责

1. **执行参数验证**：验证请求参数是否符合规则
2. **级联验证**：支持对象嵌套字段的递归验证

#### 📝 核心方法

```java
public interface IValidateFactory extends IValidateBaseFactory {

    /** 验证参数 */
    void validation(Object args, Method method, Class<?> controllerClass,
                    Class<?> paramClass, String parameterName);

    /** 级联验证子字段(嵌套对象、集合) */
    Validate.ValidateField validatorsChildren(
        String parameterName,
        Map<String, Validate.ValidateField> validateField,
        Object value,
        Object childrenValue,
        BiFunctionValidation<String, List<String>, Object, Object> function);
}
```

---

### 5. DefaultValidateFactoryImpl 类（默认实现）

`DefaultValidateFactoryImpl` 是 `IValidateFactory` 的**默认实现类**，提供完整的验证逻辑。

#### 📍 位置
```
ai.csap.validation.factory.DefaultValidateFactoryImpl
```

#### 🎯 核心职责

1. **统一验证入口**：整合基础验证、对象验证、集合验证
2. **级联验证实现**：递归验证嵌套对象和集合
3. **策略模式执行**：调用验证策略执行具体验证

#### 📝 验证流程

内部流程:先取缓存的验证规则(`request(...)`),没有规则直接返回;基础类型直接验证该参数对应的规则;复杂对象则调用 `validatorsChildren` 递归验证所有字段。

#### 💡 验证逻辑说明

```
┌──────────────────────────────────────────────────────────┐
│ 1. 获取验证规则（从缓存 validateMap 中获取）             │
└────────────────────┬─────────────────────────────────────┘
                     │
                     ▼
┌──────────────────────────────────────────────────────────┐
│ 2. 验证对象本身（如果有类级别的验证注解）                │
└────────────────────┬─────────────────────────────────────┘
                     │
                     ▼
            ┌────────┴────────┐
            │                 │
            ▼                 ▼
┌─────────────────┐  ┌──────────────────┐
│  基础数据类型    │  │   复杂对象类型    │
│  (String等)     │  │  (User、Order等)  │
└────────┬────────┘  └────────┬─────────┘
         │                    │
         ▼                    ▼
┌─────────────────┐  ┌──────────────────┐
│  直接验证参数    │  │   级联验证字段    │
│  (单个值验证)    │  │  (递归验证子字段) │
└─────────────────┘  └────────┬─────────┘
                              │
                              ▼
                     ┌──────────────────┐
                     │  集合类型处理     │
                     │ (遍历验证每一项) │
                     └──────────────────┘
```

---

### 6. 辅助数据类

#### Validate 类（验证数据容器）

```java
@Data
public class Validate {
    /**
     * 返回值过滤参数
     */
    private List<FilterClassParam> filterClassParams = new ArrayList<>();
    
    /**
     * 字段验证规则
     * key: 字段名称（如 "User" 或 "User.name"）
     * value: 验证规则
     */
    private Map<String, ValidateField> validateField = new ConcurrentHashMap<>();
    
    /**
     * 字段验证信息
     */
    @Data
    public static class ValidateField {
        private List<ConstraintValidatorField> constraintValidators; // 验证器列表
        private Map<String, ValidateField> children;                 // 子字段验证
        private String fieldName;                                    // 字段名
        private String remark;                                       // 字段描述
        private ModelType modelType;                                 // 模型类型
        
        /**
         * 执行验证
         */
        public ValidateField validators(Object value, ValidationTipType tipType) {
            if (CollectionUtil.isNotEmpty(constraintValidators)) {
                constraintValidators.stream()
                    .filter(i -> ValidationStrategyContext.validate(i, value))
                    .findFirst()
                    .ifPresent(i -> {
                        throw new ValidateException(i.getCode(), tipType.validateMessage(this, i.getMessage()));
                    });
            }
            return this;
        }
    }
}
```

#### FilterClassParam 类（响应过滤参数）

```java
@Data
public class FilterClassParam {
    private Class<?> type;              // 过滤的类型
    private Set<String> includes;       // 包含的字段（白名单）
    private Set<String> excludes;       // 排除的字段（黑名单）
    private boolean response = false;   // 是否已处理
}
```

---

## ValidateIntercept 验证拦截器

位置:`ai.csap.validation.ValidateIntercept`,实现 `ApiMethodHandle`,在 Spring MVC 解析完参数后、Controller 方法执行前,对每个参数执行验证。

处理方式(按参数的方法、位置缓存成 `ParamPlan`):

1. 方法没有可用的参数名或类型信息、或属于排除类型(`ValidateData#getClObj` 过滤的 Servlet 类型等)时跳过
2. 基础类型(`String`、`Integer` 等)以及元素为基础类型的集合:按单个参数验证,是否验证由方法上的 `@ApiProperty` / `@ApiPropertys` 的 `required` 决定
3. 元素为复杂对象的集合:逐个元素验证
4. 标注了 `@ApiModel` 的对象:级联验证其字段
5. 其他类型跳过

`DEBUG` 日志只记录被验证的类、方法与参数名,不打印参数值。

运行时校验的模式开关、拦截行为见 `csap.validation.mode` 与 `ai.csap.validation.runtime` 包(`ValidatingArgumentResolver`、`ValidationExecutor`、`ViolationRecorder` 等)。

---

## 验证策略模式

### 策略接口

```java
public interface ValidationStrategy<T> {
    ValidationStrategyType validationType();

    /** 返回 true 表示验证失败,false 表示通过 */
    boolean validation(T value, Validate.ConstraintValidatorField validatorField);
}
```

### 内置策略

`ValidationStrategyType` 取值为 `NotNull`、`NotEmpty`、`Pattern`、`Annotation`、`CUSTOMER`,对应的内置实现为 `NotNullValidationStrategy`、`NotEmptyValidationStrategy`、`PatternValidationStrategy`、`AnnotationValidationStrategy`(基于 JSR-303 `ConstraintValidator`)。

### 策略上下文

`ValidationStrategyContext` 在静态初始化时注册内置策略,容器启动完成后再把 Spring 容器中的 `ValidationStrategy` Bean 注册进来(同类型的 Bean 会覆盖内置实现)。`ValidationStrategyContext.validate(validatorField, value)` 按规则类型找到策略并执行;找不到策略时记录告警并跳过该规则。

---

## 使用示例

### 1. 基础使用

```java
@RestController
@RequestMapping("/user")
public class UserController {
    
    @ApiOperation("创建用户")
    @PostMapping("/create")
    public Result createUser(@RequestBody User user) {
        // ValidateIntercept 会在此方法执行前自动验证 user 对象
        return Result.success();
    }
}

@Data
@ApiModel("用户信息")
public class User {
    @NotBlank(message = "用户名不能为空")
    @Size(min = 2, max = 20, message = "用户名长度2-20")
    @ApiProperty(value = "用户名", required = true)
    private String name;
    
    @Min(value = 0, message = "年龄不能小于0")
    @Max(value = 150, message = "年龄不能大于150")
    @ApiProperty(value = "年龄", required = true)
    private Integer age;
    
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式错误")
    @ApiProperty(value = "手机号", required = true)
    private String phone;
}
```

### 2. 级联验证

```java
@Data
@ApiModel("用户信息")
public class User {
    @NotBlank
    private String name;
    
    @Valid  // 开启级联验证
    @NotNull(message = "地址不能为空")
    @ApiProperty(value = "地址", required = true)
    private Address address;
}

@Data
@ApiModel("地址信息")
public class Address {
    @NotBlank(message = "城市不能为空")
    private String city;
    
    @NotBlank(message = "街道不能为空")
    private String street;
}
```

### 3. 集合验证

```java
@PostMapping("/batch-create")
public Result batchCreateUser(@RequestBody List<@Valid User> users) {
    // ValidateIntercept 会逐个验证 List 中的每个 User 对象
    return Result.success();
}
```

### 4. 响应过滤

```java
// 在 API 文档生成或数据返回时，过滤敏感字段
IValidateFactory factory = ...;

// 排除密码字段
factory.addExcludes("com.example.UserController.getUser", "password", User.class);

// 只返回指定字段
factory.addIncludes("com.example.UserController.getUserList", "id", User.class);
factory.addIncludes("com.example.UserController.getUserList", "name", User.class);
```

### 5. 自定义验证提示

```yaml
csap:
  validation:
    tip-type: TIP_1   # 字段%s,%s
```

提示效果：
```
TIP_1:  "字段name,用户名不能为空"
TIP_2:  "name,用户名不能为空"
TIP_3:  "用户名,用户名不能为空"
TIP_3_1: "用户名不能为空"
```

---

## 异常处理机制

### ValidateExceptionHandler(全局异常处理器)

位置:`ai.csap.validation.advice.ValidateExceptionHandler`(`@ControllerAdvice`,`@Order(1)`)。

| 异常 | 返回 |
|---|---|
| `ValidateException`(含运行时校验抛出的 `ValidationViolationException`) | `code` / `message` / `data` 取自异常 |
| `BindException` | 错误码 `411`,提示参数类型错误及字段名、值 |
| `MethodArgumentTypeMismatchException` | 参数上有 `@DataTypeValidate` 时用其 `code` / `message`,否则用默认的类型校验错误提示 |
| `MethodArgumentNotValidException` | 错误码 `413`,消息为第一个字段错误的 `defaultMessage` |

响应体是 `ApidocResult`(`{code, message, data}`);HTTP 状态码由 `csap.validation.http-status` 决定,默认 `200`。

## 配置说明

### ValidationProperties(验证配置)

位置:`ai.csap.validation.properties.ValidationProperties`,配置前缀 `csap.validation`。

| 配置项 | 默认值 | 说明 |
|---|---|---|
| `mode` | 未设置(等同 `off`) | 运行时校验模式:`off` / `observe`(记录并放行)/ `enforce`(拦截),1.2.0 起 |
| `enabled` | 未设置 | 1.1.x 的开关:`true` 等同 `mode: enforce`,`false` 强制关闭 |
| `endpoints` | 空 | 按接口覆盖模式,键为 `Controller 全限定类名.方法名` |
| `http-status` | `200` | 校验失败的 HTTP 状态码 |
| `code` | `code` | 响应中错误码字段名 |
| `message` | `message` | 响应中错误消息字段名 |
| `tip-type` | `TIP_1` | 提示文案格式 |
| `required-code` | `400` | 必填校验的错误码 |
| `required-message` | `不能为空` | 必填校验的默认提示 |

```yaml
csap:
  validation:
    mode: enforce
    http-status: 200
    tip-type: TIP_1
    endpoints:
      "[ai.csap.example.controller.ProductController.updateStock]": observe
```

#### 提示类型

| 类型 | 格式 | 示例输出 |
|------|------|----------|
| `TIP_1` | `字段%s,%s` | 字段name,用户名不能为空 |
| `TIP_11` | `字段%s%s` | 字段name用户名不能为空 |
| `TIP_2` | `%s,%s` | name,用户名不能为空 |
| `TIP_22` | `%s%s` | name用户名不能为空 |
| `TIP_3` | `%s,%s` | 用户名,用户名不能为空(字段描述取 `@ApiModelProperty` 等) |
| `TIP_33` | `%s%s` | 用户名用户名不能为空 |
| `TIP_3_1` | `%s` | 用户名不能为空 |

---

## 自定义验证注解

### 创建自定义注解

框架支持扩展自定义验证注解，以下是内置的 `@Phone` 注解示例：

#### 1. 定义注解

```java
@Target({METHOD, FIELD, ANNOTATION_TYPE, CONSTRUCTOR, PARAMETER})
@Retention(RUNTIME)
@Documented
@Constraint(validatedBy = PhoneValidator.class)  // 指定验证器
public @interface Phone {
    
    /**
     * 错误码
     */
    String code() default "";
    
    /**
     * 是否必填
     */
    boolean required() default true;
    
    /**
     * 错误消息
     */
    String message() default "手机号码格式错误";
    
    /**
     * 分组
     */
    Class<?>[] groups() default {};
    
    /**
     * 负载
     */
    Class<? extends Payload>[] payload() default {};
}
```

#### 2. 实现验证器

```java
public class PhoneValidator implements ConstraintValidator<Phone, String> {
    
    private boolean required = false;
    
    /**
     * 中国大陆手机号正则
     */
    private final Pattern pattern = Pattern.compile("1(([38]\\d)|(5[^4&&\\d])|(4[579])|(7[0135678]))\\d{8}");
    
    /**
     * 初始化（获取注解配置）
     */
    @Override
    public void initialize(Phone constraintAnnotation) {
        required = constraintAnnotation.required();
    }
    
    /**
     * 验证逻辑
     */
    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        // 必填验证
        if (required) {
            if (StringUtils.isEmpty(value)) {
                return false;
            }
            return pattern.matcher(value).matches();
        }
        // 非必填，空值通过
        if (StringUtils.isEmpty(value)) {
            return true;
        }
        // 有值则验证格式
        return pattern.matcher(value).matches();
    }
}
```

#### 3. 使用自定义注解

```java
@Data
@ApiModel("用户信息")
public class User {
    
    @Phone(required = true, message = "手机号格式不正确", code = "PHONE_ERROR")
    @ApiProperty(value = "手机号", required = true)
    private String phone;
    
    @Phone(required = false, message = "备用手机号格式不正确")
    @ApiProperty(value = "备用手机号", required = false)
    private String backupPhone;  // 非必填，但有值时验证格式
}
```

### 自定义验证策略

除了使用标准的 `ConstraintValidator`，还可以实现自定义验证策略：

```java
@Component
public class CustomValidationStrategy implements ValidationStrategy<Object> {
    
    @Override
    public ValidationStrategyType validationType() {
        return ValidationStrategyType.CUSTOMER;  // 自定义类型
    }
    
    @Override
    public boolean validation(Object value, Validate.ConstraintValidatorField validatorField) {
        // 实现自定义验证逻辑
        // 返回 true 表示验证失败，false 表示通过
        return false;
    }
}
```

---

## 最佳实践

### 1. 验证注解的放置位置

✅ **推荐**
```java
@Data
@ApiModel("用户信息")
public class User {
    // 在实体类字段上添加验证注解
    @NotBlank(message = "用户名不能为空")
    @Size(min = 2, max = 20, message = "用户名长度2-20")
    private String name;
}

@PostMapping("/create")
public Result createUser(@RequestBody @Valid User user) {  // Controller 层启用验证
    return userService.save(user);
}
```

❌ **不推荐**
```java
@PostMapping("/create")
public Result createUser(@RequestBody User user) {
    // 手动验证，代码冗余
    if (user.getName() == null || user.getName().isEmpty()) {
        throw new ValidateException("400", "用户名不能为空");
    }
    return userService.save(user);
}
```

### 2. 级联验证的使用

```java
@Data
@ApiModel("订单信息")
public class Order {
    @NotBlank
    private String orderNo;
    
    @Valid  // ⚠️ 必须加 @Valid 开启级联验证
    @NotNull(message = "收货地址不能为空")
    private Address address;
    
    @Valid  // ⚠️ 集合级联验证
    @Size(min = 1, message = "至少选择一个商品")
    private List<@Valid OrderItem> items;  // List 元素也需要 @Valid
}
```

### 3. 分组验证

针对不同场景使用不同验证规则：

```java
// 定义验证分组
public interface CreateGroup {}
public interface UpdateGroup {}

@Data
@ApiModel("用户信息")
public class User {
    @NotNull(groups = UpdateGroup.class, message = "更新时ID不能为空")
    private Long id;
    
    @NotBlank(groups = {CreateGroup.class, UpdateGroup.class}, message = "用户名不能为空")
    private String name;
    
    @NotBlank(groups = CreateGroup.class, message = "创建时密码不能为空")
    private String password;
}

// Controller 中指定分组
@PostMapping("/create")
public Result create(@Validated(CreateGroup.class) @RequestBody User user) {
    return userService.save(user);
}

@PutMapping("/update")
public Result update(@Validated(UpdateGroup.class) @RequestBody User user) {
    return userService.update(user);
}
```

### 4. 性能优化建议

#### （1）验证规则缓存

框架已自动缓存验证规则，无需手动处理：

```java
// 验证规则在首次使用后缓存在 validateMap 中
Map<String, Validate> validateMap = new ConcurrentHashMap<>(16);
```

#### （2）避免过度验证

```java
// ❌ 不推荐：重复验证
@PostMapping("/create")
public Result create(@RequestBody @Valid User user) {
    if (user.getName() == null) {  // 已经有 @NotNull 验证，无需重复
        throw new ValidateException("400", "用户名不能为空");
    }
    return userService.save(user);
}

// ✅ 推荐：信任验证框架
@PostMapping("/create")
public Result create(@RequestBody @Valid User user) {
    // 验证通过后直接使用
    return userService.save(user);
}
```

#### （3）合理使用响应过滤

```java
// 列表接口过滤敏感字段
factory.addExcludes("com.example.UserController.list", "password", User.class);
factory.addExcludes("com.example.UserController.list", "salt", User.class);

// 详情接口返回完整信息（不添加过滤规则）
```

### 5. 错误消息国际化

```java
// messages.properties
user.name.notblank=用户名不能为空
user.age.min=年龄不能小于{value}

// messages_en.properties
user.name.notblank=Username cannot be blank
user.age.min=Age cannot be less than {value}

// 使用国际化消息
@Data
public class User {
    @NotBlank(message = "{user.name.notblank}")
    private String name;
    
    @Min(value = 0, message = "{user.age.min}")
    private Integer age;
}
```

---

## 常见问题（FAQ）

### Q1: 验证不生效怎么办？

**A:** 检查以下几点：

1. **Controller 参数是否添加 `@Valid` 或 `@Validated`**
   ```java
   // ❌ 错误：缺少 @Valid
   public Result create(@RequestBody User user) { }
   
   // ✅ 正确
   public Result create(@RequestBody @Valid User user) { }
   ```

2. **级联验证是否添加 `@Valid`**
   ```java
   @Data
   public class Order {
       @Valid  // ⚠️ 必须添加
       @NotNull
       private Address address;
   }
   ```

3. **验证配置是否启用**
   ```yaml
   csap:
     validation:
       enabled: true  # 确保为 true
   ```

### Q2: 如何自定义错误码？

**A:** 在验证注解中指定 `code` 属性（如果支持）：

```java
@NotBlank(message = "用户名不能为空")
@ApiProperty(value = "用户名", code = "USER_NAME_EMPTY")
private String name;
```

或在自定义注解中定义：

```java
@Phone(code = "PHONE_FORMAT_ERROR", message = "手机号格式错误")
private String phone;
```

### Q3: 集合验证如何处理？

**A:** 使用 `@Valid` 注解集合元素：

```java
// List 验证
public Result batchCreate(@RequestBody List<@Valid User> users) { }

// 实体内 List 验证
@Data
public class Order {
    @Valid
    @Size(min = 1, message = "至少选择一个商品")
    private List<@Valid OrderItem> items;
}
```

### Q4: 如何跳过某些字段的验证？

**A:** 使用分组验证或条件验证：

```java
// 方式1：不添加验证注解
private String optionalField;  // 无注解，不验证

// 方式2：使用分组
@NotBlank(groups = CreateGroup.class)
private String onlyCreateValidate;

// 方式3：自定义验证器中判断
@Override
public boolean isValid(String value, ConstraintValidatorContext context) {
    if (某些条件) {
        return true;  // 跳过验证
    }
    return 验证逻辑;
}
```

### Q5: 验证失败如何返回多个错误？

**A:** 默认只返回第一个错误(`Validate.ValidateField#validators` 命中第一条失败规则即抛出 `ValidateException`)。如需返回所有错误,可以自定义 `@ExceptionHandler` 汇总 `MethodArgumentNotValidException` 的 `FieldError`,按自己的响应结构返回。

### Q6: 如何处理动态验证规则？

**A:** 实现自定义验证器，在 `isValid` 方法中根据业务逻辑动态判断：

```java
public class DynamicValidator implements ConstraintValidator<DynamicValidate, Object> {
    
    @Autowired
    private SomeService someService;  // 注入业务服务
    
    @Override
    public boolean isValid(Object value, ConstraintValidatorContext context) {
        // 根据数据库配置或其他业务规则动态验证
        boolean isValid = someService.validate(value);
        if (!isValid) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate("动态错误消息")
                   .addConstraintViolation();
        }
        return isValid;
    }
}
```

---

## 附录

### 依赖项

```xml
<dependency>
    <groupId>org.hibernate.validator</groupId>
    <artifactId>hibernate-validator</artifactId>
</dependency>
<dependency>
   <groupId>jakarta.validation</groupId>
   <artifactId>jakarta.validation-api</artifactId>
</dependency>
```

### 相关类图

```
ValidateData (基础接口)
    ├─ isPropertieValidate()
    └─ getClObj()
        │
        ├─ ValidateFilter (验证过滤)
        │   ├─ getAllFieldConstraintValidator()
        │   └─ validator()
        │       │
        │       └─ IValidateBaseFactory (基础工厂)
        │           ├─ validateMap (缓存)
        │           ├─ method()
        │           ├─ request()
        │           ├─ response()
        │           ├─ addIncludes()
        │           └─ addExcludes()
        │               │
        │               └─ IValidateFactory (标准接口)
        │                   ├─ validation()
        │                   └─ validatorsChildren()
        │                       │
        │                       └─ DefaultValidateFactoryImpl (默认实现)
```

---

**说明**: 本文是对验证模块内部结构的说明,以源码为准;使用与配置以官网 https://apidoc.csap.ai 为准。
