package ai.csap.apidoc.strategy.handle;

import com.fasterxml.jackson.core.type.TypeReference;
import com.google.common.collect.Lists;

import ai.csap.apidoc.FilePrefixStrategyType;
import ai.csap.apidoc.StandardProperties;
import ai.csap.apidoc.autoconfigure.EnableApidocConfig;
import ai.csap.apidoc.core.ApidocStrategyName;
import ai.csap.apidoc.properties.CsapApiInfo;
import ai.csap.apidoc.strategy.AbstractHandle;
import ai.csap.apidoc.strategy.Handle;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 基本信息处理
 *
 * @Author ycf
 * @Date 2021/11/3 6:22 下午
 * @Version 1.0
 */
@AllArgsConstructor
public class InfoHandle extends AbstractHandle<StandardProperties, CsapApiInfo> {
    /**
     * 文档配置
     */
    @Getter
    private final EnableApidocConfig enableApidocConfig;

    @Override
    public String fileName() {
        return "-info";
    }

    @Override
    public void writeAndMerge(CsapApiInfo value, ApidocStrategyName strategyType, String path) {
        writeAndMerge(value, strategyType, path, FilePrefixStrategyType.FIXED.getName());
    }

    @Override
    public void writeAndMerge(CsapApiInfo value, ApidocStrategyName strategyType) {
        writeAndMerge(value, strategyType, getEnableApidocConfig().getPath());
    }

    @Override
    public void writeAndMerge(CsapApiInfo value, ApidocStrategyName strategyType, String path, String prefixName) {
        write(value, strategyType, path, prefixName);
    }

    @Override
    public CsapApiInfo handle(ApidocStrategyName strategyType, String path, String prefixName) {
        return convertYaml(new TypeReference<>() {
        }, strategyType, path, prefixName).orElseGet(() -> Lists.newArrayList(new CsapApiInfo())).get(0);
    }

    @Override
    public Handle<StandardProperties, CsapApiInfo> handle(StandardProperties standardProperties, ApidocStrategyName strategyType, String path) {
        standardProperties.setApiInfo(handle(strategyType, path));
        return this;
    }
}
