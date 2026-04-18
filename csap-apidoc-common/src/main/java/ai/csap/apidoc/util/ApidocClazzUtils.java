package ai.csap.apidoc.util;

import static cn.hutool.core.text.CharSequenceUtil.upperFirst;

import java.io.File;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.invoke.MethodHandle;
import java.lang.reflect.Field;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.JarURLConnection;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.util.CollectionUtils;
import org.springframework.util.ConcurrentReferenceHashMap;
import org.springframework.web.multipart.MultipartFile;

import com.alibaba.fastjson.JSONObject;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

import ai.csap.apidoc.core.ModelTypepProperties;
import ai.csap.apidoc.type.FieldMethodHandleType;
import ai.csap.apidoc.type.ModelType;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.util.ArrayUtil;
import lombok.SneakyThrows;

/**
 * Class反射工具类
 * 提供全面的Class操作、字段处理、类型解析等反射功能
 *
 * <p>主要功能：</p>
 * <ul>
 *   <li>类扫描：扫描指定包下的所有类</li>
 *   <li>字段处理：获取类的所有字段（包括父类），支持缓存机制</li>
 *   <li>类型解析：解析泛型类型、处理TypeVariable等复杂类型</li>
 *   <li>方法处理：获取getter/setter方法，支持MethodHandle</li>
 * </ul>
 *
 * @author yangchengfu
 * @dataTime 2019年-12月-27日 15:02:00
 **/
public final class ApidocClazzUtils {
    /**
     * 基本数据类型与包装类型映射表
     * 用于基本类型（int、long等）与包装类型（Integer、Long等）的转换
     */
    public static final Map<String, Class<?>> BASIC_DATA_TYPE_MAP = new HashMap<>();

    /**
     * Class文件后缀
     */
    private static final String CLASS_SUFFIX = ".class";

    /**
     * 编译后的class文件路径前缀
     */
    private static final String CLASS_FILE_PREFIX = File.separator + "classes" + File.separator;

    /**
     * 测试类编译后的路径前缀
     */
    private static final String TEST_CLASS_FILE_PREFIX = File.separator + "test-classes" + File.separator;

    /**
     * 包名分隔符
     */
    private static final String PACKAGE_SEPARATOR = ".";

    /**
     * 泛型右尖括号
     */
    private static final String RIGHT_CHEV = ">";

    /**
     * 泛型左尖括号
     */
    private static final String LEFT_CHEV = "<";

    /**
     * 类字段缓存
     * 使用弱引用缓存，避免内存泄漏
     * key: Class对象, value: 字段名到Field对象的映射
     */
    private static final Map<Class<?>, Map<String, Field>> DECLARED_FIELDS_CACHE = new ConcurrentReferenceHashMap<>(256);

    /**
     * 集合类型全限定名列表
     * 用于判断一个类型是否为集合类型
     */
    public static final List<String> LIST = Arrays.asList(List.class.getName(), Set.class.getName(), Collection.class.getName());

    /**
     * Java常规数据类型列表
     * 包括所有基本类型、包装类型、时间类型和BigDecimal等
     * 用于判断一个类型是否为基础数据类型
     */
    public static final List<String> DATA_TYPE = Arrays.asList(
            String.class.getName(), Byte.class.getName(), Short.class.getName(),
            Integer.class.getName(), Long.class.getName(), Float.class.getName(),
            Double.class.getName(), Character.class.getName(), Boolean.class.getName(), BigInteger.class.getName(), BigDecimal.class.getName(),
            Date.class.getName(), LocalTime.class.getName(), Timestamp.class.getName(), LocalDateTime.class.getName(), LocalDate.class.getName(),
            "byte", "short", "int",
            "long", "float", "double", "char", "boolean");

    /**
     * 其他特殊数据类型列表
     * 如文件上传类型MultipartFile
     */
    public static final List<String> OTHER_DATA_TYPE = Arrays.asList(MultipartFile.class.getName());

