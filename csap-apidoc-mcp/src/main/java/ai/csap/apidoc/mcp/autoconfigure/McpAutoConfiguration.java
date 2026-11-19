package ai.csap.apidoc.mcp.autoconfigure;

import java.io.File;
import java.util.ArrayList;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.context.WebServerInitializedEvent;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.util.ClassUtils;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import ai.csap.apidoc.ApiStrategyType;
import ai.csap.apidoc.autoconfigure.EnableApidocConfig;
import ai.csap.apidoc.event.ApidocRulesRefreshedEvent;
import ai.csap.apidoc.mcp.McpService;
import ai.csap.apidoc.mcp.audit.AuditLog;
import ai.csap.apidoc.mcp.audit.AuditSink;
import ai.csap.apidoc.mcp.config.McpSettingsStore;
import ai.csap.apidoc.mcp.config.McpSettingsStores;
import ai.csap.apidoc.mcp.invoke.LoopbackInvoker;
import ai.csap.apidoc.mcp.protocol.McpServer;
import ai.csap.apidoc.mcp.security.McpAuthenticator;
import ai.csap.apidoc.mcp.tool.HandlerIndex;
import ai.csap.apidoc.mcp.web.McpEndpointController;
import ai.csap.apidoc.model.CsapDocResponse;
import ai.csap.apidoc.properties.CsapApiInfo;
import ai.csap.apidoc.service.ApidocContext;

