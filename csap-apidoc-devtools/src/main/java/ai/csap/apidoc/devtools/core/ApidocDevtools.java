package ai.csap.apidoc.devtools.core;

import ai.csap.apidoc.ApiStrategyType;
import ai.csap.apidoc.StandardProperties;
import ai.csap.apidoc.annotation.Api;
import ai.csap.apidoc.annotation.ApiModel;
import ai.csap.apidoc.annotation.ApiModelProperty;
import ai.csap.apidoc.annotation.ApiOperation;
import ai.csap.apidoc.annotation.ApiStatus;
import ai.csap.apidoc.annotation.ParamType;
import ai.csap.apidoc.annotation.Protocols;
import ai.csap.apidoc.autoconfigure.EnableApidocConfig;
import ai.csap.apidoc.autoconfigure.StrategyModel;
import ai.csap.apidoc.core.ApidocOptional;
import ai.csap.apidoc.core.ModelTypepProperties;
import ai.csap.apidoc.devtools.model.GetFieldExcled;
import ai.csap.apidoc.devtools.model.api.Field;
import ai.csap.apidoc.devtools.model.api.MethodModel;
import ai.csap.apidoc.devtools.model.api.RequestField;
import ai.csap.apidoc.devtools.util.GenericTypeUtil;
import ai.csap.apidoc.model.CsapDocMethod;
import ai.csap.apidoc.model.CsapDocModel;
import ai.csap.apidoc.model.CsapDocModelController;
import ai.csap.apidoc.model.CsapDocParameter;
import ai.csap.apidoc.model.ParamGroupMethodProperty;
import ai.csap.apidoc.properties.CsapDocConfig;
import ai.csap.apidoc.service.ApiDocService;
import ai.csap.apidoc.service.ApidocContext;
import ai.csap.apidoc.service.FilterData;
import ai.csap.apidoc.util.ApidocClazzUtils;
import ai.csap.apidoc.util.ExceptionUtils;
import ai.csap.validation.factory.Validate;
import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.map.MapUtil;
import cn.hutool.core.util.ArrayUtil;
import cn.hutool.core.util.StrUtil;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.ApplicationContext;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.stereotype.Controller;
import org.springframework.util.Assert;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @Author ycf
 * @Date 2025/9/10 00:23
 * @Version 1.0
 */
@Slf4j
public class ApidocDevtools implements FilterData {
    /**
     * 接口文档配置
     */
    private final EnableApidocConfig enableApidocConfig;
    private final ApplicationContext applicationContext;
    private final CsapDocConfig csapDocConfig;
    protected static final List<Class<? extends Annotation>> MAPPING = Lists.newArrayList(
            GetMapping.class, PostMapping.class, DeleteMapping.class,
            PutMapping.class, PatchMapping.class, RequestMapping.class);

    public ApidocDevtools(EnableApidocConfig enableApidocConfig, ApplicationContext applicationContext,
                          CsapDocConfig csapDocConfig) {
        this.enableApidocConfig = enableApidocConfig;
        this.applicationContext = applicationContext;
        this.csapDocConfig = csapDocConfig;
    }


    /**
     * 检查给定的方法是否带有任何指定的注解。
     *
     * @param method 需要检查的方法对象
     * @return 如果方法带有任何一个注解，则返回true；否则返回false
     */
    public boolean filterMethod(Method method) {
        return MAPPING.stream().anyMatch(method::isAnnotationPresent);
    }


    /**
     * 处理返回信息
     *
     * @param aClass 返回的类型
     * @return
     */
    protected List<CsapDocModel> response(Type aClass) {
        return getFieldList(new Type[]{aClass}, null);
    }

    /**
     * 请求信息
     *
     * @param method 方法
     * @return 返回当前请求参数列表
     */
    protected List<CsapDocModel> request(Method method) {
        Type[] genericParameterTypes = method.getGenericParameterTypes();
        if (ArrayUtil.isEmpty(genericParameterTypes)) {
            return Collections.emptyList();
        }
        String[] strings = PARAMETER_NAME_DISCOVERER.getParameterNames(method);
        return getFieldList(genericParameterTypes, strings);
    }

    /**
     * 从方法中获取泛型类型并返回字段信息
     * 支持从方法参数或返回值中获取泛型类型
     *
     * @param getFieldExcled 包含 controllerClassName、methodName、parameterIndex
     * @return 字段信息列表
     */
    @SneakyThrows
    public List<Field> getFieldsFromMethod(GetFieldExcled getFieldExcled) {
        if (StrUtil.isEmpty(getFieldExcled.getControllerClassName()) ||
                StrUtil.isEmpty(getFieldExcled.getMethodName())) {
            throw new IllegalArgumentException("controllerClassName 和 methodName 不能为空");
        }

        // 1. 获取 Controller 类
        Class<?> controllerClass = Class.forName(getFieldExcled.getControllerClassName());

        // 2. 获取方法（包括从父类和接口继承的方法）
        Method targetMethod = null;
        for (Method m : controllerClass.getMethods()) {
            if (m.getName().equals(getFieldExcled.getMethodName()) && !m.isBridge()) {
                targetMethod = m;
                break;
            }
        }

        if (targetMethod == null) {
            throw new NoSuchMethodException("未找到方法: " + getFieldExcled.getMethodName());
        }

        // 3. 获取方法声明的类（可能在父类或接口中）
        Class<?> declaringClass = targetMethod.getDeclaringClass();

        // 4. 构建方法的泛型上下文：从方法的声明类和当前类的继承链中解析泛型类型
        Type targetType;
        Integer parameterIndex = getFieldExcled.getParameterIndex();

        if (parameterIndex != null && parameterIndex < 0) {
            // 获取返回值类型
            Type genericReturnType = targetMethod.getGenericReturnType();
            // 解析泛型类型（处理继承场景）
            targetType = resolveGenericTypeFromInheritance(genericReturnType, declaringClass, controllerClass);
        } else {
            // 获取参数类型
            Type[] genericParameterTypes = targetMethod.getGenericParameterTypes();
            int index = (parameterIndex != null && parameterIndex >= 0) ? parameterIndex : 0;

            if (index >= genericParameterTypes.length) {
                throw new IndexOutOfBoundsException("参数索引超出范围: " + index + ", 方法参数数量: " + genericParameterTypes.length);
            }

            Type genericParameterType = genericParameterTypes[index];
            // 解析泛型类型（处理继承场景）
            targetType = resolveGenericTypeFromInheritance(genericParameterType, declaringClass, controllerClass);
        }

        // 5. 使用获取到的 Type 对象调用 getFields
        GetFieldExcled newRequest = GetFieldExcled.builder()
                                                  .aClass(targetType)  // 使用从方法中获取的 Type 对象
                                                  .excledFields(getFieldExcled.getExcledFields())
                                                  .appendName(getFieldExcled.getAppendName())
                                                  .build();

        return getFields(newRequest);
    }

