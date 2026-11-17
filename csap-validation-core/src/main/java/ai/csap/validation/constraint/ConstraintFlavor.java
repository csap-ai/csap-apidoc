package ai.csap.validation.constraint;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.time.Clock;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.util.ClassUtils;

/**
 * 一套 Bean Validation API(jakarta.validation 或 javax.validation)的反射入口.
 *
 * <p>csap-validation-core 同时运行在 Spring Boot 2(javax.validation + Hibernate Validator 6)
 * 和 Spring Boot 3(jakarta.validation + Hibernate Validator 8)上,两套 API 任一套缺失都不能导致类加载失败,
 * 所以这里只按类名反射访问 {@code ConstraintValidator},不在方法签名里出现任何一套的类型。
 *
 * @author ycf
 * @since 1.2.0
 */
public final class ConstraintFlavor {
    /**
     * jakarta.validation(Jakarta EE 9+,Spring Boot 3)
     */
    public static final String JAKARTA = "jakarta.validation";
    /**
     * javax.validation(Java EE,Spring Boot 2)
     */
    public static final String JAVAX = "javax.validation";

    private static final Map<String, Optional<ConstraintFlavor>> CACHE = new ConcurrentHashMap<>(4);

    private final String packageName;
    private final Class<?> validatorInterface;
    private final Method initialize;
    private final Method isValid;
    private final Object context;

    private ConstraintFlavor(String packageName, Class<?> validatorInterface, Class<?> contextInterface) throws NoSuchMethodException {
        this.packageName = packageName;
        this.validatorInterface = validatorInterface;
        this.initialize = validatorInterface.getMethod("initialize", Annotation.class);
        this.isValid = validatorInterface.getMethod("isValid", Object.class, contextInterface);
        this.context = lenientProxy(contextInterface);
    }

    /**
     * 按包名取一套 API,classpath 中没有时返回 null.
     *
     * @param packageName {@link #JAKARTA} 或 {@link #JAVAX}
     * @param classLoader 类加载器
     * @return API 入口
     */
    public static ConstraintFlavor of(String packageName, ClassLoader classLoader) {
        return CACHE.computeIfAbsent(packageName, p -> {
            try {
                Class<?> validator = ClassUtils.forName(p + ".ConstraintValidator", classLoader);
                Class<?> context = ClassUtils.forName(p + ".ConstraintValidatorContext", classLoader);
                return Optional.of(new ConstraintFlavor(p, validator, context));
            } catch (ClassNotFoundException | LinkageError | NoSuchMethodException e) {
                return Optional.empty();
            }
        }).orElse(null);
    }

    /**
     * 注解属于哪一套约束体系:被 {@code jakarta.validation.Constraint} 或 {@code javax.validation.Constraint} 标注.
     *
     * @param annotationType 注解类型
     * @return 包名,不是约束注解时返回 null
     */
    public static String packageOf(Class<? extends Annotation> annotationType) {
        Annotation constraint = constraintAnnotation(annotationType);
        if (constraint == null) {
            return null;
        }
        String name = constraint.annotationType().getName();
        return name.substring(0, name.length() - ".Constraint".length());
    }

    /**
     * 取注解上的 {@code @Constraint} 元注解.
     *
     * @param annotationType 注解类型
     * @return 元注解,没有时返回 null
     */
    public static Annotation constraintAnnotation(Class<? extends Annotation> annotationType) {
        for (Annotation meta : annotationType.getAnnotations()) {
            String name = meta.annotationType().getName();
            if ((JAKARTA + ".Constraint").equals(name) || (JAVAX + ".Constraint").equals(name)) {
                return meta;
            }
        }
        return null;
    }

    public String getPackageName() {
        return packageName;
    }

    public Class<?> getValidatorInterface() {
        return validatorInterface;
    }

    /**
     * 是否为本套 API 的 ConstraintValidator 实现
     *
     * @param validatorClass 校验器类
     * @return 是否匹配
     */
    public boolean accepts(Class<?> validatorClass) {
        return validatorInterface.isAssignableFrom(validatorClass);
    }

    void initialize(Object validator, Annotation annotation) throws ReflectiveOperationException {
        initialize.invoke(validator, annotation);
    }

    boolean isValid(Object validator, Object value) throws ReflectiveOperationException {
        return (Boolean) isValid.invoke(validator, value, context);
    }

    /**
     * 宽松的上下文代理:校验器只能拿到时钟、构建违规信息等无副作用的调用,
     * 返回接口的方法继续返回代理,{@code getClock()} 返回系统时钟,其他返回默认值.
     */
    private static Object lenientProxy(Class<?> type) {
        return Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (proxy, method, args) -> {
            Class<?> returnType = method.getReturnType();
            if ("unwrap".equals(method.getName()) && args != null && args.length == 1 && args[0] instanceof Class
                    && ((Class<?>) args[0]).isInterface()) {
                return lenientProxy((Class<?>) args[0]);
            }
            switch (method.getName()) {
                case "toString":
                    return "LenientConstraintValidatorContext";
                case "hashCode":
                    return System.identityHashCode(proxy);
                case "equals":
                    return proxy == args[0];
                default:
                    break;
            }
            if (Clock.class.equals(returnType)) {
                return Clock.systemDefaultZone();
            }
            if (returnType.isInterface()) {
                return lenientProxy(returnType);
            }
            if (boolean.class.equals(returnType)) {
                return false;
            }
            if (returnType.isPrimitive() && !void.class.equals(returnType)) {
                return java.lang.reflect.Array.get(java.lang.reflect.Array.newInstance(returnType, 1), 0);
            }
            return null;
        });
    }
}