/**
 * 内嵌 MCP 端点的自动配置。默认关闭,{@code csap.apidoc.mcp.enabled=true} 时生效。
 *
 * <p>只依赖 spring-web / spring-webmvc 的抽象,同一个 jar 可用于 Spring Boot 2 和 3。
 *
 * @author ycf
 * @since 1.3.0
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass(name = "org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping")
@ConditionalOnProperty(prefix = McpProperties.PREFIX, name = "enabled", havingValue = "true")
@EnableConfigurationProperties(McpProperties.class)
public class McpAutoConfiguration {

    private static final Logger LOG = LoggerFactory.getLogger(McpAutoConfiguration.class);

    static final String SERVER_NAME = "csap-apidoc";

    @Bean
    @ConditionalOnMissingBean
    public McpSettingsStore csapMcpSettingsStore(McpProperties properties,
                                                 ObjectProvider<EnableApidocConfig> enableApidocConfig) {
        EnableApidocConfig cfg = enableApidocConfig.getIfAvailable();
        McpSettingsStore store = McpSettingsStores.create(properties, cfg == null ? null : cfg.getPath(),
                cfg != null && cfg.getParamType() == ApiStrategyType.SQL_LITE, cfg == null ? null : cfg.getFileName());
        LOG.info("csap-apidoc MCP: 设置存储 {} {}{}", store.type(), store.location(),
                store.isPersistent() ? "" : "(修改不持久化)");
        return store;
    }

    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean
    public AuditLog csapMcpAuditLog(McpProperties properties, ObjectProvider<EnableApidocConfig> enableApidocConfig,
                                    BeanFactory beanFactory, Environment environment) {
        McpProperties.Audit a = properties.getAudit();
        switch (a.effectiveStore()) {
            case SQLITE:
                if (ClassUtils.isPresent("org.sqlite.JDBC", getClass().getClassLoader())) {
                    return new AuditLog(a.getCapacity(), new File(a.getSqliteFile()), a.getMaxRows());
                }
                LOG.warn("csap-apidoc MCP: 审计落盘需要 sqlite-jdbc,改为只记录在内存");
                break;
            case DATABASE:
                AuditSink sink = databaseSink(a, enableApidocConfig.getIfAvailable(), beanFactory, environment);
                if (sink != null) {
                    return new AuditLog(a.getCapacity(), sink);
                }
                break;
            default:
                break;
        }
        return new AuditLog(a.getCapacity(), (File) null, a.getMaxRows());
    }

    /**
     * csap.apidoc.mcp.audit.store=database:需要数据库模式已启用;不满足时 warn 并返回 null(退回内存)。
     */
    private AuditSink databaseSink(McpProperties.Audit audit, EnableApidocConfig cfg, BeanFactory beanFactory,
                                   Environment environment) {
        if (cfg == null || cfg.getParamType() != ApiStrategyType.DATABASE) {
            LOG.warn("csap-apidoc MCP: csap.apidoc.mcp.audit.store=database 需要启用数据库模式"
                    + "(@EnableApidoc(paramType = ApiStrategyType.DATABASE)),改为只记录在内存");
            return null;
        }
        if (!ClassUtils.isPresent("ai.csap.apidoc.database.autoconfigure.DatabaseTarget", getClass().getClassLoader())) {
            LOG.warn("csap-apidoc MCP: csap.apidoc.mcp.audit.store=database 需要 csap-apidoc-database 模块,改为只记录在内存");
            return null;
        }
        return DatabaseAuditFactory.create(audit, beanFactory, environment);
    }

    @Bean
    @ConditionalOnMissingBean
    public McpAuthenticator csapMcpAuthenticator(McpProperties properties) {
        McpAuthenticator authenticator = new McpAuthenticator(properties.getAuth().getTokens());
        if (properties.getAuth().getMode() == McpProperties.AuthMode.TOKEN && !authenticator.hasTokens()) {
            LOG.warn("csap-apidoc MCP: 鉴权方式为 token 但没有配置令牌(csap.apidoc.mcp.auth.tokens),所有请求都会被拒绝(401)");
        }
        return authenticator;
    }

    @Bean
    public LoopbackBaseUrl csapMcpLoopbackBaseUrl(McpProperties properties, Environment environment) {
        return new LoopbackBaseUrl(properties, environment);
    }

    @Bean
    @ConditionalOnMissingBean
    public LoopbackInvoker csapMcpLoopbackInvoker(McpProperties properties, LoopbackBaseUrl baseUrl) {
        return new LoopbackInvoker(baseUrl, properties.getCallTimeout());
    }

    @Bean
    @ConditionalOnMissingBean
    public McpService csapMcpService(McpProperties properties, McpSettingsStore store, LoopbackInvoker invoker,
                                     McpAuthenticator authenticator, AuditLog auditLog,
                                     ApplicationContext applicationContext,
                                     ObjectProvider<ApidocContext> apidocContext) {
        Supplier<CsapDocResponse> docs = () -> apidocContext.getIfAvailable() == null ? null
                : ApidocContext.cmd(false, false);
        Supplier<HandlerIndex> handlers = () -> new HandlerIndex(new ArrayList<>(
                applicationContext.getBeansOfType(RequestMappingHandlerMapping.class).values()));
        return new McpService(properties, store, docs, handlers, invoker, authenticator, auditLog);
    }

    @Bean
    @ConditionalOnMissingBean
    public McpServer csapMcpServer(McpService service, McpProperties properties,
                                   ObjectProvider<ApidocContext> apidocContext) {
        Supplier<CsapApiInfo> info = () -> {
            if (apidocContext.getIfAvailable() == null) {
                return null;
            }
            CsapDocResponse doc = ApidocContext.cmd(false, false);
            return doc == null ? null : doc.getApiInfo();
        };
        Supplier<String> title = () -> {
            CsapApiInfo i = info.get();
            return i == null ? null : i.getTitle();
        };
        Supplier<String> instructions = () -> {
            if (properties.getInstructions() != null && !properties.getInstructions().trim().isEmpty()) {
                return properties.getInstructions().trim();
            }
            CsapApiInfo i = info.get();
            StringBuilder sb = new StringBuilder();
            if (i != null && i.getTitle() != null) {
                sb.append(i.getTitle());
                if (i.getDescription() != null && !i.getDescription().isEmpty()) {
                    sb.append(": ").append(i.getDescription());
                }
                sb.append("\n");
            }
            sb.append("每个工具对应本服务的一个 HTTP 接口(说明末行是 HTTP 方法和路径)。"
                    + "工具返回接口的原始响应体;非 2xx 时 isError=true 且文本以 \"HTTP 状态码\" 开头。");
            return sb.toString();
        };
        String version = McpAutoConfiguration.class.getPackage().getImplementationVersion();
        return new McpServer(service, SERVER_NAME, version == null ? "dev" : version, title, instructions);
    }

    @Bean
    public McpEndpointController csapMcpEndpointController(McpServer server, McpService service,
                                                           McpProperties properties) {
        LOG.info("csap-apidoc MCP: 端点已开启 POST {}(鉴权方式 {})", properties.getPath(),
                service.authMode().name().toLowerCase());
        return new McpEndpointController(server, service, properties.getAllowedOrigins());
    }

    /**
     * 文档刷新(扫描完成、DevTools 修改规则)后重建工具目录
     */
    @Bean
    public ApplicationListener<ApidocRulesRefreshedEvent> csapMcpDocumentListener(McpService service) {
        return event -> service.onDocumentRefreshed();
    }

    /**
     * 回环地址:配置的 base-url,或 http://{server.address 或 127.0.0.1}:{端口}{context-path}{servlet-path}
     */
    public static class LoopbackBaseUrl implements Supplier<String>, ApplicationListener<WebServerInitializedEvent> {

        private final McpProperties properties;
        private final Environment environment;
        private volatile int port = -1;

        public LoopbackBaseUrl(McpProperties properties, Environment environment) {
            this.properties = properties;
            this.environment = environment;
        }

        @Override
        public void onApplicationEvent(WebServerInitializedEvent event) {
            String ns = event.getApplicationContext().getServerNamespace();
            // 忽略 management 等独立端口
            if (ns == null || ns.isEmpty()) {
                port = event.getWebServer().getPort();
            }
        }

        @Override
        public String get() {
            if (properties.getBaseUrl() != null && !properties.getBaseUrl().trim().isEmpty()) {
                return properties.getBaseUrl().trim();
            }
            int p = port > 0 ? port : environment.getProperty("local.server.port", Integer.class, -1);
            if (p <= 0) {
                return null;
            }
            String address = environment.getProperty("server.address");
            String host = address == null || address.isEmpty() || "0.0.0.0".equals(address) || "::".equals(address)
                    ? "127.0.0.1" : (address.contains(":") ? "[" + address + "]" : address);
            String contextPath = trim(environment.getProperty("server.servlet.context-path", ""));
            String servletPath = trim(environment.getProperty("spring.mvc.servlet.path", ""));
            return "http://" + host + ":" + p + contextPath + servletPath;
        }

        private static String trim(String p) {
            if (p == null || p.isEmpty() || "/".equals(p)) {
                return "";
            }
            String r = p.startsWith("/") ? p : "/" + p;
            return r.endsWith("/") ? r.substring(0, r.length() - 1) : r;
        }
    }
}