    /**
     * 从继承链中解析泛型类型
     * 处理从抽象类或接口继承的方法，其中泛型类型需要在子类的上下文中解析
     *
     * 支持的场景：
     * 1. 直接的 TypeVariable（如方法参数 T entity）
     * 2. 嵌套的 ParameterizedType（如 Response<List<T>>）
     * 3. 多层继承链中的泛型传递
     * 4. 泛型数组（如 T[]）
     * 5. 通配符类型（如 ? extends T）
     *
     * @param type           原始类型（可能包含类型变量）
     * @param declaringClass 方法声明的类（父类或接口）
     * @param actualClass    实际使用的类（子类）
     * @return 解析后的类型
     */
    private Type resolveGenericTypeFromInheritance(Type type, Class<?> declaringClass, Class<?> actualClass) {
        // 首先构建全局的泛型映射（从 actualClass 遍历整个继承链）
        Map<String, Type> globalTypeMap = buildGlobalTypeVariableMap(actualClass);

        if (log.isDebugEnabled()) {
            log.debug("全局泛型映射: actualClass={}, mapSize={}", actualClass.getSimpleName(), globalTypeMap.size());
        }

        // 使用全局映射解析类型
        return resolveTypeWithGlobalMap(type, globalTypeMap, new HashSet<>());
    }

    /**
     * 使用全局泛型映射解析类型
     * 递归处理所有类型变量，支持嵌套泛型
     *
     * @param type          要解析的类型
     * @param globalTypeMap 全局泛型映射（key: 类名.变量名）
     * @param visited       已访问的类型变量（防止循环）
     * @return 解析后的类型
     */
    private Type resolveTypeWithGlobalMap(Type type, Map<String, Type> globalTypeMap, Set<String> visited) {
        if (type == null) {
            return null;
        }

        // 处理 TypeVariable
        if (type instanceof TypeVariable) {
            TypeVariable<?> typeVar = (TypeVariable<?>) type;
            String fullKey = getTypeVariableFullKey(typeVar);

            // 防止循环引用
            if (visited.contains(fullKey)) {
                return type;
            }
            visited.add(fullKey);

            Type resolved = globalTypeMap.get(fullKey);
            if (resolved != null && resolved != type) {
                // 递归解析，因为解析出来的类型可能还包含 TypeVariable
                return resolveTypeWithGlobalMap(resolved, globalTypeMap, visited);
            }

            // 尝试用简单名称查找（兼容某些场景）
            String simpleName = typeVar.getName();
            for (Map.Entry<String, Type> entry : globalTypeMap.entrySet()) {
                if (entry.getKey().endsWith("." + simpleName)) {
                    Type simpleResolved = entry.getValue();
                    if (simpleResolved != null && !(simpleResolved instanceof TypeVariable)) {
                        return resolveTypeWithGlobalMap(simpleResolved, globalTypeMap, visited);
                    }
                }
            }

            return type;
        }

        // 处理 ParameterizedType（如 Response<T>、List<User>）
        if (type instanceof ParameterizedType) {
            ParameterizedType parameterizedType = (ParameterizedType) type;
            Type[] actualTypeArguments = parameterizedType.getActualTypeArguments();
            Type[] resolvedArguments = new Type[actualTypeArguments.length];
            boolean changed = false;

            for (int i = 0; i < actualTypeArguments.length; i++) {
                Type arg = actualTypeArguments[i];
                Type resolved = resolveTypeWithGlobalMap(arg, globalTypeMap, new HashSet<>(visited));
                resolvedArguments[i] = resolved;
                if (resolved != arg) {
                    changed = true;
                }
            }

            if (changed) {
                return new ResolvedParameterizedType(
                        parameterizedType.getRawType(),
                        resolvedArguments,
                        parameterizedType.getOwnerType()
                );
            }
            return type;
        }

        // 处理泛型数组类型（如 T[]）
        if (type instanceof java.lang.reflect.GenericArrayType) {
            java.lang.reflect.GenericArrayType arrayType = (java.lang.reflect.GenericArrayType) type;
            Type componentType = arrayType.getGenericComponentType();
            Type resolvedComponent = resolveTypeWithGlobalMap(componentType, globalTypeMap, visited);
            if (resolvedComponent != componentType) {
                return new ResolvedGenericArrayType(resolvedComponent);
            }
            return type;
        }

        // 处理通配符类型（如 ? extends T、? super T）
        if (type instanceof java.lang.reflect.WildcardType) {
            java.lang.reflect.WildcardType wildcardType = (java.lang.reflect.WildcardType) type;
            Type[] upperBounds = wildcardType.getUpperBounds();
            Type[] lowerBounds = wildcardType.getLowerBounds();

            Type[] resolvedUpperBounds = new Type[upperBounds.length];
            Type[] resolvedLowerBounds = new Type[lowerBounds.length];
            boolean changed = false;

            for (int i = 0; i < upperBounds.length; i++) {
                Type resolved = resolveTypeWithGlobalMap(upperBounds[i], globalTypeMap, new HashSet<>(visited));
                resolvedUpperBounds[i] = resolved;
                if (resolved != upperBounds[i]) changed = true;
            }

            for (int i = 0; i < lowerBounds.length; i++) {
                Type resolved = resolveTypeWithGlobalMap(lowerBounds[i], globalTypeMap, new HashSet<>(visited));
                resolvedLowerBounds[i] = resolved;
                if (resolved != lowerBounds[i]) changed = true;
            }

            if (changed) {
                return new ResolvedWildcardType(resolvedUpperBounds, resolvedLowerBounds);
            }
            return type;
        }

        // 其他类型（如 Class）直接返回
        return type;
    }

