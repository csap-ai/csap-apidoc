package ai.csap.apidoc.team.it;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import ai.csap.apidoc.team.docs.DocsService;
import ai.csap.apidoc.team.service.ServiceService;
import ai.csap.apidoc.team.support.AbstractIT;
import ai.csap.apidoc.team.support.Api;
import ai.csap.apidoc.team.support.LocalHttp;
import ai.csap.apidoc.team.token.TokenService;
import com.fasterxml.jackson.databind.JsonNode;

/** 契约 5、7.1:服务目录、注册(开源协议)、令牌、文档中心拉取与搜索、开放接口。 */
class ServicesDocsIT extends AbstractIT {

    @DynamicPropertySource
    static void db(DynamicPropertyRegistry r) {
        H2Db.register(r);
        r.add("csap.team.docs.headers.X-Docs-Key", () -> "k-123");
    }

    @Autowired
    DocsService docs;
    @Autowired
    ServiceService services;

    static final String DOC = "{\"code\":\"0\",\"data\":{\"apiList\":[{\"name\":\"订单\",\"methodList\":["
            + "{\"name\":\"创建订单\",\"description\":\"创建一个新的订单并扣减库存\",\"paths\":[\"/orders\"],\"methods\":[\"POST\"],"
            + "\"request\":[{\"parameters\":[{\"name\":\"customerName\",\"description\":\"客户姓名\",\"parameters\":["
            + "{\"name\":\"nickname\",\"description\":\"昵称\"}]}]}],"
            + "\"response\":[{\"parameters\":[{\"name\":\"orderId\",\"description\":\"订单编号\"}]}]},"
            + "{\"name\":\"查询订单\",\"description\":\"\",\"paths\":[\"/orders/{id}\"],\"methods\":[\"GET\"],\"request\":[],\"response\":[]}]}]}}";

    private Map<String, Object> register(String service, String instance, String baseUrl) {
        return Map.of("service", service, "instanceId", instance, "baseUrl", baseUrl, "docPath", "/csap/apidoc", "language", "java",
                "frameworkVersion", "1.5.0");
    }

    private String token(Api admin, String scope, Long projectId) {
        Map<String, Object> body = new java.util.HashMap<>(Map.of("name", unique("tok"), "scope", scope));
        if (projectId != null) {
            body.put("projectId", projectId);
        }
        Api.Resp r = admin.post("/api/v1/tokens", body);
        assertEquals(200, r.status(), r.text());
        String t = r.json().get("token").asText();
        assertTrue(t.matches("csapt_[0-9A-Za-z]{40}"), t);
        assertEquals(t.substring(0, 8), r.json().get("prefix").asText());
        return t;
    }

