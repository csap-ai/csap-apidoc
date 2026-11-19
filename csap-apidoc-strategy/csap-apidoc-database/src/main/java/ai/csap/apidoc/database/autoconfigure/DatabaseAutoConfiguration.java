package ai.csap.apidoc.database.autoconfigure;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Locale;
import java.util.Set;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import ai.csap.apidoc.service.ApidocContext;
import ai.csap.apidoc.database.DatabaseDialect;
import ai.csap.apidoc.database.DatabaseParamGroupStrategy;
import ai.csap.apidoc.database.DatabaseRuleStore;
import ai.csap.validation.factory.IValidateFactory;
import ai.csap.validation.factory.DefaultValidateFactoryImpl;
import ai.csap.validation.properties.ValidationProperties;
import org.hibernate.validator.internal.metadata.core.ConstraintHelper;

/**
 * 数据库模式的自动配置:注册 {@link DatabaseParamGroupStrategy}。
 *
 * <p>Bean 总是注册,但数据库只在真正用到时才连接(第一次查规则或保存规则);
 * 没有使用 {@code paramType = DATABASE} 的应用不会碰数据库,也不要求有 DataSource。
 * 只依赖 JDBC 的 {@link DataSource},同一个 jar 可用于 Spring Boot 2 和 3。
 *
 * @author ycf
 * @since 1.4.0
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(DatabaseProperties.class)
public class DatabaseAutoConfiguration {

    private static final Logger LOG = LoggerFactory.getLogger(DatabaseAutoConfiguration.class);

    @Bean
    @ConditionalOnMissingBean
    public DatabaseParamGroupStrategy databaseParamGroupStrategy(DatabaseProperties properties,
                                                                 ObjectProvider<IValidateFactory> validateFactory,
                                                                 ObjectProvider<DataSource> dataSources,
                                                                 BeanFactory beanFactory,
                                                                 Environment environment) {
        IValidateFactory factory = validateFactory.getIfAvailable();
        if (factory == null) {
            factory = new DefaultValidateFactoryImpl(ConstraintHelper.forAllBuiltinConstraints(), new ValidationProperties());
        }
        DatabaseParamGroupStrategy strategy = new DatabaseParamGroupStrategy(factory,
                () -> createStore(properties, dataSources, beanFactory, environment),
                properties.isInitSchema(), properties.isReadOnly(), properties.getPollInterval());
        // 别的实例保存(或本实例回滚)后重新加载了规则:按新规则重新生成受影响类的文档和字段规则
        strategy.setReloadListener(DatabaseAutoConfiguration::refreshDocuments);
        return strategy;
    }

    /**
     * 规则变化后重新生成文档和字段规则(运行时过滤 / 校验靠规则刷新事件重建缓存)。
     * DevTools 开启时与 DevTools 保存后的做法一致:按类刷新(flush 必须为 false,否则会把该类再追加一份);
     * DevTools 关闭(生产环境)时按类刷新不生效,改为整体重新扫描。
     */
    static void refreshDocuments(Set<String> classNames) {
        try {
            if (Boolean.TRUE.equals(ApidocContext.isDevEnabled())) {
                for (String className : classNames) {
                    try {
                        ApidocContext.cmd(className, null, false);
                    } catch (RuntimeException e) {
                        LOG.warn("csap-apidoc: 规则已同步,但重新生成 {} 的文档失败: {}", className, e.getMessage());
                    }
                }
            } else {
                ApidocContext.cmd(true, false);
            }
            LOG.info("csap-apidoc: 数据库规则已变化,已重新生成 {} 个类的文档和字段规则", classNames.size());
        } catch (RuntimeException e) {
            LOG.warn("csap-apidoc: 规则已同步,但重新生成文档失败: {}", e.getMessage());
        }
    }

    static DatabaseRuleStore createStore(DatabaseProperties properties, ObjectProvider<DataSource> dataSources,
                                         BeanFactory beanFactory, Environment environment) {
        DatabaseTarget t = resolveTarget(properties, dataSources, beanFactory, environment);
        return new DatabaseRuleStore(t.getDataSource(), t.getDialect(), t.getTablePrefix(), t.getService());
    }

    /**
     * 解析数据库模式使用的 DataSource、方言、表前缀和服务标识(不连接规则表、不建表;识别方言时会取一次连接)。
     * csap-apidoc-mcp 把审计写进同一个库时用它,保证与数据库模式用的是同一份配置。
     *
     * @param properties  数据库模式配置
     * @param dataSources 容器中的 DataSource
     * @param beanFactory Bean 工厂
     * @param environment 环境
     * @return 解析结果
     * @throws IllegalStateException 找不到(或无法确定)DataSource、识别不了方言
     * @since 1.5.0
     */
    public static DatabaseTarget resolveTarget(DatabaseProperties properties, ObjectProvider<DataSource> dataSources,
                                               BeanFactory beanFactory, Environment environment) {
        DataSource dataSource = resolveDataSource(properties, dataSources, beanFactory);
        DatabaseDialect dialect = DatabaseDialect.parse(properties.getDialect());
        if (dialect == null) {
            dialect = detect(dataSource);
        }
        return new DatabaseTarget(dataSource, dialect, properties.getTablePrefix(),
                resolveService(properties, environment), properties.isInitSchema(), properties.isReadOnly());
    }

    static String resolveService(DatabaseProperties properties, Environment environment) {
        if (hasText(properties.getService())) {
            return properties.getService().trim();
        }
        String name = environment == null ? null : environment.getProperty("spring.application.name");
        return hasText(name) ? name.trim() : "default";
    }

    private static DataSource resolveDataSource(DatabaseProperties properties, ObjectProvider<DataSource> dataSources,
                                                BeanFactory beanFactory) {
        String name = properties.getDatasourceBean();
        if (hasText(name)) {
            try {
                return beanFactory.getBean(name.trim(), DataSource.class);
            } catch (RuntimeException e) {
                throw new IllegalStateException("@EnableApidoc(paramType = ApiStrategyType.DATABASE) 需要 DataSource,"
                        + "但找不到 csap.apidoc.database.datasource-bean=" + name + " 对应的 DataSource Bean: "
                        + e.getMessage(), e);
            }
        }
        DataSource unique = dataSources.getIfUnique();
        if (unique != null) {
            return unique;
        }
        long count = dataSources.stream().count();
        throw new IllegalStateException(count == 0
                ? "@EnableApidoc(paramType = ApiStrategyType.DATABASE) 需要应用提供 DataSource,但容器中没有 DataSource Bean。"
                + "请配置数据源(如 spring.datasource.url),并在 classpath 加入对应的 JDBC 驱动。"
                : "容器中有 " + count + " 个 DataSource,无法确定数据库模式用哪个。"
                + "请设置 csap.apidoc.database.datasource-bean 指定 Bean 名称,或把其中一个标记为 @Primary。");
    }

    private static DatabaseDialect detect(DataSource dataSource) {
        try (Connection conn = dataSource.getConnection()) {
            String url = conn.getMetaData().getURL();
            DatabaseDialect d = DatabaseDialect.fromJdbcUrl(url);
            if (d != null) {
                return d;
            }
            String product = conn.getMetaData().getDatabaseProductName();
            String p = product == null ? "" : product.toLowerCase(Locale.ROOT);
            if (p.contains("mysql") || p.contains("mariadb")) {
                return DatabaseDialect.MYSQL;
            }
            if (p.contains("postgres")) {
                return DatabaseDialect.POSTGRESQL;
            }
            if (p.startsWith("dm")) {
                return DatabaseDialect.DM;
            }
            if (p.contains("kingbase")) {
                return DatabaseDialect.KINGBASE;
            }
            if (p.contains("sqlite")) {
                return DatabaseDialect.SQLITE;
            }
            if (p.contains("h2")) {
                return DatabaseDialect.H2;
            }
            throw new IllegalStateException("无法从 JDBC URL(" + url + ")或数据库产品名(" + product + ")识别方言,"
                    + "请设置 csap.apidoc.database.dialect(mysql / postgresql / dm / kingbase / sqlite / h2)。");
        } catch (SQLException e) {
            throw new IllegalStateException("连接数据库失败,无法识别方言: " + e.getMessage(), e);
        }
    }

    private static boolean hasText(String s) {
        return s != null && !s.trim().isEmpty();
    }
}
