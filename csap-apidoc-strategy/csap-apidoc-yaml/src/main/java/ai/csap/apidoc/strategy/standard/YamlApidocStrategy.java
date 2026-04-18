package ai.csap.apidoc.strategy.standard;

import java.lang.reflect.Method;

import ai.csap.apidoc.AbstractApidocStrategy;
import ai.csap.apidoc.ApiStrategyType;
import ai.csap.apidoc.autoconfigure.EnableApidocConfig;
import ai.csap.apidoc.autoconfigure.StrategyModel;
import ai.csap.apidoc.core.ApidocStrategyName;
import ai.csap.apidoc.model.CsapDocMethod;
import ai.csap.apidoc.model.CsapDocModelController;
import ai.csap.apidoc.strategy.handle.ControllerHandle;
import ai.csap.apidoc.strategy.handle.EnumHandle;
import ai.csap.apidoc.strategy.handle.InfoHandle;
import ai.csap.apidoc.strategy.handle.MethodFieldHandle;
import ai.csap.apidoc.strategy.handle.MethodHandle;
import ai.csap.apidoc.strategy.handle.ParamHandle;
import ai.csap.apidoc.util.TypeVariableModel;

/**
 * yaml形式文档配置实现
 *
 * @Author ycf
 * @Date 2021/9/9 3:47 下午
 * @Version 1.0
 */
public class YamlApidocStrategy extends AbstractApidocStrategy {
    public YamlApidocStrategy(ControllerHandle controllerHandle, EnumHandle enumHandle,
            InfoHandle infoHandle, MethodHandle methodHandle, MethodFieldHandle methodFieldHandle,
            ParamHandle paramHandle, EnableApidocConfig enableApidocConfig) {
        super(controllerHandle, enumHandle, infoHandle, methodHandle, methodFieldHandle, paramHandle,
                enableApidocConfig);
    }

    @Override
    public String getName() {
        return ApiStrategyType.YAML.getName();
    }

    @Override
    public String getSuffix() {
        return ApiStrategyType.YAML.getSuffix();
    }

    @Override
    public ApidocStrategyName strategyType() {
        return this;
    }

    @Override
    public CsapDocModelController controller(StrategyModel strategyModel, String className) {
        return null;
    }

    @Override
    public CsapDocMethod method(CsapDocModelController docController, Method method, TypeVariableModel typeVariableModel, StrategyModel strategyModel) {
        return null;
    }

}
