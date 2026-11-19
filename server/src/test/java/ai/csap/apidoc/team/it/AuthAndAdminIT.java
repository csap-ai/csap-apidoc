package ai.csap.apidoc.team.it;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import ai.csap.apidoc.team.support.AbstractIT;
import ai.csap.apidoc.team.support.Api;
import ai.csap.apidoc.team.support.TestKeys;
import com.fasterxml.jackson.databind.JsonNode;

/** 契约 3、7.2、9:初始化、登录、CSRF、锁定、用户、项目与成员权限、授权人数。 */
class AuthAndAdminIT extends AbstractIT {

    @DynamicPropertySource
    static void db(DynamicPropertyRegistry r) {
        H2Db.register(r);
    }

    @Test
    void setupIsRequiredBeforeAnythingElseAndOnlyOnce() {
        Api admin = admin(); // 触发初始化
        Api anon = new Api(port);
        assertTrue(anon.get("/api/v1/setup/status").json().get("initialized").asBoolean());
        assertEquals(409, anon.post("/api/v1/setup", Map.of("orgName", "x", "username", "again", "password", "Passw0rd!!", "displayName", "x")).status());
        assertEquals(401, anon.get("/api/v1/auth/me").status());
        assertEquals("UNAUTHORIZED", anon.get("/api/v1/projects").code());
        assertEquals(200, admin.get("/api/v1/auth/me").status());
    }

    @Test
    void loginCookieAttributesAndCsrfHeader() throws Exception {
        admin();
        java.net.http.HttpClient c = java.net.http.HttpClient.newHttpClient();
        java.net.http.HttpRequest req = java.net.http.HttpRequest.newBuilder(java.net.URI.create("http://127.0.0.1:" + port + "/api/v1/auth/login"))
                .header("Content-Type", "application/json").header("X-CSAP-Team", "1")
                .POST(java.net.http.HttpRequest.BodyPublishers.ofString("{\"username\":\"admin\",\"password\":\"" + ADMIN_PASSWORD + "\"}")).build();
        java.net.http.HttpResponse<String> res = c.send(req, java.net.http.HttpResponse.BodyHandlers.ofString());
        assertEquals(200, res.statusCode());
        String cookie = res.headers().firstValue("Set-Cookie").orElseThrow();
        assertTrue(cookie.startsWith("CSAP_TEAM_SESSION="));
        assertTrue(cookie.contains("HttpOnly"));
        assertTrue(cookie.contains("SameSite=Strict"));
        JsonNode me = Api.MAPPER.readTree(res.body());
        assertEquals("ADMIN", me.get("orgRole").asText());
        assertTrue(me.get("projects").isArray());

        // 缺少 X-CSAP-Team 的写请求被拒
        Api a = admin();
        Api.Resp r = a.sendNoCsrf("POST", "/api/v1/projects", Map.of("name", unique("csrf")));
        assertEquals(403, r.status());
        assertEquals("FORBIDDEN", r.code());
        // GET 不需要
        assertEquals(200, a.get("/api/v1/projects").status());
    }

    @Test
    void failedLoginLocksAfterFiveAttempts() {
        Api admin = admin();
        U u = newUser(admin, "MEMBER");
        Api anon = new Api(port);
        for (int i = 0; i < 5; i++) {
            assertEquals(401, anon.post("/api/v1/auth/login", Map.of("username", u.username(), "password", "wrong-password")).status());
        }
        // 第 6 次即使密码正确也被锁
        Api.Resp r = anon.post("/api/v1/auth/login", Map.of("username", u.username(), "password", "Passw0rd!!"));
        assertEquals(401, r.status());
        assertTrue(r.json().get("message").asText().contains("锁定"));
        assertTrue(auditList(admin, "action=auth.login.failed&actor=" + u.username()).get("total").asInt() >= 5);
    }

    @Test
    void passwordChangeInvalidatesOldSessions() {
        Api admin = admin();
        U u = newUser(admin, "MEMBER");
        Api second = new Api(port);
        assertEquals(200, second.post("/api/v1/auth/login", Map.of("username", u.username(), "password", "Passw0rd!!")).status());
        assertEquals(400, u.api().post("/api/v1/auth/password", Map.of("oldPassword", "bad", "newPassword", "N3wPassword!")).status());
        assertEquals(200, u.api().post("/api/v1/auth/password", Map.of("oldPassword", "Passw0rd!!", "newPassword", "N3wPassword!")).status());
        assertEquals(200, u.api().get("/api/v1/auth/me").status(), "当前会话换发新 Cookie");
        assertEquals(401, second.get("/api/v1/auth/me").status(), "其他旧会话失效");
        assertEquals(200, new Api(port).post("/api/v1/auth/login", Map.of("username", u.username(), "password", "N3wPassword!")).status());
    }

