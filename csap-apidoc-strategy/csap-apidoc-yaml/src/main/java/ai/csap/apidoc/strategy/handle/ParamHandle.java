package ai.csap.apidoc.strategy.handle;

import java.util.Map;

import com.fasterxml.jackson.core.type.TypeReference;
import com.google.common.collect.Maps;

import ai.csap.apidoc.StandardProperties;
import ai.csap.apidoc.autoconfigure.EnableApidocConfig;
import ai.csap.apidoc.core.ApidocStrategyName;
import ai.csap.apidoc.model.CsapDocModel;
import ai.csap.apidoc.strategy.AbstractMapHandle;
import ai.csap.apidoc.strategy.Handle;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 参数处理
 *
 * @Author ycf
 * @Date 2021/11/3 6:22 下午
 * @Version 1.0
 */
@AllArgsConstructor
public class ParamHandle extends AbstractMapHandle<StandardProperties, String, CsapDocModel> {
    /**
     * 文档配置
     */
    @Getter
    private final EnableApidocConfig enableApidocConfig;

    @Override
    public String fileName() {
        return "-param";
    }

    @Override
    public String path() {
        return "param";
    }

    @Override
    public Map<String, CsapDocModel> handle(ApidocStrategyName strategyType, String path, String prefixName) {
        return convertYaml(new TypeReference<>() {
        }, strategyType, path, prefixName).map(this::flatMap).orElseGet(Maps::newHashMap);
    }

    @Override
    public Handle<StandardProperties, Map<String, CsapDocModel>> handle(StandardProperties standardProperties, ApidocStrategyName strategyType, String path) {
        standardProperties.setParamMap(handle(strategyType, path));
        return this;
    }
}
