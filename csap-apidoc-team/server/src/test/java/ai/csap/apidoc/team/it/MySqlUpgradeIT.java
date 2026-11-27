package ai.csap.apidoc.team.it;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

/** MySQL 8(Testcontainers):1.0 → 1.1 升级。 */
@Testcontainers
class MySqlUpgradeIT extends UpgradeTests {

    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0").withDatabaseName("team")
            .withCommand("--character-set-server=utf8mb4", "--collation-server=utf8mb4_unicode_ci");

    static {
        MYSQL.start();
        try {
            prepare10(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword(), "mysql");
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @DynamicPropertySource
    static void db(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", MYSQL::getJdbcUrl);
        r.add("spring.datasource.username", MYSQL::getUsername);
        r.add("spring.datasource.password", MYSQL::getPassword);
    }
}
