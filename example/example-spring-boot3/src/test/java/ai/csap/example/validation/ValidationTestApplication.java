package ai.csap.example.validation;

import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Import;

import ai.csap.apidoc.ApiStrategyType;
import ai.csap.apidoc.boot.autoconfigure.EnableApidoc;
import ai.csap.example.controller.ProductController;

/**
 * 运行时校验测试应用:AccountController(jakarta 注解)+ ProductController(@ApiOperation 写在接口上).
 * 不加载示例的安全配置。
 */
@SpringBootConfiguration
@EnableAutoConfiguration(excludeName = {
        "org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration",
        "org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration",
        "org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration",
        "org.springframework.boot.actuate.autoconfigure.security.servlet.ManagementWebSecurityAutoConfiguration"})
@EnableApidoc(apiPackageClasses = {AccountController.class, ProductController.class}, paramType = ApiStrategyType.YAML)
@Import({AccountController.class, ProductController.class})
public class ValidationTestApplication {
}
