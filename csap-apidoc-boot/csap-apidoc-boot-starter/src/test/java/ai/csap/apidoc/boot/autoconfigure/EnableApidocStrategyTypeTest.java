package ai.csap.apidoc.boot.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import ai.csap.apidoc.ApiStrategyType;
import ai.csap.apidoc.service.ApidocContext;
import ai.csap.apidoc.strategy.ApidocStrategy;
import ai.csap.apidoc.strategy.standard.JsonApidocStrategy;
import ai.csap.apidoc.strategy.standard.YamlApidocStrategy;

/**
 * {@code @EnableApidoc(type = ...)} 必须有对应的文档策略 Bean;不支持的 type 启动即报错,不能静默生成空文档。
 */
class EnableApidocStrategyTypeTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ApidocAutoConfiguration.class));

    @Configuration
    @EnableApidoc(type = ApiStrategyType.YAML, path = "csap-api-doc")
    static class YamlApp {
    }

    @Configuration
    @EnableApidoc(type = ApiStrategyType.JSON, path = "csap-api-doc")
    static class JsonApp {
    }

    @Configuration
    @EnableApidoc(type = ApiStrategyType.SQL_LITE)
    static class SqliteApp {
    }

    @Configuration
    @EnableApidoc(paramType = ApiStrategyType.YAML)
    static class AnnotationWithYamlParamApp {
    }

    private static Set<String> strategyNames(org.springframework.context.ApplicationContext ctx) {
        return ctx.getBeansOfType(ApidocStrategy.class).values().stream()
                .map(ApidocStrategy::getName).collect(Collectors.toSet());
    }

    @Test
    void yamlTypeRegistersYamlStrategy() {
        runner.withUserConfiguration(YamlApp.class).run(ctx -> {
            assertThat(ctx).hasNotFailed();
            assertThat(ctx).hasSingleBean(YamlApidocStrategy.class);
            assertThat(ctx).doesNotHaveBean(JsonApidocStrategy.class);
            assertThat(strategyNames(ctx)).contains(ApiStrategyType.YAML.getName());
            // ApidocContext 按 docType 能取到策略(之前取到 null,文档静默为空)
            assertThat(ApidocContext.apidocStrategy(ApiStrategyType.YAML)).isInstanceOf(YamlApidocStrategy.class);
        });
    }

    @Test
    void jsonTypeRegistersJsonStrategy() {
        runner.withUserConfiguration(JsonApp.class).run(ctx -> {
            assertThat(ctx).hasNotFailed();
            assertThat(ctx).hasSingleBean(JsonApidocStrategy.class);
            assertThat(ApidocContext.apidocStrategy(ApiStrategyType.JSON)).isInstanceOf(JsonApidocStrategy.class);
        });
    }

    @Test
    void sqliteTypeFailsFastWithClearMessage() {
        runner.withUserConfiguration(SqliteApp.class).run(ctx -> {
            assertThat(ctx).hasFailed();
            Throwable root = ctx.getStartupFailure();
            while (root.getCause() != null) {
                root = root.getCause();
            }
            assertThat(root).isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("SQL_LITE")
                    .hasMessageContaining("paramType");
        });
    }

    @Test
    void annotationTypeWithYamlParamTypeStillWorks() {
        runner.withUserConfiguration(AnnotationWithYamlParamApp.class).run(ctx -> {
            assertThat(ctx).hasNotFailed();
            assertThat(ctx).doesNotHaveBean(YamlApidocStrategy.class);
            assertThat(strategyNames(ctx)).contains(ApiStrategyType.ANNOTATION.getName());
            assertThat(ctx).hasBean("yamlParamGroupStrategy");
        });
    }
}
