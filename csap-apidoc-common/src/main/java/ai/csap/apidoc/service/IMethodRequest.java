package ai.csap.apidoc.service;

import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.List;

import org.springframework.web.method.HandlerMethod;

import ai.csap.apidoc.autoconfigure.StrategyModel;
import ai.csap.apidoc.model.CsapDocMethod;
import ai.csap.apidoc.model.CsapDocModel;
import ai.csap.apidoc.util.TypeVariableModel;

/**
 * 请求参数
 *
 * @Author ycf
 * @Date 2025/9/9 14:03
 * @Version 1.0
 */
public interface IMethodRequest extends IMethodBaseStrategy {
    /**
     * 请求参数标准处理方法
     *
     * @param docMethod         文档方法对象
     * @param classes           方法参数
     * @param method            方法
     * @param typeVariableModel 泛型对象
     * @param handlerMethod     Spring方法对象
     * @param strategyModel     策略对象
     * @return 实际参数结果
     */
    List<CsapDocModel> handle(CsapDocMethod docMethod, Type[] classes, Method method,
                              TypeVariableModel typeVariableModel, HandlerMethod handlerMethod,
                              StrategyModel strategyModel);
}
