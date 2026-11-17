package ai.csap.apidoc.devtools.proxy;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.BufferedReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

/**
 * Spring Boot 2.x 读 spring.factories,3.x 只读 AutoConfiguration.imports;两处登记必须一致,
 * 否则 Boot 3 下 DevtoolsProxyAutoConfiguration 不生效。
 */
class AutoConfigurationRegistrationTest {

    private static final String FACTORIES = "META-INF/spring.factories";
    private static final String IMPORTS = "META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports";

    private static URL ownResource(String name) {
        URL url = AutoConfigurationRegistrationTest.class.getClassLoader().getResource(name);
        assertThat(url).as(name).isNotNull();
        return url;
    }

    private static Set<String> factoriesEntries() throws IOException {
        Properties properties = new Properties();
        try (InputStream in = ownResource(FACTORIES).openStream()) {
            properties.load(in);
        }
        String value = properties.getProperty("org.springframework.boot.autoconfigure.EnableAutoConfiguration", "");
        return Arrays.stream(value.split(",")).map(String::trim).filter(s -> !s.isEmpty())
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private static Set<String> importsEntries() throws IOException {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(ownResource(IMPORTS).openStream(), StandardCharsets.UTF_8))) {
            return reader.lines().map(String::trim).filter(s -> !s.isEmpty() && !s.startsWith("#"))
                    .collect(Collectors.toCollection(LinkedHashSet::new));
        }
    }

    @Test
    void proxyAutoConfigurationIsRegisteredForBoot2AndBoot3() throws IOException {
        assertThat(factoriesEntries()).contains(DevtoolsProxyAutoConfiguration.class.getName());
        assertThat(importsEntries()).contains(DevtoolsProxyAutoConfiguration.class.getName());
        assertThat(importsEntries()).isEqualTo(factoriesEntries());
    }
}
