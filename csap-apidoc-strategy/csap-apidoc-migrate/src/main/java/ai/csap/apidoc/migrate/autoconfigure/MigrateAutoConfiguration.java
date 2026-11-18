package ai.csap.apidoc.migrate.autoconfigure;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 规则迁移的自动配置:设置了 {@code csap.apidoc.migrate.from} 才注册启动时执行迁移的 Runner。
 *
 * @author ycf
 * @since 1.4.0
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = MigrateProperties.PREFIX, name = "from")
@EnableConfigurationProperties(MigrateProperties.class)
public class MigrateAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public RuleMigrationRunner ruleMigrationRunner(MigrateProperties properties, ApplicationContext context) {
        return new RuleMigrationRunner(properties, context);
    }
}
