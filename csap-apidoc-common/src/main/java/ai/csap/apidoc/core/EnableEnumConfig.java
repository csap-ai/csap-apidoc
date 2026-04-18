package ai.csap.apidoc.core;

import java.util.Optional;
import java.util.Set;

import ai.csap.apidoc.util.ApidocClazzUtils;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.collection.ConcurrentHashSet;
import lombok.Data;

/**
 * @author yangchengfu
 * @description 自动化配置
 * @dataTime 2020年-09月-22日 15:28:00
 **/
@Data
public class EnableEnumConfig {
    /**
     * 扫描的包
     */
    private Set<String> enumPackages = new ConcurrentHashSet<>();
    /**
     * 扫描的class对象
     */
    private Set<Class<?>> classSet = new ConcurrentHashSet<>();

    public EnableEnumConfig addEnumPackages(Set<String> strings) {
        if (CollectionUtil.isNotEmpty(strings)) {
            Optional.of(ApidocClazzUtils.getClass(strings, true, Enum.class::isAssignableFrom))
                    .ifPresent(classSet::addAll);
        }
        return this;
    }

    public EnableEnumConfig addEnumPackageSet(Set<Class<?>> classes) {
        if (CollectionUtil.isNotEmpty(classes)) {
            classSet.addAll(classes);
        }
        return this;
    }
}
