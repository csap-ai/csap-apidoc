package ai.csap.apidoc.devtools.util;

import java.lang.reflect.GenericArrayType;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import ai.csap.apidoc.annotation.ApiModel;
import ai.csap.apidoc.annotation.ApiModelProperty;
import ai.csap.apidoc.core.ModelTypepProperties;
import ai.csap.apidoc.devtools.model.api.Field;
import ai.csap.apidoc.util.ApidocClazzUtils;

import lombok.extern.slf4j.Slf4j;

/**
 * 泛型类型工具类
 * 用于完美支持泛型字段的获取和解析
 *
 * <p>主要功能：</p>
 * <ul>
 *   <li>从方法的泛型参数中提取实际的类型参数</li>
 *   <li>解析泛型字段的实际类型（支持嵌套泛型）</li>
 *   <li>构建类型变量到实际类型的映射</li>
 * </ul>
 *
 * @author ycf
 * @date 2025/01/XX
 */
@Slf4j
public class GenericTypeUtil {

    /**
     * 从给定的类型中解析泛型信息，并获取所有字段
     *
     * @param type 目标类型（可能包含泛型）
     * @param excludedFields 需要排除的字段集合
     * @param parentName 父字段名称前缀
     * @param typePathStack 类型路径栈，用于检测循环引用
     * @return 字段列表
     */
    public static List<Field> getFieldsWithGenericType(Type type, Set<String> excludedFields, String parentName, Set<String> typePathStack) {
        // 解析泛型类型，构建类型变量映射
        GenericTypeContext context = buildGenericTypeContext(type);
        return getFieldsWithGenericTypeInternal(type, excludedFields, parentName, typePathStack, context);
    }

    /**
     * 内部方法：从给定的类型中解析泛型信息，并获取所有字段
     *
     * @param type 目标类型（可能包含泛型）
     * @param excludedFields 需要排除的字段集合
     * @param parentName 父字段名称前缀
     * @param typePathStack 类型路径栈，用于检测循环引用
     * @param context 泛型类型上下文
     * @return 字段列表
     */
    private static List<Field> getFieldsWithGenericTypeInternal(Type type, Set<String> excludedFields, String parentName, Set<String> typePathStack, GenericTypeContext context) {
        // 获取原始类
        Class<?> rawClass = getRawClass(type);
        if (rawClass == null || !rawClass.isAnnotationPresent(ApiModel.class)) {
            return Collections.emptyList();
        }

        // 无论类型是什么，都需要从继承链中构建完整的泛型上下文
        // 因为即使当前类型是 ParameterizedType，其父类的泛型参数可能也需要解析
        GenericTypeContext inheritanceContext = buildGenericTypeContextFromClass(rawClass);
        context.merge(inheritanceContext);

        if (log.isDebugEnabled()) {
            log.debug("处理类型: {}, 泛型上下文映射数量: {}", rawClass.getName(), context.getTypeVariableCount());
        }

        // 初始化类型路径栈
        if (typePathStack == null) {
            typePathStack = new HashSet<>();
        }
        Set<String> newTypePathStack = new HashSet<>(typePathStack);
        String currentTypeName = rawClass.getName();

        // 检测循环引用
        if (newTypePathStack.contains(currentTypeName)) {
            if (log.isDebugEnabled()) {
                log.debug("检测到循环引用，停止递归: {} -> {}", String.join(" -> ", newTypePathStack), currentTypeName);
            }
            return Collections.emptyList();
        }
        newTypePathStack.add(currentTypeName);

        // 获取所有字段（包括父类）
        List<java.lang.reflect.Field> allFields = ApidocClazzUtils.getAllFields(rawClass)
                .stream()
                .filter(field -> field.isAnnotationPresent(ApiModelProperty.class))
                .filter(field -> !isExcluded(field.getName(), parentName, excludedFields))
                .collect(Collectors.toList());

        List<Field> result = new ArrayList<>();

        for (java.lang.reflect.Field field : allFields) {
            // 解析字段的实际类型（处理泛型）
            Type fieldActualType = resolveFieldType(field, context);

            // 创建字段对象
            Field fieldObj = createField(field, fieldActualType, parentName);

            // 处理子字段：支持实体类、集合类型和数组类型
            Type elementType = extractElementType(fieldActualType, context);
            if (elementType != null) {
                // 提取出元素类型（可能是集合的元素类型或数组的元素类型）
                // 需要再次解析类型变量，因为元素类型可能是 TypeVariable（如 T）
                ModelTypepProperties elementProps = ApidocClazzUtils.getRawClassType(elementType);
                if (elementProps.getCl() != null && elementProps.getCl().isAnnotationPresent(ApiModel.class)) {
                    // 为子字段构建泛型上下文，合并父级的上下文
                    GenericTypeContext childContext = buildGenericTypeContext(elementType);
                    childContext.merge(context); // 合并父级的泛型上下文

                    List<Field> children = getFieldsWithGenericTypeInternal(
                            elementType,
                            excludedFields,
                            buildFieldPath(parentName, field.getName()),
                            newTypePathStack,
                            childContext
                    );
                    if (!children.isEmpty()) {
                        fieldObj.setChildrenField((List) children);
                    }
                }
            } else {
                // 如果不是集合或数组，检查字段类型本身是否是实体类
                ModelTypepProperties fieldProps = ApidocClazzUtils.getRawClassType(fieldActualType);
                if (fieldProps.getCl() != null && fieldProps.getCl().isAnnotationPresent(ApiModel.class)) {
                    // 为子字段构建泛型上下文，合并父级的上下文
                    GenericTypeContext childContext = buildGenericTypeContext(fieldActualType);
                    childContext.merge(context); // 合并父级的泛型上下文

                    List<Field> children = getFieldsWithGenericTypeInternal(
                            fieldActualType,
                            excludedFields,
                            buildFieldPath(parentName, field.getName()),
                            newTypePathStack,
                            childContext
                    );
                    if (!children.isEmpty()) {
                        fieldObj.setChildrenField((List) children);
                    }
                }
            }

            result.add(fieldObj);
        }

        return result;
    }

