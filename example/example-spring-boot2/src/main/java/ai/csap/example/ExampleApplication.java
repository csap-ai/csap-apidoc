package ai.csap.example;

import ai.csap.apidoc.ApiStrategyType;
import ai.csap.apidoc.boot.autoconfigure.EnableApidoc;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * CSAP API Doc Example Application
 * <p>
 * This example demonstrates:
 * 1. Using @EnableApidoc annotation
 * 2. API documentation with annotations
 * 3. Parameter validation
 * 4. DevTools integration
 * 5. Multiple storage strategies
 *
 * @author CSAP Team
 */
@SpringBootApplication
@EnableApidoc(
        value = "ai.csap.example",          // 扫描 ai.csap.example 下的全部 Controller
        paramType = ApiStrategyType.YAML    // 字段规则用 YAML 文件(csap-api-doc/ 目录)
)
public class ExampleApplication {
    public static void main(String[] args) {
        SpringApplication.run(ExampleApplication.class, args);
    }
}

