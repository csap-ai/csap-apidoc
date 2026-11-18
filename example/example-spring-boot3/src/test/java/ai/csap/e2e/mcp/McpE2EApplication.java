package ai.csap.e2e.mcp;

import org.springframework.boot.autoconfigure.SpringBootApplication;

import ai.csap.apidoc.ApiStrategyType;
import ai.csap.apidoc.boot.autoconfigure.EnableApidoc;

/**
 * MCP 端到端测试应用。示例工程带了 Spring Security,这里用真实端口测试,关掉默认的安全自动配置。
 */
@SpringBootApplication(excludeName = {
        "org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration",
        "org.springframework.boot.actuate.autoconfigure.security.servlet.ManagementWebSecurityAutoConfiguration"})
@EnableApidoc(apiPackageClasses = McpOrderController.class, paramType = ApiStrategyType.YAML, path = "csap-mcp-e2e")
public class McpE2EApplication {
}
