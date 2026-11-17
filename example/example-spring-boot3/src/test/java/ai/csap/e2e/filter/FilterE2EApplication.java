package ai.csap.e2e.filter;

import org.springframework.boot.autoconfigure.SpringBootApplication;

import ai.csap.apidoc.ApiStrategyType;
import ai.csap.apidoc.boot.autoconfigure.EnableApidoc;

/**
 * 返回字段过滤端到端测试用的应用:规则用 YAML 策略保存,通过 DevTools 接口修改。
 */
@SpringBootApplication
@EnableApidoc(apiPackageClasses = OrderController.class, paramType = ApiStrategyType.YAML,
        path = FilterE2EApplication.RULE_PATH)
public class FilterE2EApplication {
    static final String RULE_PATH = "csap-filter-e2e";
}
