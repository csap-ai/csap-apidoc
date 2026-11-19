package ai.csap.apidoc.mcp.audit;

import javax.sql.DataSource;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.MySQLContainer;

import ai.csap.apidoc.database.DatabaseDialect;

/**
 * MySQL 8(Testcontainers);没有 Docker 时跳过。
 */
class MySqlJdbcAuditStoreTest extends AbstractJdbcAuditStoreTest {

    private static MySQLContainer<?> container;
    private static DataSource dataSource;

    @BeforeAll
    static void start() {
        Assumptions.assumeTrue(dockerAvailable(), "没有 Docker,跳过 MySQL 测试");
        container = new MySQLContainer<>("mysql:8.0");
        container.start();
        dataSource = new TestDataSource(container.getJdbcUrl(), container.getUsername(), container.getPassword());
    }

    @AfterAll
    static void stop() {
        if (container != null) {
            container.stop();
        }
    }

    static boolean dockerAvailable() {
        try {
            return DockerClientFactory.instance().isDockerAvailable();
        } catch (Throwable e) {
            return false;
        }
    }

    @Override
    DataSource dataSource() {
        return dataSource;
    }

    @Override
    DatabaseDialect dialect() {
        return DatabaseDialect.MYSQL;
    }
}
