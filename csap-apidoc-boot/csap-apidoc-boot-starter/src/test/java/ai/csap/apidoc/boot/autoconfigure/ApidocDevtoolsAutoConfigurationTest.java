package ai.csap.apidoc.boot.autoconfigure;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import ai.csap.apidoc.autoconfigure.EnableApidocConfig;
import ai.csap.apidoc.devtools.core.ApidocDevtools;
import ai.csap.apidoc.devtools.proxy.DevtoolsProxyController;
import ai.csap.apidoc.devtools.proxy.DevtoolsProxyProperties;
import ai.csap.apidoc.properties.CsapDocConfig;

import static org.assertj.core.api.Assertions.assertThat;

class ApidocDevtoolsAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(ApidocDevtoolsAutoConfiguration.class)
            .withBean(EnableApidocConfig.class, EnableApidocConfig::new)
            .withBean(CsapDocConfig.class, CsapDocConfig::new);

    @Test
    void devtoolsScanDoesNotRegisterProxyControllerWhenProxyIsDisabled() {
        contextRunner
                .withPropertyValues("csap.apidoc.devtool.enabled=true")
                .run(ctx -> {
                    assertThat(ctx).hasSingleBean(ApidocDevtools.class);
                    assertThat(ctx).doesNotHaveBean(DevtoolsProxyController.class);
                    assertThat(ctx).doesNotHaveBean(DevtoolsProxyProperties.class);
                });
    }
}
