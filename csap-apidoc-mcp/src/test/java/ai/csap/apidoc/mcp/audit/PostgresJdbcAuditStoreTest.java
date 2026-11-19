package ai.csap.apidoc.mcp.audit;

import javax.sql.DataSource;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.testcontainers.containers.PostgreSQLContainer;

import ai.csap.apidoc.database.DatabaseDialect;

/**
 * PostgreSQL 16(Testcontainers);没有 Docker 时跳过。
 */
class PostgresJdbcAuditStoreTest extends AbstractJdbcAuditStoreTest {

    private static PostgreSQLContainer<?> container;
    private static DataSource dataSource;

    @BeforeAll
    static void start() {
        Assumptions.assumeTrue(MySqlJdbcAuditStoreTest.dockerAvailable(), "没有 Docker,跳过 PostgreSQL 测试");
        container = new PostgreSQLContainer<>("postgres:16-alpine");
        container.start();
        dataSource = new TestDataSource(container.getJdbcUrl(), container.getUsername(), container.getPassword());
    }

    @AfterAll
    static void stop() {
        if (container != null) {
            container.stop();
        }
    }

    @Override
    DataSource dataSource() {
        return dataSource;
    }

    @Override
    DatabaseDialect dialect() {
        return DatabaseDialect.POSTGRESQL;
    }
}
