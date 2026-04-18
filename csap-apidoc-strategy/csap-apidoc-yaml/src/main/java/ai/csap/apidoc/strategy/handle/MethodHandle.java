package ai.csap.apidoc.strategy.handle;

import java.util.Map;

import com.fasterxml.jackson.core.type.TypeReference;
import com.google.common.collect.Maps;

import ai.csap.apidoc.StandardProperties;
import ai.csap.apidoc.autoconfigure.EnableApidocConfig;
import ai.csap.apidoc.core.ApidocStrategyName;
import ai.csap.apidoc.model.CsapDocMethod;
import ai.csap.apidoc.strategy.AbstractMapHandle;
import ai.csap.apidoc.strategy.Handle;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 方法接口处理
 *
 * @Author ycf
 * @Date 2021/11/3 6:22 下午
 * @Version 1.0
 */
@AllArgsConstructor
public class MethodHandle extends AbstractMapHandle<StandardProperties, String, Map<String, CsapDocMethod>> {
    /**
     * 文档配置
     */
    @Getter
    private final EnableApidocConfig enableApidocConfig;

    @Override
    public String fileName() {
        return "-method";
    }

    @Override
    public String path() {
        return "method";
    }

    @Override
    public Map<String, Map<String, CsapDocMethod>> handle(ApidocStrategyName strategyType, String path, String prefixName) {
        return convertYaml(new TypeReference<>() {
        }, strategyType, path, prefixName).map(this::flatMap).orElseGet(Maps::newHashMap);
    }

    @Override
    public Handle<StandardProperties, Map<String, Map<String, CsapDocMethod>>> handle(StandardProperties standardProperties, ApidocStrategyName strategyType, String path) {
        standardProperties.setMethodMap(handle(strategyType, path));
        return this;
    }
}
