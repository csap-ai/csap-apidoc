package ai.csap.validation.boot.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import ai.csap.validation.ValidateIntercept;
import ai.csap.validation.advice.ValidateExceptionHandler;
import ai.csap.validation.factory.IValidateFactory;
import ai.csap.validation.runtime.ValidationExecutor;

/**
 * 运行时校验默认关闭;配置元数据与实际行为一致.
 */
class ValidationEnabledMetadataTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ValidationAutoConfiguration.class));

    @Test
    void runtimeValidationIsOffByDefault() {
        runner.run(ctx -> {
            assertThat(ctx).doesNotHaveBean(ValidateIntercept.class);
            assertThat(ctx).doesNotHaveBean(ValidationExecutor.class);
            // 生成文档需要规则工厂,统一返回需要异常处理器:总是注册
            assertThat(ctx).hasSingleBean(IValidateFactory.class);
            assertThat(ctx).hasSingleBean(ValidateExceptionHandler.class);
            assertThat(ctx).hasBean("jakartaConstraintViolationExceptionHandler");
            assertThat(ctx).hasBean("javaxConstraintViolationExceptionHandler");
        });
        runner.withPropertyValues("csap.validation.mode=off")
                .run(ctx -> assertThat(ctx).doesNotHaveBean(ValidateIntercept.class));
        runner.withPropertyValues("csap.validation.enabled=false", "csap.validation.mode=enforce")
                .run(ctx -> assertThat(ctx).doesNotHaveBean(ValidateIntercept.class));
    }

    @Test
    void anyActiveModeRegistersRuntime() {
        for (String property : new String[]{"csap.validation.enabled=true", "csap.validation.mode=observe",
                "csap.validation.mode=ENFORCE", "csap.validation.endpoints[com.x.UserController.create]=enforce"}) {
            runner.withPropertyValues(property).run(ctx -> {
                assertThat(ctx).as(property).hasSingleBean(ValidateIntercept.class);
                assertThat(ctx).as(property).hasSingleBean(ValidationExecutor.class);
            });
        }
    }

    @Test
    void metadataDefaults() throws Exception {
        Map<String, JsonNode> properties = new HashMap<>();
        try (InputStream in = getClass().getClassLoader()
                .getResourceAsStream("META-INF/additional-spring-configuration-metadata.json")) {
            for (JsonNode p : new ObjectMapper().readTree(in).get("properties")) {
                properties.put(p.get("name").asText(), p);
            }
        }
        assertThat(properties.get("csap.validation.enabled").get("defaultValue").asText()).isEqualTo("false");
        assertThat(properties.get("csap.validation.mode").get("defaultValue").asText()).isEqualTo("off");
        assertThat(properties.get("csap.validation.http-status").get("defaultValue").asInt()).isEqualTo(200);
        assertThat(properties).containsKey("csap.validation.endpoints");
    }
}
