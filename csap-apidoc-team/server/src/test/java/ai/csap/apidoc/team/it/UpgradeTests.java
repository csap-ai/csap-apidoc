package ai.csap.apidoc.team.it;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import ai.csap.apidoc.database.DatabaseDialect;
import ai.csap.apidoc.database.DatabaseRuleStore;
import ai.csap.apidoc.team.support.AbstractIT;
import ai.csap.apidoc.team.support.Api;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * 契约 7:1.0(schema 版本 1)的库启动 1.1 自动升级到版本 2。子类在 Spring 上下文启动之前,
 * 用 1.0 的建表脚本(src/test/resources/schema-1.0)和样例数据准备好库;H2、MySQL 8、PostgreSQL 16 各跑一遍。
 */
public abstract class UpgradeTests extends AbstractIT {

    static final long T0 = 1_760_000_000_000L;

    /** 用 1.0 的建表脚本建库并放进样例数据。 */
    static void prepare10(String url, String user, String password, String dialect) throws Exception {
        JdbcTemplate j = new JdbcTemplate(new DriverManagerDataSource(url, user, password));
        for (String sql : statements("schema-1.0/" + dialect + ".sql")) {
            j.execute(sql);
        }
        for (String sql : DatabaseRuleStore.schema(DatabaseDialect.parse(dialect), "csap_apidoc_")) {
            try {
                j.execute(sql);
            } catch (RuntimeException e) {
                if (!sql.startsWith("CREATE INDEX")) {
                    throw e;
                }
            }
        }
        j.update("INSERT INTO csap_team_setting (name, setting_value) VALUES ('schema.version', '1')");
        j.update("INSERT INTO csap_team_setting (name, setting_value) VALUES ('org.name', '旧组织')");
        j.update("INSERT INTO csap_team_user (username, display_name, password_hash, status, org_role, created_at, updated_at)"
                + " VALUES ('admin', '管理员', ?, 'ACTIVE', 'ADMIN', ?, ?)", new BCryptPasswordEncoder(4).encode(ADMIN_PASSWORD), T0, T0);
        j.update("INSERT INTO csap_team_project (name, description, require_approval, created_at, updated_at) VALUES ('旧项目', '1.0 数据', 1, ?, ?)", T0, T0);
        Long project = j.queryForObject("SELECT id FROM csap_team_project WHERE name = '旧项目'", Long.class);
        j.update("INSERT INTO csap_team_project_service (service, project_id, base_url, doc_path, language, framework_version, source,"
                + " last_seen_at, created_at) VALUES ('old-registered', ?, 'http://reg.example:8080', '/csap/apidoc', 'java', '1.4.0', 'REGISTERED', ?, ?)",
                project, T0 + 5, T0);
        j.update("INSERT INTO csap_team_project_service (service, project_id, base_url, doc_path, source, created_at)"
                + " VALUES ('old-manual', ?, 'http://manual.example', '/docs/api', 'MANUAL', ?)", project, T0);
        j.update("INSERT INTO csap_team_project_service (service, doc_path, source, created_at) VALUES ('old-unassigned', '/csap/apidoc', 'MANUAL', ?)", T0);
        j.update("INSERT INTO csap_team_service_instance (service, instance_id, base_url, last_seen_at) VALUES ('old-registered', 'i-1', 'http://reg.example:8080', ?)", T0);
        Long admin = j.queryForObject("SELECT id FROM csap_team_user WHERE username = 'admin'", Long.class);
        j.update("INSERT INTO csap_team_change (project_id, title, reason, author_id, status, created_at, updated_at, kind) VALUES (?, '已发布的旧修改单', 'r', ?, 'PUBLISHED', ?, ?, 'EDIT')",
                project, admin, T0, T0);
        j.update("INSERT INTO csap_team_change (project_id, title, author_id, status, created_at, updated_at, kind) VALUES (?, '草稿', ?, 'DRAFT', ?, ?, 'EDIT')",
                project, admin, T0, T0);
        Long c1 = j.queryForObject("SELECT id FROM csap_team_change WHERE title = '已发布的旧修改单'", Long.class);
        j.update("INSERT INTO csap_team_change_item (change_id, service, rule_key, base_version, before_rules, after_rules, published_version, published_history_id)"
                + " VALUES (?, 'old-registered', 'K', 0, NULL, '{\"request\":{},\"response\":{}}', 1, 7)", c1);
        j.update("INSERT INTO csap_team_token (name, token_hash, prefix, scope, project_id, created_by, created_at, revoked) VALUES ('旧令牌', ?, 'csapt_ab', 'REGISTER', ?, ?, ?, 0)",
                "a".repeat(64), project, admin, T0);
        j.update("INSERT INTO csap_team_webhook (project_id, name, kind, url, events, enabled, created_at, updated_at) VALUES (?, '旧通知', 'GENERIC', 'http://hook.example/x', 'change.published', 1, ?, ?)",
                project, T0, T0);
        for (int i = 1; i <= 5; i++) {
            j.update("INSERT INTO csap_team_audit (at, actor_type, actor_name, source, action, project_id, service, reason, before_value) VALUES (?, 'USER', ?, 'CONSOLE', ?, ?, ?, ?, ?)",
                    T0 + i, "张三\"" + i, "old.action." + i, project, i % 2 == 0 ? "old-registered" : null, "旧审计 " + i + " 含中文\n和换行", i == 3 ? "{\"k\":\"值\"}" : null);
        }
        j.update("INSERT INTO csap_team_cursor (name, cursor_value) VALUES ('oss.history', '7')");
        j.update("INSERT INTO csap_team_cursor (name, cursor_value) VALUES ('oss.mcp_audit', '3')");
    }