    @Test
    void passwordIsBcryptAndDisabledUserCannotUseSession() {
        Api admin = admin();
        U u = newUser(admin, "MEMBER");
        String hash = jdbc.queryForObject("SELECT password_hash FROM csap_team_user WHERE id = ?", String.class, u.id());
        assertTrue(hash.startsWith("$2"), "BCrypt");
        assertFalse(hash.contains("Passw0rd"));
        assertEquals(200, admin.patch("/api/v1/users/" + u.id(), Map.of("status", "DISABLED")).status());
        assertEquals(401, u.api().get("/api/v1/auth/me").status());
        assertEquals(401, new Api(port).post("/api/v1/auth/login", Map.of("username", u.username(), "password", "Passw0rd!!")).status());
        assertEquals(200, admin.patch("/api/v1/users/" + u.id(), Map.of("status", "ACTIVE", "displayName", "改名")).status());
        assertEquals("改名", new Api(port) {{ post("/api/v1/auth/login", Map.of("username", u.username(), "password", "Passw0rd!!")); }}.get("/api/v1/auth/me").json().get("displayName").asText());
    }

    @Test
    void meCarriesLicenseCandidatesAndAuditDetail() {
        Api admin = admin();
        U u = newUser(admin, "MEMBER");
        JsonNode lic = u.api().get("/api/v1/auth/me").json().get("license");
        assertEquals("VALID", lic.get("status").asText());
        assertTrue(lic.has("maxMembers") && lic.has("activeMembers") && lic.has("expiresAt"));
        assertFalse(lic.has("licensee"));
        long project = newProject(admin, true);
        U m = newUser(admin, "MEMBER");
        member(admin, project, m, "MAINTAINER");
        JsonNode cands = m.api().get("/api/v1/projects/" + project + "/candidates").json();
        for (JsonNode c : cands) {
            assertTrue(c.has("id") && c.has("username") && c.has("displayName"));
            assertTrue(c.get("id").asLong() != m.id());
        }
        assertTrue(cands.size() >= 1);
        assertEquals(403, u.api().get("/api/v1/projects/" + project + "/candidates").status());
        long aid = auditList(admin, "action=project.create&projectId=" + project).get("items").get(0).get("id").asLong();
        assertEquals(aid, admin.get("/api/v1/audit/" + aid).json().get("id").asLong());
        assertEquals(403, u.api().get("/api/v1/audit/" + aid).status());
    }

    @Test
    void userAdministrationIsAdminOnly() {
        Api admin = admin();
        U u = newUser(admin, "MEMBER");
        assertEquals(403, u.api().get("/api/v1/users").status());
        assertEquals(403, u.api().post("/api/v1/users", Map.of("username", "zz", "password", "Passw0rd!!")).status());
        assertEquals(403, u.api().get("/api/v1/tokens").status());
        assertEquals(403, u.api().get("/api/v1/license").status());
        assertEquals(403, u.api().post("/api/v1/projects", Map.of("name", unique("p"))).status());
        assertEquals(400, admin.post("/api/v1/users", Map.of("username", "a b", "password", "Passw0rd!!")).status());
        assertEquals(400, admin.post("/api/v1/users", Map.of("username", unique("short"), "password", "123")).status());
        assertEquals(409, admin.post("/api/v1/users", Map.of("username", u.username(), "password", "Passw0rd!!")).status());
        // 列表不回显密码哈希
        for (JsonNode n : admin.get("/api/v1/users").json()) {
            assertNull(n.get("passwordHash"));
            assertNull(n.get("password_hash"));
        }
    }

