package ai.csap.validation;

import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.MethodParameter;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.web.multipart.MultipartFile;

import com.alibaba.fastjson.JSON;

import ai.csap.apidoc.annotation.ApiModel;
import ai.csap.apidoc.annotation.ApiOperation;
import ai.csap.apidoc.core.ApiMethodHandle;
import ai.csap.apidoc.core.ModelTypepProperties;
import ai.csap.apidoc.util.ApidocClazzUtils;
import ai.csap.apidoc.util.TypeVariableModel;
import ai.csap.validation.factory.IValidateFactory;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.util.ArrayUtil;
import jakarta.validation.ConstraintValidatorContext;
import lombok.extern.slf4j.Slf4j;

/**
 * 统一验证.
 * <p>Created on 2020/1/4
 *
 * @author yangchengfu
 * @since 1.0
 */
@Slf4j
public final class ValidateIntercept implements ApiMethodHandle {
    private final IValidateFactory validateFactory;
    private final ParameterNameDiscoverer parameterNameDiscoverer = new DefaultParameterNameDiscoverer();
    private static final Map<Class<?>, TypeVariableModel> TYPE_MAP = new ConcurrentHashMap<>(128);


    public ValidateIntercept(IValidateFactory validateFactory) {
        this.validateFactory = validateFactory;
    }

    @Override
    public void resolve(MethodParameter parameter, Object value) {
        TypeVariableModel typeVariableModel = TYPE_MAP.computeIfAbsent(
                parameter.getContainingClass(),
                i -> new TypeVariableModel(parameter.getContainingClass()));
        //参数
        Type[] cl = Objects.requireNonNull(parameter.getMethod()).getGenericParameterTypes();
        String[] parameterNames = parameterNameDiscoverer.getParameterNames(parameter.getMethod());
        if (ArrayUtil.isEmpty(parameterNames) || ArrayUtil.isEmpty(cl)) {
            return;
        }
        ModelTypepProperties properties = ApidocClazzUtils.getRawClassType(
                cl[parameter.getParameterIndex()], typeVariableModel);
        String type = properties.getRawType();
        if (!validateFactory.getClObj(type)) {
            return;
        }
        String parameterName = parameterNames[parameter.getParameterIndex()];
        Method method = parameter.getMethod();
        try {
            ConstraintValidatorContext validatorContext = null;
            if (ApidocClazzUtils.DATA_TYPE.contains(type) ||
                    ApidocClazzUtils.OTHER_DATA_TYPE.contains(type)) {
                validateFactory.validation(value, method, typeVariableModel.getAClass(),
                        properties.getCl(), parameterName, validatorContext);
            } else if (ApidocClazzUtils.LIST.contains(type)) {
                if (ApidocClazzUtils.DATA_TYPE.contains(properties.getCl().getName()) ||
                        ApidocClazzUtils.OTHER_DATA_TYPE.contains(type)) {
                    validateFactory.validation(value, method, typeVariableModel.getAClass(),
                            properties.getCl(), parameterName, validatorContext);
                } else {
                    if (CollectionUtil.isNotEmpty((Collection<?>) value)) {
                        ((Collection<?>) value).forEach(item -> validateFactory.validation(item,
                                method, typeVariableModel.getAClass(), properties.getCl(),
                                parameterName, validatorContext));
                    }
                }
            } else if (properties.getRawCl().isAnnotationPresent(ApiModel.class)) {
                validateFactory.validation(value, method, typeVariableModel.getAClass(),
                        properties.getRawCl(), parameterName, validatorContext);
            }
        } finally {
            log(method, value);
        }
    }


    private void log(Method method, Object value) {
        if (log.isDebugEnabled()) {
            try {
                ApiOperation apiOperation = method.getAnnotation(ApiOperation.class);
                String descr = "";
                if (apiOperation != null) {
                    descr = apiOperation.value();
                }
                List<String> paramTypes = Stream.of(method.getGenericParameterTypes())
                        .map(Type::getTypeName)
                        .filter(typeName -> !typeName.equals("jakarta.servlet.ServletRequest") &&
                                !typeName.equals("jakarta.servlet.ServletResponse") &&
                                !typeName.equals(MultipartFile.class.getName()))
                        .collect(Collectors.toList());
                log.debug("method {} description {} request paramType {} paramsName {} data {}",
                        method.getName(), descr, paramTypes,
                        parameterNameDiscoverer.getParameterNames(method), JSON.toJSONString(value));
            } catch (Exception e) {
                log.error(e.getMessage(), e);
            }
        }
    }


}
