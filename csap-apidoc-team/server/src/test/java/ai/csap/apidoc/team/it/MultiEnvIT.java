package ai.csap.apidoc.team.it;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import ai.csap.apidoc.team.audit.McpAuditService;
import ai.csap.apidoc.team.docs.DocsService;
import ai.csap.apidoc.team.service.ServiceService;
import ai.csap.apidoc.team.support.AbstractIT;
import ai.csap.apidoc.team.support.Api;
import ai.csap.apidoc.team.support.LocalHttp;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * 契约 1–4:多环境(两个以上独立的 H2 内存库)——按环境读规则、发布、推广、差异对比、受保护 / 只读 / 不可用环境、
 * 按环境的 HistorySync 与 MCP 审计、服务与文档、注册令牌绑定环境、Webhook 带环境。
 * 环境:dev(主库)、test(独立库)、prod(独立库,受保护)、ro(独立库,只读)、down(连不上)。
 */
class MultiEnvIT extends AbstractIT {

    static final String TEST_URL = "jdbc:h2:mem:multienv_test;DB_CLOSE_DELAY=-1";
    static final String PROD_URL = "jdbc:h2:mem:multienv_prod;DB_CLOSE_DELAY=-1";
    static final String RO_URL = "jdbc:h2:mem:multienv_ro;DB_CLOSE_DELAY=-1";

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", () -> "jdbc:h2:mem:multienv_main;DB_CLOSE_DELAY=-1");
        r.add("spring.datasource.username", () -> "sa");
        r.add("spring.datasource.password", () -> "");
        r.add("csap.team.environments[0].key", () -> "dev");
        r.add("csap.team.environments[0].name", () -> "开发");
        r.add("csap.team.environments[1].key", () -> "test");
        r.add("csap.team.environments[1].name", () -> "测试");
        r.add("csap.team.environments[1].datasource.url", () -> TEST_URL);
        r.add("csap.team.environments[1].datasource.username", () -> "sa");
        r.add("csap.team.environments[1].datasource.password", () -> "pw-secret-test");
        r.add("csap.team.environments[2].key", () -> "prod");
        r.add("csap.team.environments[2].name", () -> "生产");
        r.add("csap.team.environments[2].protected", () -> "true");
        r.add("csap.team.environments[2].datasource.url", () -> PROD_URL);
        r.add("csap.team.environments[2].datasource.username", () -> "sa");
        r.add("csap.team.environments[3].key", () -> "ro");
        r.add("csap.team.environments[3].name", () -> "归档");
        r.add("csap.team.environments[3].read-only", () -> "true");
        r.add("csap.team.environments[3].datasource.url", () -> RO_URL);
        r.add("csap.team.environments[3].datasource.username", () -> "sa");
        r.add("csap.team.environments[4].key", () -> "down");
        r.add("csap.team.environments[4].name", () -> "故障库");
        r.add("csap.team.environments[4].datasource.url", () -> "jdbc:h2:tcp://127.0.0.1:1/nodb");
        r.add("csap.team.environments[4].datasource.username", () -> "sa");
        r.add("csap.team.environments[4].datasource.password", () -> "pw-secret-down");
    }

    @Autowired
    DocsService docs;
    @Autowired
    ServiceService services;
    @Autowired
    McpAuditService mcp;

    static JdbcTemplate env(String url) {
        return new JdbcTemplate(new DriverManagerDataSource(url, "sa", TEST_URL.equals(url) ? "pw-secret-test" : ""));
    }

    JdbcTemplate envDb(String key) {
        return switch (key) {
            case "dev" -> jdbc;
            case "test" -> env(TEST_URL);
            case "prod" -> env(PROD_URL);
            case "ro" -> env(RO_URL);
            default -> throw new IllegalArgumentException(key);
        };
    }

    private Api.Resp createIn(Api api, long project, String env, Map<String, Object>... items) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("projectId", project);
        if (env != null) {
            body.put("env", env);
        }
        body.put("title", "改 " + unique("t"));
        body.put("reason", "测试");
        body.put("items", new ArrayList<>(List.of(items)));
        return api.post("/api/v1/changes", body);
    }

    /** 创建并提交(项目无需审批 / 环境不受保护时直接发布)。 */
    private JsonNode publishIn(Api api, long project, String env, String svc, String key, String rules) {
        long id = changeId(createIn(api, project, env, item(svc, key, null, rules)));
        Api.Resp s = api.post("/api/v1/changes/" + id + "/submit", null);
        assertEquals(200, s.status(), s.text());
        assertEquals("PUBLISHED", s.json().get("status").asText(), s.text());
        return s.json();
    }

    // ---------- 环境列表 ----------

    @Test
    void envsListShowsStatusFlagsAndHidesDatasourceDetails() {
        Api admin = admin();
        U member = newUser(admin, "MEMBER");
        Api.Resp r = member.api().get("/api/v1/envs");
        assertEquals(200, r.status(), r.text());
        JsonNode list = r.json();
        assertEquals(5, list.size());
        assertEquals(List.of("dev", "test", "prod", "ro", "down"), List.of(list.get(0).get("key").asText(), list.get(1).get("key").asText(),
                list.get(2).get("key").asText(), list.get(3).get("key").asText(), list.get(4).get("key").asText()));
        for (int i = 0; i < 5; i++) {
            assertEquals(i, list.get(i).get("order").asInt());
            assertEquals(i == 0, list.get(i).get("default").asBoolean());
        }
        assertEquals("开发", list.get(0).get("name").asText());
        assertTrue(list.get(2).get("protected").asBoolean());
        assertFalse(list.get(1).get("protected").asBoolean());
        assertTrue(list.get(3).get("readOnly").asBoolean());
        for (int i = 0; i < 4; i++) {
            assertEquals("UP", list.get(i).get("status").asText());
            assertTrue(list.get(i).get("error").isNull());
        }
        assertEquals("DOWN", list.get(4).get("status").asText());
        assertFalse(list.get(4).get("error").isNull());
        // 不返回数据源地址,错误信息里没有密码
        assertFalse(r.text().contains("jdbc:"), r.text());
        assertFalse(r.text().contains("pw-secret"), r.text());
        assertEquals(401, new Api(port).get("/api/v1/envs").status());
    }

    @Test
    void unknownEnvIsRejectedAndEnvDefaultsToFirst() {
        Api admin = admin();
        long project = newProject(admin, false);
        String svc = newService(admin, project);
        assertEquals(400, admin.get("/api/v1/rules/" + svc + "?env=nope").status());
        assertEquals("VALIDATION", admin.get("/api/v1/rules/" + svc + "?env=nope").code());
        assertEquals(200, admin.get("/api/v1/rules/" + svc).status(), "省略 env = 默认环境");
        assertEquals(400, createIn(admin, project, "nope", item(svc, "K", null, rules("a"))).status());
    }

    // ---------- 规则与修改单按环境 ----------

    @Test
    void rulesAreReadAndPublishedPerEnvironment() {
        Api admin = admin();
        long project = newProject(admin, false);
        String svc = newService(admin, project);
        JsonNode pub = publishIn(admin, project, "test", svc, "E.key", rules("t"));
        assertEquals("test", pub.get("env").asText());
        assertEquals("测试", pub.get("envName").asText());
        assertTrue(pub.get("sourceEnv").isNull());
        assertFalse(pub.get("requireApproval").asBoolean());

        // 规则只在测试环境的库里
        assertEquals(1, env(TEST_URL).queryForObject("SELECT COUNT(*) FROM csap_apidoc_rule WHERE service = ?", Integer.class, svc));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM csap_apidoc_rule WHERE service = ?", Integer.class, svc));
        assertTrue(admin.get("/api/v1/rules/" + svc + "/item?key=E.key&env=test").json().get("version").asLong() > 0);
        assertEquals(0, admin.get("/api/v1/rules/" + svc + "/item?key=E.key").json().get("version").asLong(), "默认环境没有");
        assertEquals(1, admin.get("/api/v1/rules/" + svc + "?env=test").json().get("items").size());
        assertEquals(0, admin.get("/api/v1/rules/" + svc + "?env=dev").json().get("items").size());
        // 历史带发布它的修改单;游标按环境
        JsonNode h = admin.get("/api/v1/rules/" + svc + "/history?key=E.key&env=test").json();
        assertEquals(1, h.size());
        assertEquals(pub.get("id").asLong(), h.get(0).get("changeId").asLong());
        assertEquals(0, admin.get("/api/v1/rules/" + svc + "/history?key=E.key&env=dev").json().size());
        // 开放接口读规则
        String token = token(admin, "OPEN_API", project, null);
        JsonNode open = Api.bearer(port, token).get("/api/open/v1/rules/" + svc + "?env=test").json();
        assertEquals(1, open.get("rules").size());
        assertEquals(0, Api.bearer(port, token).get("/api/open/v1/rules/" + svc).json().get("rules").size());
        // 修改单列表按环境筛选
        assertTrue(admin.get("/api/v1/changes?env=test&projectId=" + project).json().get("total").asInt() >= 1);
        assertEquals(0, admin.get("/api/v1/changes?env=dev&projectId=" + project).json().get("total").asInt());
        // 回滚在该环境:历史 → 修改单
        long hid = h.get(0).get("id").asLong();
        Api.Resp rb = admin.post("/api/v1/rules/" + svc + "/rollback?env=test", Map.of("key", "E.key", "historyId", hid, "reason", "回"));
        assertEquals(200, rb.status(), rb.text());
        assertEquals("test", rb.json().get("env").asText());
        assertEquals("ROLLBACK", rb.json().get("kind").asText());
        // 审计带环境,可按环境筛选
        JsonNode audits = auditList(admin, "env=test&action=change.publish&service=" + svc);
        assertEquals(1, audits.get("total").asInt());
        assertEquals("test", audits.get("items").get(0).get("env").asText());
        assertEquals(0, auditList(admin, "env=dev&action=change.publish&service=" + svc).get("total").asInt());
    }

    @Test
    void openApiCreatesChangeInTheRequestedEnvironment() {
        Api admin = admin();
        long project = newProject(admin, false);
        String svc = newService(admin, project);
        Api open = Api.bearer(port, token(admin, "OPEN_API", project, null));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("projectId", project);
        body.put("env", "test");
        body.put("title", "开放接口");
        body.put("submit", true);
        body.put("items", List.of(item(svc, "Open.key", null, rules("o"))));
        Api.Resp r = open.post("/api/open/v1/changes", body);
        assertEquals(200, r.status(), r.text());
        assertEquals("test", r.json().get("env").asText());
        assertEquals("PUBLISHED", r.json().get("status").asText());
        assertEquals(1, env(TEST_URL).queryForObject("SELECT COUNT(*) FROM csap_apidoc_rule WHERE service = ?", Integer.class, svc));
    }

    @Test
    void protectedEnvironmentAlwaysRequiresApproval() {
        Api admin = admin();
        long project = newProject(admin, false);
        String svc = newService(admin, project);
        U dev = newUser(admin, "MEMBER");
        member(admin, project, dev, "DEVELOPER");
        long id = changeId(createIn(dev.api(), project, "prod", item(svc, "P.key", null, rules("p"))));
        Api.Resp s = dev.api().post("/api/v1/changes/" + id + "/submit", null);
        assertEquals("PENDING", s.json().get("status").asText(), "受保护环境不看项目的 require_approval");
        assertTrue(s.json().get("requireApproval").asBoolean());
        assertEquals(0, env(PROD_URL).queryForObject("SELECT COUNT(*) FROM csap_apidoc_rule WHERE service = ?", Integer.class, svc));
        Api.Resp a = admin.post("/api/v1/changes/" + id + "/approve", Map.of("note", "ok"));
        assertEquals("PUBLISHED", a.json().get("status").asText(), a.text());
        assertEquals(1, env(PROD_URL).queryForObject("SELECT COUNT(*) FROM csap_apidoc_rule WHERE service = ?", Integer.class, svc));
        // 同一项目在不受保护的环境直接发布
        assertEquals("PUBLISHED", publishIn(dev.api(), project, "test", svc, "P.key", rules("q")).get("status").asText());
    }

    @Test
    void readOnlyEnvironmentRefusesEveryPublishingAction() {
        Api admin = admin();
        long project = newProject(admin, false);
        String svc = newService(admin, project);
        Api.Resp c = createIn(admin, project, "ro", item(svc, "R.key", null, rules("r")));
        assertEquals(403, c.status());
        assertEquals("FORBIDDEN", c.code());
        assertTrue(c.json().get("message").asText().contains("只读"));
        // 能看
        assertEquals(200, admin.get("/api/v1/rules/" + svc + "?env=ro").status());
        // 推广到只读环境
        publishIn(admin, project, "dev", svc, "R.key", rules("r"));
        Api.Resp p = admin.post("/api/v1/promotions", Map.of("projectId", project, "from", "dev", "to", "ro", "title", "推"));
        assertEquals(403, p.status(), p.text());
        // 修改单创建后环境才被设为只读不可测;这里验证回滚入口
        env(RO_URL).update("INSERT INTO csap_apidoc_history (service, rule_key, before_rules, after_rules, version, updated_by, updated_at)"
                + " VALUES (?, 'R.key', NULL, '{}', 1, 'x', ?)", svc, System.currentTimeMillis());
        Long hid = env(RO_URL).queryForObject("SELECT MAX(id) FROM csap_apidoc_history WHERE service = ?", Long.class, svc);
        assertEquals(403, admin.post("/api/v1/rules/" + svc + "/rollback?env=ro", Map.of("key", "R.key", "historyId", hid)).status());
    }

    @Test
    void downEnvironmentFailsOnlyItsOwnRequests() {
        Api admin = admin();
        long project = newProject(admin, false);
        String svc = newService(admin, project);
        Api.Resp r = admin.get("/api/v1/rules/" + svc + "?env=down");
        assertEquals(503, r.status(), r.text());
        assertEquals("ENV_UNAVAILABLE", r.code());
        assertTrue(r.json().get("message").asText().contains("故障库"));
        assertFalse(r.text().contains("pw-secret"));
        assertEquals(503, admin.get("/api/v1/rules/" + svc + "/item?key=K&env=down").status());
        assertEquals(503, admin.get("/api/v1/rules/" + svc + "/history?env=down").status());
        assertEquals("ENV_UNAVAILABLE", createIn(admin, project, "down", item(svc, "D.key", null, rules("d"))).code());
        assertEquals("ENV_UNAVAILABLE", admin.get("/api/v1/envs/diff?from=dev&to=down&service=" + svc).code());
        assertEquals("ENV_UNAVAILABLE", admin.get("/api/v1/audit/mcp?env=down").code());
        // 其它环境、服务列表、文档接口不受影响
        assertEquals(200, admin.get("/api/v1/rules/" + svc + "?env=test").status());
        assertEquals(200, admin.get("/api/v1/services?env=down").status());
        assertEquals("DOWN", admin.get("/api/v1/envs").json().get(4).get("status").asText());
        assertEquals("UP", admin.get("/api/v1/envs").json().get(1).get("status").asText());
        assertEquals(200, createIn(admin, project, "test", item(svc, "D.key", null, rules("d"))).status());
    }

    // ---------- 差异与推广 ----------

    @Test
    void diffAndPromote() {
        Api admin = admin();
        long project = newProject(admin, false);
        String svc = newService(admin, project);
        publishIn(admin, project, "dev", svc, "A", rules("a-dev"));
        publishIn(admin, project, "dev", svc, "B", rules("b-dev"));
        publishIn(admin, project, "test", svc, "A", rules("a-test"));
        publishIn(admin, project, "test", svc, "C", rules("c-test"));
        publishIn(admin, project, "dev", svc, "S", rules("same"));
        publishIn(admin, project, "test", svc, "S", rules("same"));

        JsonNode diff = admin.get("/api/v1/envs/diff?from=dev&to=test&projectId=" + project).json();
        assertEquals("dev", diff.get("from").asText());
        assertEquals("test", diff.get("to").asText());
        assertEquals(1, diff.get("services").size());
        JsonNode items = diff.get("services").get(0).get("items");
        assertEquals(svc, diff.get("services").get(0).get("service").asText());
        Map<String, JsonNode> byKey = new LinkedHashMap<>();
        items.forEach(i -> byKey.put(i.get("ruleKey").asText(), i));
        assertEquals(List.of("A", "B", "C"), List.copyOf(byKey.keySet()), "相同内容的 S 不列");
        assertEquals("DIFFERENT", byKey.get("A").get("status").asText());
        assertTrue(byKey.get("A").get("fromVersion").asLong() > 0 && byKey.get("A").get("toVersion").asLong() > 0);
        assertEquals("ONLY_FROM", byKey.get("B").get("status").asText());
        assertTrue(byKey.get("B").get("toVersion").isNull());
        assertEquals("ONLY_TO", byKey.get("C").get("status").asText());
        assertTrue(byKey.get("C").get("fromVersion").isNull());
        // 参数校验
        assertEquals(400, admin.get("/api/v1/envs/diff?from=dev&to=dev&projectId=" + project).status());
        assertEquals(400, admin.get("/api/v1/envs/diff?from=dev&to=test").status());
        assertEquals(400, admin.get("/api/v1/envs/diff?to=test&projectId=" + project).status());
        assertEquals(1, admin.get("/api/v1/envs/diff?from=dev&to=test&service=" + svc).json().get("services").size());
        // 单条:diff 为 to → from
        JsonNode one = admin.get("/api/v1/envs/diff/item?from=dev&to=test&service=" + svc + "&key=A").json();
        assertEquals("A", one.get("ruleKey").asText());
        assertTrue(one.get("fromRules").asText().contains("a-dev"));
        assertTrue(one.get("toRules").asText().contains("a-test"));
        assertTrue(one.get("diff").asText().contains("-"), one.get("diff").asText());
        assertTrue(one.get("diff").asText().lines().anyMatch(l -> l.startsWith("-") && l.contains("a-test")));
        assertTrue(one.get("diff").asText().lines().anyMatch(l -> l.startsWith("+") && l.contains("a-dev")));

        // 推广:只选 A
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("projectId", project);
        body.put("from", "dev");
        body.put("to", "test");
        body.put("items", List.of(Map.of("service", svc, "ruleKey", "A")));
        body.put("title", "推 A");
        body.put("reason", "灰度通过");
        Api.Resp p = admin.post("/api/v1/promotions", body);
        assertEquals(200, p.status(), p.text());
        JsonNode ch = p.json();
        assertEquals("PROMOTE", ch.get("kind").asText());
        assertEquals("test", ch.get("env").asText());
        assertEquals("dev", ch.get("sourceEnv").asText());
        assertEquals("DRAFT", ch.get("status").asText());
        assertEquals(1, ch.get("items").size());
        assertEquals(env(TEST_URL).queryForObject("SELECT version FROM csap_apidoc_rule WHERE service = ? AND rule_key = 'A'", Long.class, svc),
                ch.get("items").get(0).get("baseVersion").asLong(), "base_version = 目标环境当前版本");
        assertTrue(ch.get("items").get(0).get("beforeRules").asText().contains("a-test"));
        assertTrue(ch.get("items").get(0).get("afterRules").asText().contains("a-dev"));
        JsonNode created = auditList(admin, "action=change.create&env=test").get("items").get(0);
        assertTrue(created.get("reason").asText().contains("promote dev→test"), created.get("reason").asText());
        Api.Resp sub = admin.post("/api/v1/changes/" + ch.get("id").asLong() + "/submit", null);
        assertEquals("PUBLISHED", sub.json().get("status").asText(), sub.text());
        assertTrue(env(TEST_URL).queryForObject("SELECT rules FROM csap_apidoc_rule WHERE service = ? AND rule_key = 'A'", String.class, svc)
                .contains("a-dev"));

        // 推广全部差异并包含删除;submit=true 一键推广
        Map<String, Object> all = new LinkedHashMap<>();
        all.put("projectId", project);
        all.put("from", "dev");
        all.put("to", "test");
        all.put("includeDeletes", true);
        all.put("submit", true);
        all.put("title", "全量");
        Api.Resp pa = admin.post("/api/v1/promotions", all);
        assertEquals(200, pa.status(), pa.text());
        assertEquals("PUBLISHED", pa.json().get("status").asText());
        assertEquals(2, pa.json().get("items").size(), "B(新增)+ C(推广后在目标环境删除)");
        assertEquals(1, env(TEST_URL).queryForObject("SELECT COUNT(*) FROM csap_apidoc_rule WHERE service = ? AND rule_key = 'B'", Integer.class, svc));
        assertEquals(0, env(TEST_URL).queryForObject("SELECT COUNT(*) FROM csap_apidoc_rule WHERE service = ? AND rule_key = 'C'", Integer.class, svc));
        // 现在没有差异
        Api.Resp none = admin.post("/api/v1/promotions", Map.of("projectId", project, "from", "dev", "to", "test", "title", "空"));
        assertEquals(400, none.status());
        assertEquals("VALIDATION", none.code());
        assertTrue(none.json().get("message").asText().contains("两个环境没有差异"));
        assertEquals(0, admin.get("/api/v1/envs/diff?from=dev&to=test&projectId=" + project).json().get("services").size());
    }

    @Test
    void includeDeletesIsOffByDefault() {
        Api admin = admin();
        long project = newProject(admin, false);
        String svc = newService(admin, project);
        publishIn(admin, project, "dev", svc, "N", rules("n"));
        publishIn(admin, project, "test", svc, "O", rules("o-only"));
        Api.Resp p = admin.post("/api/v1/promotions", Map.of("projectId", project, "from", "dev", "to", "test", "title", "x"));
        assertEquals(200, p.status(), p.text());
        assertEquals(1, p.json().get("items").size());
        assertEquals("N", p.json().get("items").get(0).get("ruleKey").asText());
        assertFalse(p.json().get("items").get(0).get("afterRules").isNull());
    }

    @Test
    void promoteToProtectedEnvGoesPendingAndNeedsApproval() {
        Api admin = admin();
        long project = newProject(admin, false);
        String svc = newService(admin, project);
        U dev = newUser(admin, "MEMBER");
        member(admin, project, dev, "DEVELOPER");
        publishIn(admin, project, "test", svc, "Pr", rules("pr"));
        Api.Resp p = dev.api().post("/api/v1/promotions", Map.of("projectId", project, "from", "test", "to", "prod", "title", "上线",
                "submit", true));
        assertEquals(200, p.status(), p.text());
        assertEquals("PENDING", p.json().get("status").asText());
        assertEquals("PROMOTE", p.json().get("kind").asText());
        assertEquals(0, env(PROD_URL).queryForObject("SELECT COUNT(*) FROM csap_apidoc_rule WHERE service = ? AND rule_key = 'Pr'", Integer.class, svc));
        // 作者不能审批(开发本来就不能),管理员通过后发布
        assertEquals(403, dev.api().post("/api/v1/changes/" + p.json().get("id").asLong() + "/approve", Map.of("note", "x")).status());
        Api.Resp a = admin.post("/api/v1/changes/" + p.json().get("id").asLong() + "/approve", Map.of("note", "ok"));
        assertEquals("PUBLISHED", a.json().get("status").asText(), a.text());
        assertEquals(1, env(PROD_URL).queryForObject("SELECT COUNT(*) FROM csap_apidoc_rule WHERE service = ? AND rule_key = 'Pr'", Integer.class, svc));
    }

    @Test
    void promotionPermissionsAndValidation() {
        Api admin = admin();
        long project = newProject(admin, false);
        String svc = newService(admin, project);
        publishIn(admin, project, "dev", svc, "V", rules("v"));
        U viewer = newUser(admin, "MEMBER");
        member(admin, project, viewer, "VIEWER");
        U outsider = newUser(admin, "MEMBER");
        assertEquals(403, viewer.api().post("/api/v1/promotions", Map.of("projectId", project, "from", "dev", "to", "test", "title", "x")).status());
        assertEquals(403, outsider.api().post("/api/v1/promotions", Map.of("projectId", project, "from", "dev", "to", "test", "title", "x")).status());
        assertEquals(400, admin.post("/api/v1/promotions", Map.of("projectId", project, "from", "dev", "to", "dev", "title", "x")).status());
        assertEquals(400, admin.post("/api/v1/promotions", Map.of("projectId", project, "from", "dev", "to", "nope", "title", "x")).status());
        assertEquals("ENV_UNAVAILABLE", admin.post("/api/v1/promotions", Map.of("projectId", project, "from", "dev", "to", "down", "title", "x")).code());
        // 看得到差异的权限同"看规则":项目外的人看不到
        assertEquals(403, outsider.api().get("/api/v1/envs/diff?from=dev&to=test&projectId=" + project).status());
        assertEquals(404, outsider.api().get("/api/v1/envs/diff?from=dev&to=test&service=" + svc).status());
        assertEquals(200, viewer.api().get("/api/v1/envs/diff?from=dev&to=test&projectId=" + project).status());
        // 服务不属于项目
        long other = newProject(admin, false);
        Api.Resp bad = admin.post("/api/v1/promotions", Map.of("projectId", other, "from", "dev", "to", "test", "title", "x",
                "items", List.of(Map.of("service", svc, "ruleKey", "V"))));
        assertEquals(400, bad.status());
    }

    // ---------- 服务侧修改与 MCP 审计按环境 ----------

    @Test
    void historySyncRunsPerEnvironmentWithItsOwnCursor() {
        Api admin = admin();
        long project = newProject(admin, false);
        String svc = newService(admin, project);
        historySync.syncOnce();
        long now = System.currentTimeMillis() - 10_000;
        String insert = "INSERT INTO csap_apidoc_history (service, rule_key, before_rules, after_rules, version, updated_by, updated_at)"
                + " VALUES (?, ?, NULL, '{\"request\":{},\"response\":{}}', 1, ?, ?)";
        env(TEST_URL).update(insert, svc, "T.out", "devtools-test", now);
        jdbc.update(insert, svc, "D.out", "devtools-dev", now);
        env(PROD_URL).update(insert, svc, "P.out", "devtools-prod", now);
        // 团队版自己在测试环境发布的,不记 changed_outside
        publishIn(admin, project, "test", svc, "T.team", rules("t"));
        int n = historySync.syncOnce();
        assertEquals(3, n);
        JsonNode t = auditList(admin, "action=rule.changed_outside&service=" + svc + "&env=test");
        assertEquals(1, t.get("total").asInt());
        assertEquals("T.out", t.get("items").get(0).get("ruleKey").asText());
        assertEquals("test", t.get("items").get(0).get("env").asText());
        assertEquals("devtools-test", t.get("items").get(0).get("actorName").asText());
        assertEquals(1, auditList(admin, "action=rule.changed_outside&service=" + svc + "&env=dev").get("total").asInt());
        assertEquals(1, auditList(admin, "action=rule.changed_outside&service=" + svc + "&env=prod").get("total").asInt());
        assertEquals(0, historySync.syncOnce(), "游标按环境推进,不重复记");
        // 游标名
        assertNotNull(jdbc.queryForObject("SELECT cursor_value FROM csap_team_cursor WHERE name = 'oss.history.test'", String.class));
        assertNotNull(jdbc.queryForObject("SELECT cursor_value FROM csap_team_cursor WHERE name = 'oss.history.dev'", String.class));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM csap_team_cursor WHERE name = 'oss.history'", Integer.class));
    }

    @Test
    void mcpAuditIsReadPerEnvironment() {
        Api admin = admin();
        String ddl = "CREATE TABLE IF NOT EXISTS csap_apidoc_mcp_audit (id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY, service VARCHAR(128) NOT NULL,"
                + " at BIGINT NOT NULL, caller VARCHAR(128), remote_address VARCHAR(64), tool VARCHAR(128), target VARCHAR(512),"
                + " arguments CLOB, status INT, success SMALLINT, result CLOB, duration_ms BIGINT)";
        jdbc.execute("DROP TABLE IF EXISTS csap_apidoc_mcp_audit");
        env(TEST_URL).execute(ddl);
        mcp.resetCache();
        env(TEST_URL).update("INSERT INTO csap_apidoc_mcp_audit (service, at, caller, remote_address, tool, target, arguments, status, success,"
                + " result, duration_ms) VALUES ('mcp-svc', ?, 'bot', '1.1.1.1', 'rules.get', 'k', '{}', 200, 1, 'ok', 5)", System.currentTimeMillis());
        JsonNode t = admin.get("/api/v1/audit/mcp?env=test").json();
        assertTrue(t.get("available").asBoolean());
        assertEquals(1, t.get("total").asInt());
        JsonNode dev = admin.get("/api/v1/audit/mcp").json();
        assertFalse(dev.get("available").asBoolean(), "默认环境(开发)没有这张表");
        assertFalse(admin.get("/api/v1/audit/mcp?env=prod").json().get("available").asBoolean());
        Api.Resp exp = admin.get("/api/v1/audit/export?kind=mcp&format=json&env=test");
        assertEquals(200, exp.status());
        assertEquals(1, exp.json().size());
        assertEquals(0, admin.get("/api/v1/audit/export?kind=mcp&format=json").json().size());
        env(TEST_URL).execute("DROP TABLE csap_apidoc_mcp_audit");
        mcp.resetCache();
    }

    // ---------- 服务、文档、令牌、Webhook ----------

    private static final String DOC_DEV = "{\"data\":{\"apiList\":[{\"methodList\":[{\"name\":\"开发环境接口\",\"paths\":[\"/dev-only\"],\"methods\":[\"GET\"]}]}]}}";
    private static final String DOC_TEST = "{\"data\":{\"apiList\":[{\"methodList\":[{\"name\":\"测试环境接口\",\"paths\":[\"/test-only\"],\"methods\":[\"GET\"]}]}]}}";

    @Test
    void servicesAndDocsAreSeparatedPerEnvironment() throws Exception {
        Api admin = admin();
        long project = newProject(admin, false);
        try (LocalHttp devHttp = new LocalHttp(); LocalHttp testHttp = new LocalHttp()) {
            devHttp.handler = r -> new LocalHttp.Res(200, DOC_DEV);
            testHttp.handler = r -> new LocalHttp.Res(200, DOC_TEST);
            String svc = unique("multi");
            Api.Resp a = admin.post("/api/v1/services", Map.of("service", svc, "baseUrl", devHttp.url(), "projectId", project));
            assertEquals(200, a.status(), a.text());
            Api.Resp b = admin.post("/api/v1/services", Map.of("service", svc, "baseUrl", testHttp.url(), "projectId", project, "env", "test"));
            assertEquals(200, b.status(), "同一服务在另一个环境再加一次 = 给该环境加地址: " + b.text());
            assertEquals(409, admin.post("/api/v1/services", Map.of("service", svc, "baseUrl", testHttp.url(), "env", "test")).status());

            JsonNode def = find(admin.get("/api/v1/services?projectId=" + project).json(), svc);
            assertEquals(devHttp.url(), def.get("baseUrl").asText(), "省略 env = 默认环境");
            assertEquals(2, def.get("envs").size());
            assertEquals("dev", def.get("envs").get(0).get("env").asText());
            assertEquals("test", def.get("envs").get(1).get("env").asText());
            assertEquals(testHttp.url(), def.get("envs").get(1).get("baseUrl").asText());
            assertEquals(project, def.get("projectId").asLong(), "项目归属不分环境");
            JsonNode inTest = find(admin.get("/api/v1/services?projectId=" + project + "&env=test").json(), svc);
            assertEquals(testHttp.url(), inTest.get("baseUrl").asText());
            JsonNode inProd = find(admin.get("/api/v1/services?projectId=" + project + "&env=prod").json(), svc);
            assertTrue(inProd.get("baseUrl").isNull());
            assertEquals("OFFLINE", inProd.get("status").asText());
            assertEquals(2, inProd.get("envs").size());

            // 文档按环境拉取、按环境搜索
            Thread.sleep(500);
            assertTrue(docs.refresh(services.envRow(svc, "dev")));
            assertTrue(docs.refresh(services.envRow(svc, "test")));
            assertEquals(DOC_DEV, admin.get("/api/v1/docs/" + svc).text());
            assertEquals(DOC_TEST, admin.get("/api/v1/docs/" + svc + "?env=test").text());
            assertEquals(404, admin.get("/api/v1/docs/" + svc + "?env=prod").status());
            JsonNode hits = admin.get("/api/v1/docs/search?q=" + java.net.URLEncoder.encode("测试环境", "UTF-8") + "&env=test").json();
            assertEquals(1, hits.size());
            assertEquals("test", hits.get(0).get("env").asText());
            assertEquals(svc, hits.get(0).get("service").asText());
            assertEquals(0, admin.get("/api/v1/docs/search?q=" + java.net.URLEncoder.encode("测试环境", "UTF-8")).json().size());
            assertEquals("dev", admin.get("/api/v1/docs/search?q=dev-only").json().get(0).get("env").asText());

            // PATCH / DELETE 带 env 只动该环境
            assertEquals(200, admin.patch("/api/v1/services/" + svc + "?env=test", Map.of("baseUrl", "http://127.0.0.1:9")).status());
            assertEquals("http://127.0.0.1:9", find(admin.get("/api/v1/services?env=test").json(), svc).get("baseUrl").asText());
            assertEquals(devHttp.url(), find(admin.get("/api/v1/services").json(), svc).get("baseUrl").asText());
            assertEquals(200, admin.delete("/api/v1/services/" + svc + "?env=test").status());
            JsonNode after = find(admin.get("/api/v1/services").json(), svc);
            assertEquals(1, after.get("envs").size());
            assertEquals(404, admin.get("/api/v1/docs/" + svc + "?env=test").status() == 404 ? 404 : 0);
            assertEquals(200, admin.delete("/api/v1/services/" + svc).status());
            assertNull(find(admin.get("/api/v1/services").json(), svc));
            assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM csap_team_service_env WHERE service = ?", Integer.class, svc));
        }
    }

    private static JsonNode find(JsonNode list, String svc) {
        for (JsonNode n : list) {
            if (svc.equals(n.get("service").asText())) {
                return n;
            }
        }
        return null;
    }

    private String token(Api admin, String scope, Long project, String env) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", unique("tok"));
        body.put("scope", scope);
        if (project != null) {
            body.put("projectId", project);
        }
        if (env != null) {
            body.put("env", env);
        }
        Api.Resp r = admin.post("/api/v1/tokens", body);
        assertEquals(200, r.status(), r.text());
        return r.json().get("token").asText();
    }

    @Test
    void registerTokenBoundToAnEnvironmentRegistersThere() {
        Api admin = admin();
        long project = newProject(admin, false);
        Api.Resp created = admin.post("/api/v1/tokens", Map.of("name", unique("bound"), "scope", "REGISTER", "projectId", project, "env", "test"));
        assertEquals(200, created.status(), created.text());
        assertEquals("test", created.json().get("env").asText());
        assertEquals(400, admin.post("/api/v1/tokens", Map.of("name", "x", "scope", "REGISTER", "env", "nope")).status());
        String bound = created.json().get("token").asText();
        String unbound = token(admin, "REGISTER", project, null);
        String svc = unique("reg");
        Map<String, Object> reg = new LinkedHashMap<>();
        reg.put("service", svc);
        reg.put("instanceId", "i-1");
        reg.put("baseUrl", "http://127.0.0.1:9");
        reg.put("language", "java");
        reg.put("frameworkVersion", "1.5.0");
        reg.put("environment", "prod");
        assertEquals(200, Api.bearer(port, bound).post("/api/open/v1/services/register", reg).status());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM csap_team_service_env WHERE service = ? AND env_key = 'test'", Integer.class, svc));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM csap_team_service_env WHERE service = ? AND env_key <> 'test'", Integer.class, svc),
                "请求体里的 environment 字段忽略");
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM csap_team_env_instance WHERE service = ? AND env_key = 'test'", Integer.class, svc));
        JsonNode s = find(admin.get("/api/v1/services?env=test").json(), svc);
        assertEquals("ONLINE", s.get("status").asText().equals("ONLINE") ? "ONLINE" : s.get("status").asText());
        assertEquals(1, s.get("instances").asInt());
        JsonNode inDev = find(admin.get("/api/v1/services").json(), svc);
        assertEquals("OFFLINE", inDev.get("status").asText());
        // 未绑定的令牌 → 默认环境;同一服务在另一个环境出现 = 同一服务的另一个环境
        reg.put("instanceId", "i-2");
        assertEquals(200, Api.bearer(port, unbound).post("/api/open/v1/services/register", reg).status());
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM csap_team_service_env WHERE service = ?", Integer.class, svc));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM csap_team_service_env WHERE service = ? AND env_key = 'dev'", Integer.class, svc));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM csap_team_project_service WHERE service = ?", Integer.class, svc));
        // 注销只影响令牌所在环境的实例
        assertEquals(200, Api.bearer(port, bound).post("/api/open/v1/services/deregister", Map.of("service", svc, "instanceId", "i-1")).status());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM csap_team_env_instance WHERE service = ? AND env_key = 'test'", Integer.class, svc));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM csap_team_env_instance WHERE service = ? AND env_key = 'dev'", Integer.class, svc));
    }

    @Test
    void webhookCarriesEnvironmentAndCanBeScopedToEnvironments() throws Exception {
        Api admin = admin();
        long project = newProject(admin, false);
        String svc = newService(admin, project);
        try (LocalHttp http = new LocalHttp()) {
            Map<String, Object> hook = new LinkedHashMap<>();
            hook.put("projectId", project);
            hook.put("name", "只要测试");
            hook.put("kind", "GENERIC");
            hook.put("url", http.url() + "/only-test");
            hook.put("events", List.of("change.published"));
            hook.put("environments", List.of("test"));
            Api.Resp h1 = admin.post("/api/v1/webhooks", hook);
            assertEquals(200, h1.status(), h1.text());
            assertEquals(List.of("test"), List.of(h1.json().get("environments").get(0).asText()));
            hook.put("name", "全部");
            hook.put("url", http.url() + "/all");
            hook.remove("environments");
            Api.Resp h2 = admin.post("/api/v1/webhooks", hook);
            assertEquals(0, h2.json().get("environments").size(), "空 = 全部环境");
            hook.put("name", "只要生产");
            hook.put("url", http.url() + "/only-prod");
            hook.put("environments", List.of("prod"));
            assertEquals(200, admin.post("/api/v1/webhooks", hook).status());
            hook.put("environments", List.of("nope"));
            assertEquals(400, admin.post("/api/v1/webhooks", hook).status());
            // 修改订阅环境
            assertEquals(200, admin.patch("/api/v1/webhooks/" + h2.json().get("id").asLong(), Map.of("environments", List.of("dev", "test"))).status());
            assertEquals(2, admin.get("/api/v1/webhooks?projectId=" + project).json().get(1).get("environments").size());

            JsonNode pub = publishIn(admin, project, "test", svc, "W.key", rules("w"));
            List<LocalHttp.Req> a = http.await(r -> r.path().equals("/only-test"), 1, 8000);
            assertEquals(1, a.size());
            JsonNode body = Api.MAPPER.readTree(a.get(0).body());
            assertEquals("test", body.get("env").get("key").asText());
            assertEquals("测试", body.get("env").get("name").asText());
            assertTrue(body.get("text").asText().contains("/ 测试]"), body.get("text").asText());
            assertEquals(pub.get("id").asLong(), body.get("change").get("id").asLong());
            assertEquals(1, http.await(r -> r.path().equals("/all"), 1, 8000).size());
            Thread.sleep(300);
            assertEquals(0, http.requests.stream().filter(r -> r.path().equals("/only-prod")).count(), "没订阅测试环境的不发");
        }
    }

    @Test
    void offlineEventIsPerEnvironmentAndService() throws Exception {
        Api admin = admin();
        long project = newProject(admin, false);
        try (LocalHttp svcHttp = new LocalHttp(); LocalHttp hookHttp = new LocalHttp()) {
            svcHttp.handler = r -> new LocalHttp.Res(200, DOC_TEST);
            Map<String, Object> hook = new LinkedHashMap<>();
            hook.put("projectId", project);
            hook.put("name", "离线");
            hook.put("kind", "GENERIC");
            hook.put("url", hookHttp.url() + "/off");
            hook.put("events", List.of("service.offline"));
            assertEquals(200, admin.post("/api/v1/webhooks", hook).status());
            String svc = unique("off");
            Map<String, Object> reg = new LinkedHashMap<>();
            reg.put("service", svc);
            reg.put("instanceId", "i-1");
            reg.put("baseUrl", svcHttp.url());
            reg.put("language", "go");
            reg.put("frameworkVersion", "0.3.0");
            String tTest = token(admin, "REGISTER", project, "test");
            String tDev = token(admin, "REGISTER", project, null);
            assertEquals(200, Api.bearer(port, tTest).post("/api/open/v1/services/register", reg).status());
            assertEquals(200, Api.bearer(port, tDev).post("/api/open/v1/services/register", reg).status());
            docs.refreshAll();
            assertEquals("ONLINE", find(admin.get("/api/v1/services?env=test&projectId=" + project).json(), svc).get("status").asText());
            // 只让测试环境的实例过期
            jdbc.update("UPDATE csap_team_env_instance SET last_seen_at = ? WHERE service = ? AND env_key = 'test'",
                    System.currentTimeMillis() - 200_000, svc);
            docs.refreshAll();
            List<LocalHttp.Req> ev = hookHttp.await(r -> r.path().equals("/off"), 1, 8000);
            assertEquals(1, ev.size());
            JsonNode body = Api.MAPPER.readTree(ev.get(0).body());
            assertEquals("service.offline", body.get("event").asText());
            assertEquals("test", body.get("env").get("key").asText());
            assertEquals(svc, body.get("items").get(0).get("service").asText());
            // 10 分钟内同一 (环境, 服务) 不重复发
            docs.refreshAll();
            Thread.sleep(300);
            assertEquals(1, hookHttp.requests.stream().filter(r -> r.path().equals("/off")).count());
        }
    }
}
