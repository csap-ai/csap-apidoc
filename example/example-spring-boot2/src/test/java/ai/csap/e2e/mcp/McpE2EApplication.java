package ai.csap.e2e.mcp;

import org.springframework.boot.autoconfigure.SpringBootApplication;

import ai.csap.apidoc.ApiStrategyType;
import ai.csap.apidoc.boot.autoconfigure.EnableApidoc;

/**
 * MCP 端到端测试应用(Spring Boot 2,javax.servlet)
 */
@SpringBootApplication
@EnableApidoc(apiPackageClasses = McpOrderController.class, paramType = ApiStrategyType.YAML, path = "csap-mcp-e2e")
public class McpE2EApplication {
}
