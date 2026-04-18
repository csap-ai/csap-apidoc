# CSAP Framework Validation Core - 技术文档

## 📚 目录
- [项目概述](#项目概述)
- [核心架构](#核心架构)
- [ValidateData 接口体系](#validatedata-接口体系)
- [ValidateIntercept 验证拦截器](#validateintercept-验证拦截器)
- [验证策略模式](#验证策略模式)
- [使用示例](#使用示例)

---

## 项目概述

`csap-framework-validation-core` 是 CSAP 框架的核心验证模块，提供了一套完整的、可扩展的参数验证解决方案。它不仅支持标准的 JSR-303/JSR-380 Bean Validation，还扩展了级联验证、自定义验证策略等高级特性。

### 主要特性

- ✅ **统一验证入口**：通过 `ValidateIntercept` 拦截器统一处理所有验证逻辑
- ✅ **多层级验证**：支持基础类型、复杂对象、集合类型的级联验证
- ✅ **策略模式**：可插拔的验证策略，易于扩展
- ✅ **响应过滤**：支持根据验证规则过滤返回参数
- ✅ **验证缓存**：智能缓存验证规则，提升性能

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

1. **性能优化**：通过缓存避免重复解析验证规则，提升验证性能
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
    
    /**
     * 验证参数
     * 
     * @param args            参数值
     * @param method          方法对象
     * @param controllerClass Controller 类
     * @param paramClass      参数类型
     * @param parameterName   参数名称
     * @param validatorContext 验证上下文
     */
    void validation(Object args, Method method, Class<?> controllerClass, 
                   Class<?> paramClass, String parameterName, 
                   ConstraintValidatorContext validatorContext);

    /**
     * 级联验证子字段
     * 
     * 应用场景：验证嵌套对象，如：
     * User {
     *   String name;
     *   Address address {  // 级联验证这个对象
     *     String city;
     *     String street;
     *   }
     * }
     * 
     * @param parameterName   参数名称
     * @param validateField   验证字段信息
     * @param value           当前值
     * @param childrenValue   子对象值
     * @param function        取值函数
     * @param validatorContext 验证上下文
     * @return 验证结果
     */
    Validate.ValidateField validatorsChildren(
        String parameterName,
        Map<String, Validate.ValidateField> validateField,
        Object value,
        Object childrenValue,
        BiFunctionValidation<String, List<String>, Object, Object> function,
        ConstraintValidatorContext validatorContext
    );
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

```java
@Override
public void validation(Object args, Method method, Class<?> controllerClass, 
                      Class<?> paramClass, String parameterName, 
                      ConstraintValidatorContext validatorContext) {
    // 1. 获取缓存的验证规则
    Validate.ValidateField validateField = request(controllerClass, method, paramClass.getSimpleName());
    if (validateField == null) {
        return; // 无验证规则，直接返回
    }
    
    // 2. 验证当前对象本身
    validateField.validators(args, validationProperties.getTipType(), validatorContext);
    
    // 3. 根据类型选择验证策略
    if (validateField.getModelType().equals(ModelType.BASE_DATA)) {
        // 基础类型：直接验证
        if (validateField.getChildren().containsKey(parameterName)) {
            validateField.getChildren().get(parameterName)
                .validators(args, validationProperties.getTipType(), validatorContext);
        }
    } else {
        // 复杂对象：级联验证所有字段
        validatorsChildren(parameterName, validateField.getChildren(), 
                          args, null, ReflectionKit::getValidateValue, validatorContext);
    }
}
```

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
        public ValidateField validators(Object value, ValidationTipType tipType, 
                                       ConstraintValidatorContext validatorContext) {
            if (CollectionUtil.isNotEmpty(constraintValidators)) {
                constraintValidators.stream()
                    .filter(i -> ValidationStrategyContext.validate(i, value, validatorContext))
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

### 📍 位置
```
ai.csap.validation.ValidateIntercept
```

### 🎯 核心职责

`ValidateIntercept` 是整个验证框架的**统一入口**，实现了 `ApiMethodHandle` 接口，在请求到达 Controller 之前拦截并执行验证。

### 📝 核心代码解析

```java
@Slf4j
public final class ValidateIntercept implements ApiMethodHandle {
    private final IValidateFactory validateFactory;
    private final ParameterNameDiscoverer parameterNameDiscoverer = new DefaultParameterNameDiscoverer();
    private static final Map<Class<?>, TypeVariableModel> TYPE_MAP = new ConcurrentHashMap<>(128);

    /**
     * 验证入口方法
     * 
     * 调用时机：Spring MVC 解析完参数后，Controller 方法执行前
     * 
     * @param parameter 方法参数对象（包含参数索引、类型、注解等信息）
     * @param value     参数实际值
     */
    @Override
    public void resolve(MethodParameter parameter, Object value) {
        // 1. 获取泛型信息（用于处理泛型类）
        TypeVariableModel typeVariableModel = TYPE_MAP.computeIfAbsent(
            parameter.getContainingClass(), 
            i -> new TypeVariableModel(parameter.getContainingClass())
        );
        
        // 2. 获取方法参数类型和参数名
        Type[] cl = Objects.requireNonNull(parameter.getMethod()).getGenericParameterTypes();
        String[] parameterNames = parameterNameDiscoverer.getParameterNames(parameter.getMethod());
        
        if (ArrayUtil.isEmpty(parameterNames) || ArrayUtil.isEmpty(cl)) {
            return; // 无参数，直接返回
        }
        
        // 3. 解析当前参数的真实类型（处理泛型）
        ModelTypepProperties properties = ClazzUtils.getRawClassType(
            cl[parameter.getParameterIndex()], 
            typeVariableModel
        );
        String type = properties.getRawType();
        
        // 4. 过滤不需要验证的类型（如 ServletRequest、HttpSession 等）
        if (!validateFactory.getClObj(type)) {
            return;
        }
        
        // 5. 执行验证
        String parameterName = parameterNames[parameter.getParameterIndex()];
        Method method = parameter.getMethod();
        
        try {
            ConstraintValidatorContext validatorContext = null;
            
            if (ClazzUtils.DATA_TYPE.contains(type) || ClazzUtils.OTHER_DATA_TYPE.contains(type)) {
                // 基础数据类型验证（String、Integer、Long 等）
                validateFactory.validation(value, method, typeVariableModel.getAClass(), 
                    properties.getCl(), parameterName, validatorContext);
                    
            } else if (ClazzUtils.LIST.contains(type)) {
                // 集合类型验证
                if (ClazzUtils.DATA_TYPE.contains(properties.getCl().getName()) || 
                    ClazzUtils.OTHER_DATA_TYPE.contains(type)) {
                    // 集合元素是基础类型
                    validateFactory.validation(value, method, typeVariableModel.getAClass(), 
                        properties.getCl(), parameterName, validatorContext);
                } else {
                    // 集合元素是复杂对象，逐个验证
                    if (CollectionUtil.isNotEmpty((Collection<?>) value)) {
                        ((Collection<?>) value).forEach(item -> 
                            validateFactory.validation(item, method, typeVariableModel.getAClass(), 
                                properties.getCl(), parameterName, validatorContext)
                        );
                    }
                }
                
            } else if (properties.getRawCl().isAnnotationPresent(ApiModel.class)) {
                // 复杂对象验证（标注了 @ApiModel 的业务模型）
                validateFactory.validation(value, method, typeVariableModel.getAClass(), 
                    properties.getRawCl(), parameterName, validatorContext);
            }
        } finally {
            log(method, value); // 记录验证日志
        }
    }
}
```

### 🔍 验证流程详解

#### 流程图

```
┌───────────────────────────────────────────────────────────┐
│ 1. HTTP 请求到达 Controller                                │
└─────────────────────┬─────────────────────────────────────┘
                      │
                      ▼
┌───────────────────────────────────────────────────────────┐
│ 2. Spring MVC 解析请求参数                                 │
│    (@RequestBody、@RequestParam 等)                       │
└─────────────────────┬─────────────────────────────────────┘
                      │
                      ▼
┌───────────────────────────────────────────────────────────┐
│ 3. ValidateIntercept.resolve() 拦截                       │
│    - 获取参数类型和值                                      │
│    - 缓存泛型信息                                          │
└─────────────────────┬─────────────────────────────────────┘
                      │
                      ▼
┌───────────────────────────────────────────────────────────┐
│ 4. 过滤系统参数                                            │
│    - ServletRequest、ServletResponse                      │
│    - MultipartFile、HttpSession 等                        │
└─────────────────────┬─────────────────────────────────────┘
                      │
                      ▼
          ┌───────────┴───────────┐
          │                       │
          ▼                       ▼
┌──────────────────┐    ┌──────────────────┐
│  基础类型参数     │    │  复杂对象参数     │
│  String id       │    │  User user       │
└────────┬─────────┘    └────────┬─────────┘
         │                       │
         ▼                       ▼
┌──────────────────┐    ┌──────────────────┐
│  单值验证         │    │  级联验证         │
│  @NotBlank       │    │  递归验证字段     │
└────────┬─────────┘    └────────┬─────────┘
         │                       │
         └───────────┬───────────┘
                     │
                     ▼
┌───────────────────────────────────────────────────────────┐
│ 5. 验证失败 → 抛出 ValidateException                       │
│    验证成功 → 进入 Controller 方法                         │
└───────────────────────────────────────────────────────────┘
```

### 💡 关键特性

#### 1. 类型智能识别

```java
// 支持多种参数类型
public void createUser(@RequestBody User user) { }           // 对象
public void updateName(@RequestParam String name) { }        // 基础类型
public void batchCreate(@RequestBody List<User> users) { }   // 集合
public void upload(@RequestParam MultipartFile file) { }     // 文件（自动跳过）
```

#### 2. 泛型处理

```java
// 支持泛型 Controller
public class BaseController<T> {
    public void save(@RequestBody T entity) { }
}

// ValidateIntercept 会正确解析 T 的实际类型
public class UserController extends BaseController<User> { }
```

#### 3. 验证日志

```java
private void log(Method method, Object value) {
    if (log.isDebugEnabled()) {
        ApiOperation apiOperation = method.getAnnotation(ApiOperation.class);
        String descr = apiOperation != null ? apiOperation.value() : "";
        
        log.debug("method {} description {} request paramType {} paramsName {} data {}", 
            method.getName(), 
            descr,
            Stream.of(method.getGenericParameterTypes())
                .filter(i -> !(i instanceof ServletRequest || i instanceof ServletResponse))
                .map(Type::getTypeName)
                .collect(Collectors.toList()),
            parameterNameDiscoverer.getParameterNames(method), 
            JSON.toJSONString(value)
        );
    }
}
```

输出示例：
```
method createUser description 创建用户 request paramType [com.example.User] paramsName [user] data {"name":"张三","age":25}
```

---

## 验证策略模式

### 策略接口

```java
public interface ValidationStrategy<T> {
    /**
     * 验证类型
     */
    ValidationStrategyType validationType();

    /**
     * 执行验证
     * 
     * @return true-验证失败，false-验证通过
     */
    boolean validation(T value, Validate.ConstraintValidatorField validatorField, 
                      ConstraintValidatorContext validatorContext);
}
```

### 内置策略

#### 1. AnnotationValidationStrategy（注解验证）

```java
@Component
public class AnnotationValidationStrategy implements ValidationStrategy<Object> {
    @Override
    public ValidationStrategyType validationType() {
        return ValidationStrategyType.Annotation;
    }

    @Override
    public boolean validation(Object value, Validate.ConstraintValidatorField validatorField, 
                             ConstraintValidatorContext validatorContext) {
        return !validatorField.getValidator().isValid(value, validatorContext);
    }
}
```

#### 2. NotEmptyValidationStrategy（非空验证）

```java
@Component
public class NotEmptyValidationStrategy implements ValidationStrategy<Object> {
    @Override
    public ValidationStrategyType validationType() {
        return ValidationStrategyType.NotEmpty;
    }

    @Override
    public boolean validation(Object value, Validate.ConstraintValidatorField validatorField, 
                             ConstraintValidatorContext validatorContext) {
        return ObjectUtil.isEmpty(value);
    }
}
```

### 策略上下文

```java
@Component
public class ValidationStrategyContext implements ApplicationContextAware {
    private static final Map<ValidationStrategyType, ValidationStrategy<Object>> 
        VALIDATION_STRATEGY_MAP = new ConcurrentHashMap<>();

    /**
     * 自动注册所有验证策略
     */
    @Override
    public void setApplicationContext(ApplicationContext applicationContext) {
        applicationContext.getBeansOfType(ValidationStrategy.class)
            .forEach((k, v) -> VALIDATION_STRATEGY_MAP.put(v.validationType(), v));
    }

    /**
     * 执行验证
     */
    public static Boolean validate(Validate.ConstraintValidatorField validatorField, 
                                   Object value, ConstraintValidatorContext validatorContext) {
        return VALIDATION_STRATEGY_MAP.get(validatorField.getType())
            .validation(value, validatorField, validatorContext);
    }
}
```

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

```java
// 配置验证提示类型
@Configuration
public class ValidationConfig {
    
    @Bean
    public ValidationProperties validationProperties() {
        ValidationProperties properties = new ValidationProperties();
        properties.setTipType(ValidationTipType.TIP_1); // 字段%s,%s
        return properties;
    }
}
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

### ValidateExceptionHandler（全局异常处理器）

框架提供了统一的异常处理器，捕获验证失败时抛出的异常并返回标准化的错误响应。

#### 📍 位置
```
ai.csap.validation.advice.ValidateExceptionHandler
```

#### 🎯 核心功能

处理多种类型的验证异常：

```java
@Order(1)
@ControllerAdvice
@Slf4j
public class ValidateExceptionHandler {

    /**
     * 处理自定义验证异常
     * 
     * 触发场景：验证器抛出 ValidateException
     */
    @ExceptionHandler({ValidateException.class})
    public ResponseEntity<ApiResult<?>> handlerSellerException(ValidateException e, HttpServletRequest request) {
        log.error("ValidateException{}", e.getMessage(), e);
        return getResponse(ApiResult.error(e.getCode(), e.getMessage(), e.getData()), request);
    }

    /**
     * 处理 @RequestBody 参数验证失败
     * 
     * 触发场景：POST/PUT 请求的 JSON 参数验证失败
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResult<?>> bindException(MethodArgumentNotValidException e, HttpServletRequest request) {
        log.error(e.getMessage(), e);
        return getResponse(ApiResult.error("413", e.getBindingResult().getFieldError().getDefaultMessage()), request);
    }

    /**
     * 处理单参数验证失败
     * 
     * 触发场景：@RequestParam、@PathVariable 等参数验证失败
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResult<?>> constraintViolationException(ConstraintViolationException e, HttpServletRequest request) {
        return getResponse(ApiResult.error("400", e.getMessage().split(",")[0].split(":")[1]), request);
    }

    /**
     * 处理参数类型转换错误
     * 
     * 触发场景：参数类型不匹配（如传入字符串给 Integer 参数）
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResult<?>> parameterTypeException(MethodArgumentTypeMismatchException exception, HttpServletRequest request) {
        // 支持自定义错误提示
        if (exception.getParameter().getParameter().isAnnotationPresent(DataTypeValidate.class)) {
            DataTypeValidate dataTypeValidate = exception.getParameter().getParameter().getAnnotation(DataTypeValidate.class);
            return getResponse(ApiResult.error(dataTypeValidate.code(), dataTypeValidate.message()), request);
        }
        // 默认错误提示
        String msg = ApiResultEnum.DATA_VALIDATE_ERROT.getDesc();
        return getResponse(ApiResult.error(ApiResultEnum.DATA_VALIDATE_ERROT.getCode(), msg), request);
    }

    /**
     * 处理缺少必需参数
     * 
     * 触发场景：@RequestParam(required=true) 但未传参
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiResult<?>> mssingServletRequestParameterException(MissingServletRequestParameterException e, HttpServletRequest request) {
        log.error("mssingServletRequestParameterException{}", e.getMessage(), e);
        return getResponse(ApiResult.error("400", String.format("属性:%s 未传,类型为:%s", e.getParameterName(), e.getParameterType())), request);
    }
}
```

### 错误响应格式

所有验证异常统一返回 `ApiResult` 格式：

```json
{
  "code": "400",
  "message": "字段name,用户名不能为空",
  "data": null,
  "success": false
}
```

### 异常处理流程图

```
┌─────────────────────────────────────────────────────────┐
│ 1. 请求参数验证失败                                      │
└───────────────────┬─────────────────────────────────────┘
                    │
                    ▼
┌─────────────────────────────────────────────────────────┐
│ 2. 抛出对应异常                                          │
│    - ValidateException (自定义验证)                      │
│    - MethodArgumentNotValidException (@RequestBody)     │
│    - ConstraintViolationException (单参数)               │
│    - MethodArgumentTypeMismatchException (类型错误)      │
└───────────────────┬─────────────────────────────────────┘
                    │
                    ▼
┌─────────────────────────────────────────────────────────┐
│ 3. ValidateExceptionHandler 捕获异常                    │
└───────────────────┬─────────────────────────────────────┘
                    │
                    ▼
┌─────────────────────────────────────────────────────────┐
│ 4. 记录日志（包含请求路径、错误信息）                    │
└───────────────────┬─────────────────────────────────────┘
                    │
                    ▼
┌─────────────────────────────────────────────────────────┐
│ 5. 返回标准化错误响应（HTTP 200 + ApiResult）           │
└─────────────────────────────────────────────────────────┘
```

---

## 配置说明

### ValidationProperties（验证配置）

框架支持通过配置文件自定义验证行为。

#### 📍 位置
```
ai.csap.validation.properties.ValidationProperties
```

#### 🔧 配置项

```java
@Data
public class ValidationProperties {
    public static final String PREFIX = "csap.validation";
    
    /**
     * 错误码字段名称
     * 默认: "code"
     */
    private String code = "code";
    
    /**
     * 错误消息字段名称
     * 默认: "message"
     */
    private String message = "message";
    
    /**
     * 是否启用验证
     * 默认: true
     */
    private Boolean enabled = Boolean.TRUE;
    
    /**
     * 验证提示类型
     * 默认: TIP_1（字段%s,%s）
     */
    private ValidationTipType tipType = ValidationTipType.TIP_1;
}
```

#### 配置文件示例

**application.yml**
```yaml
csap:
  validation:
    # 是否启用验证
    enabled: true
    # 错误码字段名
    code: code
    # 错误消息字段名
    message: message
    # 提示类型：TIP_1, TIP_2, TIP_3, TIP_3_1
    tip-type: TIP_1
```

**application.properties**
```properties
# 启用验证
csap.validation.enabled=true
# 错误码字段名
csap.validation.code=code
# 错误消息字段名
csap.validation.message=message
# 提示类型
csap.validation.tip-type=TIP_1
```

#### 提示类型对比

| 类型 | 格式 | 示例输出 | 适用场景 |
|------|------|----------|----------|
| **TIP_1** | `字段%s,%s` | 字段name,用户名不能为空 | 开发调试，需要字段名 |
| **TIP_11** | `字段%s%s` | 字段name用户名不能为空 | 开发调试，紧凑格式 |
| **TIP_2** | `%s,%s` | name,用户名不能为空 | 简洁提示 |
| **TIP_22** | `%s%s` | name用户名不能为空 | 最简洁格式 |
| **TIP_3** | `%s,%s` | 用户名,用户名不能为空 | 面向用户，使用中文描述 |
| **TIP_33** | `%s%s` | 用户名用户名不能为空 | 用户友好，紧凑格式 |
| **TIP_3_1** | `%s` | 用户名不能为空 | 最简洁，直接显示消息 |

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
    public boolean validation(Object value, Validate.ConstraintValidatorField validatorField, 
                             ConstraintValidatorContext validatorContext) {
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

**A:** 默认返回第一个错误。如需返回所有错误，可以：

```java
@ExceptionHandler(MethodArgumentNotValidException.class)
public ResponseEntity<ApiResult<?>> handleValidationException(MethodArgumentNotValidException e) {
    List<String> errors = e.getBindingResult()
        .getFieldErrors()
        .stream()
        .map(error -> error.getField() + ": " + error.getDefaultMessage())
        .collect(Collectors.toList());
    
    return getResponse(ApiResult.error("400", String.join("; ", errors)), request);
}
```

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

## 与其他框架对比

### vs Spring Validation (JSR-303)

| 特性 | CSAP Validation | Spring Validation |
|------|----------------|-------------------|
| **基础验证** | ✅ 支持（基于 JSR-303） | ✅ 支持 |
| **级联验证** | ✅ 自动递归验证 | ✅ 需要 @Valid |
| **验证缓存** | ✅ 自动缓存验证规则 | ❌ 每次都解析 |
| **响应过滤** | ✅ 支持动态字段过滤 | ❌ 不支持 |
| **验证策略** | ✅ 可插拔策略模式 | ❌ 固定实现 |
| **API 文档集成** | ✅ 无缝集成 | ❌ 需要额外工作 |
| **泛型支持** | ✅ 完整支持 | ⚠️ 部分支持 |
| **错误提示** | ✅ 多种格式可选 | ⚠️ 固定格式 |

### vs Hibernate Validator

| 特性 | CSAP Validation | Hibernate Validator |
|------|----------------|---------------------|
| **验证引擎** | ✅ 基于 Hibernate Validator | ✅ 原生 |
| **扩展性** | ✅ 策略模式，易扩展 | ⚠️ 需要自定义验证器 |
| **性能** | ✅ 验证规则缓存 | ⚠️ 每次都初始化 |
| **框架集成** | ✅ Spring Boot 开箱即用 | ⚠️ 需要配置 |

---

## 总结

### ValidateData 接口体系的核心价值

1. **分层设计**：从 `ValidateData` → `ValidateFilter` → `IValidateBaseFactory` → `IValidateFactory`，职责清晰
2. **验证入口**：提供统一的验证规则管理和执行入口
3. **真实数据过滤**：通过 `response()` 方法获取方法返回参数，支持动态字段过滤
4. **性能优化**：验证规则缓存，避免重复解析
5. **扩展性强**：基于策略模式，易于扩展自定义验证逻辑

### ValidateIntercept 的核心价值

1. **统一拦截**：所有请求参数自动验证，无需在每个 Controller 方法中重复验证逻辑
2. **类型智能**：自动识别基础类型、对象、集合、泛型等多种参数类型
3. **级联验证**：支持嵌套对象的递归验证
4. **框架集成**：与 Spring MVC 无缝集成，透明化验证过程

### 适用场景

✅ **推荐使用**
- 复杂的业务参数验证
- 需要级联验证嵌套对象
- API 文档生成（提供真实验证规则）
- 需要动态控制返回字段

❌ **不推荐使用**
- 简单的单字段验证（可直接使用 `@Valid`）
- 性能要求极高的场景（缓存已优化，但仍有开销）

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

**文档版本**: 1.0.0  
**更新时间**: 2025-10-23  
**维护者**: CSAP Framework Team
