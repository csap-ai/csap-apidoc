package ai.csap.apidoc.database;

import javax.sql.DataSource;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * PostgreSQL 16(Testcontainers);没有 Docker 时跳过。
 */
class PostgresDatabaseRuleTest extends AbstractDatabaseRuleTest {

    private static PostgreSQLContainer<?> container;
    private static DataSource dataSource;

    @BeforeAll
    static void start() {
        Assumptions.assumeTrue(MySqlDatabaseRuleTest.dockerAvailable(), "没有 Docker,跳过 PostgreSQL 测试");
        container = new PostgreSQLContainer<>("postgres:16-alpine");
        container.start();
        dataSource = new SimpleDataSource(container.getJdbcUrl(), container.getUsername(), container.getPassword());
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
