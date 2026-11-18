package ai.csap.validation;

import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.MethodParameter;
import org.springframework.core.ParameterNameDiscoverer;

import ai.csap.apidoc.annotation.ApiModel;
import ai.csap.apidoc.core.ApiMethodHandle;
import ai.csap.apidoc.core.ModelTypepProperties;
import ai.csap.apidoc.util.ApidocClazzUtils;
import ai.csap.apidoc.util.TypeVariableModel;
import ai.csap.validation.factory.IValidateFactory;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.util.ArrayUtil;
import lombok.extern.slf4j.Slf4j;

/**
 * 统一验证:按 {@link IValidateFactory} 中登记的规则校验一个已解析的方法参数.
 *
 * <p>规则每次都从 {@link IValidateFactory} 读取,DevTools 修改规则后下一个请求即生效;
 * 只缓存与规则无关的参数元数据(参数名、泛型解析结果)。
 * <p>Created on 2020/1/4
 *
 * @author yangchengfu
 * @since 1.0
 */
@Slf4j
public final class ValidateIntercept implements ApiMethodHandle {
    private static final ParamPlan SKIP = new ParamPlan(null, null, null, Kind.SKIP);

    private final IValidateFactory validateFactory;
    private final ParameterNameDiscoverer parameterNameDiscoverer = new DefaultParameterNameDiscoverer();
    private final Map<Class<?>, TypeVariableModel> typeMap = new ConcurrentHashMap<>(128);
    private final Map<PlanKey, ParamPlan> plans = new ConcurrentHashMap<>(256);

    public ValidateIntercept(IValidateFactory validateFactory) {
        this.validateFactory = validateFactory;
    }

    @Override
    public void resolve(MethodParameter parameter, Object value) {
        Method method = parameter.getMethod();
        if (method == null) {
            return;
        }
        ParamPlan plan = plans.computeIfAbsent(new PlanKey(parameter.getContainingClass(), method,
                parameter.getParameterIndex()), k -> plan(parameter, method));
        switch (plan.kind) {
            case BASIC:
                validateFactory.validation(value, method, plan.controller, plan.paramClass, plan.parameterName);
                break;
            case COLLECTION:
                if (value instanceof Collection && CollectionUtil.isNotEmpty((Collection<?>) value)) {
                    for (Object item : (Collection<?>) value) {
                        validateFactory.validation(item, method, plan.controller, plan.paramClass, plan.parameterName);
                    }
                }
                break;
            case MODEL:
                validateFactory.validation(value, method, plan.controller, plan.paramClass, plan.parameterName);
                break;
            default:
                break;
        }
        if (log.isDebugEnabled() && plan.kind != Kind.SKIP) {
            // 不打印参数值,避免泄露敏感数据
            log.debug("validated {}.{} parameter {}", plan.controller.getName(), method.getName(), plan.parameterName);
        }
    }

    private ParamPlan plan(MethodParameter parameter, Method method) {
        Class<?> containingClass = parameter.getContainingClass();
        TypeVariableModel typeVariableModel = typeMap.computeIfAbsent(containingClass, TypeVariableModel::new);
        Type[] types = method.getGenericParameterTypes();
        String[] parameterNames = parameterNameDiscoverer.getParameterNames(method);
        int index = parameter.getParameterIndex();
        if (ArrayUtil.isEmpty(parameterNames) || ArrayUtil.isEmpty(types) || index < 0 || index >= types.length) {
            return SKIP;
        }
        ModelTypepProperties properties = ApidocClazzUtils.getRawClassType(types[index], typeVariableModel);
        String type = properties.getRawType();
        if (!validateFactory.getClObj(type)) {
            return SKIP;
        }
        String parameterName = parameterNames[index];
        Class<?> controller = typeVariableModel.getAClass();
        if (ApidocClazzUtils.DATA_TYPE.contains(type) || ApidocClazzUtils.OTHER_DATA_TYPE.contains(type)) {
            return new ParamPlan(controller, properties.getCl(), parameterName, Kind.BASIC);
        } else if (ApidocClazzUtils.LIST.contains(type)) {
            if (properties.getCl() != null && ApidocClazzUtils.DATA_TYPE.contains(properties.getCl().getName())) {
                return new ParamPlan(controller, properties.getCl(), parameterName, Kind.BASIC);
            }
            return properties.getCl() == null ? SKIP
                    : new ParamPlan(controller, properties.getCl(), parameterName, Kind.COLLECTION);
        } else if (properties.getRawCl() != null && properties.getRawCl().isAnnotationPresent(ApiModel.class)) {
            return new ParamPlan(controller, properties.getRawCl(), parameterName, Kind.MODEL);
        }
        return SKIP;
    }

    private enum Kind {
        SKIP, BASIC, COLLECTION, MODEL
    }

    private static final class ParamPlan {
        private final Class<?> controller;
        private final Class<?> paramClass;
        private final String parameterName;
        private final Kind kind;

        private ParamPlan(Class<?> controller, Class<?> paramClass, String parameterName, Kind kind) {
            this.controller = controller;
            this.paramClass = paramClass;
            this.parameterName = parameterName;
            this.kind = kind;
        }
    }

    private static final class PlanKey {
        private final Class<?> containingClass;
        private final Method method;
        private final int index;

        private PlanKey(Class<?> containingClass, Method method, int index) {
            this.containingClass = containingClass;
            this.method = method;
            this.index = index;
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
            return index == that.index && containingClass == that.containingClass && method.equals(that.method);
        }

        @Override
        public int hashCode() {
            return Objects.hash(containingClass, method, index);
        }
    }
}
