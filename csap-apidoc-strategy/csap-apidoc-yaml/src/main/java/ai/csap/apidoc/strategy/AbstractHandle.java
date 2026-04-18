package ai.csap.apidoc.strategy;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;

import ai.csap.apidoc.FilePrefixStrategyType;
import ai.csap.apidoc.autoconfigure.EnableApidocConfig;
import ai.csap.apidoc.core.ApidocStrategyName;

import cn.hutool.core.collection.CollectionUtil;
import lombok.extern.slf4j.Slf4j;

/**
 * 抽象处理类
 *
 * @Author ycf
 * @Date 2021/11/4 11:42 下午
 * @Version 1.0
 */
@Slf4j
public abstract class AbstractHandle<T, R> implements YamlHandle<T, R> {

    public abstract EnableApidocConfig getEnableApidocConfig();

    static {
        YAML_MAPPER.findAndRegisterModules();
        YAML_MAPPER.setSerializationInclusion(JsonInclude.Include.NON_EMPTY);
        YAML_MAPPER.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        YAML_MAPPER.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        YAML_MAPPER.disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER);
        YAML_MAPPER.enable(YAMLGenerator.Feature.USE_NATIVE_OBJECT_ID);
        YAML_MAPPER.enable(YAMLGenerator.Feature.SPLIT_LINES);
        YAML_MAPPER.enable(YAMLGenerator.Feature.MINIMIZE_QUOTES);
        YAML_MAPPER.disable(YAMLGenerator.Feature.ALWAYS_QUOTE_NUMBERS_AS_STRINGS);
        YAML_MAPPER.disable(YAMLGenerator.Feature.LITERAL_BLOCK_STYLE);
        YAML_MAPPER.enable(YAMLGenerator.Feature.INDENT_ARRAYS);
        YAML_MAPPER.enable(YAMLGenerator.Feature.USE_PLATFORM_LINE_BREAKS);
    }


    @Override
    public R handle(ApidocStrategyName strategyType, String path) {
        FilePrefixStrategyType strategy = getEnableApidocConfig().getPrefixStrategy();
        if (log.isDebugEnabled()) {
            log.debug("handle type {},path:{},getPrefixStrategy.name {}", strategyType, path, strategy.getName());
        }
        return handle(strategyType, path, strategy.getName());
    }

    @Override
    public R handle(ApidocStrategyName strategyType) {
        if (log.isDebugEnabled()) {
            log.debug("handle type {},path {}", strategyType, getEnableApidocConfig().getPath());
        }
        return handle(strategyType, getEnableApidocConfig().getPath());
    }

    public <K, V> Map<K, V> flatMap(List<Map<K, V>> values) {
        return values
                .stream()
                .filter(CollectionUtil::isNotEmpty)
                .flatMap(i -> i.entrySet().stream())
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (v1, v2) -> v1));
    }

    @Override
    public String pattern() {
        return getEnableApidocConfig().getPrefixStrategy().getName();
    }
}
