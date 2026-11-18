package ai.csap.validation.runtime;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.MethodParameter;

import ai.csap.apidoc.annotation.ApiOperation;
import ai.csap.apidoc.core.ApiMethodHandle;
import ai.csap.apidoc.util.ValidateException;
import ai.csap.validation.ValidateIntercept;
import ai.csap.validation.ValidationViolationException;
import ai.csap.validation.properties.ValidationMode;
import ai.csap.validation.properties.ValidationProperties;

/**
 * 按模式执行运行时参数校验.
 *
 * <ul>
 *     <li>只对带 {@link ApiOperation} 的方法生效;</li>
 *     <li>模式取 {@code csap.validation.endpoints} 中的接口覆盖,否则取全局模式;每个方法的模式只计算一次;</li>
 *     <li>按顺序调用所有 {@link ApiMethodHandle}(默认就是 {@code ValidateIntercept});</li>
 *     <li>失败时交给 {@link ViolationRecorder} 记录,observe 放行,enforce 抛出 {@link ValidateException}。</li>
 * </ul>
 * 校验组件自身出错(非 {@link ValidateException})时不影响请求,只记一次 warn。
 *
 * @author ycf
 * @since 1.2.0
 */
public class ValidationExecutor {
    private static final Logger LOG = LoggerFactory.getLogger(ValidationExecutor.class);
    private static final MethodPlan NOT_API = new MethodPlan(null, ValidationMode.OFF);

    private final ValidationProperties properties;
    private final Supplier<List<ApiMethodHandle>> handleSupplier;
    private final Supplier<List<ViolationRecorder>> recorderSupplier;
    private final Map<PlanKey, MethodPlan> plans = new ConcurrentHashMap<>(256);
    private final Set<String> failedEndpoints = ConcurrentHashMap.newKeySet();
    private volatile List<ApiMethodHandle> handles;
    private volatile List<ViolationRecorder> recorders;

    /**
     * @param properties       配置
     * @param handleSupplier   参数处理器(已排序),首次校验时取一次
     * @param recorderSupplier 记录器,首次记录时取一次
     */
    public ValidationExecutor(ValidationProperties properties,
                              Supplier<List<ApiMethodHandle>> handleSupplier,
                              Supplier<List<ViolationRecorder>> recorderSupplier) {
        this.properties = Objects.requireNonNull(properties, "properties");
        this.handleSupplier = handleSupplier;
        this.recorderSupplier = recorderSupplier;
    }

    /**
     * 参数解析完成后调用
     *
     * @param parameter 方法参数
     * @param value     已解析的参数值
     */
    public void validate(MethodParameter parameter, Object value) {
        MethodPlan plan = plan(parameter);
        if (plan.mode == ValidationMode.OFF) {
            return;
        }
        for (ApiMethodHandle handle : handles()) {
            try {
                handle.resolve(parameter, value);
            } catch (ValidateException e) {
                if (e instanceof ValidationViolationException || !(handle instanceof ValidateIntercept)) {
                    record(violation(plan, e));
                    if (plan.mode == ValidationMode.ENFORCE) {
                        throw e;
                    }
                    return;
                }
                // ValidateIntercept 内部取值失败(如反射取字段)时抛出的普通 ValidateException,不算校验失败
                skipped(plan, e);
                return;
            } catch (RuntimeException e) {
                skipped(plan, e);
                return;
            }
        }
    }

    private void skipped(MethodPlan plan, RuntimeException e) {
        if (failedEndpoints.add(plan.endpoint)) {
            LOG.warn("csap validation skipped on {} because the validator failed", plan.endpoint, e);
        } else {
            LOG.debug("csap validation skipped on {}: {}", plan.endpoint, e.toString());
        }
    }

    /**
     * 方法参数所在接口生效的模式(不是 {@link ApiOperation} 接口时为 OFF)
     *
     * @param parameter 方法参数
     * @return 模式
     */
    public ValidationMode modeFor(MethodParameter parameter) {
        return plan(parameter).mode;
    }

    /**
     * 接口键:Controller 全限定类名.方法名
     *
     * @param containingClass Controller 类
     * @param method          方法
     * @return 键
     */
    public static String endpointKey(Class<?> containingClass, Method method) {
        return containingClass.getName() + "." + method.getName();
    }

    private MethodPlan plan(MethodParameter parameter) {
        Method method = parameter.getMethod();
        if (method == null) {
            return NOT_API;
        }
        return plans.computeIfAbsent(new PlanKey(parameter.getContainingClass(), method), k -> {
            if (!parameter.hasMethodAnnotation(ApiOperation.class)) {
                return NOT_API;
            }
            String endpoint = endpointKey(k.containingClass, method);
            return new MethodPlan(endpoint, properties.modeFor(endpoint));
        });
    }

    private ValidationViolation violation(MethodPlan plan, ValidateException e) {
        String field = null;
        String rule = "ValidateException";
        if (e instanceof ValidationViolationException) {
            field = ((ValidationViolationException) e).getField();
            rule = ((ValidationViolationException) e).getRule();
        }
        return new ValidationViolation(plan.endpoint, field, rule, e.getCode(), e.getMessage(), plan.mode);
    }

    private void record(ValidationViolation violation) {
        for (ViolationRecorder recorder : recorders()) {
            try {
                recorder.record(violation);
            } catch (RuntimeException e) {
                LOG.debug("violation recorder {} failed: {}", recorder, e.toString());
            }
        }
    }

    private List<ApiMethodHandle> handles() {
        List<ApiMethodHandle> current = handles;
        if (current == null) {
            current = handleSupplier == null ? Collections.emptyList() : List.copyOf(handleSupplier.get());
            handles = current;
        }
        return current;
    }

    private List<ViolationRecorder> recorders() {
        List<ViolationRecorder> current = recorders;
        if (current == null) {
            current = recorderSupplier == null ? Collections.emptyList() : List.copyOf(recorderSupplier.get());
            recorders = current;
        }
        return current;
    }

    private static final class MethodPlan {
        private final String endpoint;
        private final ValidationMode mode;

        private MethodPlan(String endpoint, ValidationMode mode) {
            this.endpoint = endpoint;
            this.mode = mode;
        }
    }

    private static final class PlanKey {
        private final Class<?> containingClass;
        private final Method method;

        private PlanKey(Class<?> containingClass, Method method) {
            this.containingClass = containingClass;
            this.method = method;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof PlanKey)) {
                return false;
            }
            PlanKey that = (PlanKey) o;
            return containingClass == that.containingClass && method.equals(that.method);
        }

        @Override
        public int hashCode() {
            return 31 * System.identityHashCode(containingClass) + method.hashCode();
        }
    }
}