    /**
     * 验证时需要排除的类型列表
     * 这些类型不需要进行参数验证，如Model、HttpServletRequest等
     */
    public static final List<String> VALIDATE_EXCLUDE_MAP = Arrays.asList(
            "org.springframework.ui.Model",
            "javax.servlet.http.HttpServletRequest",
            "javax.servlet.http.HttpServletResponse");

    /**
     * 泛型类型匹配正则表达式
     * 正则表达式说明：
     * (\w+) - 匹配类型名（字母、数字、下划线组成）
     * {@literal <.+>} - 贪婪匹配尖括号之间的泛型参数（包括嵌套泛型）
     * 注意：使用贪婪匹配以正确处理 {@code Map<K,V>} 等多参数泛型
     * 示例：{@code List<String>} -&gt; 匹配"List"，{@code Map<K,V>} -&gt; 匹配"Map"
     */
    private static final Pattern TYPE_VARIABLE_PATTERN = Pattern.compile("(\\w+)<.+>");

    /**
     * 通用对象类型列表
     * 用于判断是否为Object、Map或JSONObject等通用类型
     */
    public static final List<String> OBJ = Arrays.asList(JSONObject.class.getName(), Map.class.getName(), Object.class.getName());

    static {
        BASIC_DATA_TYPE_MAP.put("byte", Byte.class);
        BASIC_DATA_TYPE_MAP.put("short", Short.class);
        BASIC_DATA_TYPE_MAP.put("int", Integer.class);
        BASIC_DATA_TYPE_MAP.put("long", Long.class);
        BASIC_DATA_TYPE_MAP.put("float", Float.class);
        BASIC_DATA_TYPE_MAP.put("double", Double.class);
        BASIC_DATA_TYPE_MAP.put("char", Character.class);
        BASIC_DATA_TYPE_MAP.put("boolean", Boolean.class);
    }

    /**
     * 处理反射字段方法类型
     */
    private static final Map<Class<?>, Map<String, Map<FieldMethodHandleType, MethodHandle>>> DECLARED_FIELDS_HANDLE_CACHE = new ConcurrentReferenceHashMap<>(256);

    public static ModelTypepProperties getRawClassType(Type t) {
        return getRawClassType(t, null);
    }

    /**
     * 将类型字符串中的泛型参数替换为自定义字符串
     * 保留主类型名称，替换尖括号内的泛型部分
     *
     * <p>使用示例：</p>
     * <pre>{@code
     * replaceGenericType("List<String>", "MyType")  -> "List<MyType>"
     * replaceGenericType("Map<K,V>", "Object")     -> "Map<Object>"
     * }</pre>
     *
     * @param input       原始类型字符串，如{@code "List<String>"}
     * @param replacement 用于替换泛型的新类型名称
     * @return 替换后的类型字符串
     */
    public static String replaceGenericType(String input, String replacement) {
        // $1 作为捕获组引用，保留类型名
        // 只对 replacement 进行 quoteReplacement，避免其中的特殊字符被解释
        return TYPE_VARIABLE_PATTERN.matcher(input).replaceAll("$1<" + java.util.regex.Matcher.quoteReplacement(replacement) + ">");
    }

    /**
     * 获取所有字段包括父类
     *
     * @param object
     * @return
     */
    public static List<Field> getAllFields(Object object) {
        return object != null ? getAllFields(object.getClass()) : null;
    }

    /**
     * 获取所有字段
     *
     * @param clazz  目标对象
     * @param supper 是否获取父类的字段
     * @return
     */
    public static List<Field> getAllFields(Class<?> clazz, boolean supper) {
        return Lists.newArrayList(initCacheFieldMap(clazz, supper).values());
    }

    public static boolean containsList(Class<?> cl) {
        List<Class<?>> cs = Optional.of(cl.getInterfaces()).map(Lists::newArrayList).orElseGet(ArrayList::new);
        if (!CollectionUtils.isEmpty(cs)) {
            for (Class<?> c : cs) {
                if (LIST.contains(c.getName())) {
                    return true;
                }
            }
        }
        return false;
    }