    @Test
    void projectRolesFollowThePermissionMatrix() {
        Api admin = admin();
        long project = newProject(admin, true);
        long other = newProject(admin, true);
        String svc = newService(admin, project);
        U maintainer = newUser(admin, "MEMBER");
        U dev = newUser(admin, "MEMBER");
        U viewer = newUser(admin, "MEMBER");
        U outsider = newUser(admin, "MEMBER");
        member(admin, project, maintainer, "MAINTAINER");
        member(admin, project, dev, "DEVELOPER");
        member(admin, project, viewer, "VIEWER");

        // 可见范围
        assertEquals(1, dev.api().get("/api/v1/projects").json().size());
        assertEquals(0, outsider.api().get("/api/v1/projects").json().size());
        assertEquals(404, outsider.api().get("/api/v1/projects/" + project).status());
        assertEquals(404, outsider.api().get("/api/v1/rules/" + svc).status());
        assertEquals(200, viewer.api().get("/api/v1/rules/" + svc).status());
        assertEquals(1, viewer.api().get("/api/v1/services").json().size());
        JsonNode me = dev.api().get("/api/v1/auth/me").json();
        assertEquals("DEVELOPER", me.get("projects").get(0).get("role").asText());

        // 只读不能建修改单;开发可以
        assertEquals(403, createChange(viewer.api(), project, item(svc, "K", 0L, rules("a"))).status());
        long id = changeId(createChange(dev.api(), project, item(svc, "K", 0L, rules("a"))));
        // 开发不能审批,不能管成员/设置
        assertEquals(200, dev.api().post("/api/v1/changes/" + id + "/submit", null).status());
        assertEquals(403, dev.api().post("/api/v1/changes/" + id + "/approve", Map.of()).status());
        assertEquals(403, dev.api().put("/api/v1/projects/" + project + "/members/" + outsider.id(), Map.of("role", "VIEWER")).status());
        assertEquals(403, dev.api().patch("/api/v1/projects/" + project, Map.of("requireApproval", false)).status());
        // 开发查审计不可导出;只读不能查
        assertEquals(200, dev.api().get("/api/v1/audit").status());
        assertEquals(403, dev.api().get("/api/v1/audit/export?format=csv").status());
        assertEquals(403, viewer.api().get("/api/v1/audit").status());
        // 其他项目的审计不可见
        assertEquals(403, dev.api().get("/api/v1/audit?projectId=" + other).status());

        // 维护者:能管成员(不能设管理员),能审批别人的
        assertEquals(200, maintainer.api().put("/api/v1/projects/" + project + "/members/" + outsider.id(), Map.of("role", "VIEWER")).status());
        assertEquals(403, maintainer.api().put("/api/v1/projects/" + project + "/members/1", Map.of("role", "VIEWER")).status(), "不能动管理员");
        assertEquals(200, maintainer.api().delete("/api/v1/projects/" + project + "/members/" + outsider.id()).status());
        assertEquals(200, maintainer.api().post("/api/v1/changes/" + id + "/approve", Map.of("note", "ok")).status());
        assertEquals(200, maintainer.api().get("/api/v1/audit/export?format=json").status());
        assertEquals(400, maintainer.api().get("/api/v1/audit/export?format=xml").status());

        // 维护者不能审批自己提交的;管理员可以
        long mine = changeId(createChange(maintainer.api(), project, item(svc, "K2", 0L, rules("m"))));
        maintainer.api().post("/api/v1/changes/" + mine + "/submit", null);
        assertEquals(403, maintainer.api().post("/api/v1/changes/" + mine + "/approve", Map.of()).status());
        assertEquals(200, admin.post("/api/v1/changes/" + mine + "/approve", Map.of()).status());

        // 驳回与撤回
        long rej = changeId(createChange(dev.api(), project, item(svc, "K3", 0L, rules("r"))));
        dev.api().post("/api/v1/changes/" + rej + "/submit", null);
        Api.Resp rr = maintainer.api().post("/api/v1/changes/" + rej + "/reject", Map.of("note", "不行"));
        assertEquals("REJECTED", rr.json().get("status").asText());
        assertEquals("不行", rr.json().get("decisionNote").asText());
        long wd = changeId(createChange(dev.api(), project, item(svc, "K4", 0L, rules("w"))));
        assertEquals("WITHDRAWN", dev.api().post("/api/v1/changes/" + wd + "/withdraw", null).json().get("status").asText());
        assertEquals(400, dev.api().post("/api/v1/changes/" + wd + "/submit", null).status(), "已撤回不能再提交");

        // 编辑草稿:仅作者
        long draft = changeId(createChange(dev.api(), project, item(svc, "K5", 0L, rules("d"))));
        assertEquals(403, maintainer.api().patch("/api/v1/changes/" + draft, Map.of("title", "x")).status());
        Api.Resp up = dev.api().patch("/api/v1/changes/" + draft, Map.of("title", "新标题", "items", List.of(item(svc, "K5", 0L, rules("d2")))));
        assertEquals(200, up.status(), up.text());
        assertEquals("新标题", up.json().get("title").asText());

        // 列表与筛选
        JsonNode list = dev.api().get("/api/v1/changes?projectId=" + project + "&mine=true&size=2&page=1").json();
        assertTrue(list.get("total").asInt() >= 4);
        assertEquals(2, list.get("items").size());
        assertEquals("REJECTED", dev.api().get("/api/v1/changes?projectId=" + project + "&status=REJECTED").json().get("items").get(0).get("status").asText());

        // 服务在项目外 -> 校验失败
        String foreign = newService(admin, other);
        assertEquals(400, createChange(dev.api(), project, item(foreign, "K", 0L, rules("x"))).status());
        assertEquals(400, createChange(dev.api(), project, item(svc, "K", 0L, "not json")).status());
        // afterRules 缺失不能当作删除
        Api.Resp missing = dev.api().post("/api/v1/changes", Map.of("projectId", project, "title", "t", "items", List.of(Map.of("service", svc, "ruleKey", "Z", "baseVersion", 0))));
        assertEquals(400, missing.status());

        // 删除项目:有服务时不能删;成员关系随项目清理
        assertEquals(409, admin.delete("/api/v1/projects/" + project).status());
        long empty = newProject(admin, true);
        assertEquals(200, admin.delete("/api/v1/projects/" + empty).status());
        assertEquals(404, admin.get("/api/v1/projects/" + empty).status());
    }