    /**
     * 构建全局的泛型类型映射
     * 从 actualClass 开始，遍历整个继承链（父类、接口），收集所有类的泛型参数映射
     *
     * @param actualClass 实际的类（最具体的子类）
     * @return 全局泛型映射（key: 类全限定名.类型变量名, value: 实际类型）
     */
    private Map<String, Type> buildGlobalTypeVariableMap(Class<?> actualClass) {
        Map<String, Type> globalMap = new HashMap<>();
        buildTypeVariableMapRecursive(actualClass, globalMap, new HashSet<>());

        // 多轮解析：确保所有 TypeVariable 都被完全解析
        // 因为泛型可能有多层传递：A<T> -> B<T> -> C<T> -> 具体类型
        int maxIterations = 10; // 防止无限循环
        for (int i = 0; i < maxIterations; i++) {
            boolean hasUnresolved = false;
            Map<String, Type> resolvedMap = new HashMap<>();

            for (Map.Entry<String, Type> entry : globalMap.entrySet()) {
                Type value = entry.getValue();
                if (value instanceof TypeVariable) {
                    Type resolved = resolveTypeVariableFromMap((TypeVariable<?>) value, globalMap);
                    resolvedMap.put(entry.getKey(), resolved);
                    if (resolved instanceof TypeVariable) {
                        hasUnresolved = true;
                    }
                } else if (value instanceof ParameterizedType) {
                    // 解析嵌套的泛型参数
                    Type resolved = resolveNestedParameterizedType((ParameterizedType) value, globalMap);
                    resolvedMap.put(entry.getKey(), resolved);
                } else {
                    resolvedMap.put(entry.getKey(), value);
                }
            }

            globalMap = resolvedMap;

            if (!hasUnresolved) {
                break;
            }
        }

        return globalMap;
    }

    /**
     * 从映射中解析 TypeVariable
     */
    private Type resolveTypeVariableFromMap(TypeVariable<?> typeVar, Map<String, Type> globalMap) {
        String fullKey = getTypeVariableFullKey(typeVar);
        Type resolved = globalMap.get(fullKey);

        if (resolved != null && resolved != typeVar) {
            if (resolved instanceof TypeVariable) {
                // 继续解析
                return resolveTypeVariableFromMap((TypeVariable<?>) resolved, globalMap);
            }
            return resolved;
        }

        return typeVar;
    }

    /**
     * 解析嵌套的 ParameterizedType 中的泛型参数
     */
    private Type resolveNestedParameterizedType(ParameterizedType type, Map<String, Type> globalMap) {
        Type[] args = type.getActualTypeArguments();
        Type[] resolvedArgs = new Type[args.length];
        boolean changed = false;

        for (int i = 0; i < args.length; i++) {
            Type arg = args[i];
            if (arg instanceof TypeVariable) {
                Type resolved = resolveTypeVariableFromMap((TypeVariable<?>) arg, globalMap);
                resolvedArgs[i] = resolved;
                if (resolved != arg) changed = true;
            } else if (arg instanceof ParameterizedType) {
                Type resolved = resolveNestedParameterizedType((ParameterizedType) arg, globalMap);
                resolvedArgs[i] = resolved;
                if (resolved != arg) changed = true;
            } else {
                resolvedArgs[i] = arg;
            }
        }

        if (changed) {
            return new ResolvedParameterizedType(type.getRawType(), resolvedArgs, type.getOwnerType());
        }
        return type;
    }

    /**
     * 构建从 actualClass 到 declaringClass 的完整泛型类型映射
     * （保留此方法用于兼容）
     *
     * @param actualClass    实际使用的类（子类）
     * @param declaringClass 方法声明的类（父类或接口）
     * @return 类型变量名到实际类型的映射（键为 declaringClass 的类型变量名）
     */
    private Map<String, Type> buildTypeVariableMap(Class<?> actualClass, Class<?> declaringClass) {
        Map<String, Type> globalMap = buildGlobalTypeVariableMap(actualClass);

        // 提取目标声明类的类型变量映射
        Map<String, Type> result = new HashMap<>();
        TypeVariable<?>[] declaringTypeParams = declaringClass.getTypeParameters();
        for (TypeVariable<?> typeParam : declaringTypeParams) {
            String fullKey = declaringClass.getName() + "." + typeParam.getName();
            Type resolvedType = globalMap.get(fullKey);
            if (resolvedType != null) {
                result.put(typeParam.getName(), resolvedType);
            }
        }

        if (log.isDebugEnabled()) {
            log.debug("最终泛型映射: declaringClass={}, result={}", declaringClass.getSimpleName(), result);
        }

        return result;
    }

    /**
     * 完全解析类型变量，直到得到具体类型或无法继续解析
     */
    private Type resolveTypeVariableCompletely(Type type, Map<String, Type> fullKeyMap) {
        Set<String> visited = new HashSet<>(); // 防止循环引用

        while (type instanceof TypeVariable) {
            TypeVariable<?> tv = (TypeVariable<?>) type;
            String key = getTypeVariableFullKey(tv);

            if (visited.contains(key)) {
                // 检测到循环引用，返回当前类型
                break;
            }
            visited.add(key);

            Type resolved = fullKeyMap.get(key);
            if (resolved == null || resolved == type) {
                break;
            }
            type = resolved;
        }

        return type;
    }

    /**
     * 获取 TypeVariable 的完整键名（类名.变量名）
     */
    private String getTypeVariableFullKey(TypeVariable<?> tv) {
        Object genericDeclaration = tv.getGenericDeclaration();
        if (genericDeclaration instanceof Class) {
            return ((Class<?>) genericDeclaration).getName() + "." + tv.getName();
        } else if (genericDeclaration instanceof java.lang.reflect.Method) {
            java.lang.reflect.Method method = (java.lang.reflect.Method) genericDeclaration;
            return method.getDeclaringClass().getName() + "." + method.getName() + "." + tv.getName();
        }
        return tv.getName();
    }

