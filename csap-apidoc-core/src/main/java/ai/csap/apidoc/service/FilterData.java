package ai.csap.apidoc.service;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.web.method.HandlerMethod;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

import ai.csap.apidoc.annotation.ApiProperty;
import ai.csap.apidoc.annotation.ApiPropertys;
import ai.csap.apidoc.annotation.EnumMessage;
import ai.csap.apidoc.annotation.EnumValue;
import ai.csap.apidoc.core.ModelTypepProperties;
import ai.csap.apidoc.model.CsapDocMethod;
import ai.csap.apidoc.model.CsapDocModel;
import ai.csap.apidoc.model.CsapDocParameter;
import ai.csap.apidoc.type.ModelType;
import ai.csap.apidoc.util.ApidocClazzUtils;
import ai.csap.apidoc.util.TypeVariableModel;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.util.ArrayUtil;

/**
 * @author yangchengfu
 * @description
 * @dataTime 2021年-01月-22日 15:32:00
 **/
public interface FilterData {
    ParameterNameDiscoverer PARAMETER_NAME_DISCOVERER = new DefaultParameterNameDiscoverer();
    /**
     * 默认分组ClassRead
     */
    Set<String> GROUP = Sets.newHashSet("default");
    /**
     * 默认版本
     */
    Set<String> VERSION = Sets.newHashSet("default");
    String ENUM_CODE_NAME = "code";
    String ENUM_MESSAGE_NAME = "message";

    /**
     * 统一set处理
     *
     * @param group 处理的数据
     * @return 返回唯一的数据
     */
    default Set<String> set(String[] group, Set<String> set) {
        Set<String> set1 = ConcurrentHashMap.newKeySet();
        set1.addAll(Arrays.asList(group));
        if (GROUP.containsAll(set1) && CollectionUtil.isNotEmpty(set)) {
            set1 = set;
        }
        return set1;
    }

    default Set<String> set(String[] group) {
        return set(group, null);
    }

    /**
     * 过滤对应表
     *
     * @param cl     api属性
     * @param string 表名称
     * @return true/false
     */
    default boolean filterTable(Class<?> cl, String string) {
        return cl.getName().equals(string);
    }

    /**
     * 方法返回数据类型【基本数据类型处理方案】
     *
     * @param typepProperties 模型属性
     * @param docMethod       文档方法
     * @return 结果
     */
    default CsapDocModel methodReturnBasicType(ModelTypepProperties typepProperties, CsapDocMethod docMethod) {
        return CsapDocModel.builder()
                           .modelType(ModelType.BASE_DATA)
                           .name(typepProperties.getCl().getName())
                           .parameters(Lists.newArrayList(CsapDocParameter.builder()
                                                                          .dataType(typepProperties.getSubStr())
                                                                          .name(typepProperties.getSubStr())
                                                                          .required(true)
                                                                          .group(docMethod.getGroup())
                                                                          .version(docMethod.getVersion())
                                                                          .value(typepProperties.getSubStr())
                                                                          .build()))
                           .build();
    }

    /**
     * 获取单属性匹配的注解（用于方法级注解匹配）
     * 方法级使用时，name 属性用于匹配参数名
     * 参数级使用时，应直接通过 Parameter.getAnnotation() 获取，不经过此方法
     *
     * @param apiProperty 文档注解
     * @param name        参数名称
     * @return 匹配的文档注解，如果不匹配或为空则返回 null
     */
    default ApiProperty apiProperty(ApiProperty apiProperty, String name) {
        if (apiProperty == null || apiProperty.hidden()) {
            return null;
        }
        // 方法级注解必须指定 name 来匹配参数
        // 如果 name 为空，说明可能是参数级注解，不应该通过此方法匹配
        if (apiProperty.name() == null || apiProperty.name().isEmpty()) {
            return null;
        }
        if (name.equals(apiProperty.name())) {
            return apiProperty;
        }
        return null;
    }

    /**
     * 处理枚举类型数据，提取带 @EnumValue 和 @EnumMessage 注解的字段值
     * <p>
     * 例如对于枚举:
     * <pre>{@code
     * public enum ProductStatus {
     *     PENDING_REVIEW(1, "待审核");
     *
     *     @EnumValue
     *     private final Integer code;
     *
     *     @EnumMessage
     *     private final String description;
     * }
     * }</pre>
     * 将返回: [{"name": "PENDING_REVIEW", "code": "1", "description": "待审核"}, ...]
     * </p>
     *
     * @param type 枚举类型
     * @return 枚举实际数据，键为字段名，值为字段值的字符串表示
     */
    default List<Map<String, String>> enumData(Class<?> type) {
        if (type == null || !Enum.class.isAssignableFrom(type)) {
            return Lists.newArrayList();
        }

        // 找出所有带注解的实例字段并预处理访问器（性能优化：避免重复反射）
        List<EnumFieldAccessor> fieldAccessors = Arrays.stream(type.getDeclaredFields())
                                                       .filter(f -> !f.isEnumConstant())  // 排除枚举常量
                                                       .filter(f -> !java.lang.reflect.Modifier.isStatic(f.getModifiers()))  // 排除静态字段
                                                       .filter(f -> f.isAnnotationPresent(EnumValue.class) || f.isAnnotationPresent(EnumMessage.class))
                                                       .map(field -> createFieldAccessor(type, field))
                                                       .filter(java.util.Objects::nonNull)
                                                       .collect(java.util.stream.Collectors.toList());

        // 直接将枚举常量数组转换为 List<Map<String, String>>
        Enum<?>[] enumConstants = (Enum<?>[]) type.getEnumConstants();
        return Arrays.stream(enumConstants)
                     .map(enumConstant -> {
                         Map<String, String> dataMap = new HashMap<>();
                         // 添加枚举常量名称
                         dataMap.put("name", enumConstant.name());

                         // 提取每个注解字段的值（使用预处理的访问器，性能更好）
                         fieldAccessors.forEach(accessor -> {
                             String value = accessor.getValue(enumConstant);
                             if (value != null) {
                                 dataMap.put(accessor.isCode ? ENUM_CODE_NAME : ENUM_MESSAGE_NAME, value);
                             }
                         });
                         return dataMap;
                     })
                     .filter(map -> !map.isEmpty())  // 过滤只有 name 的 map（说明没有有效字段）
                     .collect(java.util.stream.Collectors.toList());
    }

