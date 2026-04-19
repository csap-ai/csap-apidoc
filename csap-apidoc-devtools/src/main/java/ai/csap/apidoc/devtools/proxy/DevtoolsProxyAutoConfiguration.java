package ai.csap.apidoc.devtools.proxy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Auto-configuration for the optional {@code POST /csap/apidoc/devtools/proxy}
 * reverse-proxy endpoint.
 *
 * <p>Activation: {@code csap.apidoc.devtools.proxy.enabled=true} on the host
 * application's environment. When the flag is absent / false, none of the
 * proxy beans are registered and the endpoint returns 404.
 *
 * <p>Registered beans:
 * <ul>
 *   <li>{@link DevtoolsProxyProperties} — bound from
 *       {@code csap.apidoc.devtools.proxy.*}.</li>
 *   <li>{@link HostAllowList} — built once from the configured allowlist.</li>
 *   <li>{@link UpstreamClient} — wraps a timeout-configured RestTemplate.</li>
 *   <li>{@link DevtoolsProxyController} — the actual HTTP endpoint.</li>
 * </ul>
 *
 * <p>This class is wired via {@code META-INF/spring.factories} (Spring Boot
 * 2.x discovery), matching the pattern used by the existing
 * {@code ApidocDevtoolsAutoConfiguration} in
 * {@code csap-apidoc-boot-starter}.
 *
 * @author yangchengfu
 * @since 1.0
 */
@Configuration
@ConditionalOnProperty(prefix = "csap.apidoc.devtools.proxy",
        name = "enabled", havingValue = "true")
@EnableConfigurationProperties(DevtoolsProxyProperties.class)
public class DevtoolsProxyAutoConfiguration {

    private static final Logger LOG = LoggerFactory.getLogger(DevtoolsProxyAutoConfiguration.class);

    private final DevtoolsProxyProperties props;

    public DevtoolsProxyAutoConfiguration(DevtoolsProxyProperties props) {
        this.props = props;
        // Emit a clear startup banner whenever the proxy is enabled, with extra
        // loud warnings for misconfigurations and the SSRF risk. Done in the
        // constructor (not @PostConstruct) because javax.annotation.PostConstruct
        // is no longer in JDK 11+ and we don't want to pull jakarta-annotation
        // just for one log line.
        LOG.warn("[devtools-proxy] csap-apidoc-devtools reverse-proxy ENABLED at "
                + "POST /csap/apidoc/devtools/proxy. This is an SSRF primitive — "
                + "never expose this endpoint on a public network boundary.");
        if (props.getAllowedHosts() == null || props.getAllowedHosts().isEmpty()) {
            LOG.warn("[devtools-proxy] csap.apidoc.devtools.proxy.allowed-hosts is EMPTY. "
                    + "Every proxy request will be denied (fail-closed). "
                    + "Populate the list to actually use the proxy.");
        } else {
            LOG.info("[devtools-proxy] allowed-hosts={} max-body-bytes={} timeout-ms={} log-bodies={}",
                    props.getAllowedHosts(), props.getMaxBodyBytes(),
                    props.getTimeoutMs(), props.isLogBodies());
        }
    }

    @Bean
    @ConditionalOnMissingBean
    public HostAllowList devtoolsProxyHostAllowList() {
        return new HostAllowList(props.getAllowedHosts());
    }

    @Bean
    @ConditionalOnMissingBean
    public UpstreamClient devtoolsProxyUpstreamClient() {
        return new UpstreamClient(props);
    }

    @Bean
    @ConditionalOnMissingBean
    public DevtoolsProxyController devtoolsProxyController(HostAllowList allowList,
                                                           UpstreamClient upstream,
                                                           ObjectMapper objectMapper) {
        return new DevtoolsProxyController(props, allowList, upstream, objectMapper);
    }
}