    static List<String> statements(String resource) throws Exception {
        try (InputStream in = UpgradeTests.class.getClassLoader().getResourceAsStream(resource)) {
            assertNotNull(in, resource);
            List<String> out = new ArrayList<>();
            StringBuilder cur = new StringBuilder();
            for (String line : new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\r?\n")) {
                if (line.trim().startsWith("--")) {
                    continue;
                }
                cur.append(line).append('\n');
                if (line.trim().endsWith(";")) {
                    String s = cur.toString().trim();
                    out.add(s.substring(0, s.length() - 1));
                    cur.setLength(0);
                }
            }
            return out;
        }
    }

    @Test
    void version1DatabaseIsUpgradedToVersion2OnStartup() {
        assertEquals("2", jdbc.queryForObject("SELECT setting_value FROM csap_team_setting WHERE name = 'schema.version'", String.class));
        assertEquals("旧组织", jdbc.queryForObject("SELECT setting_value FROM csap_team_setting WHERE name = 'org.name'", String.class), "原有设置保留");

        // 新表、新列
        assertEquals(3, jdbc.queryForObject("SELECT COUNT(*) FROM csap_team_service_env", Integer.class));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM csap_team_comment", Integer.class));
        jdbc.queryForList("SELECT env_key FROM csap_team_token");
        jdbc.queryForList("SELECT env_keys FROM csap_team_webhook");
        jdbc.queryForList("SELECT env_key, source_env FROM csap_team_change");

        // 2. 服务地址 -> csap_team_service_env(默认环境 default);project_service 的列保留
        Map<String, Object> reg = jdbc.queryForMap("SELECT * FROM csap_team_service_env WHERE service = 'old-registered'");
        assertEquals("default", reg.get("ENV_KEY") != null ? reg.get("ENV_KEY") : reg.get("env_key"));
        assertEquals("http://reg.example:8080", value(reg, "base_url"));
        assertEquals("REGISTERED", value(reg, "source"));
        assertEquals(T0 + 5, ((Number) value(reg, "last_seen_at")).longValue());
        Map<String, Object> man = jdbc.queryForMap("SELECT * FROM csap_team_service_env WHERE service = 'old-manual'");
        assertEquals("/docs/api", value(man, "doc_path"));
        assertEquals("MANUAL", value(man, "source"));
        assertNull(value(jdbc.queryForMap("SELECT * FROM csap_team_service_env WHERE service = 'old-unassigned'"), "base_url"));
        assertEquals("http://reg.example:8080", jdbc.queryForObject("SELECT base_url FROM csap_team_project_service WHERE service = 'old-registered'", String.class),
                "project_service 的列保留(便于回退)");
        // 4. 实例数据丢弃(旧表保留),新表为空
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM csap_team_env_instance", Integer.class));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM csap_team_service_instance", Integer.class));
        // 3. 修改单的 env_key 填默认环境;审计的 env_key 不回填
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM csap_team_change WHERE env_key = 'default'", Integer.class));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM csap_team_change WHERE source_env IS NOT NULL", Integer.class));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM csap_team_audit WHERE env_key IS NOT NULL", Integer.class));
        // 5. 游标改名
        assertEquals("7", jdbc.queryForObject("SELECT cursor_value FROM csap_team_cursor WHERE name = 'oss.history.default'", String.class));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM csap_team_cursor WHERE name = 'oss.history'", Integer.class));
        assertEquals("3", jdbc.queryForObject("SELECT cursor_value FROM csap_team_cursor WHERE name = 'oss.mcp_audit.default'", String.class));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM csap_team_cursor WHERE name = 'oss.mcp_audit'", Integer.class));
        // 6. 审计哈希链:旧记录封存,追加 audit.chain.sealed
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM csap_team_audit WHERE hash IS NULL OR prev_hash IS NULL", Integer.class));
        assertEquals("0".repeat(64), jdbc.queryForObject("SELECT prev_hash FROM csap_team_audit WHERE action = 'old.action.1'", String.class));
        Map<String, Object> sealed = jdbc.queryForMap("SELECT * FROM csap_team_audit WHERE action = 'audit.chain.sealed'");
        assertEquals("SYSTEM", value(sealed, "source"));
        assertEquals("sealed 5 records at upgrade", value(sealed, "reason"));

        // 升级后的功能:登录(旧用户)、环境、服务列表、哈希链校验、新写入接在链上
        Api admin = admin();
        JsonNode envs = admin.get("/api/v1/envs").json();
        assertEquals(1, envs.size());
        assertEquals("default", envs.get(0).get("key").asText());
        assertEquals("UP", envs.get(0).get("status").asText());
        JsonNode svcs = admin.get("/api/v1/services").json();
        JsonNode r = null;
        for (JsonNode n : svcs) {
            if ("old-registered".equals(n.get("service").asText())) {
                r = n;
            }
        }
        assertNotNull(r);
        assertEquals("http://reg.example:8080", r.get("baseUrl").asText());
        assertEquals("java", r.get("language").asText());
        assertEquals(1, r.get("envs").size());
        assertEquals("default", r.get("envs").get(0).get("env").asText());
        JsonNode v = admin.get("/api/v1/audit/verify").json();
        assertTrue(v.get("ok").asBoolean(), v.toString());
        assertTrue(v.get("checked").asInt() >= 6);
        long project = newProject(admin, false);
        assertTrue(project > 0);
        v = admin.get("/api/v1/audit/verify").json();
        assertTrue(v.get("ok").asBoolean(), v.toString());
        // 旧修改单仍能读,带环境
        JsonNode changes = admin.get("/api/v1/changes?size=50").json();
        assertTrue(changes.get("total").asInt() >= 2);
        assertEquals("default", changes.get("items").get(0).get("env").asText());
        assertFalse(changes.get("items").get(0).get("envName").isNull());
        // 旧令牌仍可用于鉴权(env_key 为空 = 默认环境),旧 Webhook 订阅全部环境
        JsonNode hooks = admin.get("/api/v1/webhooks").json();
        assertEquals(0, hooks.get(0).get("environments").size());
    }

    private static Object value(Map<String, Object> row, String col) {
        for (Map.Entry<String, Object> e : row.entrySet()) {
            if (e.getKey().equalsIgnoreCase(col)) {
                return e.getValue();
            }
        }
        return null;
    }
}