    /**
     * 构建泛型类型上下文
     * 从给定的类型中提取类型变量到实际类型的映射
     * 包括从类的继承链（父类和接口）中解析泛型类型
     *
     * @param type 目标类型
     * @return 泛型类型上下文
     */
    private static GenericTypeContext buildGenericTypeContext(Type type) {
        GenericTypeContext context = new GenericTypeContext();

        if (type instanceof ParameterizedType) {
            ParameterizedType parameterizedType = (ParameterizedType) type;
            Type rawType = parameterizedType.getRawType();

            if (rawType instanceof Class) {
                Class<?> clazz = (Class<?>) rawType;
                TypeVariable<?>[] typeParameters = clazz.getTypeParameters();
                Type[] actualTypeArguments = parameterizedType.getActualTypeArguments();

                // 构建当前类的类型变量映射
                for (int i = 0; i < typeParameters.length && i < actualTypeArguments.length; i++) {
                    TypeVariable<?> typeVar = typeParameters[i];
                    Type actualType = actualTypeArguments[i];
                    context.addTypeVariableMapping(typeVar.getName(), actualType);

                    // 如果实际类型也是参数化类型，递归处理
                    if (actualType instanceof ParameterizedType) {
                        GenericTypeContext nestedContext = buildGenericTypeContext(actualType);
                        context.merge(nestedContext);
                    }
                }

                // 从继承链中解析泛型类型（父类和接口）
                buildGenericTypeContextFromInheritance(clazz, parameterizedType, context);
            }
        } else if (type instanceof Class) {
            // 即使类型本身不是 ParameterizedType，也可能需要从继承链中解析
            Class<?> clazz = (Class<?>) type;
            buildGenericTypeContextFromInheritance(clazz, null, context);
        }

        return context;
    }

