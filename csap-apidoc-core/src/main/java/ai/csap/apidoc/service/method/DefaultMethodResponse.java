package ai.csap.apidoc.service.method;

import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.List;

import org.springframework.web.method.HandlerMethod;

import ai.csap.apidoc.ApiParamStrategyType;
import ai.csap.apidoc.autoconfigure.StrategyModel;
import ai.csap.apidoc.config.ScannerPackageConfig;
import ai.csap.apidoc.model.CsapDocMethod;
import ai.csap.apidoc.model.CsapDocModel;
import ai.csap.apidoc.service.IMethodResponse;
import ai.csap.apidoc.util.TypeVariableModel;
import ai.csap.validation.factory.BaseModel;
import ai.csap.validation.factory.IValidateFactory;

/**
 * 方法返回参数默认实现
 *
 * @Author ycf
 * @Date 2025/9/9 14:58
 * @Version 1.0
 */
public class DefaultMethodResponse extends DefaultAbstractMethod implements IMethodResponse {
    public DefaultMethodResponse(IValidateFactory validateFactory, ScannerPackageConfig packageConfig) {
        super(validateFactory, packageConfig);
    }

    @Override
    public String name() {
        return ApiParamStrategyType.DEFAULT_TYPE;
    }

    @Override
    public List<CsapDocModel> handle(Method method, CsapDocMethod docMethod, TypeVariableModel typeVariableModel,
                                     HandlerMethod handlerMethod, StrategyModel strategyModel) {
        Type type = method.getGenericReturnType();
        if (method.getGenericReturnType() instanceof ParameterizedType) {
            //TODO 针对返回类型为Optional的类型处理
            Type rawType = ((ParameterizedType) method.getGenericReturnType()).getRawType();
            if (rawType.getTypeName()
                       .equals("ai.csap.apidoc.util.optional.Optional") || rawType.equals(java.util.Optional.class)) {
                type = ((ParameterizedType) method.getGenericReturnType()).getActualTypeArguments()[0];
            }
        }
        return model(BaseModel.build().method(method).key(docMethod.getKey()),
                docMethod,
                new Type[]{type},
                method,
                false,
                typeVariableModel,
                true,
                true,
                strategyModel, true);
    }
}
