package ai.csap.apidoc.team.it;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

/** PostgreSQL 16(Testcontainers):1.0 → 1.1 升级。 */
@Testcontainers
class PostgresUpgradeIT extends UpgradeTests {

    static final PostgreSQLContainer<?> PG = new PostgreSQLContainer<>("postgres:16-alpine").withDatabaseName("team");

    static {
        PG.start();
        try {
            prepare10(PG.getJdbcUrl(), PG.getUsername(), PG.getPassword(), "postgresql");
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @DynamicPropertySource
    static void db(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", PG::getJdbcUrl);
        r.add("spring.datasource.username", PG::getUsername);
        r.add("spring.datasource.password", PG::getPassword);
    }
}
