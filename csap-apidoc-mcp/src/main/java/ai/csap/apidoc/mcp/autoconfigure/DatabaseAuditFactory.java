package ai.csap.apidoc.mcp.autoconfigure;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.Environment;

import ai.csap.apidoc.database.autoconfigure.DatabaseAutoConfiguration;
import ai.csap.apidoc.database.autoconfigure.DatabaseProperties;
import ai.csap.apidoc.database.autoconfigure.DatabaseTarget;
import ai.csap.apidoc.mcp.audit.AuditSink;
import ai.csap.apidoc.mcp.audit.JdbcAuditStore;

import javax.sql.DataSource;

/**
 * 创建写入数据库模式的库的审计落库对象。
 *
 * <p>只在 classpath 里有 csap-apidoc-database 时才会被加载({@link McpAutoConfiguration} 先检查),
 * 所以 csap-apidoc-mcp 对数据库模块只是可选依赖。任何失败都返回 null(调用方退回内存),不抛异常。
 *
 * @author ycf
 * @since 1.5.0
 */
final class DatabaseAuditFactory {

    private static final Logger LOG = LoggerFactory.getLogger(DatabaseAuditFactory.class);

    private DatabaseAuditFactory() {
    }

    static AuditSink create(McpProperties.Audit audit, BeanFactory beanFactory, Environment environment) {
        try {
            DatabaseProperties dbProps = Binder.get(environment).bind(DatabaseProperties.PREFIX, DatabaseProperties.class)
                                               .orElseGet(DatabaseProperties::new);
            DatabaseTarget target = DatabaseAutoConfiguration.resolveTarget(dbProps,
                    beanFactory.getBeanProvider(DataSource.class), beanFactory, environment);
            JdbcAuditStore store = new JdbcAuditStore(target.getDataSource(), target.getDialect(),
                    target.getTablePrefix(), target.getService(), audit.getMaxRows());
            store.init(target.isInitSchema() && !target.isReadOnly());
            LOG.info("csap-apidoc MCP: 审计同时写入数据库({})", store.describe());
            return store;
        } catch (Exception | LinkageError e) {
            LOG.warn("csap-apidoc MCP: 审计写入数据库不可用,改为只记录在内存: {}", e.getMessage());
            return null;
        }
    }
}