    public static String getGetterName(String name, Class<?> type) {
        if (type.equals(Boolean.TYPE)) {
            return name.startsWith("is") ? name : "is" + upperFirst(name);
        } else {
            return "get" + upperFirst(name);
        }
    }

    public static Method getMethod(Class<?> cls, Field field) {
        try {
            return cls.getDeclaredMethod(guessGetterName(field, field.getName()));
        } catch (NoSuchMethodException e) {
            throw new RuntimeException(String.format("Error: NoSuchMethod in %s.  Cause:", cls.getName()), e);
        }
    }

    private static String guessGetterName(Field field, final String str) {
        return getGetterName(str, field.getType());
    }

    private static String getSetterName(Field field, final String str) {
        return getSetterName(str, field.getType());
    }

    public static String getSetterName(String name, Class<?> type) {
        return "set" + upperFirst(name);
    }

    /**
     * 初始化并获取类的字段缓存映射
     * 使用缓存机制提升性能，避免重复反射
     *
     * <p>处理流程：</p>
     * <ol>
     *   <li>检查缓存，若不存在则创建并缓存</li>
     *   <li>若需要处理父类，则递归获取父类字段直到Object</li>
     *   <li>将所有字段合并到一个Map中返回</li>
     * </ol>
     *
     * @param clazz  目标类对象
     * @param supper 是否递归获取父类字段，true表示获取父类字段
     * @return 字段名到Field对象的映射表
     */
    public static Map<String, Field> initCacheFieldMap(Class<?> clazz, boolean supper) {
        Map<String, Field> map = new HashMap<>(16);
        // 递归处理当前类及其父类
        while (clazz != null) {
            // 若缓存中不存在，则创建缓存
            if (!DECLARED_FIELDS_CACHE.containsKey(clazz)) {
                DECLARED_FIELDS_CACHE.put(clazz, Stream.of(clazz.getDeclaredFields()).collect(Collectors.toConcurrentMap(Field::getName, i -> i)));
            }
            // 将当前类的字段添加到结果集
            map.putAll(DECLARED_FIELDS_CACHE.get(clazz));

            // 根据supper参数决定是否继续处理父类
            if (supper) {
                clazz = clazz.getSuperclass();
                // 处理到Object类时停止
                if (Objects.isNull(clazz) || clazz.equals(Object.class)) {
                    clazz = null;
                }
            } else {
                clazz = null;
            }
        }
        return map;
    }

    public static MethodHandle getFieldHandleType(FieldMethodHandleType type, Class<?> cls, Field field) {
        return DECLARED_FIELDS_HANDLE_CACHE.computeIfAbsent(cls, i -> new ConcurrentReferenceHashMap<>(256))
                .computeIfAbsent(field.getName(), i -> new ConcurrentReferenceHashMap<>(16))
                .computeIfAbsent(type, i -> i.handle(field));
    }

    /**
     * 根据字段名称查询字段对象
     *
     * @param clazz  类型
     * @param name   名称
     * @param supper 是否处理父类
     * @return 字段结果
     */
    public static Field findField(Class<?> clazz, String name, boolean supper) {
        return initCacheFieldMap(clazz, supper).getOrDefault(name, null);
    }


    /**
     * 根据字段名称查询字段对象
     *
     * @param clazz 类型
     * @param name  名称
     * @return 字段结果
     */
    public static Field findField(Class<?> clazz, String name) {
        return findField(clazz, name, true);
    }

    /**
     * 获取所有字段
     *
     * @param clazz 获取字段目标对象
     * @return 结果
     */
    public static List<Field> getAllFields(Class<?> clazz) {
        return getAllFields(clazz, true);
    }

    public static List<Field> getFields(String className) {
        try {
            return getAllFields(ApidocClazzUtils.getRawClassType(Class.forName(className)).getCl(), false);
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
            return Collections.emptyList();
        }
    }

    /**
     * 获取当前类字段
     *
     * @param clazz
     * @return
     */
    public static List<Field> getFields(Class clazz) {
        return getAllFields(clazz, false);
    }

