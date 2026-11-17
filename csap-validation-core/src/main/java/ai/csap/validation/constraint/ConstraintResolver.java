package ai.csap.validation.constraint;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.hibernate.validator.internal.engine.constraintvalidation.ConstraintValidatorDescriptor;
import org.hibernate.validator.internal.metadata.core.ConstraintHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ResolvableType;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.util.ClassUtils;

/**
 * 把字段上的约束注解解析为可执行的校验器,同时支持 jakarta.validation 与 javax.validation.
 *
 * <p>查找校验器的顺序:
 * <ol>
 *     <li>Hibernate Validator 的内置约束表({@link ConstraintHelper}):HV 8 认 jakarta 注解,HV 6 认 javax 注解;</li>
 *     <li>注解上 {@code @Constraint(validatedBy = ...)} 声明的校验器(自定义约束,两套 API 都适用);</li>
 *     <li>以上都没有、且是另一套 API 的内置约束(例如 Spring Boot 3 项目里残留的 {@code javax.validation.constraints.NotNull})时,
 *     换成当前 Hibernate Validator 认识的同名注解再查一次。</li>
 * </ol>
 * 对 HV 内部类 {@link ConstraintHelper} 的调用只用到 HV 6/8 二进制兼容的方法,返回值按擦除后的 {@code Class} 处理。
 *
 * @author ycf
 * @since 1.2.0
 */
public final class ConstraintResolver {
    private static final Logger LOG = LoggerFactory.getLogger(ConstraintResolver.class);
    private static final Set<String> WARNED = ConcurrentHashMap.newKeySet();
    private static final Set<String> SKIPPED_ATTRIBUTES = Set.of("groups", "payload", "message");

    private ConstraintResolver() {
    }

    /**
     * 解析出一个注解对字段类型适用的校验器
     *
     * @param helper     Hibernate Validator 内置约束表,可为 null
     * @param fieldClass 字段类型
     * @param annotation 注解
     * @return 校验器,不是约束注解或没有适用的校验器时返回 null
     */
    public static ConstraintValidatorAdapter resolve(ConstraintHelper helper, Class<?> fieldClass, Annotation annotation) {
        if (fieldClass == null || annotation == null) {
            return null;
        }
        Class<? extends Annotation> annotationType = annotation.annotationType();
        String api = ConstraintFlavor.packageOf(annotationType);
        if (api == null) {
            return null;
        }
        ClassLoader classLoader = annotationType.getClassLoader();
        ConstraintFlavor flavor = ConstraintFlavor.of(api, classLoader);
        Annotation effective = annotation;
        List<Class<?>> candidates = flavor == null ? Collections.emptyList() : validatorClasses(helper, annotationType, flavor);
        if (candidates.isEmpty()) {
            Annotation counterpart = counterpart(annotation, api);
            if (counterpart != null) {
                ConstraintFlavor other = ConstraintFlavor.of(ConstraintFlavor.packageOf(counterpart.annotationType()), classLoader);
                if (other != null) {
                    List<Class<?>> otherCandidates = validatorClasses(helper, counterpart.annotationType(), other);
                    if (!otherCandidates.isEmpty()) {
                        flavor = other;
                        effective = counterpart;
                        candidates = otherCandidates;
                    }
                }
            }
        }
        if (candidates.isEmpty()) {
            warnOnce(annotationType.getName(), "no constraint validator found for @{}, it is skipped at runtime",
                    annotationType.getName());
            return null;
        }
        Class<?> target = ClassUtils.resolvePrimitiveIfNecessary(fieldClass);
        Class<?> best = null;
        Class<?> bestType = null;
        for (Class<?> candidate : candidates) {
            Class<?> type = validatedType(candidate, flavor);
            if (ClassUtils.isAssignable(type, target) && (bestType == null || bestType.isAssignableFrom(type))) {
                best = candidate;
                bestType = type;
            }
        }
        if (best == null) {
            return null;
        }
        try {
            return ConstraintValidatorAdapter.create(best, flavor, effective);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            warnOnce(best.getName(), "cannot initialize constraint validator {}: {}", best.getName(), e.toString());
            return null;
        }
    }

