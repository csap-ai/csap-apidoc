package ai.csap.apidoc.mcp.autoconfigure;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import ai.csap.apidoc.mcp.McpService;
import ai.csap.apidoc.mcp.web.McpAdminController;

/**
 * DevTools 的 MCP 管理接口,与其他 DevTools 接口同一开关(csap.apidoc.devtool.enabled)。
 * MCP 未开启时接口仍在,只返回"未开启"状态,便于在页面上提示。
 *
 * @author ycf
 * @since 1.3.0
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass(name = "org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping")
@ConditionalOnProperty(prefix = "csap.apidoc.devtool", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(McpProperties.class)
@AutoConfigureAfter(McpAutoConfiguration.class)
public class McpDevtoolsAutoConfiguration {

    @Bean
    public McpAdminController csapMcpAdminController(ObjectProvider<McpService> service, McpProperties properties) {
        return new McpAdminController(service, properties);
    }
}