    /**
     * 从类的完整继承链中构建泛型类型上下文
     * 专门用于处理普通 Class 类型，从其父类和接口中提取泛型映射
     *
     * <p>例如：如果类 UserRequest 继承 BaseRequest<User>，则会提取出 T -> User 的映射</p>
     *
     * @param clazz 目标类
     * @return 泛型类型上下文
     */
    private static GenericTypeContext buildGenericTypeContextFromClass(Class<?> clazz) {
        GenericTypeContext context = new GenericTypeContext();

        if (clazz == null || clazz == Object.class) {
            return context;
        }

        // 遍历整个继承链
        Class<?> currentClass = clazz;
        while (currentClass != null && currentClass != Object.class) {
            // 处理当前类的父类
            Type genericSuperclass = currentClass.getGenericSuperclass();
            if (genericSuperclass instanceof ParameterizedType) {
                ParameterizedType superPt = (ParameterizedType) genericSuperclass;
                Type superRawType = superPt.getRawType();

                if (superRawType instanceof Class) {
                    Class<?> superClass = (Class<?>) superRawType;
                    TypeVariable<?>[] superTypeParams = superClass.getTypeParameters();
                    Type[] actualArgs = superPt.getActualTypeArguments();

                    for (int i = 0; i < superTypeParams.length && i < actualArgs.length; i++) {
                        String typeVarName = superTypeParams[i].getName();
                        Type actualType = actualArgs[i];

                        // 如果实际类型是 TypeVariable，尝试从已有映射中解析
                        if (actualType instanceof TypeVariable) {
                            Type resolved = context.resolveTypeVariable(((TypeVariable<?>) actualType).getName());
                            if (resolved != null) {
                                actualType = resolved;
                            }
                        }

                        // 只有当映射不存在时才添加（避免覆盖更具体的类型）
                        if (context.resolveTypeVariable(typeVarName) == null) {
                            context.addTypeVariableMapping(typeVarName, actualType);

                            if (log.isDebugEnabled()) {
                                log.debug("从类 {} 的父类 {} 中提取泛型映射: {} -> {}",
                                    currentClass.getSimpleName(), superClass.getSimpleName(),
                                    typeVarName, actualType);
                            }
                        }
                    }
                }
            }

            // 处理当前类实现的接口
            Type[] genericInterfaces = currentClass.getGenericInterfaces();
            for (Type genericInterface : genericInterfaces) {
                if (genericInterface instanceof ParameterizedType) {
                    ParameterizedType interfacePt = (ParameterizedType) genericInterface;
                    Type interfaceRawType = interfacePt.getRawType();

                    if (interfaceRawType instanceof Class) {
                        Class<?> interfaceClass = (Class<?>) interfaceRawType;
                        TypeVariable<?>[] interfaceTypeParams = interfaceClass.getTypeParameters();
                        Type[] actualArgs = interfacePt.getActualTypeArguments();

                        for (int i = 0; i < interfaceTypeParams.length && i < actualArgs.length; i++) {
                            String typeVarName = interfaceTypeParams[i].getName();
                            Type actualType = actualArgs[i];

                            // 如果实际类型是 TypeVariable，尝试从已有映射中解析
                            if (actualType instanceof TypeVariable) {
                                Type resolved = context.resolveTypeVariable(((TypeVariable<?>) actualType).getName());
                                if (resolved != null) {
                                    actualType = resolved;
                                }
                            }

                            // 只有当映射不存在时才添加
                            if (context.resolveTypeVariable(typeVarName) == null) {
                                context.addTypeVariableMapping(typeVarName, actualType);

                                if (log.isDebugEnabled()) {
                                    log.debug("从类 {} 的接口 {} 中提取泛型映射: {} -> {}",
                                        currentClass.getSimpleName(), interfaceClass.getSimpleName(),
                                        typeVarName, actualType);
                                }
                            }
                        }
                    }
                }
            }

            // 继续处理父类
            currentClass = currentClass.getSuperclass();
        }

        return context;
    }

