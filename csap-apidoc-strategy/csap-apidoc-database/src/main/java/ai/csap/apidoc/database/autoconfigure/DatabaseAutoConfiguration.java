package ai.csap.apidoc.database.autoconfigure;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Locale;

import javax.sql.DataSource;

import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

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
        return new DatabaseParamGroupStrategy(factory,
                () -> createStore(properties, dataSources, beanFactory, environment),
                properties.isInitSchema(), properties.isReadOnly(), properties.getPollInterval());
    }

    static DatabaseRuleStore createStore(DatabaseProperties properties, ObjectProvider<DataSource> dataSources,
                                         BeanFactory beanFactory, Environment environment) {
        DataSource dataSource = resolveDataSource(properties, dataSources, beanFactory);
        DatabaseDialect dialect = DatabaseDialect.parse(properties.getDialect());
        if (dialect == null) {
            dialect = detect(dataSource);
        }
        return new DatabaseRuleStore(dataSource, dialect, properties.getTablePrefix(), resolveService(properties, environment));
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
