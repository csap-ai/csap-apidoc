package ai.csap.apidoc.team.it;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import ai.csap.apidoc.team.TeamApplication;

/** H2:1.0 的建表脚本 + 样例数据启动 1.1 升级成功;create-tables=false 时的行为。 */
class H2UpgradeIT extends UpgradeTests {

    static final String URL = "jdbc:h2:mem:upgrade_h2;DB_CLOSE_DELAY=-1";

    static {
        try {
            prepare10(URL, "sa", "", "h2");
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @DynamicPropertySource
    static void db(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", () -> URL);
        r.add("spring.datasource.username", () -> "sa");
        r.add("spring.datasource.password", () -> "");
    }

    @Test
    void createTablesFalseRefusesToUpgradeAndTellsToRunTheScriptByHand() throws Exception {
        String url = "jdbc:h2:mem:upgrade_h2_manual;DB_CLOSE_DELAY=-1";
        prepare10(url, "sa", "", "h2");
        String[] args = {"--spring.datasource.url=" + url, "--spring.datasource.username=sa", "--spring.datasource.password=",
                "--csap.team.database.create-tables=false", "--server.port=0", "--spring.main.banner-mode=off"};
        Throwable e = assertThrows(Throwable.class, () -> new SpringApplicationBuilder(TeamApplication.class).web(WebApplicationType.NONE).run(args));
        Throwable root = e;
        while (root.getCause() != null) {
            root = root.getCause();
        }
        assertTrue(root.getMessage().contains("upgrade/2/h2.sql"), root.getMessage());
        JdbcTemplate j = new JdbcTemplate(new DriverManagerDataSource(url, "sa", ""));
        assertEquals("1", j.queryForObject("SELECT setting_value FROM csap_team_setting WHERE name = 'schema.version'", String.class), "没有升级");

        // 手工执行升级脚本后再启动(create-tables=false):Java 部分的数据迁移在启动时自动完成
        for (String sql : statements("META-INF/csap-team/schema/upgrade/2/h2.sql")) {
            j.execute(sql);
        }
        try (var ctx = new SpringApplicationBuilder(TeamApplication.class).web(WebApplicationType.NONE).run(args)) {
            assertEquals("2", j.queryForObject("SELECT setting_value FROM csap_team_setting WHERE name = 'schema.version'", String.class));
            assertEquals(3, j.queryForObject("SELECT COUNT(*) FROM csap_team_service_env", Integer.class));
            assertEquals(0, j.queryForObject("SELECT COUNT(*) FROM csap_team_audit WHERE hash IS NULL", Integer.class));
            assertEquals(1, j.queryForObject("SELECT COUNT(*) FROM csap_team_audit WHERE action = 'audit.chain.sealed'", Integer.class));
            assertEquals(2, j.queryForObject("SELECT COUNT(*) FROM csap_team_change WHERE env_key = 'default'", Integer.class));
        }
    }
}
