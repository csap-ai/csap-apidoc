package ai.csap.apidoc.redis.autoconfigure;

import java.util.Set;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import ai.csap.apidoc.redis.RedisParamGroupStrategy;
import ai.csap.apidoc.redis.RedisRuleStore;
import ai.csap.apidoc.service.ApidocContext;
import ai.csap.validation.factory.DefaultValidateFactoryImpl;
import ai.csap.validation.factory.IValidateFactory;
import ai.csap.validation.properties.ValidationProperties;

import org.hibernate.validator.internal.metadata.core.ConstraintHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Redis 模式的自动配置:注册 {@link RedisParamGroupStrategy}。
 *
 * <p>只有 classpath 上有 Spring Data Redis 时才生效;Bean 总是注册但 Redis 只在真正用到时才连接。
 * 用应用已有的 {@link StringRedisTemplate}(没有就用 {@link RedisConnectionFactory} 建一个)。
 * 同一个 jar 可用于 Spring Boot 2 和 3。
 *
 * @author ycf
 * @since 1.4.0
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnClass(name = "org.springframework.data.redis.core.StringRedisTemplate")
@EnableConfigurationProperties(RedisProperties.class)
public class RedisRuleAutoConfiguration {

    private static final Logger LOG = LoggerFactory.getLogger(RedisRuleAutoConfiguration.class);

    @Bean
    @ConditionalOnMissingBean
    public RedisParamGroupStrategy redisParamGroupStrategy(RedisProperties properties,
                                                           ObjectProvider<IValidateFactory> validateFactory,
                                                           ObjectProvider<StringRedisTemplate> templates,
                                                           ObjectProvider<RedisConnectionFactory> factories,
                                                           Environment environment) {
        IValidateFactory factory = validateFactory.getIfAvailable();
        if (factory == null) {
            factory = new DefaultValidateFactoryImpl(ConstraintHelper.forAllBuiltinConstraints(), new ValidationProperties());
        }
        RedisParamGroupStrategy strategy = new RedisParamGroupStrategy(factory,
                () -> createStore(properties, templates, factories, environment),
                properties.isReadOnly(), properties.getPollInterval());
        strategy.setReloadListener(RedisRuleAutoConfiguration::refreshDocuments);
        return strategy;
    }

    static RedisRuleStore createStore(RedisProperties properties, ObjectProvider<StringRedisTemplate> templates,
                                      ObjectProvider<RedisConnectionFactory> factories, Environment environment) {
        StringRedisTemplate template = templates.getIfUnique();
        if (template == null) {
            RedisConnectionFactory cf = factories.getIfUnique();
            if (cf == null) {
                throw new IllegalStateException("@EnableApidoc(paramType = ApiStrategyType.REDIS) 需要应用提供 Redis 连接,"
                        + "但容器中没有唯一的 StringRedisTemplate / RedisConnectionFactory。"
                        + "请引入 spring-boot-starter-data-redis 并配置 spring.redis.*(Boot 3 为 spring.data.redis.*)。");
            }
            template = new StringRedisTemplate(cf);
            template.afterPropertiesSet();
        }
        String service = properties.getService() != null && !properties.getService().trim().isEmpty()
                ? properties.getService().trim() : environment.getProperty("spring.application.name");
        if (service == null || service.trim().isEmpty()) {
            service = "default";
        }
        return new RedisRuleStore(template, properties.getKeyPrefix(), service, properties.getHistoryLimit(),
                properties.isSubscribe());
    }

    /**
     * 规则变化后重新生成文档和字段规则,与数据库模式相同:DevTools 开启按类刷新,关闭时整体重新扫描。
     */
    static void refreshDocuments(Set<String> classNames) {
        try {
            if (Boolean.TRUE.equals(ApidocContext.isDevEnabled())) {
                for (String className : classNames) {
                    try {
                        ApidocContext.cmd(className, null, false);
                    } catch (RuntimeException e) {
                        LOG.warn("csap-apidoc: 规则已同步,但重新生成 {} 的文档失败: {}", className, e.getMessage());
                    }
                }
            } else {
                ApidocContext.cmd(true, false);
            }
            LOG.info("csap-apidoc: Redis 规则已变化,已重新生成 {} 个类的文档和字段规则", classNames.size());
        } catch (RuntimeException e) {
            LOG.warn("csap-apidoc: 规则已同步,但重新生成文档失败: {}", e.getMessage());
        }
    }
}