    /**
     * 获取类型 字符串 包括泛型
     *
     * @param t
     * @return
     */
    public static Set<String> getTypeStringReplace(Type t, Set<String> typeString, TypeVariableModel variableModel) {
        if (t instanceof TypeVariable) {
            t = getTypeVariables((TypeVariable<?>) t, variableModel);
        }
        if (t instanceof ParameterizedType) {
            ParameterizedType parameterizedType = (ParameterizedType) t;
            for (Type type : parameterizedType.getActualTypeArguments()) {
                getTypeStringReplace(type, typeString, variableModel);
            }
            typeString.add(((Class<?>) parameterizedType.getRawType()).getPackage().getName() + PACKAGE_SEPARATOR);
        } else {
            if (!BASIC_DATA_TYPE_MAP.containsKey(((Class<?>) t).getName())) {
                typeString.add(((Class<?>) t).getPackage().getName() + PACKAGE_SEPARATOR);
            }
        }
        return typeString;
    }

    /**
     * 获取泛型或者类型的具体名称--去除包名
     *
     * @param t
     * @return
     */
    public static String getTypeString(Type t, TypeVariableModel variableModel) {
        return getTypeString(t, variableModel, null);
    }

    /**
     * 提取字符串中最后一个"."后面的内容
     * 注意：此方法仅适用于简单类型，不适用于包含泛型的复杂类型
     *
     * @param input 输入字符串
     * @return 最后一个"."后的子串，无"."则返回原字符串
     */
    public static String getLastPartAfterDot(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }
        int lastDotIndex = input.lastIndexOf('.');
        return lastDotIndex == -1 ? input : input.substring(lastDotIndex + 1);
    }

    /**
     * 移除类型字符串中的所有包名，保留类型结构和泛型
     *
     * <p>使用示例：</p>
     * <pre>{@code
     * removePackageNames("java.util.List<com.example.User>")  -> "List<User>"
     * removePackageNames("java.util.Map<java.lang.String, com.example.User>")  -> "Map<String, User>"
     * removePackageNames("java.util.Map<java.lang.String, java.util.List<com.example.User>>")  -> "Map<String, List<User>>"
     * }</pre>
     *
     * @param typeString 包含包名的类型字符串
     * @return 移除包名后的类型字符串
     */
    public static String removePackageNames(String typeString) {
        if (typeString == null || typeString.isEmpty()) {
            return typeString;
        }

        StringBuilder result = new StringBuilder();
        int i = 0;
        int length = typeString.length();

        while (i < length) {
            char ch = typeString.charAt(i);

            // 遇到特殊字符直接保留
            if (ch == '<' || ch == '>' || ch == ',' || ch == ' ' || ch == '[' || ch == ']') {
                result.append(ch);
                i++;
                continue;
            }

            // 尝试读取一个完整的类型名（可能包含包名）
            StringBuilder token = new StringBuilder();

            // 读取到下一个特殊字符或结束
            while (i < length) {
                char c = typeString.charAt(i);
                if (c == '<' || c == '>' || c == ',' || c == ' ' || c == '[' || c == ']') {
                    break;
                }
                token.append(c);
                i++;
            }

            // 处理读取到的 token（如 "java.util.List" 或 "String"）
            String tokenStr = token.toString();
            if (tokenStr.contains(".")) {
                // 包含点号，可能是包名.类名
                int lastDot = tokenStr.lastIndexOf('.');
                String potentialPackage = tokenStr.substring(0, lastDot);
                String className = tokenStr.substring(lastDot + 1);

                // 判断是否为包名：小写字母开头则认为是包名
                if (!potentialPackage.isEmpty() && Character.isLowerCase(potentialPackage.charAt(0))) {
                    // 只保留类名
                    result.append(className);
                } else {
                    // 不是包名（可能是内部类），全部保留
                    result.append(tokenStr);
                }
            } else {
                // 没有点号，直接是类名，全部保留
                result.append(tokenStr);
            }
        }

        return result.toString();
    }

    /**
     * 美化类型名称展示
     * 将所有 $ 符号替换为 . 使其更易读
     *
     * <p>使用示例：</p>
     * <pre>{@code
     * beautifyTypeName("ComplexTestModels$UserInfo")  -> "ComplexTestModels.UserInfo"
     * beautifyTypeName("Outer$Inner$Deep")            -> "Outer.Inner.Deep"
     * beautifyTypeName("Outer$1")                     -> "Outer.1"
     * beautifyTypeName("Outer$1<String>")             -> "Outer.1<String>"
     * beautifyTypeName("List<Outer$UserInfo>")        -> "List<Outer.UserInfo>"
     * beautifyTypeName("String[]")                    -> "String[]"
     * }</pre>
     *
     * @param typeName 原始类型名称
     * @return 美化后的类型名称
     */
    public static String beautifyTypeName(String typeName) {
        if (typeName == null || typeName.isEmpty()) {
            return typeName;
        }

        // 直接将所有 $ 替换为 .
        // 简单粗暴但有效，让类型展示更符合 Java 语法习惯
        return typeName.replace('$', '.');
    }

    /**
     * 获取泛型或者类型的具体名称--去除包名
     *
     * @param t
     * @return
     */
    public static String getTypeString(Type t, TypeVariableModel variableModel, Type typeVariable) {
        // 使用 getTypeName() 而不是 toString()，避免 Class 类型带上 "class " 前缀
        String string = t.getTypeName();

        // 移除所有包名，保留类型结构
        string = removePackageNames(string);
        // 美化内部类名称（$ 替换为 .）
        string = beautifyTypeName(string);

        if (variableModel != null && CollectionUtil.isNotEmpty(variableModel.getTypeVariables())) {
            for (int i = 0; i < variableModel.getTypeVariables().size(); i++) {
                String name = variableModel.getTypeVariables().get(i).getName();
                if (string.length() == 1 && name.equals(string)) {
                    string = name;
                    break;
                } else if (string.contains(LEFT_CHEV + name + RIGHT_CHEV)) {
                    String replacement = removePackageNames(variableModel.getRawType().get(i).getTypeName());
                    replacement = beautifyTypeName(replacement);
                    string = string.replace(LEFT_CHEV + name + RIGHT_CHEV, LEFT_CHEV + replacement + RIGHT_CHEV);
                }
            }
        }
        return string;
    }

    /**
     * 获取最真实的对象类型
     *
     * @param t             类型
     * @param variableModel 当前类的实际类型
     * @return
     */
    @SneakyThrows
    public static ModelTypepProperties getRawClassType(Type t, TypeVariableModel variableModel) {
        return getRawClassType(t, variableModel, null);
    }

    /**
     * 获取最真实的对象类型
     *
     * @param t             类型
     * @param variableModel 当前类的实际类型
     * @return
     */
    @SneakyThrows
    public static ModelTypepProperties getRawClassType(Type t, TypeVariableModel variableModel, Type typeVariable) {
        ModelTypepProperties properties = new ModelTypepProperties();
        properties.setType(t);
        if (t instanceof ParameterizedType) {
            properties.setT(true);
            //泛型 包括对象泛型 ，list,set泛型
            Type[] type = ((ParameterizedType) t).getActualTypeArguments();
            if (Objects.isNull(typeVariable)) {
                typeVariable = type[0];
            }
            Class<?> cls = (Class<?>) ((ParameterizedType) t).getRawType();
            properties.setClList(Collections.singletonList(typeVariable));
            if (ArrayUtil.isNotEmpty(type)) {
                if (typeVariable instanceof Class) {
                    properties.setCl((Class<?>) typeVariable);
                } else if (typeVariable instanceof ParameterizedType) {
                    properties.setCl((Class<?>) ((ParameterizedType) typeVariable).getRawType());
                } else {
                    if (containsList(cls)) {
                        properties.setCl(getTypeVariables((TypeVariable<?>) typeVariable, variableModel));
                    } else {
                        properties.setCl(cls);
                    }
                }
            } else {
                properties.setCl(cls);
            }
            properties.setRawCl((Class<?>) ((ParameterizedType) t).getRawType());
            properties.setType(t);
            properties.setRawType(((ParameterizedType) t).getRawType().getTypeName());
            properties.setModelType(LIST.contains(properties.getRawType()) ? ModelType.ARRAY : ModelType.T_OBJECT);
            // getTypeString 内部已经调用了 beautifyTypeName，无需重复调用
            properties.setSubStr(getTypeString(t, variableModel, typeVariable));
        } else if (t instanceof Class) {
            Class<?> clazz = (Class<?>) t;
            // 检查是否为数组类型
            if (clazz.isArray()) {
                // 处理数组类型
                Class<?> componentType = clazz.getComponentType();
                properties.setCl(componentType);
                properties.setRawCl(clazz);
                properties.setRawType(clazz.getName());
                properties.setModelType(ModelType.ARRAY);

                // 递归获取元素类型的展示名称，然后加上 []
                // 使用 removePackageNames 去除包名，然后美化内部类
                String componentTypeName = removePackageNames(componentType.getName());
                componentTypeName = beautifyTypeName(componentTypeName);
                properties.setSubStr(componentTypeName + "[]");
            } else {
                // 处理普通类型
                properties.setCl(clazz);
                properties.setRawCl(properties.getCl());
                properties.setRawType(properties.getCl().getName());
                properties.setModelType(DATA_TYPE.contains(t.getTypeName()) ? ModelType.BASE_DATA : ModelType.OBJECT);
                // 使用 getName() 而不是 getSimpleName()，以保留内部类的完整名称
                String typeName = removePackageNames(clazz.getName());
                properties.setSubStr(beautifyTypeName(typeName));
            }
        } else if (t instanceof GenericArrayType) {
            // 处理泛型数组类型，如 List<String>[], T[] 等
            GenericArrayType genericArrayType = (GenericArrayType) t;
            Type componentType = genericArrayType.getGenericComponentType();

            // 递归获取元素类型的信息
            ModelTypepProperties componentProps = getRawClassType(componentType, variableModel, typeVariable);

            // 设置数组的属性
            properties.setCl(componentProps.getCl());
            properties.setRawCl(componentProps.getRawCl());
            properties.setRawType(componentProps.getRawType());
            properties.setModelType(ModelType.ARRAY);
            // 在元素类型后面加上 []
            properties.setSubStr(componentProps.getSubStr() + "[]");
            properties.setT(componentProps.isT());
        } else if (t instanceof TypeVariable) {
            if (Objects.nonNull(typeVariable) && typeVariable instanceof TypeVariable) {
                t = typeVariable;
            }
            properties.setCl(getTypeVariables((TypeVariable<?>) t, variableModel));
            properties.setRawCl(properties.getCl());
            properties.setRawType(properties.getCl().getName());
            properties.setModelType(ModelType.OBJECT);
            // 使用 getName() 而不是 getSimpleName()，以保留内部类的完整名称
            String typeName = removePackageNames(properties.getCl().getName());
            properties.setSubStr(beautifyTypeName(typeName));
            properties.setT(true);

        } else {
            throw new ValidateException("未知数据类型尚未处理，请联系开发人员。");
        }
        if (properties.getSubStr() == null) {
            // getTypeString 内部已经调用了 beautifyTypeName，无需重复调用
            properties.setSubStr(getTypeString(t, variableModel));
        }
        return properties;
    }

    /**
     * 根据类型变量名从 TypeVariableModel 中查找实际类型
     *
     * @param typeVariable  类型变量
     * @param variableModel 类型变量模型（包含类型变量到实际类型的映射）
     * @return 实际的 Class 类型，如果无法解析则返回 Object.class
     */
    private static Class<?> getTypeVariables(TypeVariable<?> typeVariable, TypeVariableModel variableModel) {
        // 空值检查：如果 variableModel 为空或其内部列表为空，返回 Object.class 作为默认值
        if (variableModel == null || variableModel.getTypeVariables() == null || variableModel.getRawType() == null) {
            // 无法解析泛型类型变量，返回 Object.class 作为兜底
            return Object.class;
        }
        
        Class<?> cl = null;
        for (int i = 0; i < variableModel.getTypeVariables().size(); i++) {
            TypeVariable<?> tv = variableModel.getTypeVariables().get(i);
            if (tv != null && typeVariable.getName().equals(tv.getName())) {
                Type rawType = variableModel.getRawType().get(i);
                if (rawType instanceof Class) {
                    cl = (Class<?>) rawType;
                } else if (rawType instanceof ParameterizedType) {
                    // 如果实际类型是参数化类型，获取其原始类型
                    cl = (Class<?>) ((ParameterizedType) rawType).getRawType();
                }
                break;
            }
        }
        
        // 如果仍然无法解析，返回 Object.class
        return cl != null ? cl : Object.class;
    }

    /**
     * 查找包下的所有类的名字
     *
     * @param packageNames         包名
     * @param showChildPackageFlag 是否需要显示子包内容
     * @return 结果
     */
    public static List<String> getClazzName(Collection<String> packageNames, boolean showChildPackageFlag) {
        if (CollectionUtil.isEmpty(packageNames)) {
            return Lists.newArrayList();
        }
        List<String> list = new ArrayList<>();
        for (String str : packageNames) {
            list.addAll(getClazzName(str, showChildPackageFlag));
        }
        return list;
    }

    /**
     * 扫描获取反射对象class
     *
     * @param packageNames         扫描包路径
     * @param showChildPackageFlag 是否扫描子包
     * @param predicate            自定义条件
     * @return 结果
     */
    public static Set<Class<?>> getClass(Collection<String> packageNames, boolean showChildPackageFlag, Predicate<Class<?>> predicate) {
        List<String> list = getClazzName(packageNames, showChildPackageFlag);
        if (CollectionUtil.isEmpty(list)) {
            return Sets.newHashSet();
        }
        Set<Class<?>> classes = new HashSet<>();
        for (String str : list) {
            try {
                Class<?> c = Class.forName(str);
                if (predicate == null || predicate.test(c)) {
                    classes.add(c);
                }
            } catch (ClassNotFoundException e) {
                e.printStackTrace();
            }
        }
        return classes;
    }

    /**
     * 扫描获取反射对象class
     *
     * @param packageNames         包名
     * @param showChildPackageFlag 是否需要显示子包内容
     * @return 结果
     */
    public static Set<Class<?>> getClass(Collection<String> packageNames, boolean showChildPackageFlag) {
        return getClass(packageNames, showChildPackageFlag, null);
    }

    /**
     * 查找包下的所有类的名字
     *
     * @param packageName          包名
     * @param showChildPackageFlag 是否需要显示子包内容
     * @return List集合，内容为类的全名
     */
    public static List<String> getClazzName(String packageName, boolean showChildPackageFlag) {
        List<String> result = new ArrayList<>();
        String suffixPath = packageName.replaceAll("\\.", "/");
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        try {
            Enumeration<URL> urls = loader.getResources(suffixPath);
            while (urls.hasMoreElements()) {
                URL url = urls.nextElement();
                if (url != null) {
                    String protocol = url.getProtocol();
                    if ("file".equals(protocol)) {
                        String path = url.getPath();
                        result.addAll(getAllClassNameByFile(new File(URLDecoder.decode(path, StandardCharsets.UTF_8)), showChildPackageFlag));
                    } else if ("jar".equals(protocol)) {
                        JarFile jarFile = null;
                        try {
                            jarFile = ((JarURLConnection) url.openConnection()).getJarFile();
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                        if (jarFile != null) {
                            result.addAll(getAllClassNameByJar(jarFile, packageName, showChildPackageFlag, true));
                        }
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return result;
    }

    private static void add(String path, List<String> result) {
        add(path, result, true);
    }

    /**
     * 添加class文件
     *
     * @param path       文件路径
     * @param result     返回值
     * @param innerClass 是否包含内部类
     */
    private static void add(String path, List<String> result, boolean innerClass) {
        // 注意：这里替换文件分割符要用replace。因为replaceAll里面的参数是正则表达式,而windows环境中File.separator="\\"的,因此会有问题
        if (path.endsWith(CLASS_SUFFIX)) {
            path = path.replace(CLASS_SUFFIX, "");
            // 从"/classes/ 或者 /test-classes/"后面开始截取
            String classPath = path.contains(TEST_CLASS_FILE_PREFIX) ? TEST_CLASS_FILE_PREFIX : CLASS_FILE_PREFIX;
            String clazzName = path.substring(path.indexOf(classPath) + classPath.length())
                    .replace(File.separator, PACKAGE_SEPARATOR);
            if (!clazzName.contains("$") || innerClass) {
                result.add(clazzName);
            }
        }
    }

    /**
     * 递归获取所有class文件的名字
     *
     * @param file
     * @param flag 是否需要迭代遍历
     * @return List
     */
    private static List<String> getAllClassNameByFile(File file, boolean flag) {
        List<String> result = new ArrayList<>();
        if (!file.exists()) {
            return result;
        }
        if (file.isFile()) {
            add(file.getPath(), result);
        } else {
            File[] listFiles = file.listFiles();
            if (ArrayUtil.isNotEmpty(listFiles)) {
                for (File f : listFiles) {
                    if (flag) {
                        result.addAll(getAllClassNameByFile(f, flag));
                    } else {
                        if (f.isFile()) {
                            add(f.getPath(), result);
                        }
                    }
                }
            }
        }
        return result;
    }

    /**
     * 递归获取jar所有class文件的名字
     *
     * @param jarFile
     * @param packageName 包名
     * @param flag        是否需要迭代遍历
     * @return List
     */
    private static List<String> getAllClassNameByJar(JarFile jarFile, String packageName, boolean flag, boolean innerClass) {
        List<String> result = new ArrayList<>();
        Enumeration<JarEntry> entries = jarFile.entries();
        while (entries.hasMoreElements()) {
            JarEntry jarEntry = entries.nextElement();
            String name = jarEntry.getName();
            // 判断是不是class文件
            if (name.endsWith(CLASS_SUFFIX)) {
                name = name.replace(CLASS_SUFFIX, "").replace("/", ".");
                if (flag) {
                    // 如果要子包的文件,那么就只要开头相同且不是内部类就ok
                    if (name.startsWith(packageName) && (!name.contains("$") || innerClass)) {
                        result.add(name);
                    }
                } else {
                    // 如果不要子包的文件,那么就必须保证最后一个"."之前的字符串和包名一样且不是内部类
                    if (packageName.equals(name.substring(0, name.lastIndexOf("."))) && -1 == name.indexOf("$")) {
                        result.add(name);
                    }
                }
            }
        }
        return result;
    }

    /**
     * 获取包含有全部的包路径 类名和方法名
     *
     * @param method
     * @return
     */
    public static String getPackageMethodName(Method method) {
        return method.getDeclaringClass().getName() + "." + method.getName();
    }

    /**
     * 获取只包含类名称和方法名
     *
     * @param method
     * @return
     */
    public static String getClassAndMethodName(Method method) {
        return method.getDeclaringClass().getSimpleName() + "." + method.getName();
    }

    /**
     * 获取类名称和方法名称
     *
     * @param c
     * @param method
     * @return
     */
    public static String getClassAndMethodName(Class c, Method method) {
        return c.getSimpleName() + "." + method.getName();
    }

    /**
     * 获取只有方法名
     *
     * @param method
     * @return
     */
    public static String getMethodName(Method method) {
        return method.getName();
    }

    /**
     * 获取枚举
     *
     * @param clazz
     * @param annotationClass
     * @return
     */
    public static Optional<Field> dealEnumType(Class<?> clazz, Class<? extends Annotation> annotationClass) {
        return clazz.isEnum() ? Arrays.stream(clazz.getDeclaredFields()).filter(field -> field.isAnnotationPresent(annotationClass)).findFirst() : Optional.empty();
    }
}
