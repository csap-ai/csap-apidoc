package ai.csap.apidoc.strategy;

import java.util.Map;

import ai.csap.apidoc.FilePrefixStrategyType;
import ai.csap.apidoc.core.ApidocStrategyName;
import ai.csap.apidoc.handle.IStandardHandle;

import cn.hutool.core.collection.CollectionUtil;
import lombok.SneakyThrows;

/**
 * 抽Map处理类
 *
 * @Author ycf
 * @Date 2021/11/4 11:42 下午
 * @Version 1.0
 */
public abstract class AbstractMapHandle<T, K extends String, V> extends AbstractHandle<T, Map<K, V>> {
    @Override
    public void writeAndMerge(Map<K, V> value, ApidocStrategyName strategyType, String path, String prefixName) {
        if (CollectionUtil.isEmpty(value)) {
            return;
        }
        Map<K, V> map = handle(strategyType, path);
        map.putAll(value);
        write(map, strategyType, path, FilePrefixStrategyType.FIXED.getName());
    }

    @Override
    public void writeAndMerge(Map<K, V> value, ApidocStrategyName strategyType) {
        writeAndMerge(value, strategyType, getEnableApidocConfig().getPath());
    }

    @Override
    public void writeAndMerge(Map<K, V> value, ApidocStrategyName strategyType, String path) {
        if (CollectionUtil.isEmpty(value)) {
            return;
        }
        if (getEnableApidocConfig().getPrefixStrategy().equals(FilePrefixStrategyType.DEFAULT)) {
            value.forEach((k, v) -> write(value, strategyType, path, IStandardHandle.splitName(k)));
        } else {
            writeAndMerge(value, strategyType, path, FilePrefixStrategyType.FIXED.getName());
        }
    }

    @SneakyThrows
    @Override
    public void write(Map<K, V> value, ApidocStrategyName strategyType, String path, String prefixName) {
        if (getEnableApidocConfig().getPrefixStrategy().equals(FilePrefixStrategyType.DEFAULT)) {
            value.forEach((k, v) -> super.write(value, strategyType, path, IStandardHandle.splitName(k)));
        } else {
            super.write(value, strategyType, path, FilePrefixStrategyType.FIXED.getName());
        }
    }
}