    /**
     * 递归构建泛型类型映射
     * 使用完整键名（类名.变量名）来存储映射
     */
    private void buildTypeVariableMapRecursive(Class<?> currentClass,
            Map<String, Type> fullKeyMap, Set<Class<?>> visited) {
        if (currentClass == null || currentClass == Object.class || visited.contains(currentClass)) {
            return;
        }
        visited.add(currentClass);

        // 1. 处理父类
        Type genericSuperclass = currentClass.getGenericSuperclass();
        if (genericSuperclass instanceof ParameterizedType) {
            ParameterizedType superPt = (ParameterizedType) genericSuperclass;
            Class<?> superClass = (Class<?>) superPt.getRawType();

            // 建立父类的类型参数映射
            TypeVariable<?>[] superTypeParams = superClass.getTypeParameters();
            Type[] actualArgs = superPt.getActualTypeArguments();

            for (int i = 0; i < superTypeParams.length && i < actualArgs.length; i++) {
                String fullKey = superClass.getName() + "." + superTypeParams[i].getName();
                Type actualType = actualArgs[i];

                // 如果 actualType 是 TypeVariable，先解析它
                if (actualType instanceof TypeVariable) {
                    Type resolved = resolveTypeVariableCompletely(actualType, fullKeyMap);
                    if (resolved != actualType) {
                        actualType = resolved;
                    }
                }

                // 只有当映射不存在或者新的类型更具体时才更新
                if (!fullKeyMap.containsKey(fullKey) ||
                    (fullKeyMap.get(fullKey) instanceof TypeVariable && !(actualType instanceof TypeVariable))) {
                    fullKeyMap.put(fullKey, actualType);
                }
            }

            // 递归处理父类
            buildTypeVariableMapRecursive(superClass, fullKeyMap, visited);
        } else if (genericSuperclass instanceof Class) {
            buildTypeVariableMapRecursive((Class<?>) genericSuperclass, fullKeyMap, visited);
        }

        // 2. 处理所有实现的接口
        Type[] genericInterfaces = currentClass.getGenericInterfaces();
        for (Type genericInterface : genericInterfaces) {
            if (genericInterface instanceof ParameterizedType) {
                ParameterizedType interfacePt = (ParameterizedType) genericInterface;
                Class<?> interfaceClass = (Class<?>) interfacePt.getRawType();

                // 建立接口的类型参数映射
                TypeVariable<?>[] interfaceTypeParams = interfaceClass.getTypeParameters();
                Type[] actualArgs = interfacePt.getActualTypeArguments();

                for (int i = 0; i < interfaceTypeParams.length && i < actualArgs.length; i++) {
                    String fullKey = interfaceClass.getName() + "." + interfaceTypeParams[i].getName();
                    Type actualType = actualArgs[i];

                    // 如果 actualType 是 TypeVariable，先解析它
                    if (actualType instanceof TypeVariable) {
                        Type resolved = resolveTypeVariableCompletely(actualType, fullKeyMap);
                        if (resolved != actualType) {
                            actualType = resolved;
                        }
                    }

                    // 只有当映射不存在或者新的类型更具体时才更新
                    if (!fullKeyMap.containsKey(fullKey) ||
                        (fullKeyMap.get(fullKey) instanceof TypeVariable && !(actualType instanceof TypeVariable))) {
                        fullKeyMap.put(fullKey, actualType);
                    }
                }

                // 递归处理接口
                buildTypeVariableMapRecursive(interfaceClass, fullKeyMap, visited);
            } else if (genericInterface instanceof Class) {
                buildTypeVariableMapRecursive((Class<?>) genericInterface, fullKeyMap, visited);
            }
        }
    }

    /**
     * 从继承链中找到类型变量的实际类型
     *
     * @param declaringClass 声明类
     * @param actualClass    实际类
     * @param typeParamIndex 类型参数索引
     * @return 实际类型
     */
    private Type findActualTypeFromInheritance(Class<?> declaringClass, Class<?> actualClass, int typeParamIndex) {
        Map<String, Type> typeVariableMap = buildTypeVariableMap(actualClass, declaringClass);

        TypeVariable<?>[] declaringTypeParams = declaringClass.getTypeParameters();
        if (typeParamIndex < declaringTypeParams.length) {
            String paramName = declaringTypeParams[typeParamIndex].getName();
            Type result = typeVariableMap.get(paramName);

            if (log.isDebugEnabled()) {
                log.debug("findActualTypeFromInheritance: declaringClass={}, actualClass={}, paramName={}, result={}",
                    declaringClass.getSimpleName(), actualClass.getSimpleName(), paramName, result);
            }

            return result;
        }

        return null;
    }

    /**
     * 已解析的参数化类型实现
     */
    private static class ResolvedParameterizedType implements ParameterizedType {
        private final Type rawType;
        private final Type[] actualTypeArguments;
        private final Type ownerType;

        ResolvedParameterizedType(Type rawType, Type[] actualTypeArguments, Type ownerType) {
            this.rawType = rawType;
            this.actualTypeArguments = actualTypeArguments.clone();
            this.ownerType = ownerType;
        }

        @Override
        public Type[] getActualTypeArguments() {
            return actualTypeArguments.clone();
        }

        @Override
        public Type getRawType() {
            return rawType;
        }

        @Override
        public Type getOwnerType() {
            return ownerType;
        }

        @Override
        public String getTypeName() {
            StringBuilder sb = new StringBuilder();
            if (rawType instanceof Class) {
                sb.append(((Class<?>) rawType).getName());
            } else {
                sb.append(rawType.getTypeName());
            }

            if (actualTypeArguments != null && actualTypeArguments.length > 0) {
                sb.append("<");
                for (int i = 0; i < actualTypeArguments.length; i++) {
                    if (i > 0) {
                        sb.append(", ");
                    }
                    Type arg = actualTypeArguments[i];
                    sb.append(arg.getTypeName());
                }
                sb.append(">");
            }
            return sb.toString();
        }

        @Override
        public String toString() {
            return getTypeName();
        }
    }

    /**
     * 已解析的泛型数组类型实现（处理 T[] 类型）
     */
    private static class ResolvedGenericArrayType implements java.lang.reflect.GenericArrayType {
        private final Type componentType;