    /**
     * 从类的继承链中构建泛型类型上下文
     * 处理父类和实现的接口中的泛型类型映射
     *
     * @param clazz 当前类
     * @param parameterizedType 参数化类型（如果是 ParameterizedType），否则为 null
     * @param context 要填充的上下文
     */
    private static void buildGenericTypeContextFromInheritance(Class<?> clazz, ParameterizedType parameterizedType, GenericTypeContext context) {
        // 处理父类
        Type genericSuperclass = clazz.getGenericSuperclass();
        if (genericSuperclass != null && genericSuperclass != Object.class) {
            if (genericSuperclass instanceof ParameterizedType) {
                ParameterizedType superParameterizedType = (ParameterizedType) genericSuperclass;
                Type superRawType = superParameterizedType.getRawType();

                if (superRawType instanceof Class) {
                    Class<?> superClass = (Class<?>) superRawType;
                    TypeVariable<?>[] superTypeParameters = superClass.getTypeParameters();
                    Type[] superActualArguments = superParameterizedType.getActualTypeArguments();

                    // 构建父类的类型变量映射
                    for (int i = 0; i < superTypeParameters.length && i < superActualArguments.length; i++) {
                        TypeVariable<?> superTypeVar = superTypeParameters[i];
                        Type superActualType = superActualArguments[i];

                        // 如果父类的实际类型参数是类型变量，尝试从当前类的上下文中解析
                        if (superActualType instanceof TypeVariable) {
                            TypeVariable<?> typeVar = (TypeVariable<?>) superActualType;
                            // 尝试从当前类的类型参数中查找
                            if (parameterizedType != null) {
                                Class<?> currentClass = (Class<?>) parameterizedType.getRawType();
                                TypeVariable<?>[] currentTypeParams = currentClass.getTypeParameters();
                                Type[] currentActualArgs = parameterizedType.getActualTypeArguments();

                                for (int j = 0; j < currentTypeParams.length && j < currentActualArgs.length; j++) {
                                    if (typeVar.getName().equals(currentTypeParams[j].getName())) {
                                        context.addTypeVariableMapping(superTypeVar.getName(), currentActualArgs[j]);
                                        break;
                                    }
                                }
                            }
                        } else {
                            // 直接映射：父类的类型变量 -> 子类指定的实际类型
                            context.addTypeVariableMapping(superTypeVar.getName(), superActualType);

                            // 如果实际类型是参数化类型，递归处理
                            if (superActualType instanceof ParameterizedType) {
                                GenericTypeContext nestedContext = buildGenericTypeContext(superActualType);
                                context.merge(nestedContext);
                            }
                        }
                    }

                    // 递归处理父类的继承链
                    buildGenericTypeContextFromInheritance(superClass, superParameterizedType, context);
                }
            } else if (genericSuperclass instanceof Class) {
                // 递归处理非参数化的父类
                buildGenericTypeContextFromInheritance((Class<?>) genericSuperclass, null, context);
            }
        }

        // 处理实现的接口
        Type[] genericInterfaces = clazz.getGenericInterfaces();
        for (Type genericInterface : genericInterfaces) {
            if (genericInterface instanceof ParameterizedType) {
                ParameterizedType interfaceParameterizedType = (ParameterizedType) genericInterface;
                Type interfaceRawType = interfaceParameterizedType.getRawType();

                if (interfaceRawType instanceof Class) {
                    Class<?> interfaceClass = (Class<?>) interfaceRawType;
                    TypeVariable<?>[] interfaceTypeParameters = interfaceClass.getTypeParameters();
                    Type[] interfaceActualArguments = interfaceParameterizedType.getActualTypeArguments();

                    // 构建接口的类型变量映射
                    for (int i = 0; i < interfaceTypeParameters.length && i < interfaceActualArguments.length; i++) {
                        TypeVariable<?> interfaceTypeVar = interfaceTypeParameters[i];
                        Type interfaceActualType = interfaceActualArguments[i];

                        // 如果接口的实际类型参数是类型变量，尝试从当前类的上下文中解析
                        if (interfaceActualType instanceof TypeVariable) {
                            TypeVariable<?> typeVar = (TypeVariable<?>) interfaceActualType;
                            if (parameterizedType != null) {
                                Class<?> currentClass = (Class<?>) parameterizedType.getRawType();
                                TypeVariable<?>[] currentTypeParams = currentClass.getTypeParameters();
                                Type[] currentActualArgs = parameterizedType.getActualTypeArguments();

                                for (int j = 0; j < currentTypeParams.length && j < currentActualArgs.length; j++) {
                                    if (typeVar.getName().equals(currentTypeParams[j].getName())) {
                                        context.addTypeVariableMapping(interfaceTypeVar.getName(), currentActualArgs[j]);
                                        break;
                                    }
                                }
                            }
                        } else {
                            context.addTypeVariableMapping(interfaceTypeVar.getName(), interfaceActualType);

                            // 如果实际类型是参数化类型，递归处理
                            if (interfaceActualType instanceof ParameterizedType) {
                                GenericTypeContext nestedContext = buildGenericTypeContext(interfaceActualType);
                                context.merge(nestedContext);
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * 解析字段的实际类型
     * 如果字段的类型是泛型变量，则从上下文中获取实际类型
     *
     * @param field 字段对象
     * @param context 泛型类型上下文
     * @return 解析后的实际类型
     */
    private static Type resolveFieldType(java.lang.reflect.Field field, GenericTypeContext context) {
        Type fieldGenericType = field.getGenericType();

        // 如果字段类型是 TypeVariable，尝试从上下文中解析
        if (fieldGenericType instanceof TypeVariable) {
            TypeVariable<?> typeVar = (TypeVariable<?>) fieldGenericType;
            Type resolvedType = context.resolveTypeVariable(typeVar.getName());
            if (resolvedType != null) {
                return resolvedType;
            }
        }

        // 如果字段类型是 ParameterizedType，需要替换其中的类型变量
        if (fieldGenericType instanceof ParameterizedType) {
            ParameterizedType parameterizedType = (ParameterizedType) fieldGenericType;
            Type[] actualTypeArguments = parameterizedType.getActualTypeArguments();
            Type[] resolvedArguments = new Type[actualTypeArguments.length];
            boolean changed = false;

            for (int i = 0; i < actualTypeArguments.length; i++) {
                Type arg = actualTypeArguments[i];

                // 处理通配符泛型
                if (arg instanceof WildcardType) {
                    WildcardType wildcardType = (WildcardType) arg;
                    Type[] upperBounds = wildcardType.getUpperBounds();
                    Type[] lowerBounds = wildcardType.getLowerBounds();

                    // 优先使用上界（extends），如果没有上界则使用下界（super）
                    if (upperBounds.length > 0 && upperBounds[0] != Object.class) {
                        arg = upperBounds[0];
                        changed = true;
                    } else if (lowerBounds.length > 0) {
                        arg = lowerBounds[0];
                        changed = true;
                    } else {
                        // 无界通配符 ?，使用 Object 类型
                        resolvedArguments[i] = Object.class;
                        changed = true;
                        continue;
                    }
                }

                if (arg instanceof TypeVariable) {
                    TypeVariable<?> typeVar = (TypeVariable<?>) arg;
                    Type resolved = context.resolveTypeVariable(typeVar.getName());
                    if (resolved != null) {
                        resolvedArguments[i] = resolved;
                        changed = true;
                    } else {
                        resolvedArguments[i] = arg;
                    }
                } else if (arg instanceof ParameterizedType) {
                    // 递归处理嵌套的泛型类型
                    GenericTypeContext nestedContext = buildGenericTypeContext(arg);
                    nestedContext.merge(context);
                    resolvedArguments[i] = resolveParameterizedType((ParameterizedType) arg, nestedContext);
                    changed = true;
                } else {
                    resolvedArguments[i] = arg;
                }
            }

            if (changed) {
                // 创建新的 ParameterizedType，使用解析后的类型参数
                return new ResolvedParameterizedType(
                        parameterizedType.getRawType(),
                        resolvedArguments,
                        parameterizedType.getOwnerType()
                );
            }
        }

        return fieldGenericType;
    }

    /**
     * 递归解析参数化类型中的类型变量
     *
     * @param parameterizedType 参数化类型
     * @param context 泛型类型上下文
     * @return 解析后的类型
     */
    private static Type resolveParameterizedType(ParameterizedType parameterizedType, GenericTypeContext context) {
        Type[] actualTypeArguments = parameterizedType.getActualTypeArguments();
        Type[] resolvedArguments = new Type[actualTypeArguments.length];
        boolean changed = false;

        for (int i = 0; i < actualTypeArguments.length; i++) {
            Type arg = actualTypeArguments[i];

            // 处理通配符泛型
            if (arg instanceof WildcardType) {
                WildcardType wildcardType = (WildcardType) arg;
                Type[] upperBounds = wildcardType.getUpperBounds();
                Type[] lowerBounds = wildcardType.getLowerBounds();

                // 优先使用上界（extends），如果没有上界则使用下界（super）
                if (upperBounds.length > 0 && upperBounds[0] != Object.class) {
                    arg = upperBounds[0];
                    changed = true;
                } else if (lowerBounds.length > 0) {
                    arg = lowerBounds[0];
                    changed = true;
                } else {
                    // 无界通配符 ?，使用 Object 类型
                    resolvedArguments[i] = Object.class;
                    changed = true;
                    continue;
                }
            }

            if (arg instanceof TypeVariable) {
                TypeVariable<?> typeVar = (TypeVariable<?>) arg;
                Type resolved = context.resolveTypeVariable(typeVar.getName());
                if (resolved != null) {
                    resolvedArguments[i] = resolved;
                    changed = true;
                } else {
                    resolvedArguments[i] = arg;
                }
            } else if (arg instanceof ParameterizedType) {
                GenericTypeContext nestedContext = buildGenericTypeContext(arg);
                nestedContext.merge(context);
                resolvedArguments[i] = resolveParameterizedType((ParameterizedType) arg, nestedContext);
                changed = true;
            } else {
                resolvedArguments[i] = arg;
            }
        }

        if (changed) {
            return new ResolvedParameterizedType(
                    parameterizedType.getRawType(),
                    resolvedArguments,
                    parameterizedType.getOwnerType()
            );
        }

        return parameterizedType;
    }

    /**
     * 提取元素类型
     * 从集合类型（List、Set）或数组类型中提取元素类型
     * Map 类型不处理，因为还有 key
     *
     * @param type 字段类型
     * @param context 泛型类型上下文，用于解析类型变量
     * @return 元素类型，如果不是集合或数组则返回 null
     */
    private static Type extractElementType(Type type, GenericTypeContext context) {
        if (type instanceof ParameterizedType) {
            ParameterizedType parameterizedType = (ParameterizedType) type;
            Type rawType = parameterizedType.getRawType();

            // 检查是否是集合类型（List、Set、Collection），排除 Map
            if (rawType instanceof Class) {
                Class<?> clazz = (Class<?>) rawType;

                // 排除 Map 类型
                if (java.util.Map.class.isAssignableFrom(clazz)) {
                    return null;
                }

                if (ApidocClazzUtils.LIST.contains(clazz.getName())) {
                    // 获取泛型参数（元素类型）
                    Type[] actualTypeArguments = parameterizedType.getActualTypeArguments();
                    if (actualTypeArguments.length > 0) {
                        Type elementType = actualTypeArguments[0];

                        // 处理通配符泛型
                        if (elementType instanceof WildcardType) {
                            WildcardType wildcardType = (WildcardType) elementType;
                            Type[] upperBounds = wildcardType.getUpperBounds();
                            Type[] lowerBounds = wildcardType.getLowerBounds();

                            // 优先使用上界（extends），如果没有上界则使用下界（super）
                            if (upperBounds.length > 0 && upperBounds[0] != Object.class) {
                                elementType = upperBounds[0];
                            } else if (lowerBounds.length > 0) {
                                elementType = lowerBounds[0];
                            } else {
                                // 无界通配符 ?，无法确定类型，返回 null
                                return null;
                            }
                        }

                        // 如果元素类型是类型变量，尝试从上下文中解析
                        if (elementType instanceof TypeVariable) {
                            TypeVariable<?> typeVar = (TypeVariable<?>) elementType;
                            Type resolved = context.resolveTypeVariable(typeVar.getName());
                            if (resolved != null) {
                                return resolved;
                            }
                        }

                        // 如果元素类型是参数化类型，需要递归解析其中的类型变量
                        if (elementType instanceof ParameterizedType) {
                            return resolveParameterizedType((ParameterizedType) elementType, context);
                        }

                        return elementType;
                    }
                }
            }
        } else if (type instanceof GenericArrayType) {
            // 处理泛型数组类型，如 List<String>[], T[] 等
            GenericArrayType genericArrayType = (GenericArrayType) type;
            Type componentType = genericArrayType.getGenericComponentType();

            // 如果组件类型是类型变量，尝试从上下文中解析
            if (componentType instanceof TypeVariable) {
                TypeVariable<?> typeVar = (TypeVariable<?>) componentType;
                Type resolved = context.resolveTypeVariable(typeVar.getName());
                if (resolved != null) {
                    return resolved;
                }
            }

            return componentType;
        } else if (type instanceof Class) {
            Class<?> clazz = (Class<?>) type;
            // 处理普通数组类型，如 String[], int[] 等
            if (clazz.isArray()) {
                return clazz.getComponentType();
            }
        }

        return null;
    }

    /**
     * 获取类型的原始类
     *
     * @param type 类型
     * @return 原始类
     */
    private static Class<?> getRawClass(Type type) {
        if (type instanceof Class) {
            return (Class<?>) type;
        } else if (type instanceof ParameterizedType) {
            Type rawType = ((ParameterizedType) type).getRawType();
            if (rawType instanceof Class) {
                return (Class<?>) rawType;
            }
        }
        return null;
    }

    /**
     * 创建字段对象
     *
     * @param field 反射字段对象
     * @param actualType 实际类型
     * @param parentName 父字段名称前缀
     * @return 字段对象
     */
    private static Field createField(java.lang.reflect.Field field, Type actualType, String parentName) {
        ApiModelProperty apiModelProperty = field.getAnnotation(ApiModelProperty.class);
        ModelTypepProperties modelTypepProperties = ApidocClazzUtils.getRawClassType(actualType);

        Field fieldObj = Field.builder()
                .name(field.getName())
                .dataType(modelTypepProperties.getSubStr())
                .childrenField(new ArrayList<>())
                .modelType(modelTypepProperties.getModelType())
                .build();

        fieldObj.setId(UUID.randomUUID().toString());
        fieldObj.setKey((parentName != null && !parentName.isEmpty() ? parentName + "." : "") + field.getName());
        fieldObj.setPackageName(field.getType().getName());
        fieldObj.setSimpleName(field.getType().getSimpleName());
        fieldObj.setFinalClassName(modelTypepProperties.getCl().getName());

        if (apiModelProperty != null) {
            fieldObj.setValue(apiModelProperty.value());
            fieldObj.setExample(apiModelProperty.example());
            fieldObj.setDefaultValue(apiModelProperty.defaultValue());
            fieldObj.setDescription(apiModelProperty.description());
        } else {
            fieldObj.setValue(field.getType().getSimpleName());
        }

        return fieldObj;
    }

    /**
     * 检查字段是否被排除
     *
     * @param fieldName 字段名
     * @param parentName 父字段名称前缀
     * @param excludedFields 排除的字段集合
     * @return true 如果字段被排除
     */
    private static boolean isExcluded(String fieldName, String parentName, Set<String> excludedFields) {
        if (excludedFields == null || excludedFields.isEmpty()) {
            return false;
        }
        String fullPath = (parentName != null && !parentName.isEmpty() ? parentName + "." : "") + fieldName;
        return excludedFields.contains(fullPath);
    }

    /**
     * 构建字段路径
     *
     * @param parentName 父字段名称前缀
     * @param fieldName 字段名
     * @return 完整的字段路径
     */
    private static String buildFieldPath(String parentName, String fieldName) {
        if (parentName == null || parentName.isEmpty()) {
            return fieldName;
        }
        return parentName + "." + fieldName;
    }

    /**
     * 泛型类型上下文
     * 用于存储类型变量到实际类型的映射
     */
    private static final class GenericTypeContext {
        private final Map<String, Type> typeVariableMap = new HashMap<>();

        /**
         * 添加类型变量映射
         *
         * @param typeVarName 类型变量名
         * @param actualType 实际类型
         */
        public void addTypeVariableMapping(String typeVarName, Type actualType) {
            typeVariableMap.put(typeVarName, actualType);
        }

        /**
         * 解析类型变量
         *
         * @param typeVarName 类型变量名
         * @return 实际类型，如果未找到则返回 null
         */
        public Type resolveTypeVariable(String typeVarName) {
            return typeVariableMap.get(typeVarName);
        }

        /**
         * 合并另一个上下文
         * 只添加当前上下文中不存在的映射，并且跳过值为 TypeVariable 的映射
         * 这样可以保留已经解析的具体类型，避免被未解析的 TypeVariable 覆盖
         *
         * @param other 另一个上下文
         */
        public void merge(GenericTypeContext other) {
            if (other != null) {
                for (Map.Entry<String, Type> entry : other.typeVariableMap.entrySet()) {
                    String key = entry.getKey();
                    Type otherValue = entry.getValue();
                    Type currentValue = typeVariableMap.get(key);

                    // 如果当前没有这个映射，直接添加
                    if (currentValue == null) {
                        typeVariableMap.put(key, otherValue);
                    } else {
                        // 如果当前值是 TypeVariable，而新值不是，用新值替换
                        // 这样可以用更具体的类型替换未解析的类型变量
                        if (currentValue instanceof TypeVariable && !(otherValue instanceof TypeVariable)) {
                            typeVariableMap.put(key, otherValue);
                        }
                        // 否则保留当前已解析的具体类型
                    }
                }
            }
        }

        /**
         * 获取类型变量映射的数量
         *
         * @return 映射数量
         */
        public int getTypeVariableCount() {
            return typeVariableMap.size();
        }

        /**
         * 获取所有类型变量映射（用于调试）
         *
         * @return 类型变量映射的副本
         */
        public Map<String, Type> getTypeVariableMap() {
            return new HashMap<>(typeVariableMap);
        }
    }

    /**
     * 已解析的参数化类型实现
     * 用于表示已替换类型变量的参数化类型
     */
    private static class ResolvedParameterizedType implements ParameterizedType {
        private final Type rawType;
        private final Type[] actualTypeArguments;
        private final Type ownerType;

        ResolvedParameterizedType(Type rawType, Type[] actualTypeArguments, Type ownerType) {
            this.rawType = rawType;
            this.actualTypeArguments = actualTypeArguments.clone(); // 创建副本以避免外部修改
            this.ownerType = ownerType;
        }

        @Override
        public Type[] getActualTypeArguments() {
            return actualTypeArguments.clone(); // 返回副本以避免外部修改
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
        public String toString() {
            StringBuilder sb = new StringBuilder();
            if (rawType instanceof Class) {
                sb.append(((Class<?>) rawType).getName());
            } else {
                sb.append(rawType);
            }

            if (actualTypeArguments.length > 0) {
                sb.append("<");
                for (int i = 0; i < actualTypeArguments.length; i++) {
                    if (i > 0) {
                        sb.append(", ");
                    }
                    sb.append(actualTypeArguments[i]);
                }
                sb.append(">");
            }

            return sb.toString();
        }
    }
}
