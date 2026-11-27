package ai.csap.apidoc.team.it;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import ai.csap.apidoc.team.audit.AuditChain;
import ai.csap.apidoc.team.audit.AuditCheckpoint;
import ai.csap.apidoc.team.audit.AuditRetention;
import ai.csap.apidoc.team.audit.AuditService;
import ai.csap.apidoc.team.auth.Actor;
import ai.csap.apidoc.team.config.TeamProperties;
import ai.csap.apidoc.team.support.AbstractIT;
import ai.csap.apidoc.team.support.Api;
import ai.csap.apidoc.team.support.LocalHttp;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * 契约 6:审计哈希链。独立的 H2 内存库(测试会直接改、删审计行来模拟篡改,不能污染其它测试的库)。
 * 保留期测试必须最先跑:库里还没有任何审计,它才能构造出"最早的一段是过期记录"。
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AuditChainIT extends AbstractIT {

    @DynamicPropertySource
    static void db(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", () -> "jdbc:h2:mem:chainit;DB_CLOSE_DELAY=-1");
        r.add("spring.datasource.username", () -> "sa");
        r.add("spring.datasource.password", () -> "");
    }

    @Autowired
    AuditService audit;
    @Autowired
    AuditChain chain;
    @Autowired
    AuditRetention retention;
    @Autowired
    AuditCheckpoint checkpoint;
    @Autowired
    TeamProperties props;

    private JsonNode verify(Api admin, String query) {
        Api.Resp r = admin.get("/api/v1/audit/verify" + query);
        assertEquals(200, r.status(), r.text());
        return r.json();
    }

    private void assertOk(Api admin) {
        JsonNode v = verify(admin, "");
        assertTrue(v.get("ok").asBoolean(), v.toString());
        assertTrue(v.get("firstBrokenId").isNull());
        assertTrue(v.get("problem").isNull());
    }

    // ---------- 保留期清理与锚点 ----------

    @Test
    @Order(1)
    void retentionDeletesOnlyTheOldestSegmentAndTheChainStillVerifies() throws Exception {
        long now = System.currentTimeMillis();
        long day = 86_400_000L;
        audit.logAs(Actor.system(), "old.entry").at(now - 40 * day).save();
        audit.logAs(Actor.system(), "old.entry2").at(now - 39 * day).save();
        audit.logAs(Actor.system(), "new.entry").at(now - day).save();
        assertEquals(0, retention.purge(now), "默认 0 = 永久,不清理");
        props.getAudit().setRetentionDays(30);
        int n;
        try {
            n = retention.purge(now);
        } finally {
            props.getAudit().setRetentionDays(0);
        }
        assertEquals(2, n);
        Api admin = admin();
        assertEquals(0, auditList(admin, "action=old.entry*").get("total").asInt());
        assertEquals(1, auditList(admin, "action=new.entry").get("total").asInt());
        JsonNode purge = auditList(admin, "action=audit.purge");
        assertEquals(1, purge.get("total").asInt());
        assertEquals("SYSTEM", purge.get("items").get(0).get("source").asText());
        // anchor:被删的最后一条的 {id, hash},reason 里也带
        String anchorJson = jdbc.queryForObject("SELECT setting_value FROM csap_team_setting WHERE name = 'audit.chain.anchor'", String.class);
        JsonNode anchor = Api.MAPPER.readTree(anchorJson);
        assertTrue(anchor.get("id").asLong() > 0);
        assertEquals(64, anchor.get("hash").asText().length());
        String reason = purge.get("items").get(0).get("reason").asText();
        assertTrue(reason.contains("id=" + anchor.get("id").asLong()) && reason.contains(anchor.get("hash").asText()), reason);
        // 清理后校验仍通过(从 anchor 接上),之后继续写也通过
        assertOk(admin);
        long u = newUser(admin, "MEMBER").id();
        assertTrue(u > 0);
        assertOk(admin);
        assertEquals("OK", verifyReason(admin).substring(0, 2));
    }

    private String verifyReason(Api admin) {
        return auditList(admin, "action=audit.verify").get("items").get(0).get("reason").asText();
    }

    // ---------- 校验 ----------

    @Test
    @Order(2)
    void consecutiveWritesVerifyAndEveryVerificationIsAudited() {
        Api admin = admin();
        long project = newProject(admin, true);
        String svc = newService(admin, project);
        U dev = newUser(admin, "MEMBER");
        member(admin, project, dev, "DEVELOPER");
        publish(dev, admin, project, svc, "H.key", 0, rules("h"));
        // headId / headHash 是被校验的最后一条(校验本身的 audit.verify 记在校验之后)
        Map<String, Object> last = jdbc.queryForMap("SELECT id, hash FROM csap_team_audit WHERE id = (SELECT MAX(id) FROM csap_team_audit)");
        JsonNode v = verify(admin, "");
        assertTrue(v.get("ok").asBoolean(), v.toString());
        assertTrue(v.get("checked").asLong() >= 10);
        assertEquals(64, v.get("headHash").asText().length());
        assertEquals(((Number) last.get("id")).longValue(), v.get("headId").asLong());
        assertEquals(last.get("hash"), v.get("headHash").asText());
        // 每次校验记一条 audit.verify
        JsonNode a = auditList(admin, "action=audit.verify");
        assertTrue(a.get("total").asInt() >= 1);
        assertTrue(a.get("items").get(0).get("reason").asText().startsWith("OK"));
        // 范围校验
        List<Long> allIds = jdbc.queryForList("SELECT id FROM csap_team_audit ORDER BY id", Long.class);
        long first = allIds.get(0);
        long mid = allIds.get(4);
        JsonNode range = verify(admin, "?fromId=" + first + "&toId=" + mid);
        assertTrue(range.get("ok").asBoolean());
        assertEquals(mid, range.get("headId").asLong());
        assertTrue(verify(admin, "?fromId=" + mid).get("ok").asBoolean());
        // 仅管理员
        assertEquals(403, dev.api().get("/api/v1/audit/verify").status());
        assertEquals(401, new Api(port).get("/api/v1/audit/verify").status());
        // id 跳号(事务回滚)但 prev_hash 接得上不算错:回滚的业务事务消耗了自增 id
        long before = jdbc.queryForObject("SELECT MAX(id) FROM csap_team_audit", Long.class);
        assertEquals(400, admin.post("/api/v1/users", Map.of("username", "x", "displayName", "x", "password", "p", "orgRole", "MEMBER")).status());
        assertOk(admin);
        assertTrue(before > 0);
    }

    private Object[] rowOf(long id) {
        return jdbc.query("SELECT id, at, actor_type, actor_id, actor_name, source, ip, action, project_id, env_key, service, rule_key,"
                + " change_id, before_value, after_value, diff, reason, oss_history_id, prev_hash, hash FROM csap_team_audit WHERE id = ?",
                (rs, i) -> new Object[] {rs.getLong(1), rs.getLong(2), rs.getString(3), rs.getObject(4), rs.getString(5), rs.getString(6),
                        rs.getString(7), rs.getString(8), rs.getObject(9), rs.getString(10), rs.getString(11), rs.getString(12), rs.getObject(13),
                        rs.getString(14), rs.getString(15), rs.getString(16), rs.getString(17), rs.getObject(18), rs.getString(19), rs.getString(20)},
                id).get(0);
    }

    private void restore(Object[] r) {
        jdbc.update("INSERT INTO csap_team_audit (id, at, actor_type, actor_id, actor_name, source, ip, action, project_id, env_key, service,"
                + " rule_key, change_id, before_value, after_value, diff, reason, oss_history_id, prev_hash, hash)"
                + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)", r);
    }

    private long midRowId() {
        List<Long> ids = jdbc.queryForList("SELECT id FROM csap_team_audit ORDER BY id", Long.class);
        assertTrue(ids.size() > 6);
        return ids.get(ids.size() / 2);
    }

    @Test
    @Order(3)
    void changingARecordIsReportedAsHashMismatch() {
        Api admin = admin();
        long id = midRowId();
        String old = jdbc.queryForObject("SELECT reason FROM csap_team_audit WHERE id = ?", String.class, id);
        jdbc.update("UPDATE csap_team_audit SET reason = ? WHERE id = ?", "被改过的原因", id);
        try {
            JsonNode v = verify(admin, "");
            assertFalse(v.get("ok").asBoolean(), v.toString());
            assertEquals("HASH_MISMATCH", v.get("problem").asText());
            assertEquals(id, v.get("firstBrokenId").asLong());
            assertTrue(auditList(admin, "action=audit.verify").get("items").get(0).get("reason").asText().startsWith("FAILED HASH_MISMATCH"));
        } finally {
            jdbc.update("UPDATE csap_team_audit SET reason = ? WHERE id = ?", old, id);
        }
        assertOk(admin);
        // 把 hash 列也改掉(重算后的 hash)——下一条的 prev_hash 对不上
        Object[] row = rowOf(id);
        jdbc.update("UPDATE csap_team_audit SET after_value = 'x' WHERE id = ?", id);
        Object[] changed = rowOf(id);
        String forged = AuditChain.hash((String) changed[18], new Object[] {changed[0], changed[1], changed[2], changed[3], changed[4], changed[5],
                changed[6], changed[7], changed[8], changed[9], changed[10], changed[11], changed[12], changed[13], changed[14], changed[15],
                changed[16], changed[17], changed[18]});
        jdbc.update("UPDATE csap_team_audit SET hash = ? WHERE id = ?", forged, id);
        try {
            JsonNode v = verify(admin, "");
            assertFalse(v.get("ok").asBoolean());
            assertEquals("PREV_MISMATCH", v.get("problem").asText());
            assertTrue(v.get("firstBrokenId").asLong() > id);
        } finally {
            jdbc.update("UPDATE csap_team_audit SET after_value = ?, hash = ? WHERE id = ?", row[14], row[19], id);
        }
        assertOk(admin);
    }

    @Test
    @Order(4)
    void deletingARecordInTheMiddleIsReportedAsPrevMismatch() {
        Api admin = admin();
        long id = midRowId();
        Object[] row = rowOf(id);
        long next = jdbc.queryForObject("SELECT MIN(id) FROM csap_team_audit WHERE id > ?", Long.class, id);
        jdbc.update("DELETE FROM csap_team_audit WHERE id = ?", id);
        try {
            JsonNode v = verify(admin, "");
            assertFalse(v.get("ok").asBoolean(), v.toString());
            assertEquals("PREV_MISMATCH", v.get("problem").asText());
            assertEquals(next, v.get("firstBrokenId").asLong());
        } finally {
            restore(row);
        }
        assertOk(admin);
    }

    @Test
    @Order(5)
    void missingHashAndDeletedTailAreDetected() {
        Api admin = admin();
        long id = midRowId();
        String hash = jdbc.queryForObject("SELECT hash FROM csap_team_audit WHERE id = ?", String.class, id);
        jdbc.update("UPDATE csap_team_audit SET hash = NULL WHERE id = ?", id);
        try {
            JsonNode v = verify(admin, "");
            assertEquals("MISSING_HASH", v.get("problem").asText());
            assertEquals(id, v.get("firstBrokenId").asLong());
        } finally {
            jdbc.update("UPDATE csap_team_audit SET hash = ? WHERE id = ?", hash, id);
        }
        // 尾部被删:链头对不上
        long last = jdbc.queryForObject("SELECT MAX(id) FROM csap_team_audit", Long.class);
        Object[] row = rowOf(last);
        jdbc.update("DELETE FROM csap_team_audit WHERE id = ?", last);
        try {
            JsonNode v = verify(admin, "");
            assertFalse(v.get("ok").asBoolean(), v.toString());
            assertEquals("GAP", v.get("problem").asText());
            assertEquals(last, v.get("firstBrokenId").asLong());
            // 范围校验只看范围内,尾部缺失不报
            assertTrue(verify(admin, "?fromId=" + (last - 3) + "&toId=" + (last - 1)).get("ok").asBoolean());
        } finally {
            restore(row);
        }
        assertOk(admin);
    }

    // ---------- 并发写入 ----------

    @Test
    @Order(6)
    void fiftyConcurrentWritesKeepTheChainIntact() throws Exception {
        Api admin = admin();
        long before = jdbc.queryForObject("SELECT COUNT(*) FROM csap_team_audit", Long.class);
        ExecutorService pool = Executors.newFixedThreadPool(10);
        List<Future<Long>> fs = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            final int n = i;
            fs.add(pool.submit(() -> audit.logAs(Actor.system(), "concurrent.write").reason("写入 " + n).save()));
        }
        Set<Long> ids = new HashSet<>();
        for (Future<Long> f : fs) {
            ids.add(f.get());
        }
        pool.shutdown();
        assertEquals(50, ids.size());
        assertEquals(before + 50, jdbc.queryForObject("SELECT COUNT(*) FROM csap_team_audit", Long.class));
        assertEquals(50, jdbc.queryForObject("SELECT COUNT(DISTINCT prev_hash) FROM csap_team_audit WHERE action = 'concurrent.write'", Long.class),
                "每条的 prev_hash 都不同:没有两条接在同一条后面");
        assertOk(admin);
        long head = chain.head().id();
        assertEquals(jdbc.queryForObject("SELECT MAX(id) FROM csap_team_audit", Long.class), head);
    }

    // ---------- 导出与离线校验 ----------

    @Test
    @Order(7)
    void exportCarriesChainColumnsAndAnIndependentPythonScriptVerifiesIt() throws Exception {
        Api admin = admin();
        long project = newProject(admin, true);
        String svc = newService(admin, project);
        audit.logAs(Actor.system(), "unicode.test").reason("含中文、引号\"、反斜杠\\、换行\n和制表符\t的原因 é 😀").save();
        Api.Resp csv = admin.get("/api/v1/audit/export?format=csv");
        assertEquals(200, csv.status());
        String header = csv.text().substring(1).split("\r\n")[0];
        assertTrue(header.contains("envKey") && header.contains("prevHash") && header.endsWith("hash"), header);
        Api.Resp json = admin.get("/api/v1/audit/export?format=json");
        assertEquals(200, json.status());
        JsonNode doc = json.json();
        assertTrue(doc.has("anchor"));
        assertFalse(doc.get("anchor").isNull(), "测试 1 清理过,有 anchor");
        assertTrue(doc.get("items").size() > 20);
        JsonNode first = doc.get("items").get(0);
        for (String f : List.of("id", "at", "actorType", "actorId", "actorName", "source", "ip", "action", "projectId", "envKey", "service",
                "ruleKey", "changeId", "beforeValue", "afterValue", "diff", "reason", "ossHistoryId", "prevHash", "hash")) {
            assertTrue(first.has(f), "导出缺少字段 " + f);
        }
        assertNotNull(svc);

        assumeTrue(python3Available(), "本机没有 python3,跳过独立脚本校验");
        Path dir = Files.createTempDirectory("chain-export");
        Path file = dir.resolve("audit.json");
        Files.writeString(file, json.text(), StandardCharsets.UTF_8);
        assertEquals(0, python(file), "独立脚本校验导出的 JSON");
        // 改一条记录:脚本发现
        String tampered = json.text().replaceFirst("\"action\":\"unicode.test\"", "\"action\":\"unicode.hacked\"");
        Path bad = dir.resolve("tampered.json");
        Files.writeString(bad, tampered, StandardCharsets.UTF_8);
        assertEquals(1, python(bad));
        // 删掉中间一条:脚本发现
        JsonNode copy = Api.MAPPER.readTree(json.text());
        ((com.fasterxml.jackson.databind.node.ArrayNode) copy.get("items")).remove(5);
        Path gap = dir.resolve("gap.json");
        Files.writeString(gap, Api.MAPPER.writeValueAsString(copy), StandardCharsets.UTF_8);
        assertEquals(1, python(gap));
        // 带筛选的导出(中间有缺口)用 --allow-gaps
        Api.Resp filtered = admin.get("/api/v1/audit/export?format=json&action=unicode.test");
        Path f2 = dir.resolve("filtered.json");
        Files.writeString(f2, filtered.text(), StandardCharsets.UTF_8);
        assertEquals(0, python(f2, "--allow-gaps"));
    }

    private static boolean python3Available() {
        try {
            Process p = new ProcessBuilder("python3", "--version").redirectErrorStream(true).start();
            p.getInputStream().readAllBytes();
            return p.waitFor() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    private static int python(Path file, String... extra) throws Exception {
        List<String> cmd = new ArrayList<>(List.of("python3", "-I", Paths.get("../docs/tools/verify-audit-chain.py").toAbsolutePath().toString(),
                file.toString()));
        cmd.addAll(List.of(extra));
        Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
        String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        int code = p.waitFor();
        System.out.println("verify-audit-chain.py " + file.getFileName() + " -> " + code + " " + out.strip());
        return code;
    }

    // ---------- audit.checkpoint ----------

    @Test
    @Order(8)
    void checkpointEventIsSentOnlyToSubscribers() throws Exception {
        Api admin = admin();
        try (LocalHttp http = new LocalHttp()) {
            Map<String, Object> sub = new LinkedHashMap<>();
            sub.put("name", "检查点");
            sub.put("kind", "GENERIC");
            sub.put("url", http.url() + "/cp");
            sub.put("events", List.of("audit.checkpoint"));
            Api.Resp h = admin.post("/api/v1/webhooks", sub);
            assertEquals(200, h.status(), h.text());
            Map<String, Object> other = new LinkedHashMap<>(sub);
            other.put("url", http.url() + "/other");
            other.put("events", List.of("change.published"));
            assertEquals(200, admin.post("/api/v1/webhooks", other).status());

            AuditChain.Point head = chain.head();
            long count = jdbc.queryForObject("SELECT COUNT(*) FROM csap_team_audit", Long.class);
            checkpoint.fire();
            List<LocalHttp.Req> got = http.await(r -> r.path().equals("/cp"), 1, 8000);
            assertEquals(1, got.size());
            assertEquals("audit.checkpoint", got.get(0).header("X-CSAP-Team-Event"));
            JsonNode body = Api.MAPPER.readTree(got.get(0).body());
            assertEquals("audit.checkpoint", body.get("event").asText());
            assertTrue(body.get("at").asLong() > 0);
            assertEquals(head.id(), body.get("checkpoint").get("headId").asLong());
            assertEquals(head.hash(), body.get("checkpoint").get("headHash").asText());
            assertEquals(count, body.get("checkpoint").get("count").asLong());
            assertTrue(body.get("text").asText().contains(head.hash()));
            assertFalse(body.has("project") || body.has("change") || body.has("items"), "契约:{event, at, checkpoint, text}");
            Thread.sleep(300);
            assertEquals(0, http.requests.stream().filter(r -> r.path().equals("/other")).count());
        }
        assertTrue(props.getAudit().getCheckpointCron().equals("0 5 0 * * *"), "默认每天 00:05");
    }
}
