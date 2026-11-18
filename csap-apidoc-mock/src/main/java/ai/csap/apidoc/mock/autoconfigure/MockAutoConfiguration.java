package ai.csap.apidoc.mock.autoconfigure;

import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import ai.csap.apidoc.event.ApidocRulesRefreshedEvent;
import ai.csap.apidoc.mock.MockProperties;
import ai.csap.apidoc.mock.MockService;
import ai.csap.apidoc.mock.web.MockController;
import ai.csap.apidoc.model.CsapDocResponse;
import ai.csap.apidoc.service.ApidocContext;

/**
 * 内嵌 Mock 端点的自动配置。默认关闭,{@code csap.apidoc.mock.enabled=true} 时生效。
 *
 * @author ycf
 * @since 1.4.0
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass(name = "org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping")
@ConditionalOnProperty(prefix = MockProperties.PREFIX, name = "enabled", havingValue = "true")
@EnableConfigurationProperties(MockProperties.class)
public class MockAutoConfiguration {

    private static final Logger LOG = LoggerFactory.getLogger(MockAutoConfiguration.class);

    @Bean
    @ConditionalOnMissingBean
    public MockService csapMockService(MockProperties properties) {
        LOG.warn("csap-apidoc mock is ENABLED, {}/** returns synthetic data; do not enable in production",
                MockProperties.BASE_PATH);
        Supplier<CsapDocResponse> docs = () -> ApidocContext.cmd(false, false);
        return new MockService(properties, docs);
    }

    @Bean
    @ConditionalOnMissingBean
    public MockController csapMockController(MockService service, MockProperties properties) {
        return new MockController(service, properties);
    }

    /**
     * 文档刷新(扫描完成、DevTools 修改规则)后重建匹配索引
     */
    @Bean
    public ApplicationListener<ApidocRulesRefreshedEvent> csapMockDocumentListener(MockService service) {
        return event -> service.onDocumentRefreshed();
    }
}