    @Test
    void licenseLimitsActiveUsers() {
        Api admin = admin();
        String big = TestKeys.license("测试公司", 500, System.currentTimeMillis() + 86_400_000L * 365);
        try {
            long active = jdbc.queryForObject("SELECT COUNT(*) FROM csap_team_user WHERE status = 'ACTIVE'", Long.class);
            // 授权人数刚好等于当前活跃用户数 -> 再新增报 LICENSE_LIMIT
            Api.Resp inst = admin.post("/api/v1/license", Map.of("license", TestKeys.license("小公司", (int) active, System.currentTimeMillis() + 86_400_000L)));
            assertEquals(200, inst.status(), inst.text());
            assertEquals("VALID", inst.json().get("status").asText());
            assertEquals("小公司", inst.json().get("licensee").asText());
            Api.Resp r = admin.post("/api/v1/users", Map.of("username", unique("lim"), "password", "Passw0rd!!"));
            assertEquals(403, r.status());
            assertEquals("LICENSE_LIMIT", r.code());
            // 已过期:回到试用上限,已有用户不受影响(能登录),只限制新增
            admin.post("/api/v1/license", Map.of("license", TestKeys.license("过期公司", 500, System.currentTimeMillis() - 1000)));
            JsonNode lic = admin.get("/api/v1/license").json();
            assertEquals("EXPIRED", lic.get("status").asText());
            assertEquals(active, lic.get("activeMembers").asLong());
            assertEquals(200, admin.get("/api/v1/auth/me").status());
            // 无效授权被拒绝
            assertEquals(400, admin.post("/api/v1/license", Map.of("license", "bm90IGEgbGljZW5zZQ==")).status());
            assertEquals(400, admin.post("/api/v1/license", Map.of("license", "")).status());
        } finally {
            assertEquals(200, admin.post("/api/v1/license", Map.of("license", big)).status());
        }
        JsonNode lic = admin.get("/api/v1/license").json();
        assertEquals("VALID", lic.get("status").asText());
        assertEquals(500, lic.get("maxMembers").asInt());
        assertNotNull(lic.get("expiresAt"));
    }

    @Test
    void auditRecordsLoginsAndAdminOperations() {
        Api admin = admin();
        U u = newUser(admin, "MEMBER");
        assertTrue(auditList(admin, "action=user.create").get("total").asInt() >= 1);
        assertTrue(auditList(admin, "action=auth.login&actor=" + u.username()).get("total").asInt() >= 1);
        JsonNode any = auditList(admin, "source=CONSOLE").get("items").get(0);
        assertEquals("CONSOLE", any.get("source").asText());
        assertNotNull(any.get("ip"));
        // 前缀筛选
        assertTrue(auditList(admin, "action=user.*").get("total").asInt() >= 1);
        // 时间段筛选:未来没有记录
        assertEquals(0, auditList(admin, "from=" + (System.currentTimeMillis() + 100_000)).get("total").asInt());
    }
}