    @Test
    void tokensAreStoredHashedAndCanBeRevoked() {
        Api admin = admin();
        String t = token(admin, "REGISTER", null);
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM csap_team_token WHERE token_hash = ?", Integer.class, TokenService.sha256(t)));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM csap_team_token WHERE token_hash = ? OR prefix = ?", Integer.class, t, t));
        for (JsonNode n : admin.get("/api/v1/tokens").json()) {
            assertNull(n.get("token"), "列表不回显明文");
            assertNull(n.get("tokenHash"));
        }
        long id = jdbc.queryForObject("SELECT id FROM csap_team_token WHERE token_hash = ?", Long.class, TokenService.sha256(t));
        Api bearer = Api.bearer(port, t);
        assertEquals(200, bearer.post("/api/open/v1/services/register", register(unique("tsvc"), "i1", "http://127.0.0.1:9")).status());
        assertEquals(200, admin.delete("/api/v1/tokens/" + id).status());
        assertEquals(401, bearer.post("/api/open/v1/services/register", register("x", "i1", "http://127.0.0.1:9")).status());
        assertEquals(400, admin.post("/api/v1/tokens", Map.of("name", "n", "scope", "BAD")).status());
        assertEquals(401, Api.bearer(port, "csapt_" + "x".repeat(40)).post("/api/open/v1/services/register", Map.of()).status());
        assertEquals(401, new Api(port).post("/api/open/v1/services/register", Map.of()).status());
    }

    @Test
    void registerHeartbeatDeregisterAndScopes() {
        Api admin = admin();
        long project = newProject(admin, true);
        String svc = unique("reg");
        String reg = token(admin, "REGISTER", null);
        String openApi = token(admin, "OPEN_API", null);
        Api r = Api.bearer(port, reg);

        Api.Resp res = r.post("/api/open/v1/services/register", register(svc, "10.0.0.5:8080", "http://10.0.0.5:8080"));
        assertEquals(200, res.status(), res.text());
        assertEquals(30, res.json().get("heartbeatSeconds").asInt());
        // 心跳:同一请求重复发送,不产生重复记录
        assertEquals(200, r.post("/api/open/v1/services/register", register(svc, "10.0.0.5:8080", "http://10.0.0.5:8080")).status());
        assertEquals(200, r.post("/api/open/v1/services/register", register(svc, "10.0.0.6:8080", "http://10.0.0.6:8080")).status());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM csap_team_project_service WHERE service = ?", Integer.class, svc));
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM csap_team_env_instance WHERE service = ?", Integer.class, svc));
        // 注册的新服务:未分配 + 一条审计
        JsonNode unassigned = admin.get("/api/v1/services?unassigned=true").json();
        JsonNode mine = null;
        for (JsonNode n : unassigned) {
            if (svc.equals(n.get("service").asText())) {
                mine = n;
            }
        }
        assertNotNull(mine);
        assertEquals("REGISTERED", mine.get("source").asText());
        assertEquals("java", mine.get("language").asText());
        assertEquals("1.5.0", mine.get("frameworkVersion").asText());
        assertEquals(2, mine.get("instances").asInt());
        assertEquals(1, auditList(admin, "action=service.register&service=" + svc).get("total").asInt());
        assertEquals("OPEN_API", auditList(admin, "action=service.register&service=" + svc).get("items").get(0).get("source").asText());

        // 归属到项目
        assertEquals(200, admin.patch("/api/v1/services/" + svc, Map.of("projectId", project)).status());
        assertEquals(project, admin.get("/api/v1/services?projectId=" + project).json().get(0).get("projectId").asLong());

        // 字段校验、权限范围
        assertEquals(400, r.post("/api/open/v1/services/register", Map.of("service", svc, "instanceId", "i")).status());
        assertEquals(400, r.post("/api/open/v1/services/register", register("bad name!", "i", "http://x:1")).status());
        assertEquals(400, r.post("/api/open/v1/services/register", register(svc, "i", "ftp://x:1")).status());
        assertEquals(403, Api.bearer(port, openApi).post("/api/open/v1/services/register", register(svc, "i", "http://x:1")).status());
        assertEquals(403, r.get("/api/open/v1/rules/" + svc).status());

        // 绑定项目的令牌不能碰别的项目的服务
        long other = newProject(admin, true);
        Api bound = Api.bearer(port, token(admin, "REGISTER", other));
        assertEquals(403, bound.post("/api/open/v1/services/register", register(svc, "i9", "http://x:1")).status());
        String newSvc = unique("bound");
        assertEquals(200, bound.post("/api/open/v1/services/register", register(newSvc, "i1", "http://x:1")).status());
        assertEquals(other, services.get(newSvc).projectId().longValue(), "绑定项目的令牌注册的新服务直接归属该项目");

        // 离线判定:超过 3 个心跳周期没上报
        assertEquals(2, services.onlineInstances("default", svc, System.currentTimeMillis()));
        assertEquals(0, services.onlineInstances("default", svc, System.currentTimeMillis() + 3 * 30_000L + 5_000));
        // 注销
        assertEquals(200, r.post("/api/open/v1/services/deregister", Map.of("service", svc, "instanceId", "10.0.0.6:8080")).status());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM csap_team_env_instance WHERE service = ?", Integer.class, svc));
    }

    @Test
    void manualServiceCrudAndValidation() {
        Api admin = admin();
        long project = newProject(admin, true);
        String name = unique("man");
        Api.Resp r = admin.post("/api/v1/services", Map.of("service", name, "baseUrl", "http://127.0.0.1:9/", "projectId", project));
        assertEquals(200, r.status(), r.text());
        assertEquals("MANUAL", r.json().get("source").asText());
        assertEquals("http://127.0.0.1:9", r.json().get("baseUrl").asText(), "去掉末尾 /");
        assertEquals("/csap/apidoc", r.json().get("docPath").asText());
        assertEquals(409, admin.post("/api/v1/services", Map.of("service", name, "baseUrl", "http://127.0.0.1:9")).status());
        assertEquals(400, admin.post("/api/v1/services", Map.of("service", name + "x", "baseUrl", "not a url")).status());
        assertEquals(400, admin.post("/api/v1/services", Map.of("service", "a/b", "baseUrl", "http://h:1")).status());
        // 维护者可管理本项目服务,开发不行
        U maintainer = newUser(admin, "MEMBER");
        U dev = newUser(admin, "MEMBER");
        member(admin, project, maintainer, "MAINTAINER");
        member(admin, project, dev, "DEVELOPER");
        assertEquals(403, dev.api().patch("/api/v1/services/" + name, Map.of("docPath", "/x")).status());
        assertEquals(200, maintainer.api().patch("/api/v1/services/" + name, Map.of("docPath", "/doc/api")).status());
        assertEquals(403, maintainer.api().patch("/api/v1/services/" + name, nullProject()).status(), "取消归属只有管理员");
        assertEquals(200, admin.patch("/api/v1/services/" + name, nullProject()).status());
        assertEquals(200, admin.delete("/api/v1/services/" + name).status());
        assertEquals(404, admin.delete("/api/v1/services/" + name).status());
        assertEquals(200, admin.get("/api/v1/services").status());
    }

    private static Map<String, Object> nullProject() {
        Map<String, Object> m = new java.util.HashMap<>();
        m.put("projectId", null);
        return m;
    }

    @Test
    void docsAreFetchedWithHeadersCachedStaleAndSearchable() throws Exception {
        Api admin = admin();
        long project = newProject(admin, true);
        try (LocalHttp http = new LocalHttp()) {
            http.handler = req -> new LocalHttp.Res(200, DOC);
            String svc = unique("docs");
            assertEquals(200, admin.post("/api/v1/services", Map.of("service", svc, "baseUrl", http.url(), "projectId", project)).status());
            ServiceService.EnvRow row = services.envRow(svc, "default");

            Api.Resp d = admin.get("/api/v1/docs/" + svc);
            assertEquals(200, d.status(), d.text());
            assertEquals(DOC, d.text(), "原样 JSON");
            assertEquals("false", d.header("X-CSAP-Team-Stale"));
            assertNotNull(d.header("X-CSAP-Team-Fetched-At"));
            assertEquals("k-123", http.requests.get(0).header("X-Docs-Key"), "附带配置的 header");
            assertEquals("/csap/apidoc", http.requests.get(0).path());
            assertEquals("ONLINE", statusOf(admin, svc));

            // 搜索:路径 / 标题 / 描述 / 字段(含中文子串)
            assertEquals("path", firstHit(admin, "/orders/{id}", null).get("matchedIn").asText());
            assertEquals("title", firstHit(admin, "创建订", null).get("matchedIn").asText());
            assertEquals("description", firstHit(admin, "扣减库存", null).get("matchedIn").asText());
            JsonNode field = firstHit(admin, "客户姓", null);
            assertEquals("field", field.get("matchedIn").asText());
            assertTrue(field.get("snippet").asText().contains("customerName"));
            assertEquals("field", firstHit(admin, "nickname", null).get("matchedIn").asText(), "嵌套字段也能搜到");
            assertEquals("POST", firstHit(admin, "订单编号", null).get("method").asText());
            assertEquals(0, admin.get("/api/v1/docs/search?q=" + enc("不存在的词") ).json().size());
            assertEquals(0, admin.get("/api/v1/docs/search?q=").json().size());
            assertEquals(0, admin.get("/api/v1/docs/search?q=" + enc("订单") + "&projectId=" + (project + 1000)).json().size());

            // 失败保留上次结果并标记 stale
            http.handler = req -> new LocalHttp.Res(500, "boom");
            assertFalse(docs.refresh(row));
            Api.Resp stale = admin.get("/api/v1/docs/" + svc);
            assertEquals(200, stale.status());
            assertEquals(DOC, stale.text());
            assertEquals("true", stale.header("X-CSAP-Team-Stale"));
            assertEquals("STALE", statusOf(admin, svc));
            http.handler = req -> new LocalHttp.Res(200, DOC);
            assertTrue(docs.refresh(row));
            assertEquals("ONLINE", statusOf(admin, svc));

            // 权限:项目外的人看不到
            U outsider = newUser(admin, "MEMBER");
            assertEquals(404, outsider.api().get("/api/v1/docs/" + svc).status());
            assertEquals(0, outsider.api().get("/api/v1/docs/search?q=" + enc("订单")).json().size());
            U viewer = newUser(admin, "MEMBER");
            member(admin, project, viewer, "VIEWER");
            assertEquals(200, viewer.api().get("/api/v1/docs/" + svc).status());
            assertTrue(viewer.api().get("/api/v1/docs/search?q=" + enc("订单")).json().size() >= 1);
        }
    }

    @Test
    void registeredServiceIsFetchedFromAnOnlineInstanceAndGoesOffline() throws Exception {
        Api admin = admin();
        long project = newProject(admin, true);
        try (LocalHttp http = new LocalHttp()) {
            http.handler = req -> new LocalHttp.Res(200, DOC);
            String svc = unique("regdoc");
            Api r = Api.bearer(port, token(admin, "REGISTER", project));
            assertEquals(200, r.post("/api/open/v1/services/register", register(svc, "a:1", http.url())).status());
            assertTrue(docs.refresh(services.envRow(svc, "default")));
            assertEquals("ONLINE", statusOf(admin, svc));
            assertEquals(DOC, admin.get("/api/v1/docs/" + svc).text());
            // 实例超过 3 个心跳周期没上报 -> OFFLINE(有缓存仍可读)
            jdbc.update("UPDATE csap_team_env_instance SET last_seen_at = ? WHERE service = ?", System.currentTimeMillis() - 200_000, svc);
            assertEquals("OFFLINE", statusOf(admin, svc));
            assertEquals(200, admin.get("/api/v1/docs/" + svc).status());
        }
    }

    @Test
    void openApiRulesAndChangesWithBoundToken() {
        Api admin = admin();
        long project = newProject(admin, true);
        long other = newProject(admin, true);
        String svc = newService(admin, project);
        String otherSvc = newService(admin, other);
        Api bound = Api.bearer(port, token(admin, "OPEN_API", project));

        Api.Resp empty = bound.get("/api/open/v1/rules/" + svc);
        assertEquals(200, empty.status(), empty.text());
        assertEquals(svc, empty.json().get("service").asText());
        assertEquals(0, empty.json().get("rules").size());
        assertEquals(403, bound.get("/api/open/v1/rules/" + otherSvc).status());

        // 创建并提交(项目需要审批 -> PENDING),审计来源 OPEN_API / 操作者 TOKEN
        Api.Resp c = bound.post("/api/open/v1/changes", Map.of("title", "CI 同步", "reason", "自动", "submit", true,
                "items", List.of(Map.of("service", svc, "ruleKey", "Api.one", "baseVersion", 0, "afterRules", Map.of("request", Map.of(), "response", Map.of())))));
        assertEquals(200, c.status(), c.text());
        assertEquals("PENDING", c.json().get("status").asText());
        assertEquals(project, c.json().get("projectId").asLong());
        JsonNode created = auditList(admin, "action=change.create&projectId=" + project).get("items").get(0);
        assertEquals("OPEN_API", created.get("source").asText());
        assertEquals("TOKEN", created.get("actorType").asText());
        // 令牌不能审批;管理员审批后规则读取可见
        assertEquals(401, bound.post("/api/v1/changes/" + c.json().get("id").asLong() + "/approve", Map.of()).status(), "令牌不能调用控制台接口");
        assertEquals(200, admin.post("/api/v1/changes/" + c.json().get("id").asLong() + "/approve", Map.of("note", "ok")).status());
        JsonNode rules = bound.get("/api/open/v1/rules/" + svc).json();
        assertEquals(1, rules.get("rules").size());
        assertEquals("Api.one", rules.get("rules").get(0).get("ruleKey").asText());
        assertTrue(rules.get("version").asLong() > 0);
        assertNotNull(rules.get("rules").get(0).get("updatedBy"));
        // 其他项目的服务 -> 校验失败/拒绝
        assertEquals(403, bound.post("/api/open/v1/changes", Map.of("projectId", other, "title", "x",
                "items", List.of(Map.of("service", otherSvc, "ruleKey", "k", "afterRules", Map.of())))).status());
    }

    private String statusOf(Api admin, String svc) {
        for (JsonNode n : admin.get("/api/v1/services").json()) {
            if (svc.equals(n.get("service").asText())) {
                return n.get("status").asText();
            }
        }
        return null;
    }

    private JsonNode firstHit(Api api, String q, Long projectId) {
        JsonNode hits = api.get("/api/v1/docs/search?q=" + enc(q) + (projectId == null ? "" : "&projectId=" + projectId)).json();
        assertTrue(hits.size() >= 1, "应命中: " + q);
        return hits.get(0);
    }

    private static String enc(String s) {
        return java.net.URLEncoder.encode(s, java.nio.charset.StandardCharsets.UTF_8);
    }
}