        ResolvedGenericArrayType(Type componentType) {
            this.componentType = componentType;
        }

        @Override
        public Type getGenericComponentType() {
            return componentType;
        }

        @Override
        public String toString() {
            return componentType.getTypeName() + "[]";
        }
    }

    /**
     * 已解析的通配符类型实现（处理 ? extends T 或 ? super T）
     */
    private static class ResolvedWildcardType implements java.lang.reflect.WildcardType {
        private final Type[] upperBounds;
        private final Type[] lowerBounds;

        ResolvedWildcardType(Type[] upperBounds, Type[] lowerBounds) {
            this.upperBounds = upperBounds.clone();
            this.lowerBounds = lowerBounds.clone();
        }

        @Override
        public Type[] getUpperBounds() {
            return upperBounds.clone();
        }

        @Override
        public Type[] getLowerBounds() {
            return lowerBounds.clone();
        }

        @Override
        public String toString() {
            if (lowerBounds.length > 0) {
                return "? super " + lowerBounds[0].getTypeName();
            } else if (upperBounds.length > 0 && !upperBounds[0].equals(Object.class)) {
                return "? extends " + upperBounds[0].getTypeName();
            }
            return "?";
        }
    }

    /**
     * 根据类名获取当前类所有字段
     * 支持从方法参数中获取的泛型类型，完美支持泛型字段解析
     *
     * @param getFieldExcled
     * @return
     */
    @SneakyThrows
    public List<Field> getFields(GetFieldExcled getFieldExcled) {
        // 如果提供了 Type 对象（通常是从方法参数中获取的泛型类型），使用新的泛型工具类
        if (getFieldExcled.getAClass() != null) {
            Type type = getFieldExcled.getAClass();
            // 使用新的泛型工具类来完美支持泛型字段获取
            return GenericTypeUtil.getFieldsWithGenericType(
                    type,
                    getFieldExcled.getExcledFields(),
                    StrUtil.isEmpty(getFieldExcled.getAppendName()) ? StrUtil.EMPTY : getFieldExcled.getAppendName(),
                    new HashSet<>()
            );
        }

        // 原有逻辑：通过类名获取字段
        List<Field> fields = new ArrayList<>();
        Class<?> clas = ApidocClazzUtils
                .getRawClassType(getFieldExcled.getAClass() != null ? getFieldExcled.getAClass() : Class.forName(getFieldExcled.getClassName()))
                .getCl();

        // 初始化类型路径栈,用于检测循环引用
        Set<String> typePathStack = new HashSet<>();
        typePathStack.add(clas.getName());

        getAllFields(clas, StrUtil.EMPTY, getFieldExcled.getExcledFields()).forEach(field -> {
            Field field1 = field(field, null, "");
            fields.add(field1);
            String appendName = StrUtil.isEmpty(getFieldExcled.getAppendName()) ?
                    field1.getName() + StrUtil.DOT :
                    getFieldExcled.getAppendName() + StrUtil.DOT + field1.getName() + StrUtil.DOT;
            addChild(field1, field1.getName(), appendName, field,
                    getFieldExcled.getExcledFields(), typePathStack);
        });
        return fields;
    }

    /**
     * 获取所有字段
     *
     * @param cls          字段的类
     * @param parentName   父类的名称
     * @param excledFields 过滤的名称
     * @return
     */
    private List<java.lang.reflect.Field> getAllFields(Class<?> cls, String parentName, Set<String> excledFields) {
        return ApidocClazzUtils.getAllFields(cls)
                               .stream()
                               .filter(i -> i.isAnnotationPresent(ApiModelProperty.class))
                               .filter(i -> !excledFields.contains((StrUtil.isEmpty(parentName) ? StrUtil.EMPTY : (parentName + StrUtil.DOT)) + i.getName()))
                               .collect(Collectors.toList());
    }

    /**
     * 字段信息
     *
     * @param field
     * @return
     */
    public Field field(java.lang.reflect.Field field, Field parentField, String appendName) {
        ApiModelProperty apiModelProperty = field.getAnnotation(ApiModelProperty.class);
        ModelTypepProperties modelTypepProperties = ApidocClazzUtils.getRawClassType(field.getGenericType());
        Field field1 = Field.builder()
                            .name(field.getName())
                            .dataType(modelTypepProperties.getSubStr())
                            .childrenField(new ArrayList<>())
                            .modelType(modelTypepProperties.getModelType())
                            .build();
        field1.setId(UUID.randomUUID().toString());
        field1.setKey(appendName + field.getName());
        field1.setParent(parentField);
        field1.setPackageName(field.getType().getName());
        field1.setSimpleName(field.getType().getSimpleName());
        field1.setFinalClassName(modelTypepProperties.getCl().getName());
        if (apiModelProperty != null) {
            field1.setValue(apiModelProperty.value());
            field1.setExample(apiModelProperty.example());
            field1.setDefaultValue(apiModelProperty.defaultValue());
            field1.setDescription(apiModelProperty.description());
        } else {
            field1.setValue(field.getType().getSimpleName());
        }
        return field1;
    }

    /**
     * 添加子集字段
     *
     * @param parentField   父字段
     * @param parentName    父字段名称
     * @param appendName    追加的名称前缀
     * @param field2        当前字段的反射Field对象
     * @param excledFields  排除的字段集合
     * @param typePathStack 类型路径栈,用于检测循环引用
     */
    private void addChild(Field parentField, String parentName, String appendName, java.lang.reflect.Field field2,
                          Set<String> excledFields, Set<String> typePathStack) {
        ModelTypepProperties modelTypepProperties = ApidocClazzUtils.getRawClassType(field2.getGenericType());
        String currentTypeName = modelTypepProperties.getCl().getName();

        if (modelTypepProperties.getCl().isAnnotationPresent(ApiModel.class)) {
            // 检测循环引用:如果当前类型已经在路径栈中,则停止递归
            if (typePathStack.contains(currentTypeName)) {
                if (log.isDebugEnabled()) {
                    log.debug("检测到循环引用,停止递归: {} -> {}", String.join(" -> ", typePathStack), currentTypeName);
                }
                return;
            }

            // 旧的检查逻辑保留作为双重保险
            if (parentField.getParent() != null &&
                    parentField.getFinalClassName().equals(parentField.getParent().getFinalClassName())) {
                if (StringUtils.countMatches(parentName, field2.getName()) == 2 &&
                        (parentField.getDataType().equals(parentField.getParent().getDataType()) ||
                                parentField.getParent().getParent() != null &&
                                        parentField.getDataType()
                                                   .equals(parentField.getParent().getParent().getDataType()))) {
                    return;
                }
            }

            Field clonedField = parentField.clone().setChildrenField(Collections.emptyList());
            List<java.lang.reflect.Field> allFields = getAllFields(modelTypepProperties.getCl(), parentName, excledFields);
            child(clonedField, appendName, parentField.getChildrenField(),
                    allFields, excledFields, typePathStack, currentTypeName);
        }
    }

