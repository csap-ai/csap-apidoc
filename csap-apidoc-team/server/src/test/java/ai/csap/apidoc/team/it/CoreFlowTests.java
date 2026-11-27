package ai.csap.apidoc.team.it;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.Test;

import ai.csap.apidoc.database.DatabaseDialect;
import ai.csap.apidoc.database.DatabaseRuleStore;
import ai.csap.apidoc.strategy.store.RuleStore;
import ai.csap.apidoc.team.support.AbstractIT;
import ai.csap.apidoc.team.support.Api;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * 跨数据库的核心流程:发布、冲突、回滚、历史同步,以及与开源 DatabaseRuleStore 的互通。
 * H2、MySQL 8、PostgreSQL 16 各跑一遍(子类提供数据源)。
 */
public abstract class CoreFlowTests extends AbstractIT {

    @Test
    void publishWritesRuleHistoryAndAudit() {
        Api admin = admin();
        long project = newProject(admin, true);
        String svc = newService(admin, project);
        U dev = newUser(admin, "MEMBER");
        U maintainer = newUser(admin, "MEMBER");
        member(admin, project, dev, "DEVELOPER");
        member(admin, project, maintainer, "MAINTAINER");

        JsonNode published = publish(dev, maintainer.api(), project, svc, "K.create", 0, rules("name"));
        assertNotNull(published.get("publishedAt"));
        assertEquals(maintainer.id(), published.path("decidedBy").path("id").asLong());

        Api.Resp item = dev.api().get("/api/v1/rules/" + svc + "/item?key=K.create");
        assertEquals(200, item.status());
        assertTrue(item.json().get("version").asLong() > 0);
        assertEquals(rules("name"), item.json().get("rules").asText().replace(" ", ""));

        // 发布写入与开源 save 一致:updated_by 是审批(发布)人的用户名,history 一条
        Map<String, Object> row = jdbc.queryForMap("SELECT updated_by, version FROM csap_apidoc_rule WHERE service = ? AND rule_key = ?", svc, "K.create");
        assertEquals(maintainer.username(), row.get("updated_by"));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM csap_apidoc_history WHERE service = ?", Integer.class, svc));
        assertEquals(((Number) row.get("version")).longValue(), jdbc.queryForObject("SELECT version FROM csap_apidoc_meta WHERE service = ?", Long.class, svc));

        JsonNode history = dev.api().get("/api/v1/rules/" + svc + "/history?key=K.create").json();
        assertEquals(1, history.size());
        assertEquals(published.get("id").asLong(), history.get(0).get("changeId").asLong());
        assertTrue(history.get(0).get("beforeRules").isNull());

        JsonNode audits = auditList(admin, "action=change.publish&service=" + svc);
        assertEquals(1, audits.get("total").asInt());
        JsonNode a = audits.get("items").get(0);
        assertEquals("K.create", a.get("ruleKey").asText());
        assertEquals(history.get(0).get("id").asLong(), a.get("ossHistoryId").asLong());
        assertTrue(a.get("diff").asText().contains("+"));
        assertEquals(maintainer.username(), a.get("actorName").asText());

        // 团队版自己发布的记录不应被同步成 rule.changed_outside
        historySync.syncOnce();
        assertEquals(0, auditList(admin, "action=rule.changed_outside&service=" + svc).get("total").asInt());
    }

