package ai.csap.apidoc.service.method;

import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.List;

import org.springframework.web.method.HandlerMethod;

import ai.csap.apidoc.ApiParamStrategyType;
import ai.csap.apidoc.autoconfigure.StrategyModel;
import ai.csap.apidoc.config.ScannerPackageConfig;
import ai.csap.apidoc.model.CsapDocMethod;
import ai.csap.apidoc.model.CsapDocModel;
import ai.csap.apidoc.service.IMethodRequest;
import ai.csap.apidoc.util.TypeVariableModel;
import ai.csap.validation.factory.IValidateFactory;

/**
 * 方法请求参数默认实现
 *
 * @Author ycf
 * @Date 2025/9/9 14:58
 * @Version 1.0
 */
public class DefaultMethodRequest extends DefaultAbstractMethod implements IMethodRequest {
    public DefaultMethodRequest(IValidateFactory validateFactory, ScannerPackageConfig packageConfig) {
        super(validateFactory, packageConfig);
    }

    @Override
    public String name() {
        return ApiParamStrategyType.DEFAULT_TYPE;
    }

    @Override
    public List<CsapDocModel> handle(CsapDocMethod docMethod, Type[] classes, Method method,
                                      TypeVariableModel typeVariableModel, HandlerMethod handlerMethod,
                                      StrategyModel strategyModel) {
        return model(docMethod, classes, method, true, typeVariableModel, handlerMethod, strategyModel, true);
    }
}