    /**
     * 递归处理子字段
     *
     * @param parentField     父字段
     * @param appendName      追加的名称前缀
     * @param fields          字段列表
     * @param list            反射字段列表
     * @param excledFields    排除的字段集合
     * @param typePathStack   类型路径栈,用于检测循环引用
     * @param currentTypeName 当前类型名称
     * @return 字段列表
     */
    private List<Field> child(Field parentField, String appendName, List<Field> fields,
                              List<java.lang.reflect.Field> list, Set<String> excledFields,
                              Set<String> typePathStack, String currentTypeName) {
        // 将当前类型加入路径栈
        Set<String> newTypePathStack = new HashSet<>(typePathStack);
        newTypePathStack.add(currentTypeName);

        list.forEach(field -> {
            Field field1 = field(field, parentField, appendName);
            fields.add(field1);
            addChild(field1, parentField.getName() + StrUtil.DOT + field1.getName(), appendName + field1.getName() + StrUtil.DOT, field, excledFields, newTypePathStack);
        });
        return fields;
    }

    /**
     * 获取字段列表
     *
     * @param types   类型列表
     * @param strings 参数名称列表
     * @return
     */
    protected List<CsapDocModel> getFieldList(Type[] types, String[] strings) {
        AtomicReference<Integer> i2 = new AtomicReference<>(0);
        return Stream.of(types)
                     .map(i -> {
                         ModelTypepProperties modelTypepProperties = ApidocClazzUtils.getRawClassType(i);
                         List<Field> list = getFields(GetFieldExcled.builder().aClass(i).build());
                         String name = strings == null ? i.getTypeName() : strings[i2.get()];
                         if (CollectionUtil.isEmpty(list)) {
                             return CsapDocModel.builder()
                                                .modelType(modelTypepProperties.getModelType())
                                                .parameters(Arrays.asList(CsapDocParameter.builder()
                                                                                          .key(UUID.randomUUID()
                                                                                                   .toString())
                                                                                          .name(name)
                                                                                          .value(modelTypepProperties.getSubStr())
                                                                                          .dataType(modelTypepProperties.getSubStr())
                                                                                          .longDataType(modelTypepProperties.getRawType())
                                                                                          .modelType(modelTypepProperties.getModelType())
                                                                                          .build()))
                                                .build();
                         }
                         i2.set(i2.get() + 1);
                         CsapDocModel model = CsapDocModel.builder()
                                                          .value(modelTypepProperties.getRawType())
                                                          .modelType(modelTypepProperties.getModelType())
                                                          .keyName(UUID.randomUUID().toString())
                                                          .name(name)
                                                          .build();
                         return children(list, model);
                     }).collect(Collectors.toList());
    }

    /**
     * 处理子集字段
     *
     * @param fields       字段列表
     * @param csapDocModel 字段model信息
     * @return 字段model信息
     */
    protected CsapDocModel children(List<Field> fields, CsapDocModel csapDocModel) {
        if (csapDocModel.getParameters() == null) {
            csapDocModel.setParameters(Lists.newArrayList());
        }
        for (Field field : fields) {
            CsapDocParameter csapDocParameter = CsapDocParameter.builder()
                                                                .dataType(field.getDataType())
                                                                .value(field.getValue())
                                                                .modelType(field.getModelType())
                                                                .name(field.getName())
                                                                .longDataType(field.getFinalClassName())
                                                                .key(field.getId())
                                                                .build();
            csapDocModel.getParameters().add(csapDocParameter);
            if (CollectionUtil.isNotEmpty(field.getChildrenField())) {
                CsapDocModel csapDocModel2 = new CsapDocModel();
                csapDocModel2.setKeyName(field.getId());
                csapDocModel2.setName(field.getName());
                csapDocModel2.setModelType(field.getModelType());
                csapDocModel2.setValue(field.getValue());
                csapDocParameter.setChildren(csapDocModel2);
                children(field.getChildrenField(), csapDocModel2);
            }
        }
        return csapDocModel;
    }

    /**
     * 根据class 获取接口controller
     *
     * @param cl
     * @return
     */
    protected CsapDocModelController getControllerModel(Class<?> cl) {
        if (!cl.isAnnotationPresent(RestController.class) && !cl.isAnnotationPresent(Controller.class)) {
            return null;
        }
        Api api = cl.getAnnotation(Api.class);
        boolean ab = api == null;
        RequestMapping requestMapping = cl.getAnnotation(RequestMapping.class);
        return CsapDocModelController.builder()
                                     .description(ab ? cl.getSimpleName() : api.description())
                                     .methodList(new ArrayList<>())
                                     .hidden(!ab && api.hidden())
                                     .name(cl.getName())
                                     .simpleName(cl.getSimpleName())
                                     .path(requestMapping == null || ArrayUtil.isEmpty(requestMapping.value()) ? null : requestMapping.value())
                                     .position(ab ? 1 : api.position())
                                     .protocols(ab ? Protocols.HTTP : api.protocols())
                                     .hiddenMethod(ab ? null : Arrays.asList(api.hiddenMethod()))
                                     .group(ab ? GROUP : Sets.newHashSet(api.group()))
                                     .version(ab ? VERSION : Sets.newHashSet(api.version()))
                                     .status(ab ? ApiStatus.DEFAULT : api.status())
                                     .tags(ab ? null : api.tags())
                                     .search(new HashSet<>())
                                     .value(ab ? cl.getSimpleName() : api.value())
                                     .build();
    }