    /**
     * 创建字段访问器（预处理反射对象，提升性能）
     *
     * @param enumType 枚举类型
     * @param field    字段
     * @return 字段访问器，失败返回 null
     */
    default EnumFieldAccessor createFieldAccessor(Class<?> enumType, Field field) {
        // 优先尝试使用 getter 方法
        boolean isCode = field.isAnnotationPresent(EnumValue.class);
        try {
            Method getter = ApidocClazzUtils.getMethod(enumType, field);
            getter.setAccessible(true);  // 预先设置，避免每次检查
            return new EnumFieldAccessor(field.getName(), getter, null, isCode);
        } catch (Exception e) {
            // Getter 方法不可用，尝试直接字段访问
        }

        // 备用方案：直接字段访问
        try {
            field.setAccessible(true);  // 预先设置
            return new EnumFieldAccessor(field.getName(), null, field, isCode);
        } catch (Exception e) {
            System.err.printf(
                    "Warning: Cannot create accessor for field '%s' in enum '%s': %s%n",
                    field.getName(), enumType.getSimpleName(), e.getMessage()
            );
            return null;
        }
    }

    /**
     * 枚举字段访问器（内部类，用于缓存反射对象）
     */
    class EnumFieldAccessor {
        final String fieldName;
        final Method getter;      // 优先使用
        final Field field;        // 备用方案
        final Boolean isCode;

        EnumFieldAccessor(String fieldName, Method getter, Field field, Boolean isCode) {
            this.fieldName = fieldName;
            this.getter = getter;
            this.field = field;
            this.isCode = isCode;
        }

        /**
         * 获取字段值
         *
         * @param enumConstant 枚举常量
         * @return 字段值的字符串表示
         */
        String getValue(Enum<?> enumConstant) {
            try {
                Object value;
                if (getter != null) {
                    value = getter.invoke(enumConstant);
                } else if (field != null) {
                    value = field.get(enumConstant);
                } else {
                    return null;
                }
                return value != null ? value.toString() : "";
            } catch (Exception e) {
                return null;
            }
        }
    }

    /**
     * 过滤字段具体类型
     *
     * @param types             类型列表
     * @param field             字段
     * @param typeVariableModel 泛型对象
     * @return 模型实际属性
     */
    default ModelTypepProperties getFildType(List<Type> types, Field field, TypeVariableModel typeVariableModel) {
        Type type = field.getGenericType();
        if (CollectionUtil.isNotEmpty(types)) {
            if (type instanceof TypeVariable || type instanceof ParameterizedType) {
                TypeVariable<? extends Class<?>>[] typeVariables = field.getDeclaringClass().getTypeParameters();
                if (ArrayUtil.isNotEmpty(typeVariables)) {
                    int x = 0;
                    Type[] typeVariable = new Type[]{type};
                    if (type instanceof ParameterizedType) {
                        typeVariable = ((ParameterizedType) type).getActualTypeArguments();
                    }
                    if (typeVariable.length == 1 && typeVariable[0] instanceof Class) {
                        return ApidocClazzUtils.getRawClassType(typeVariable[0], typeVariableModel);
                    }

                    for (int i = 0; i < typeVariables.length; i++) {
                        if (typeVariables[i].getName().equals(typeVariable[i].getTypeName())) {
                            x = i;
                            break;
                        }
                    }
                    Type type1 = types.get(x);
                    if (type instanceof ParameterizedType) {
                        //处理泛型且明确类型
                        if (type1 instanceof Class) {
                            return ApidocClazzUtils.getRawClassType(type, typeVariableModel, type1);
                        } else if (type1 instanceof TypeVariable) {
                            //处理泛型不明确类型,泛型使用父类泛型
                            return ApidocClazzUtils.getRawClassType(type, typeVariableModel, type1);
                        }
                    }
                    return ApidocClazzUtils.getRawClassType(type1, typeVariableModel);
                }
            }
        }
        return ApidocClazzUtils.getRawClassType(type, typeVariableModel);
    }

    /**
     * 获取键
     *
     * @param docParameter 文档字段
     * @return 键
     */
    default String getKey(CsapDocParameter docParameter) {
        return System.nanoTime() + Integer.toHexString(System.identityHashCode(docParameter));
    }
}
