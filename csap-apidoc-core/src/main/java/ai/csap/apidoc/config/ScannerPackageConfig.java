package ai.csap.apidoc.config;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.boot.context.properties.NestedConfigurationProperty;

import ai.csap.apidoc.autoconfigure.StrategyModel;
import ai.csap.apidoc.properties.CsapDocConfig;
import ai.csap.apidoc.util.ApidocClazzUtils;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.lang.Assert;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * @author yangchengfu
 * @description 扫描包
 * @dataTime 2020年-01月-02日 16:22:00
 **/
@Getter
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ScannerPackageConfig {
    /**
     * 文档配置
     */
    @NestedConfigurationProperty
    private CsapDocConfig config;
    /**
     * api 扫描到的类
     */
    private List<StrategyModel> apiClasseList = new ArrayList<>();
    /**
     * 枚举扫描到的类
     */
    private Set<Class<?>> enumClasseList = new HashSet<>();


    public ScannerPackageConfig(CsapDocConfig csapDocConfig) {
        Assert.notNull(csapDocConfig, CsapDocConfig.class.getName() + "is not null");
        this.config = csapDocConfig;
        Set<Class<?>> enums = ApidocClazzUtils.getClass(config.getEnumPackages(), config.getShowChildPackageFlag());
        if (CollectionUtil.isNotEmpty(enums)) {
            enumClasseList.addAll(enums);
        }

    }

    public ScannerPackageConfig addApiClassesList(List<StrategyModel> classes) {
        if (CollectionUtil.isNotEmpty(classes)) {
            apiClasseList.addAll(classes);
        }
        return this;
    }

    public ScannerPackageConfig addEnumClasseLis(Collection<Class<?>> classes) {
        if (CollectionUtil.isNotEmpty(classes)) {
            enumClasseList.addAll(classes);
        }
        return this;
    }

}