    @Test
    void conflictOnApproveThenRebase() {
        Api admin = admin();
        long project = newProject(admin, true);
        String svc = newService(admin, project);
        U dev = newUser(admin, "MEMBER");
        U maintainer = newUser(admin, "MEMBER");
        member(admin, project, dev, "DEVELOPER");
        member(admin, project, maintainer, "MAINTAINER");
        U dev2 = newUser(admin, "MEMBER");
        member(admin, project, dev2, "DEVELOPER");

        // A:基于版本 0,提交后等待审批
        long a = changeId(createChange(dev.api(), project, item(svc, "K.c", 0L, rules("fromA"))));
        assertEquals(200, dev.api().post("/api/v1/changes/" + a + "/submit", null).status());

        // B 先发布
        publish(dev2, maintainer.api(), project, svc, "K.c", 0, rules("fromB"));

        // 审批 A:版本已变 -> 409 + 状态 CONFLICT
        Api.Resp r = maintainer.api().post("/api/v1/changes/" + a + "/approve", Map.of("note", "x"));
        assertEquals(409, r.status());
        assertEquals("CONFLICT", r.code());
        assertEquals(a, r.json().get("changeId").asLong());
        JsonNode ci = r.json().get("items").get(0);
        assertEquals("K.c", ci.get("ruleKey").asText());
        assertEquals(0, ci.get("baseVersion").asLong());
        assertTrue(ci.get("currentVersion").asLong() > 0);
        assertTrue(ci.get("currentRules").asText().contains("fromB"));
        JsonNode detail = dev.api().get("/api/v1/changes/" + a).json();
        assertEquals("CONFLICT", detail.get("status").asText());
        assertTrue(detail.get("items").get(0).get("conflict").asBoolean());
        // 库里仍是 B 的内容
        assertTrue(dev.api().get("/api/v1/rules/" + svc + "/item?key=K.c").json().get("rules").asText().contains("fromB"));
        assertEquals(1, auditList(admin, "action=change.conflict&service=&projectId=" + project).get("total").asInt());

        // rebase:作者确认 afterRules,回到 DRAFT,baseVersion 更新
        Api.Resp rb = dev.api().post("/api/v1/changes/" + a + "/rebase", Map.of("items", List.of(Map.of("service", svc, "ruleKey", "K.c", "afterRules", rules("merged")))));
        assertEquals(200, rb.status(), rb.text());
        assertEquals("DRAFT", rb.json().get("status").asText());
        assertEquals(ci.get("currentVersion").asLong(), rb.json().get("items").get(0).get("baseVersion").asLong());
        assertEquals(200, dev.api().post("/api/v1/changes/" + a + "/submit", null).status());
        assertEquals("PUBLISHED", maintainer.api().post("/api/v1/changes/" + a + "/approve", Map.of("note", "ok")).json().get("status").asText());
        assertTrue(dev.api().get("/api/v1/rules/" + svc + "/item?key=K.c").json().get("rules").asText().contains("merged"));
    }

    @Test
    void conflictOnSubmitMarksConflictImmediately() {
        Api admin = admin();
        long project = newProject(admin, true);
        String svc = newService(admin, project);
        U dev = newUser(admin, "MEMBER");
        member(admin, project, dev, "DEVELOPER");

        long stale = changeId(createChange(dev.api(), project, item(svc, "K.s", 0L, rules("stale"))));
        publish(new U(admin, 0, "admin"), admin, project, svc, "K.s", 0, rules("fresh"));

        Api.Resp r = dev.api().post("/api/v1/changes/" + stale + "/submit", null);
        assertEquals(409, r.status());
        assertEquals("CONFLICT", dev.api().get("/api/v1/changes/" + stale).json().get("status").asText());
    }

    @Test
    void rollbackCreatesRollbackChangeAndRestores() {
        Api admin = admin();
        long project = newProject(admin, true);
        String svc = newService(admin, project);
        U dev = newUser(admin, "MEMBER");
        U maintainer = newUser(admin, "MEMBER");
        member(admin, project, dev, "DEVELOPER");
        member(admin, project, maintainer, "MAINTAINER");

        publish(dev, maintainer.api(), project, svc, "K.r", 0, rules("v1"));
        long v1 = dev.api().get("/api/v1/rules/" + svc + "/item?key=K.r").json().get("version").asLong();
        publish(dev, maintainer.api(), project, svc, "K.r", v1, rules("v2"));

        JsonNode history = dev.api().get("/api/v1/rules/" + svc + "/history?key=K.r").json();
        assertEquals(2, history.size());
        long firstHistory = history.get(1).get("id").asLong(); // 最早一条:after = v1

        // 只读不能回滚
        U viewer = newUser(admin, "MEMBER");
        member(admin, project, viewer, "VIEWER");
        assertEquals(403, viewer.api().post("/api/v1/rules/" + svc + "/rollback", Map.of("key", "K.r", "historyId", firstHistory, "reason", "x")).status());

        Api.Resp rb = dev.api().post("/api/v1/rules/" + svc + "/rollback", Map.of("key", "K.r", "historyId", firstHistory, "reason", "改错了"));
        assertEquals(200, rb.status(), rb.text());
        assertEquals("DRAFT", rb.json().get("status").asText());
        assertEquals("ROLLBACK", rb.json().get("kind").asText());
        long id = rb.json().get("id").asLong();
        assertEquals(200, dev.api().post("/api/v1/changes/" + id + "/submit", null).status());
        assertEquals("PUBLISHED", maintainer.api().post("/api/v1/changes/" + id + "/approve", Map.of("note", "")).json().get("status").asText());
        assertTrue(dev.api().get("/api/v1/rules/" + svc + "/item?key=K.r").json().get("rules").asText().contains("v1"));

        // 回滚一次"删除":目标历史的 after 为 null -> 删除规则
        long delId = changeId(createChange(dev.api(), project, item(svc, "K.r", dev.api().get("/api/v1/rules/" + svc + "/item?key=K.r").json().get("version").asLong(), null)));
        dev.api().post("/api/v1/changes/" + delId + "/submit", null);
        assertEquals(200, maintainer.api().post("/api/v1/changes/" + delId + "/approve", Map.of()).status());
        assertNull(dev.api().get("/api/v1/rules/" + svc + "/item?key=K.r").json().get("rules").textValue());
        JsonNode h2 = dev.api().get("/api/v1/rules/" + svc + "/history?key=K.r").json();
        long deleteHistory = h2.get(0).get("id").asLong();
        assertTrue(h2.get(0).get("afterRules").isNull());
        // 回滚到"创建之前"(最早历史的 before 为 null 的情形用 after 取:这里回滚到 v2 的历史)
        long v2History = h2.get(2).get("id").asLong();
        long rb2 = changeId(dev.api().post("/api/v1/rules/" + svc + "/rollback", Map.of("key", "K.r", "historyId", v2History, "reason", "恢复")));
        dev.api().post("/api/v1/changes/" + rb2 + "/submit", null);
        assertEquals(200, maintainer.api().post("/api/v1/changes/" + rb2 + "/approve", Map.of()).status());
        assertTrue(dev.api().get("/api/v1/rules/" + svc + "/item?key=K.r").json().get("rules").asText().contains("v2"));

        // diff:比较两条历史之后的内容,to 省略 = 当前
        Api.Resp d = dev.api().get("/api/v1/rules/" + svc + "/diff?key=K.r&from=" + firstHistory + "&to=" + v2History);
        assertEquals(200, d.status());
        assertTrue(d.json().get("diff").asText().contains("v1") && d.json().get("diff").asText().contains("v2"));
        assertEquals(200, dev.api().get("/api/v1/rules/" + svc + "/diff?key=K.r&from=" + deleteHistory).status());
    }