    /**
     * 获取所有API
     *
     * @return
     */
    public Map<String, CsapDocModelController> scannerApi(String className) {
        Set<Class<?>> aClass = ApidocClazzUtils.getClass(StrUtil.isNotEmpty(className) ? Lists.newArrayList(className) : null, true);
        if (aClass == null) {
            aClass = Sets.newHashSet();
        }
        if (StrUtil.isEmpty(className) && CollectionUtil.isNotEmpty(enableApidocConfig.getFindlClazz())) {
            aClass.addAll(enableApidocConfig.getFindlClazz());
        }
        return ApidocOptional.ofNullable(aClass)
                             .orElse(Collections.emptySet())
                             .stream()
                             .map(this::getControllerModel)
                             .filter(Objects::nonNull)
                             .collect(Collectors.toMap(CsapDocModelController::getName, i -> i));
    }

    @SneakyThrows
    public Map<String, CsapDocMethod> getAllMethod(String classController) {
        if (StrUtil.isEmpty(classController)) {
            return Collections.emptyMap();
        }
        return getAllMethod(Class.forName(classController));
    }

    /**
     * 获取所有类下的接口方法
     *
     * @param aClass class类名
     * @return 返回可用方法
     */
    @SneakyThrows
    public Map<String, CsapDocMethod> getAllMethod(Class<?> aClass) {
        if (aClass == null) {
            return Collections.emptyMap();
        }
        return Stream.of(aClass.getMethods())
                     .filter(i -> !i.isBridge())
                     .filter(this::filterMethod)
                     .map(method -> {
                         ApiOperation apiOperation = AnnotatedElementUtils.findMergedAnnotation(method, ApiOperation.class);
                         boolean ab = apiOperation == null;
                         return ApiDocService.forMapping(method, CsapDocMethod.builder()
                                                                              .hidden(!ab && apiOperation.hidden())
                                                                              .description(ab ? null : apiOperation.description())
                                                                              .name(method.getName())
                                                                              .request(request(method))
                                                                              .response(response(method.getGenericReturnType()))
                                                                              .methods(Lists.newArrayList())
                                                                              .group(ab || GROUP.containsAll(Arrays.asList(apiOperation.group())) ? GROUP : set(apiOperation.group()))
                                                                              .version(ab || VERSION.containsAll(Arrays.asList(apiOperation.version())) ? VERSION : set(apiOperation.version()))
                                                                              .tags(ab ? null : apiOperation.tags())
                                                                              .search(new HashSet<>())
                                                                              .value(ab ? null : apiOperation.value())
                                                                              .status(ab ? ApiStatus.DEFAULT : apiOperation.status())
                                                                              .paramTypes(Sets.newHashSet())
                                                                              .paramType(apiOperation == null ? ParamType.DEFAULT : apiOperation.paramType())
                                                                              .methodHeaders(Lists.newArrayList())
                                                                              .build());
                     })
                     .collect(Collectors.toMap(CsapDocMethod::getName, i -> i));

    }


    /**
     * 添加方法API参数
     *
     * @param methodModel 参数
     * @param request     是否请求参数
     * @return 是否添加成功
     */
    @SneakyThrows
    public Boolean addMethodParam(MethodModel methodModel, boolean request) {
        Class<?> aClass = Class.forName(methodModel.getClassName());
        StrategyModel strategyModel = enableApidocConfig.getApiPackageClass().stream()
                                                        .filter(i -> i.getClazz().contains(aClass)).findFirst()
                                                        .orElseThrow();
        if (strategyModel.getParamType().equals(ApiStrategyType.ANNOTATION)) {
            throw ExceptionUtils.mpe("当前不支持注解形式动态配置");
        }
        Assert.hasText(methodModel.getClassName(), "类名称不能为空");
        Assert.hasText(methodModel.getMethodName(), "方法名称不能为空");
        Map<String, Map<String, ParamGroupMethodProperty>> handle = ApidocContext.paramStrategy(
                                                                                         strategyModel.getParamType())
                                                                                 .handle(strategyModel, strategyModel.getParamType(),
                                                                                         enableApidocConfig.getPath(), methodModel.getClassName());
        handle.computeIfAbsent(methodModel.getClassName(), k -> MapUtil
                      .<String, ParamGroupMethodProperty>builder()
                      .put(methodModel.getMethodName(), new ParamGroupMethodProperty())
                      .build())
              .computeIfAbsent(methodModel.getMethodName(), k -> new ParamGroupMethodProperty());
        ParamGroupMethodProperty yamlMethodProperty = handle.get(methodModel.getClassName())
                                                            .get(methodModel.getMethodName());
        if (CollectionUtil.isNotEmpty(methodModel.getRequestParams())) {
            //当前方法另外的参数
            Map<String, ParamGroupMethodProperty.ParamDataValidate> collect = new HashMap<>(16);
            Map<String, ParamGroupMethodProperty.ParamDataValidate> requestParams = methodModel.getRequestParams()
                                                                                               .stream()
                                                                                               .peek(i -> {
                                                                                                   Map<String, ParamGroupMethodProperty.ParamDataValidate> filtered = yamlMethodProperty
                                                                                                           .getRequest()
                                                                                                           .entrySet()
                                                                                                           .stream()
                                                                                                           .filter(i2 -> !i2
                                                                                                                   .getKey()
                                                                                                                   .startsWith(i.getMethodParamName()))
                                                                                                           .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
                                                                                                   collect.putAll(filtered);
                                                                                               })
                                                                                               .flatMap(i -> addParam(i.getFields(), i.getMethodParamName() + StrUtil.DOT,
                                                                                                       yamlMethodProperty.getRequest(), Boolean.FALSE)
                                                                                                       .entrySet()
                                                                                                       .stream())
                                                                                               .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
            collect.putAll(requestParams);
            yamlMethodProperty.setRequest(ParamGroupMethodProperty.sortedKeyLength(collect));
        }
        if (Objects.nonNull(methodModel.getReturnType())) {
            Map<String, ParamGroupMethodProperty.ParamDataValidate> param = addParam(
                    methodModel.getReturnType().getFields(),
                    methodModel.getReturnType().getAppendName(),
                    yamlMethodProperty.getResponse(),
                    Boolean.TRUE);
            yamlMethodProperty.setResponse(ParamGroupMethodProperty.sortedKeyLength(param));
        }

        Map<String, Map<String, ParamGroupMethodProperty>> dataMap = MapUtil.builder(
                methodModel.getClassName(), handle.get(methodModel.getClassName())).build();
        ApidocContext.paramStrategy(strategyModel.getParamType()).writeAndMerge(
                strategyModel, dataMap, strategyModel.getParamType(),
                methodModel.getClassName(), methodModel.getMethodName(), request);
        return Boolean.TRUE;
    }

