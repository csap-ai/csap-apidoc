package ai.csap.apidoc.boot.autoconfigure;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

import ai.csap.apidoc.autoconfigure.EnableApidocConfig;
import ai.csap.apidoc.devtools.ApidocStrategyController;
import ai.csap.apidoc.devtools.DevtoolsController;
import ai.csap.apidoc.devtools.DevtoolsStartupBanner;
import ai.csap.apidoc.devtools.DevtoolsViewController;
import ai.csap.apidoc.devtools.core.ApidocDevtools;
import ai.csap.apidoc.properties.CsapDocConfig;

/**
 * Apidoc devtools auto configuration.
 * <p>Created on 2025/10/4
 *
 * @author ycf
 * @since 1.0
 */
@Configuration
@ConditionalOnProperty(prefix = CsapDocConfig.PREFIX + ".devtool", name = "enabled",
        havingValue = "true")
@ComponentScan(basePackageClasses = {ApidocStrategyController.class, DevtoolsController.class,
        DevtoolsViewController.class})
public class ApidocDevtoolsAutoConfiguration {

    @Bean
    public ApidocDevtools apidocDevtools(ObjectProvider<EnableApidocConfig> enableApidocConfig,
                                         ApplicationContext applicationContext,
                                         ObjectProvider<CsapDocConfig> csapDocConfig) {
        return new ApidocDevtools(enableApidocConfig.getIfAvailable(), applicationContext,
                csapDocConfig.getIfAvailable());
    }

    /**
     * 注册启动横幅监听器
     * 在应用启动成功后打印访问地址
     */
    @Bean
    public DevtoolsStartupBanner devtoolsStartupBanner() {
        return new DevtoolsStartupBanner();
    }
}
