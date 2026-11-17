package ai.csap.validation.boot.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import ai.csap.validation.ValidateIntercept;

/**
 * csap.validation.enabled 的配置元数据必须与实际行为一致:不配置时校验拦截不生效。
 */
class ValidationEnabledMetadataTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ValidationAutoConfiguration.class));

    @Test
    void interceptorRequiresExplicitEnabledTrue() {
        runner.run(ctx -> assertThat(ctx).doesNotHaveBean(ValidateIntercept.class));
        runner.withPropertyValues("csap.validation.enabled=true")
                .run(ctx -> assertThat(ctx).hasSingleBean(ValidateIntercept.class));
    }

    @Test
    void metadataDefaultIsFalse() throws Exception {
        JsonNode enabled = null;
        try (InputStream in = getClass().getClassLoader()
                .getResourceAsStream("META-INF/additional-spring-configuration-metadata.json")) {
            for (JsonNode p : new ObjectMapper().readTree(in).get("properties")) {
                if ("csap.validation.enabled".equals(p.get("name").asText())) {
                    enabled = p;
                }
            }
        }
        assertThat(enabled).isNotNull();
        assertThat(enabled.get("defaultValue").asText()).isEqualTo("false");
    }
}