    /**
     * 添加参数
     *
     * @param paramDataValidateMap 参数公用的map属性
     * @param name                 标识名称
     * @param fields               字段列表
     * @param response             是否返回参数
     */
    private Map<String, ParamGroupMethodProperty.ParamDataValidate> addParam(List<? extends Field<?>> fields,
                                                                             String name,
                                                                             Map<String, ParamGroupMethodProperty.ParamDataValidate> paramDataValidateMap,
                                                                             boolean response) {
        if (CollectionUtil.isEmpty(fields)) {
            return Collections.emptyMap();
        }
        return fields.stream().map(i -> {
            String keyName = name + i.getName();
            Map<String, ParamGroupMethodProperty.ParamDataValidate> paramDataValidateMap2 = new HashMap<>(1);
            paramDataValidateMap2.put(keyName, ParamGroupMethodProperty.ParamDataValidate.builder()
                                                                                         .include(response ? true : null)
                                                                                         .required(i.getRequired())
                                                                                         .validate(response ? null : validate(paramDataValidateMap.get(keyName), (RequestField) i))
                                                                                         .paramType(response ? null : ((RequestField) i).getParamType())
                                                                                         .build());
            if (CollectionUtil.isNotEmpty(i.getParameters())) {
                paramDataValidateMap2.putAll(addParam(i.getParameters(), name + i.getName() + StrUtil.DOT, paramDataValidateMap, response));
            }
            return paramDataValidateMap2;
        }).flatMap(i -> i.entrySet().stream()).collect(Collectors.toMap(
                Map.Entry::getKey,
                Map.Entry::getValue,
                (existing, replacement) -> {
                    // 如果遇到重复的key，记录警告并使用第一个值（保留已存在的值）
                    // 注意：这里existing是value，我们需要从stream中获取key，但合并函数只接收value
                    // 所以我们需要一个临时变量来跟踪key，或者直接返回existing
                    log.warn("检测到重复的字段，将保留第一个值");
                    return existing;
                }
        ));
    }

    /**
     * 处理是否需要重置验证数据
     *
     * @param oldValidate  历史验证
     * @param requestField 请的字段信息
     * @return 验证结果
     */
    private List<Validate.ConstraintValidatorField> validate(ParamGroupMethodProperty.ParamDataValidate oldValidate,
                                                             RequestField requestField) {
        if (log.isDebugEnabled()) {
            log.debug("requestField,{}", requestField);
        }
        if (!requestField.getClickValidate()) {
            return ApidocOptional.ofNullable(oldValidate).map(ParamGroupMethodProperty.ParamDataValidate::getValidate)
                                 .get();
        }
        return requestField.getValidate();
    }

    /**
     * 获取当前方法字段的验证信息
     *
     * @param className  类名
     * @param methodName 方法
     * @return 结果
     */
    @SneakyThrows
    public ParamGroupMethodProperty.ParamDataValidate getMethodValidateFields(String className, String methodName,
                                                                              String fieldName) {
        Class<?> aClass = Class.forName(className);
        StrategyModel strategyModel = enableApidocConfig.filterStrategy(aClass).orElseThrow();
        Map<String, Map<String, ParamGroupMethodProperty>> handle = ApidocContext.paramStrategy(
                                                                                         strategyModel.getParamType())
                                                                                 .handle(strategyModel, strategyModel.getParamType(),
                                                                                         enableApidocConfig.getPath(), className);
        if (handle.containsKey(className) && handle.get(className).containsKey(methodName)) {
            return handle.get(className).get(methodName).getRequest().get(fieldName);
        }
        return null;
    }

    public Boolean hiddenMethod(MethodModel methodModel) {
        return true;
    }

    /**
     * 写入所有的接口文档
     *
     * @return
     */
    public StandardProperties writeSelectController() {
        StandardProperties yamlProperties = new StandardProperties();
        yamlProperties.setMethodMap(new HashMap<>(16));
        yamlProperties.setControllerMap(new HashMap<>(16));
        scannerApi(null).forEach((k, v) -> {
            yamlProperties.getControllerMap().put(k, v);
            Map<String, CsapDocMethod> allMethod = getAllMethod(v.getName());
            if (CollectionUtil.isNotEmpty(allMethod)) {
                yamlProperties.getMethodMap().put(k, allMethod);
                v.setMethods(allMethod.keySet());
            }
        });
        return yamlProperties;
    }

    /**
     * 根据当前选中的class生成api
     *
     * @param className
     * @return
     */
    public StandardProperties writeSelectController(Set<String> className) {
        if (CollectionUtil.isEmpty(className)) {
            throw ExceptionUtils.mpe("class名称未传");
        }
        StandardProperties yamlProperties = new StandardProperties();
        yamlProperties.setControllerMap(new HashMap<>(16));
        className.forEach(i -> {
            try {
                Class<?> cl = Class.forName(i);
                CsapDocModelController csapDocModelController = getControllerModel(cl);
                yamlProperties.getControllerMap().put(csapDocModelController.getName(), csapDocModelController);
                Map<String, CsapDocMethod> methodMap = getAllMethod(cl);
                if (CollectionUtil.isNotEmpty(methodMap)) {
                    yamlProperties.getMethodMap().put(csapDocModelController.getName(), methodMap);
                    csapDocModelController.setMethods(methodMap.keySet());
                }
                yamlProperties.getControllerMap().put(csapDocModelController.getName(), csapDocModelController);
            } catch (ClassNotFoundException e) {
                log.error(e.getMessage(), e);
            }
        });
        return yamlProperties;
    }

}
