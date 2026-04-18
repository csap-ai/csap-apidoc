package ai.csap.apidoc.service;

import java.lang.reflect.Method;
import java.util.List;

import org.springframework.web.method.HandlerMethod;

import ai.csap.apidoc.autoconfigure.StrategyModel;
import ai.csap.apidoc.model.CsapDocMethod;
import ai.csap.apidoc.model.CsapDocModel;
import ai.csap.apidoc.util.TypeVariableModel;

/**
 * 返回参数接口
 *
 * @Author ycf
 * @Date 2025/9/9 14:03
 * @Version 1.0
 */
public interface IMethodResponse extends IMethodBaseStrategy {
    /**
     * 返回参数标准处理方法
     *
     * @param method            当前方法对象
     * @param docMethod         文档方法对象
     * @param typeVariableModel 泛型对象
     * @param handlerMethod     Spring方法对象
     * @param strategyModel     策略对象
     * @return 实际参数结果
     */
    List<CsapDocModel> handle(Method method,
                              CsapDocMethod docMethod,
                              TypeVariableModel typeVariableModel,
                              HandlerMethod handlerMethod,
                              StrategyModel strategyModel
    );
}
