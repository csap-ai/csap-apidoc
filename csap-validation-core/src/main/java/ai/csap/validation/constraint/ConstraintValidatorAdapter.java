package ai.csap.validation.constraint;

import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 已初始化的 Bean Validation 校验器(jakarta 或 javax 均可),通过反射调用 {@code isValid}.
 *
 * <p>校验器抛出异常(例如依赖完整校验上下文的自定义校验器)时按通过处理并记一次 warn,
 * 避免校验组件自身的问题让正常请求失败。
 *
 * @author ycf
 * @since 1.2.0
 */
public final class ConstraintValidatorAdapter {
    private static final Logger LOG = LoggerFactory.getLogger(ConstraintValidatorAdapter.class);
    private static final Set<Class<?>> WARNED = ConcurrentHashMap.newKeySet();

    private final Object validator;
    private final ConstraintFlavor flavor;
    private final String constraint;

    private ConstraintValidatorAdapter(Object validator, ConstraintFlavor flavor, String constraint) {
        this.validator = validator;
        this.flavor = flavor;
        this.constraint = constraint;
    }

    /**
     * 实例化并初始化校验器
     *
     * @param validatorClass 校验器类
     * @param flavor         所属 API
     * @param annotation     约束注解(与校验器同一套 API)
     * @return 适配器
     * @throws ReflectiveOperationException 无法实例化或初始化
     */
    public static ConstraintValidatorAdapter create(Class<?> validatorClass, ConstraintFlavor flavor,
                                                    Annotation annotation) throws ReflectiveOperationException {
        java.lang.reflect.Constructor<?> constructor = validatorClass.getDeclaredConstructor();
        if (!constructor.canAccess(null)) {
            constructor.setAccessible(true);
        }
        Object validator = constructor.newInstance();
        flavor.initialize(validator, annotation);
        return new ConstraintValidatorAdapter(validator, flavor, annotation.annotationType().getName());
    }

    /**
     * 校验
     *
     * @param value 值
     * @return 是否通过
     */
    public boolean isValid(Object value) {
        try {
            return flavor.isValid(validator, value);
        } catch (InvocationTargetException e) {
            warnOnce(e.getTargetException());
            return true;
        } catch (ReflectiveOperationException | RuntimeException e) {
            warnOnce(e);
            return true;
        }
    }

    private void warnOnce(Throwable e) {
        if (WARNED.add(validator.getClass())) {
            LOG.warn("constraint validator {} for @{} failed and was skipped: {}",
                    validator.getClass().getName(), constraint, e.toString());
        }
    }

    /**
     * @return 约束注解的类名
     */
    public String getConstraint() {
        return constraint;
    }

    /**
     * @return 校验器的类名
     */
    public String getValidatorClass() {
        return validator.getClass().getName();
    }

    /**
     * @return 所属 API 的包名
     */
    public String getApi() {
        return flavor.getPackageName();
    }

    /**
     * @return 校验器实例
     */
    public Object validatorInstance() {
        return validator;
    }

    @Override
    public String toString() {
        return "ConstraintValidatorAdapter{" + constraint + " -> " + getValidatorClass() + '}';
    }
}
