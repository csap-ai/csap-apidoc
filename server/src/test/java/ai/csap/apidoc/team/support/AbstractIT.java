package ai.csap.apidoc.team.support;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;

import ai.csap.apidoc.team.TeamApplication;
import ai.csap.apidoc.team.sync.HistorySync;
import com.fasterxml.jackson.databind.JsonNode;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** 集成测试基类:启动整个应用,提供夹具(管理员、用户、项目、服务、修改单)。 */
@SpringBootTest(classes = TeamApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "csap.team.sync.settle-millis=0",
        "csap.team.sync.history-seconds=3600",
        "csap.team.docs.refresh-seconds=3600",
        "csap.team.webhook.retry-delay-seconds=1,1,1",
        "spring.main.banner-mode=off"
})
public abstract class AbstractIT {

    public static final String ADMIN_PASSWORD = "Admin#12345";
    private static final AtomicInteger SEQ = new AtomicInteger();

    @LocalServerPort
    protected int port;
    @Autowired
    protected DataSource dataSource;
    @Autowired
    protected JdbcTemplate jdbc;
    @Autowired
    protected HistorySync historySync;

    protected static String unique(String p) {
        return p + SEQ.incrementAndGet() + "_" + UUID.randomUUID().toString().substring(0, 6);
    }

    /** 已登录的管理员(首次调用时完成初始化)。 */
    protected synchronized Api admin() {
        Api a = new Api(port);
        if (!a.get("/api/v1/setup/status").json().path("initialized").asBoolean()) {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("orgName", "测试组织");
            body.put("username", "admin");
            body.put("password", ADMIN_PASSWORD);
            body.put("displayName", "管理员");
            assertEquals(200, a.post("/api/v1/setup", body).status());
        } else {
            assertEquals(200, a.post("/api/v1/auth/login", Map.of("username", "admin", "password", ADMIN_PASSWORD)).status());
        }
        return a;
    }

    public record U(Api api, long id, String username) {
    }

    protected U newUser(Api admin, String orgRole) {
        String name = unique("u");
        Api.Resp r = admin.post("/api/v1/users", Map.of("username", name, "displayName", "用户" + name, "password", "Passw0rd!!", "orgRole", orgRole));
        assertEquals(200, r.status(), r.text());
        Api api = new Api(port);
        assertEquals(200, api.post("/api/v1/auth/login", Map.of("username", name, "password", "Passw0rd!!")).status());
        return new U(api, r.json().get("id").asLong(), name);
    }

    protected long newProject(Api admin, boolean requireApproval) {
        Api.Resp r = admin.post("/api/v1/projects", Map.of("name", unique("项目"), "description", "测试", "requireApproval", requireApproval));
        assertEquals(200, r.status(), r.text());
        return r.json().get("id").asLong();
    }

    protected void member(Api admin, long projectId, U user, String role) {
        assertEquals(200, admin.put("/api/v1/projects/" + projectId + "/members/" + user.id(), Map.of("role", role)).status());
    }

    protected String newService(Api admin, long projectId) {
        String name = unique("svc");
        Api.Resp r = admin.post("/api/v1/services", Map.of("service", name, "baseUrl", "http://127.0.0.1:9", "projectId", projectId));
        assertEquals(200, r.status(), r.text());
        return name;
    }

    protected static String rules(String field) {
        return "{\"request\":{\"" + field + "\":{\"description\":\"d\"}},\"response\":{}}";
    }

    protected static Map<String, Object> item(String service, String key, Long base, String after) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("service", service);
        m.put("ruleKey", key);
        if (base != null) {
            m.put("baseVersion", base);
        }
        m.put("afterRules", after);
        return m;
    }

    protected Api.Resp createChange(Api api, long projectId, Map<String, Object>... items) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("projectId", projectId);
        body.put("title", "修改 " + unique("t"));
        body.put("reason", "测试");
        body.put("items", new ArrayList<>(List.of(items)));
        return api.post("/api/v1/changes", body);
    }

    protected long changeId(Api.Resp r) {
        assertEquals(200, r.status(), r.text());
        return r.json().get("id").asLong();
    }

    /** 创建并走完 提交→审批→发布;返回发布后的修改单。 */
    protected JsonNode publish(U author, Api approver, long projectId, String service, String key, long base, String after) {
        long id = changeId(createChange(author.api(), projectId, item(service, key, base, after)));
        Api.Resp s = author.api().post("/api/v1/changes/" + id + "/submit", null);
        assertEquals(200, s.status(), s.text());
        Api.Resp a = approver.post("/api/v1/changes/" + id + "/approve", Map.of("note", "ok"));
        assertEquals(200, a.status(), a.text());
        assertEquals("PUBLISHED", a.json().get("status").asText());
        return a.json();
    }

    protected JsonNode auditList(Api admin, String query) {
        Api.Resp r = admin.get("/api/v1/audit?size=200&" + query);
        assertEquals(200, r.status(), r.text());
        return r.json();
    }
}