    /**
     * 约束注解的所有候选校验器
     */
    private static List<Class<?>> validatorClasses(ConstraintHelper helper, Class<? extends Annotation> annotationType,
                                                   ConstraintFlavor flavor) {
        List<Class<?>> result = new ArrayList<>();
        if (helper != null) {
            try {
                for (Object descriptor : helper.getAllValidatorDescriptors(annotationType)) {
                    Class<?> validatorClass = ((ConstraintValidatorDescriptor<?>) descriptor).getValidatorClass();
                    if (validatorClass != null && flavor.accepts(validatorClass)) {
                        result.add(validatorClass);
                    }
                }
            } catch (RuntimeException | LinkageError e) {
                // Hibernate Validator 不认识这套 API 的注解(HV 8 + javax,或 HV 6 + jakarta),改用 validatedBy
                LOG.trace("hibernate validator cannot describe @{}: {}", annotationType.getName(), e.toString());
            }
        }
        if (result.isEmpty()) {
            Annotation constraint = ConstraintFlavor.constraintAnnotation(annotationType);
            if (constraint != null) {
                try {
                    Object validatedBy = AnnotationUtils.getValue(constraint, "validatedBy");
                    for (Class<?> validatorClass : (Class<?>[]) validatedBy) {
                        if (flavor.accepts(validatorClass)) {
                            result.add(validatorClass);
                        }
                    }
                } catch (RuntimeException e) {
                    LOG.trace("cannot read validatedBy of @{}: {}", annotationType.getName(), e.toString());
                }
            }
        }
        return result;
    }

    /**
     * 校验器 {@code ConstraintValidator<A, T>} 的 T
     */
    static Class<?> validatedType(Class<?> validatorClass, ConstraintFlavor flavor) {
        Class<?> type = ResolvableType.forClass(validatorClass).as(flavor.getValidatorInterface()).getGeneric(1).resolve();
        return type == null ? Object.class : type;
    }

    /**
     * 另一套 API 中同名的内置约束,属性按名称复制(类型不一致的属性保持默认值)
     */
    static Annotation counterpart(Annotation annotation, String api) {
        String name = annotation.annotationType().getName();
        String prefix = api + ".constraints.";
        if (!name.startsWith(prefix)) {
            return null;
        }
        String otherApi = ConstraintFlavor.JAKARTA.equals(api) ? ConstraintFlavor.JAVAX : ConstraintFlavor.JAKARTA;
        Class<?> otherType;
        try {
            otherType = ClassUtils.forName(otherApi + ".constraints." + name.substring(prefix.length()),
                    annotation.annotationType().getClassLoader());
        } catch (ClassNotFoundException | LinkageError e) {
            return null;
        }
        if (!otherType.isAnnotation()) {
            return null;
        }
        Map<String, Object> attributes = new HashMap<>(8);
        for (Method method : annotation.annotationType().getDeclaredMethods()) {
            if (method.getParameterCount() != 0 || SKIPPED_ATTRIBUTES.contains(method.getName())) {
                continue;
            }
            try {
                Method target = otherType.getMethod(method.getName());
                Object value = AnnotationUtils.getValue(annotation, method.getName());
                if (ClassUtils.isAssignableValue(target.getReturnType(), value)) {
                    attributes.put(method.getName(), value);
                }
            } catch (NoSuchMethodException | RuntimeException e) {
                // 对方没有这个属性或类型不兼容(如 Pattern.flags 的枚举),保持默认值
            }
        }
        try {
            @SuppressWarnings("unchecked")
            Class<Annotation> annotationType = (Class<Annotation>) otherType;
            return AnnotationUtils.synthesizeAnnotation(attributes, annotationType, null);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static void warnOnce(String key, String format, Object... args) {
        if (WARNED.add(key)) {
            LOG.warn(format, args);
        }
    }
}