    @Test
    void concurrentApprovalsOnlyOneWins() throws Exception {
        Api admin = admin();
        long project = newProject(admin, true);
        String svc = newService(admin, project);
        U dev = newUser(admin, "MEMBER");
        U m1 = newUser(admin, "MEMBER");
        U m2 = newUser(admin, "MEMBER");
        member(admin, project, dev, "DEVELOPER");
        member(admin, project, m1, "MAINTAINER");
        member(admin, project, m2, "MAINTAINER");

        List<Long> ids = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            long id = changeId(createChange(dev.api(), project, item(svc, "K.race", 0L, rules("race" + i))));
            assertEquals(200, dev.api().post("/api/v1/changes/" + id + "/submit", null).status());
            ids.add(id);
        }
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch go = new CountDownLatch(1);
        Future<Api.Resp> f1 = pool.submit(() -> { go.await(); return m1.api().post("/api/v1/changes/" + ids.get(0) + "/approve", Map.of()); });
        Future<Api.Resp> f2 = pool.submit(() -> { go.await(); return m2.api().post("/api/v1/changes/" + ids.get(1) + "/approve", Map.of()); });
        go.countDown();
        Api.Resp r1 = f1.get();
        Api.Resp r2 = f2.get();
        int s1 = r1.status();
        int s2 = r2.status();
        pool.shutdown();
        assertEquals(1, (s1 == 200 ? 1 : 0) + (s2 == 200 ? 1 : 0), "只有一个能发布: " + s1 + "," + s2);
        assertTrue(s1 == 409 || s2 == 409, r1.text() + " | " + r2.text());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM csap_apidoc_history WHERE service = ?", Integer.class, svc));
    }

    @Test
    void noApprovalProjectPublishesOnSubmit() {
        Api admin = admin();
        long project = newProject(admin, false);
        String svc = newService(admin, project);
        U dev = newUser(admin, "MEMBER");
        member(admin, project, dev, "DEVELOPER");
        long id = changeId(createChange(dev.api(), project, item(svc, "K.n", 0L, rules("direct"))));
        Api.Resp s = dev.api().post("/api/v1/changes/" + id + "/submit", null);
        assertEquals(200, s.status(), s.text());
        assertEquals("PUBLISHED", s.json().get("status").asText());
        assertTrue(dev.api().get("/api/v1/rules/" + svc + "/item?key=K.n").json().get("rules").asText().contains("direct"));
        assertEquals(1, auditList(admin, "action=change.publish&service=" + svc).get("total").asInt());
    }

    @Test
    void sameContentIsSkippedNotConflict() {
        Api admin = admin();
        long project = newProject(admin, false);
        String svc = newService(admin, project);
        U dev = newUser(admin, "MEMBER");
        member(admin, project, dev, "DEVELOPER");
        long first = changeId(createChange(dev.api(), project, item(svc, "K.same", 0L, rules("x"))));
        assertEquals("PUBLISHED", dev.api().post("/api/v1/changes/" + first + "/submit", null).json().get("status").asText());
        long versionAfter = jdbc.queryForObject("SELECT version FROM csap_apidoc_meta WHERE service = ?", Long.class, svc);
        // 基于过期版本 0,但内容与库里相同(键顺序、空白不同)-> 跳过,不算冲突,版本号不变
        long id = changeId(createChange(dev.api(), project, item(svc, "K.same", 0L, "{ \"response\": {}, \"request\": {\"x\": {\"description\": \"d\"}} }")));
        Api.Resp s = dev.api().post("/api/v1/changes/" + id + "/submit", null);
        assertEquals(200, s.status(), s.text());
        assertEquals("PUBLISHED", s.json().get("status").asText());
        assertEquals(versionAfter, jdbc.queryForObject("SELECT version FROM csap_apidoc_meta WHERE service = ?", Long.class, svc));
        assertNull(s.json().get("items").get(0).get("publishedVersion").numberValue());
    }

    /** 互通:团队版发布后,开源 DatabaseRuleStore(模拟服务)能 load 到;服务侧 save 后团队版记 rule.changed_outside。 */
    @Test
    void interopWithOpenSourceDatabaseRuleStore() throws Exception {
        Api admin = admin();
        long project = newProject(admin, true);
        String svc = newService(admin, project);
        U dev = newUser(admin, "MEMBER");
        U maintainer = newUser(admin, "MEMBER");
        member(admin, project, dev, "DEVELOPER");
        member(admin, project, maintainer, "MAINTAINER");

        DatabaseDialect dialect = DatabaseDialect.fromJdbcUrl(dataSource.getConnection().getMetaData().getURL());
        DatabaseRuleStore store = new DatabaseRuleStore(dataSource, dialect, "csap_apidoc_", svc);
        store.init(true, false);
        long v0 = store.version();

        publish(dev, maintainer.api(), project, svc, "Order.create", 0, rules("fromTeam"));
        RuleStore.Snapshot snap = store.load();
        assertTrue(snap.version() > v0, "meta.version 应增加,服务轮询才会重新加载");
        assertEquals(1, snap.rules().size());
        assertTrue(snap.rules().get("Order.create").contains("fromTeam"));
        assertEquals(maintainer.username(), store.history("Order.create", 10).get(0).getUpdatedBy());

        // 服务侧(DevTools)直接保存
        long v = store.save("Order.create", rules("fromService"), "devtools");
        assertTrue(v > snap.version());
        int before = auditList(admin, "action=rule.changed_outside&service=" + svc).get("total").asInt();
        assertEquals(0, before);
        historySync.syncOnce();
        JsonNode outside = auditList(admin, "action=rule.changed_outside&service=" + svc);
        assertEquals(1, outside.get("total").asInt());
        JsonNode a = outside.get("items").get(0);
        assertEquals("SERVICE", a.get("source").asText());
        assertEquals("SERVICE", a.get("actorType").asText());
        assertEquals("devtools", a.get("actorName").asText());
        assertEquals("Order.create", a.get("ruleKey").asText());
        assertEquals(project, a.get("projectId").asLong());
        assertTrue(a.get("diff").asText().contains("fromService"));
        // 内容相同的 history(旧版 Go 服务会写 before==after):不记审计
        long vSame = store.version();
        jdbc.update("INSERT INTO csap_apidoc_history (service, rule_key, before_rules, after_rules, version, updated_by, updated_at)"
                + " VALUES (?, 'Order.create', ?, ?, ?, 'old-go', ?)", svc, rules("fromService"), " " + rules("fromService").replace("{", "{ "), vSame + 1, System.currentTimeMillis());
        historySync.syncOnce();
        assertEquals(0, auditList(admin, "action=rule.changed_outside&actor=old-go&service=" + svc).get("total").asInt());
        assertEquals(1, auditList(admin, "action=rule.changed_outside&service=" + svc).get("total").asInt());
        // 重复同步不重复记录
        historySync.syncOnce();
        assertEquals(1, auditList(admin, "action=rule.changed_outside&service=" + svc).get("total").asInt());

        // 服务侧改了之后,基于旧版本的修改单不能覆盖
        long stale = changeId(createChange(dev.api(), project, item(svc, "Order.create", v - 1, rules("late"))));
        assertEquals(409, dev.api().post("/api/v1/changes/" + stale + "/submit", null).status());
    }

    /** 同样的操作序列,团队版发布与开源 save 写出的 rule / history / meta 完全一致(除时间与服务名)。 */
    @Test
    void publishWritesSameRowsAsOpenSourceSave() throws Exception {
        Api admin = admin();
        long project = newProject(admin, false);
        String teamSvc = newService(admin, project);
        String ossSvc = unique("oss");
        U dev = newUser(admin, "MEMBER");
        member(admin, project, dev, "DEVELOPER");
        DatabaseDialect dialect = DatabaseDialect.fromJdbcUrl(dataSource.getConnection().getMetaData().getURL());
        DatabaseRuleStore store = new DatabaseRuleStore(dataSource, dialect, "csap_apidoc_", ossSvc);
        store.init(true, false);

        String[][] seq = {{"A", rules("a1")}, {"B", rules("b1")}, {"A", rules("a2")}, {"B", null}};
        for (String[] s : seq) {
            long base = jdbc.query("SELECT version FROM csap_apidoc_rule WHERE service = ? AND rule_key = ?", rs -> rs.next() ? rs.getLong(1) : 0L, teamSvc, s[0]);
            long id = changeId(createChange(dev.api(), project, item(teamSvc, s[0], base, s[1])));
            assertEquals(200, dev.api().post("/api/v1/changes/" + id + "/submit", null).status());
            store.save(s[0], s[1], dev.username());
        }
        assertEquals(store.version(), jdbc.queryForObject("SELECT version FROM csap_apidoc_meta WHERE service = ?", Long.class, teamSvc));
        List<Map<String, Object>> t = jdbc.queryForList("SELECT rule_key, before_rules, after_rules, version FROM csap_apidoc_history WHERE service = ? ORDER BY id", teamSvc);
        List<Map<String, Object>> o = jdbc.queryForList("SELECT rule_key, before_rules, after_rules, version FROM csap_apidoc_history WHERE service = ? ORDER BY id", ossSvc);
        assertEquals(o.size(), t.size());
        for (int i = 0; i < o.size(); i++) {
            assertEquals(o.get(i).get("rule_key"), t.get(i).get("rule_key"));
            assertEquals(String.valueOf(o.get(i).get("before_rules")), String.valueOf(t.get(i).get("before_rules")));
            assertEquals(String.valueOf(o.get(i).get("after_rules")), String.valueOf(t.get(i).get("after_rules")));
            assertEquals(((Number) o.get(i).get("version")).longValue(), ((Number) t.get(i).get("version")).longValue());
        }
        assertEquals(jdbc.queryForObject("SELECT COUNT(*) FROM csap_apidoc_rule WHERE service = ?", Integer.class, ossSvc),
                jdbc.queryForObject("SELECT COUNT(*) FROM csap_apidoc_rule WHERE service = ?", Integer.class, teamSvc));
        assertFalse(t.isEmpty());
    }

    /** 同一个新服务的多个实例同时首次注册(联调时在 PostgreSQL 上出现过 500:唯一键冲突让事务中止)。 */
    @Test
    void concurrentFirstRegistrationOfSameServiceAllSucceed() throws Exception {
        Api admin = admin();
        Api.Resp tr = admin.post("/api/v1/tokens", Map.of("name", unique("tok"), "scope", "REGISTER"));
        assertEquals(200, tr.status());
        Api bearer = Api.bearer(port, tr.json().get("token").asText());
        String svc = unique("race");
        int n = 8;
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(n);
        try {
            List<Future<Integer>> fs = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                String inst = "i" + i;
                fs.add(pool.submit(() -> {
                    start.await();
                    return bearer.post("/api/open/v1/services/register", Map.of("service", svc, "instanceId", inst,
                            "baseUrl", "http://127.0.0.1:9", "docPath", "/csap/apidoc", "language", "go", "frameworkVersion", "v0.3.0"))
                            .status();
                }));
            }
            start.countDown();
            for (Future<Integer> f : fs) {
                assertEquals(200, f.get());
            }
        } finally {
            pool.shutdownNow();
        }
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM csap_team_project_service WHERE service = ?", Integer.class, svc));
        assertEquals(n, jdbc.queryForObject("SELECT COUNT(*) FROM csap_team_env_instance WHERE service = ?", Integer.class, svc));
    }
}
