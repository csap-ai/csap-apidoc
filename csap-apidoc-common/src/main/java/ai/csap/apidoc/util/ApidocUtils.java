package ai.csap.apidoc.util;

import java.lang.annotation.Annotation;
import java.lang.reflect.Executable;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Objects;

import cn.hutool.core.util.StrUtil;

/**
 * 文档工具类
 * 提供API文档生成过程中的通用工具方法，主要用于路径处理和格式化
 *
 * @Author ycf
 * @Date 2025/8/30 22:37
 * @Version 1.0
 */
public class ApidocUtils {
    /**
     * 路径分隔符常量
     */
    public static final String SLASH = "/";

    /**
     * 从API路径数组中获取第一个路径
     * 在多路径映射中，通常使用第一个路径作为主路径
     *
     * @param apiPath API路径数组，来自@RequestMapping等注解的value或path属性
     * @return 返回第一个路径，如果数组为空则返回空字符串
     */
    public static String apiPath(String[] apiPath) {
        if (Objects.isNull(apiPath) || apiPath.length == 0) {
            return "";
        }
        return apiPath[0];
    }

    /**
     * 格式化路径，确保路径以斜杠开头
     * 统一路径格式，避免路径拼接时出现双斜杠或缺少斜杠的问题
     *
     * @param path 待格式化的路径字符串
     * @return 返回以斜杠开头的路径，如果已有斜杠则直接返回
     */
    public static String formatPath(String path) {
        return StrUtil.isEmpty(path) ? path : (path.startsWith(SLASH) ? path : SLASH + path);
    }

    /**
     * 获取方法参数的注解，支持从接口方法继承
     * <p>
     * 查找顺序：
     * <ul>
     *   <li>1. 实现类方法的参数注解（最高优先级）</li>
     *   <li>2. 接口方法的参数注解（如果实现类没有）</li>
     *   <li>3. 父类方法的参数注解（如果接口也没有）</li>
     * </ul>
     * </p>
     * <p>
     * 注意：Java 的 @Inherited 注解对参数注解无效，因此需要手动实现继承逻辑
     * </p>
     *
     * @param <A>             注解类型
     * @param method          方法对象
     * @param parameterIndex  参数索引（从0开始）
     * @param annotationType  要查找的注解类型
     * @return 找到的注解对象，如果没有找到则返回 null
     *
     * @since 1.0
     */
    public static <A extends Annotation> A getParameterAnnotation(
            Method method,
            int parameterIndex,
            Class<A> annotationType) {

        if (method == null || annotationType == null) {
            return null;
        }

        if (parameterIndex < 0 || parameterIndex >= method.getParameterCount()) {
            return null;
        }

        // 1. 先尝试从当前方法的参数获取注解
        Parameter[] parameters = method.getParameters();
        A annotation = parameters[parameterIndex].getAnnotation(annotationType);
        if (annotation != null) {
            return annotation;
        }

        // 2. 从接口方法查找
        Class<?> declaringClass = method.getDeclaringClass();
        Class<?>[] parameterTypes = method.getParameterTypes();

        // 遍历所有接口
        for (Class<?> iface : declaringClass.getInterfaces()) {
            try {
                Method interfaceMethod = iface.getMethod(method.getName(), parameterTypes);
                annotation = interfaceMethod.getParameters()[parameterIndex].getAnnotation(annotationType);
                if (annotation != null) {
                    return annotation;
                }
            } catch (NoSuchMethodException e) {
                // 接口中没有这个方法，继续查找下一个接口
            }
        }

        // 3. 从父类方法查找
        Class<?> superclass = declaringClass.getSuperclass();
        if (superclass != null && superclass != Object.class) {
            try {
                Method superMethod = superclass.getMethod(method.getName(), parameterTypes);
                annotation = getParameterAnnotation(superMethod, parameterIndex, annotationType);
                if (annotation != null) {
                    return annotation;
                }
            } catch (NoSuchMethodException e) {
                // 父类中没有这个方法
            }
        }

        return null;
    }

    /**
     * 获取参数的注解，支持从接口方法继承
     * <p>
     * 注意：Parameter 对象需要通过 Method.getParameters() 获取，
     * 然后使用 Parameter.getDeclaringExecutable() 来获取所属的方法，
     * 这样才能正确地从接口方法继承注解。
     * </p>
     * <p>
     * 使用 Spring 的 MethodParameter 实现，自动支持：
     * <ul>
     *   <li>实现类方法的参数注解（最高优先级）</li>
     *   <li>接口方法的参数注解（如果实现类没有）</li>
     *   <li>父类方法的参数注解（如果接口也没有）</li>
     * </ul>
     * </p>
     *
     * @param <A>             注解类型
     * @param parameter       参数对象（Java 8+ Parameter）
     * @param annotationType  要查找的注解类型
     * @return 找到的注解对象，如果没有找到则返回 null
     *
     * @see org.springframework.core.MethodParameter#getParameterAnnotation(Class)
     * @since 1.0
     */
    public static <A extends Annotation> A getParameterAnnotation(
            Parameter parameter,
            Class<A> annotationType) {

        if (parameter == null || annotationType == null) {
            return null;
        }

        // 获取参数所属的方法或构造函数
        Executable executable = parameter.getDeclaringExecutable();

        // 只有 Method 才支持接口继承，Constructor 不支持
        if (!(executable instanceof Method)) {
            // 对于构造函数，直接返回参数上的注解
            return parameter.getAnnotation(annotationType);
        }

        Method method = (Method) executable;

        // 找到参数在方法中的索引
        Parameter[] parameters = method.getParameters();
        int parameterIndex = -1;
        for (int i = 0; i < parameters.length; i++) {
            if (parameters[i].equals(parameter)) {
                parameterIndex = i;
                break;
            }
        }

        if (parameterIndex == -1) {
            // 理论上不应该发生
            return parameter.getAnnotation(annotationType);
        }

        // 使用 Method + 索引的方式，这样才能支持接口继承
        return getParameterAnnotation(method, parameterIndex, annotationType);
    }

    /**
     * 检查方法参数是否有指定的注解，支持从接口方法继承
     *
     * @param method          方法对象
     * @param parameterIndex  参数索引（从0开始）
     * @param annotationType  要检查的注解类型
     * @return 如果参数有该注解（包括从接口/父类继承的）返回 true，否则返回 false
     *
     * @see #getParameterAnnotation(Method, int, Class)
     * @since 1.0
     */
    public static <A extends Annotation> boolean hasParameterAnnotation(
            Method method,
            int parameterIndex,
            Class<A> annotationType) {

        return getParameterAnnotation(method, parameterIndex, annotationType) != null;
    }

    /**
     * 检查参数是否有指定的注解，支持从接口方法继承
     *
     * @param parameter       参数对象（Java 8+ Parameter）
     * @param annotationType  要检查的注解类型
     * @return 如果参数有该注解（包括从接口/父类继承的）返回 true，否则返回 false
     *
     * @see #getParameterAnnotation(Parameter, Class)
     * @since 1.0
     */
    public static <A extends Annotation> boolean hasParameterAnnotation(
            Parameter parameter,
            Class<A> annotationType) {

        return getParameterAnnotation(parameter, annotationType) != null;
    }

}
