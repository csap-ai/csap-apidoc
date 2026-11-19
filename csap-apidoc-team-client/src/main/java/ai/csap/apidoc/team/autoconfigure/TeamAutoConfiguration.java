package ai.csap.apidoc.team.autoconfigure;

import java.io.IOException;
import java.io.InputStream;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.URI;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Properties;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.context.WebServerInitializedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import ai.csap.apidoc.team.TeamRegistrar;

/**
 * 向团队版注册服务的自动配置。默认关闭,{@code csap.apidoc.team.enabled=true} 且配置了 {@code url} 时生效。
 *
 * <p>Web 服务器启动完成后(此时才知道端口)在后台线程注册,应用关闭时发送注销;
 * 同一个 jar 可用于 Spring Boot 2 和 3。
 *
 * @author ycf
 * @since 1.5.0
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = TeamProperties.PREFIX, name = "enabled", havingValue = "true")
@EnableConfigurationProperties(TeamProperties.class)
public class TeamAutoConfiguration {

    private static final Logger LOG = LoggerFactory.getLogger(TeamAutoConfiguration.class);

    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean
    public TeamRegistrationStarter csapTeamRegistrationStarter(TeamProperties properties, Environment environment) {
        return new TeamRegistrationStarter(properties, environment, TeamAutoConfiguration::firstNonLoopbackIpv4);
    }

    /**
     * 监听 Web 服务器启动事件,拿到端口后创建并启动 {@link TeamRegistrar}。
     */
    public static class TeamRegistrationStarter
            implements ApplicationListener<WebServerInitializedEvent>, AutoCloseable {

        private final TeamProperties properties;
        private final Environment environment;
        private final Supplier<String> localAddress;
        private volatile TeamRegistrar registrar;

        public TeamRegistrationStarter(TeamProperties properties, Environment environment,
                                       Supplier<String> localAddress) {
            this.properties = properties;
            this.environment = environment;
            this.localAddress = localAddress;
        }

        @Override
        public synchronized void onApplicationEvent(WebServerInitializedEvent event) {
            String ns = event.getApplicationContext().getServerNamespace();
            // management 等独立端口的服务器不算
            boolean independentServer = ns != null && !ns.isEmpty();
            if (registrar != null || independentServer) {
                return;
            }
            try {
                registrar = create(event.getWebServer().getPort());
                if (registrar != null) {
                    registrar.start();
                }
            } catch (RuntimeException e) {
                // 注册相关的任何问题都不能影响应用启动
                LOG.warn("csap-apidoc 团队版注册未启动: {}", e.getMessage());
                registrar = null;
            }
        }

        TeamRegistrar create(int port) {
            if (!hasText(properties.getUrl())) {
                LOG.warn("csap-apidoc 团队版注册已开启但 csap.apidoc.team.url 为空,不注册");
                return null;
            }
            try {
                URI u = URI.create(properties.getUrl().trim());
                boolean httpScheme = "http".equals(u.getScheme()) || "https".equals(u.getScheme());
                if (u.getHost() == null || !httpScheme) {
                    throw new IllegalArgumentException("not http(s)");
                }
            } catch (IllegalArgumentException e) {
                LOG.warn("csap-apidoc 团队版地址 csap.apidoc.team.url 不是有效的 http(s) 地址,不注册");
                return null;
            }
            String baseUrl = resolveBaseUrl(port);
            if (baseUrl == null) {
                LOG.warn("csap-apidoc 无法推断本服务地址,请配置 csap.apidoc.team.base-url,不注册");
                return null;
            }
            String docPath = hasText(properties.getDocPath()) ? properties.getDocPath().trim() : "/csap/apidoc";
            if (!docPath.startsWith("/")) {
                docPath = "/" + docPath;
            }
            return new TeamRegistrar(new TeamRegistrar.Registration(properties.getUrl(), properties.getToken(),
                    resolveService(), resolveInstanceId(baseUrl), baseUrl, docPath, "java", frameworkVersion()));
        }

        String resolveService() {
            if (hasText(properties.getService())) {
                return properties.getService().trim();
            }
            for (String key : new String[]{"csap.apidoc.database.service", "spring.application.name"}) {
                String v = environment.getProperty(key);
                if (hasText(v)) {
                    return v.trim();
                }
            }
            return "default";
        }

        String resolveBaseUrl(int port) {
            if (hasText(properties.getBaseUrl())) {
                return TeamRegistrar.stripTrailingSlash(properties.getBaseUrl());
            }
            String host = localAddress.get();
            if (host == null || port <= 0) {
                return null;
            }
            boolean ssl = Boolean.parseBoolean(environment.getProperty("server.ssl.enabled", "false"));
            return (ssl ? "https" : "http") + "://" + host + ":" + port + contextPath();
        }

        private String contextPath() {
            String p = environment.getProperty("server.servlet.context-path", "");
            if (p == null || p.trim().isEmpty() || "/".equals(p.trim())) {
                return "";
            }
            p = p.trim();
            p = p.startsWith("/") ? p : "/" + p;
            return p.endsWith("/") ? p.substring(0, p.length() - 1) : p;
        }

        String resolveInstanceId(String baseUrl) {
            if (hasText(properties.getInstanceId())) {
                return properties.getInstanceId().trim();
            }
            try {
                String authority = URI.create(baseUrl).getRawAuthority();
                if (hasText(authority)) {
                    return authority;
                }
            } catch (IllegalArgumentException ignore) {
                // 落到下面
            }
            return baseUrl;
        }

        @Override
        public void close() {
            TeamRegistrar r = registrar;
            if (r != null) {
                r.close();
            }
        }
    }

    /**
     * 构建时写入的版本号(csap-apidoc-team-client.properties),读不到时为 unknown。
     */
    static String frameworkVersion() {
        try (InputStream in = TeamAutoConfiguration.class.getClassLoader()
                .getResourceAsStream("csap-apidoc-team-client.properties")) {
            if (in != null) {
                Properties p = new Properties();
                p.load(in);
                String v = p.getProperty("version");
                if (hasText(v) && !v.contains("${")) {
                    return v.trim();
                }
            }
        } catch (IOException ignore) {
            // 落到 unknown
        }
        return "unknown";
    }

    /**
     * 本机首个非回环 IPv4 地址;没有时返回 null。
     */
    static String firstNonLoopbackIpv4() {
        try {
            Enumeration<NetworkInterface> nics = NetworkInterface.getNetworkInterfaces();
            if (nics == null) {
                return null;
            }
            for (NetworkInterface nic : Collections.list(nics)) {
                if (!nic.isUp() || nic.isLoopback() || nic.isVirtual()) {
                    continue;
                }
                for (InetAddress addr : Collections.list(nic.getInetAddresses())) {
                    if (addr instanceof Inet4Address && !addr.isLoopbackAddress() && !addr.isLinkLocalAddress()) {
                        return addr.getHostAddress();
                    }
                }
            }
        } catch (IOException | RuntimeException e) {
            LOG.debug("读取本机网卡失败: {}", e.getMessage());
        }
        return null;
    }

    private static boolean hasText(String s) {
        return s != null && !s.trim().isEmpty();
    }
}
