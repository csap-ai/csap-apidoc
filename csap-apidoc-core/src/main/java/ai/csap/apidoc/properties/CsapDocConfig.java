package ai.csap.apidoc.properties;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import ai.csap.apidoc.type.CamelCaseType;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

import com.google.common.collect.Sets;

import ai.csap.apidoc.model.CsapDocResource;
import ai.csap.apidoc.strategy.ApidocStrategy;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @author yangchengfu
 * @description csap 文档属性
 * @dataTime 2019年-12月-27日 14:40:00
 **/
@Data
@AllArgsConstructor
@NoArgsConstructor
@ConfigurationProperties(
        prefix = CsapDocConfig.PREFIX
)
public class CsapDocConfig {
    public static final String PREFIX = "csap.apidoc";
    /**
     * api的基本信息
     */
    private CsapApiInfo apiInfo;
    /**
     * 返回参数下划线转驼峰处理
     */
    private CamelCaseType responseType = CamelCaseType.none;
    /**
     * 返回参数通用配置
     */
    @NestedConfigurationProperty
    private ResultConfig result = new ResultConfig();
    /**
     * 扫描的包
     */
    @Deprecated
    private Set<String> apiPackages = Sets.newHashSet();

    /**
     * 返回的枚举类，扫描包
     */
    private Set<String> enumPackages = Sets.newHashSet();

    /**
     * 模型/实体类扫描包路径
     */
    private Set<String> modelPackages = Sets.newHashSet();
    /**
     * 是否扫描子包
     */
    private Boolean showChildPackageFlag = Boolean.TRUE;
    /**
     * 文档资源
     */
    @NestedConfigurationProperty
    private List<CsapDocResource> resources;
    /**
     * 开发工具
     */
    @NestedConfigurationProperty
    private ApidocDevtoolsProperties devtool = new ApidocDevtoolsProperties();

    public void setApiPackages(List<String> apiPackages) {
        if (this.apiPackages == null) {
            this.apiPackages = new HashSet<>();
        }
        if (apiPackages != null) {
            this.apiPackages.addAll(apiPackages);
        }
    }

    public void setEnumPackages(List<String> enumPackages) {
        if (this.enumPackages == null) {
            this.enumPackages = new HashSet<>();
        }
        if (enumPackages != null) {
            this.enumPackages.addAll(enumPackages);
        }
    }

    public void setModelPackages(List<String> modelPackages) {
        if (this.modelPackages == null) {
            this.modelPackages = new HashSet<>();
        }
        if (modelPackages != null) {
            this.modelPackages.addAll(modelPackages);
        }
    }

    public CsapApiInfo getApiInfo() {
        return apiInfo == null ? ApidocStrategy.DEFAULT_APIINFO : apiInfo;
    }


    @Data
    public static class ApidocDevtoolsProperties {
        /**
         * 接口文档开发模式
         */
        private Boolean enabled = Boolean.FALSE;
        /**
         * 缓存
         */
        private Boolean cache = Boolean.TRUE;
    }
}
