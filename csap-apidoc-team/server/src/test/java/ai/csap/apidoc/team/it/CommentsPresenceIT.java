package ai.csap.apidoc.team.it;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import ai.csap.apidoc.team.presence.PresenceService;
import ai.csap.apidoc.team.support.AbstractIT;
import ai.csap.apidoc.team.support.Api;
import ai.csap.apidoc.team.support.LocalHttp;
import com.fasterxml.jackson.databind.JsonNode;

/** 契约 5:修改单评论、编辑中提示(presence)、openChanges / overlaps。单环境(1.0 兼容)下运行。 */
class CommentsPresenceIT extends AbstractIT {

    @DynamicPropertySource
    static void db(DynamicPropertyRegistry r) {
        H2Db.register(r);
    }

    @Autowired
    PresenceService presence;

    @AfterEach
    void resetClock() {
        presence.setClock(System::currentTimeMillis);
    }

    private JsonNode comments(Api api, long changeId) {
        Api.Resp r = api.get("/api/v1/changes/" + changeId + "/comments");
        assertEquals(200, r.status(), r.text());
        return r.json();
    }

    private Api.Resp comment(Api api, long changeId, String body, Long itemId) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("body", body);
        if (itemId != null) {
            m.put("itemId", itemId);
        }
        return api.post("/api/v1/changes/" + changeId + "/comments", m);
    }

    // ---------- 评论 ----------

    @Test
    void commentPermissionsEditingAndSoftDelete() {
        Api admin = admin();
        long project = newProject(admin, true);
        String svc = newService(admin, project);
        U dev = newUser(admin, "MEMBER");
        U dev2 = newUser(admin, "MEMBER");
        U viewer = newUser(admin, "MEMBER");
        U maint = newUser(admin, "MEMBER");
        U outsider = newUser(admin, "MEMBER");
        member(admin, project, dev, "DEVELOPER");
        member(admin, project, dev2, "DEVELOPER");
        member(admin, project, viewer, "VIEWER");
        member(admin, project, maint, "MAINTAINER");
        Api.Resp created = createChange(dev.api(), project, item(svc, "C.key", 0L, rules("c")));
        long cid = changeId(created);
        long itemId = created.json().get("items").get(0).get("id").asLong();
        assertEquals(0, created.json().get("commentCount").asInt());

        // 看:能看修改单的人都能看;项目外看不到
        assertEquals(0, comments(viewer.api(), cid).size());
        assertEquals(404, outsider.api().get("/api/v1/changes/" + cid + "/comments").status());
        // 发:只读不能发,项目外不能
        assertEquals(403, comment(viewer.api(), cid, "我想说", null).status());
        assertEquals(404, comment(outsider.api(), cid, "我想说", null).status());
        // 发:body 去首尾空白后 1–4000 字
        Api.Resp c1 = comment(dev.api(), cid, "  第一条  ", null);
        assertEquals(200, c1.status(), c1.text());
        assertEquals("第一条", c1.json().get("body").asText());
        assertEquals(cid, c1.json().get("changeId").asLong());
        assertTrue(c1.json().get("itemId").isNull());
        assertEquals(dev.id(), c1.json().get("author").get("id").asLong());
        assertEquals("用户" + dev.username(), c1.json().get("author").get("displayName").asText());
        assertFalse(c1.json().get("deleted").asBoolean());
        assertTrue(c1.json().get("createdAt").asLong() > 0 && c1.json().get("updatedAt").asLong() > 0);
        assertEquals("VALIDATION", comment(dev.api(), cid, "   ", null).code());
        assertEquals(400, comment(dev.api(), cid, "x".repeat(4001), null).status());
        assertEquals(200, comment(dev.api(), cid, "y".repeat(4000), null).status());
        assertEquals(400, dev.api().post("/api/v1/changes/" + cid + "/comments", Map.of("itemId", itemId)).status());
        // itemId 必须属于该修改单
        Api.Resp c3 = comment(maint.api(), cid, "针对这条规则", itemId);
        assertEquals(200, c3.status(), c3.text());
        assertEquals(itemId, c3.json().get("itemId").asLong());
        long otherChange = changeId(createChange(dev.api(), project, item(svc, "C.other", 0L, rules("o"))));
        long otherItem = admin.get("/api/v1/changes/" + otherChange).json().get("items").get(0).get("id").asLong();
        assertEquals(400, comment(dev.api(), cid, "错的", otherItem).status());
        assertEquals(400, comment(dev.api(), cid, "错的", 999999999L).status());

        // 列表按 id 升序,不分页;commentCount 只数未删除
        JsonNode list = comments(viewer.api(), cid);
        assertEquals(3, list.size());
        assertTrue(list.get(0).get("id").asLong() < list.get(1).get("id").asLong());
        assertEquals(3, admin.get("/api/v1/changes/" + cid).json().get("commentCount").asInt());

        // 改:只能改自己的
        long id1 = c1.json().get("id").asLong();
        Api.Resp up = dev.api().patch("/api/v1/comments/" + id1, Map.of("body", "改过的"));
        assertEquals(200, up.status(), up.text());
        assertEquals("改过的", up.json().get("body").asText());
        assertEquals(403, maint.api().patch("/api/v1/comments/" + id1, Map.of("body", "越权")).status(), "维护者也不能改别人的");
        assertEquals(403, admin.patch("/api/v1/comments/" + id1, Map.of("body", "越权")).status());
        assertEquals(400, dev.api().patch("/api/v1/comments/" + id1, Map.of("body", " ")).status());
        // 删:只能删自己的;管理员和项目维护者可以删任何
        assertEquals(403, dev2.api().delete("/api/v1/comments/" + id1).status());
        assertEquals(403, viewer.api().delete("/api/v1/comments/" + id1).status());
        Api.Resp del = maint.api().delete("/api/v1/comments/" + id1);
        assertEquals(200, del.status(), del.text());
        assertTrue(del.json().get("deleted").asBoolean());
        assertTrue(del.json().get("body").isNull());
        // 软删除:列表里仍返回(保持对话顺序),body 为 null
        JsonNode after = comments(dev.api(), cid);
        assertEquals(3, after.size());
        assertEquals(id1, after.get(0).get("id").asLong());
        assertTrue(after.get(0).get("deleted").asBoolean());
        assertTrue(after.get(0).get("body").isNull());
        assertEquals(2, admin.get("/api/v1/changes/" + cid).json().get("commentCount").asInt());
        assertEquals(404, dev.api().patch("/api/v1/comments/" + id1, Map.of("body", "复活")).status());
        assertEquals(200, maint.api().delete("/api/v1/comments/" + id1).status(), "重复删除幂等");
        long id3 = c3.json().get("id").asLong();
        assertEquals(403, dev.api().delete("/api/v1/comments/" + id3).status(), "开发不能删维护者的评论");
        assertEquals(200, admin.delete("/api/v1/comments/" + id3).status(), "管理员可以删任何评论");
        // 自己删自己的
        long own = comment(dev2.api(), cid, "自己的", null).json().get("id").asLong();
        assertEquals(200, dev2.api().delete("/api/v1/comments/" + own).status());
        assertEquals(404, dev.api().delete("/api/v1/comments/999999999").status());

        // 审计
        assertTrue(auditList(admin, "action=comment.create").get("total").asInt() >= 3);
        JsonNode au = auditList(admin, "action=comment.update");
        JsonNode upd = null;
        for (JsonNode n : au.get("items")) {
            if (n.get("changeId").asLong() == cid) {
                upd = n;
            }
        }
        assertNotNull(upd);
        assertEquals("改过的", upd.get("afterValue").asText());
        JsonNode dels = auditList(admin, "action=comment.delete").get("items");
        boolean found = false;
        for (JsonNode n : dels) {
            if (n.get("changeId").asLong() == cid && "改过的".equals(n.get("beforeValue").asText())) {
                found = true;
            }
        }
        assertTrue(found, "删除记 before_value");
        JsonNode creates = auditList(admin, "action=comment.create").get("items");
        boolean hasCreate = false;
        for (JsonNode n : creates) {
            if (n.get("changeId").asLong() == cid && "第一条".equals(n.get("afterValue").asText())) {
                hasCreate = true;
                assertEquals(project, n.get("projectId").asLong());
            }
        }
        assertTrue(hasCreate);
    }

    @Test
    void commentsAreAllowedOnPublishedChangesAndCappedAt500() {
        Api admin = admin();
        long project = newProject(admin, true);
        String svc = newService(admin, project);
        U dev = newUser(admin, "MEMBER");
        member(admin, project, dev, "DEVELOPER");
        JsonNode published = publish(dev, admin, project, svc, "Cap.key", 0, rules("cap"));
        long cid = published.get("id").asLong();
        assertEquals(200, comment(dev.api(), cid, "事后讨论", null).status(), "已发布也能评论");
        for (int i = 0; i < 499; i++) {
            jdbc.update("INSERT INTO csap_team_comment (change_id, author_id, body, deleted, created_at, updated_at) VALUES (?, ?, 'x', 0, 1, 1)",
                    cid, dev.id());
        }
        Api.Resp over = comment(dev.api(), cid, "第 501 条", null);
        assertEquals(400, over.status(), "单张修改单评论最多 500 条");
        assertEquals("VALIDATION", over.code());
        assertEquals(500, comments(dev.api(), cid).size());
    }

    @Test
    void changeCommentedEventIsFiredOnlyWhenCreating() throws Exception {
        Api admin = admin();
        long project = newProject(admin, true);
        String svc = newService(admin, project);
        U dev = newUser(admin, "MEMBER");
        member(admin, project, dev, "DEVELOPER");
        long cid = changeId(createChange(dev.api(), project, item(svc, "Ev.key", 0L, rules("e"))));
        try (LocalHttp http = new LocalHttp()) {
            Map<String, Object> hook = new LinkedHashMap<>();
            hook.put("projectId", project);
            hook.put("name", "评论");
            hook.put("kind", "GENERIC");
            hook.put("url", http.url() + "/c");
            hook.put("events", List.of("change.commented"));
            Api.Resp h = admin.post("/api/v1/webhooks", hook);
            assertEquals(200, h.status(), h.text());
            assertEquals("change.commented", h.json().get("events").get(0).asText());

            String longBody = "长".repeat(800);
            Api.Resp c = comment(dev.api(), cid, longBody, null);
            assertEquals(200, c.status(), c.text());
            List<LocalHttp.Req> got = http.await(r -> r.path().equals("/c"), 1, 8000);
            assertEquals(1, got.size());
            assertEquals("change.commented", got.get(0).header("X-CSAP-Team-Event"));
            JsonNode body = Api.MAPPER.readTree(got.get(0).body());
            assertEquals("change.commented", body.get("event").asText());
            assertEquals(cid, body.get("change").get("id").asLong());
            assertEquals(c.json().get("id").asLong(), body.get("comment").get("id").asLong());
            assertEquals("用户" + dev.username(), body.get("comment").get("author").asText());
            assertEquals(500, body.get("comment").get("body").asText().length(), "body 截断到 500 字");
            assertEquals(project, body.get("project").get("id").asLong());
            assertEquals("default", body.get("env").get("key").asText());
            // 修改、删除不触发
            long id = c.json().get("id").asLong();
            assertEquals(200, dev.api().patch("/api/v1/comments/" + id, Map.of("body", "改")).status());
            assertEquals(200, dev.api().delete("/api/v1/comments/" + id).status());
            Thread.sleep(500);
            assertEquals(1, http.requests.stream().filter(r -> r.path().equals("/c")).count());
        }
    }

    // ---------- 编辑中提示 ----------

    private Map<String, Object> target(String svc, String key) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("env", "default");
        m.put("service", svc);
        m.put("ruleKey", key);
        return m;
    }

    @Test
    void presenceShowsOthersExcludesSelfAndExpires() {
        Api admin = admin();
        long project = newProject(admin, true);
        String svc = newService(admin, project);
        U a = newUser(admin, "MEMBER");
        U b = newUser(admin, "MEMBER");
        U viewer = newUser(admin, "MEMBER");
        U outsider = newUser(admin, "MEMBER");
        member(admin, project, a, "DEVELOPER");
        member(admin, project, b, "DEVELOPER");
        member(admin, project, viewer, "VIEWER");
        AtomicLong now = new AtomicLong(1_000_000L);
        presence.setClock(now::get);

        assertEquals(200, a.api().put("/api/v1/presence", target(svc, "K1")).status());
        JsonNode seenByB = b.api().get("/api/v1/presence?env=default&service=" + svc).json();
        assertEquals(1, seenByB.size());
        assertEquals("K1", seenByB.get(0).get("ruleKey").asText());
        assertEquals(a.id(), seenByB.get(0).get("users").get(0).get("id").asLong());
        assertEquals("用户" + a.username(), seenByB.get(0).get("users").get(0).get("displayName").asText());
        assertEquals(1_000_000L, seenByB.get(0).get("users").get(0).get("since").asLong());
        assertEquals(0, a.api().get("/api/v1/presence?env=default&service=" + svc).json().size(), "不含当前用户自己");
        assertEquals(1, b.api().get("/api/v1/presence?service=" + svc).json().size(), "env 省略 = 默认环境");
        // 权限:只读不能上报,但能看;项目外看不到
        assertEquals(403, viewer.api().put("/api/v1/presence", target(svc, "K1")).status());
        assertEquals(1, viewer.api().get("/api/v1/presence?env=default&service=" + svc).json().size());
        assertEquals(404, outsider.api().get("/api/v1/presence?env=default&service=" + svc).status());
        assertEquals(404, outsider.api().put("/api/v1/presence", target(svc, "K1")).status());
        assertEquals(400, a.api().put("/api/v1/presence", Map.of("env", "default", "service", svc)).status());
        // 续期保留 since;90 秒没续上视为离开
        now.addAndGet(60_000);
        assertEquals(200, a.api().put("/api/v1/presence", target(svc, "K1")).status());
        assertEquals(1_000_000L, b.api().get("/api/v1/presence?env=default&service=" + svc).json().get(0).get("users").get(0).get("since").asLong());
        now.addAndGet(89_000);
        assertEquals(1, b.api().get("/api/v1/presence?env=default&service=" + svc).json().size(), "89 秒内仍在");
        now.addAndGet(2_000);
        assertEquals(0, b.api().get("/api/v1/presence?env=default&service=" + svc).json().size(), "超过 90 秒视为离开");
        // 关闭编辑器时 DELETE
        assertEquals(200, a.api().put("/api/v1/presence", target(svc, "K2")).status());
        assertEquals(1, b.api().get("/api/v1/presence?service=" + svc).json().size());
        assertEquals(200, a.api().send("DELETE", "/api/v1/presence", target(svc, "K2"), true).status());
        assertEquals(0, b.api().get("/api/v1/presence?service=" + svc).json().size());
        assertEquals(200, a.api().send("DELETE", "/api/v1/presence", target(svc, "never"), true).status(), "失败也无妨:没有记录也返回成功");
        // 不写审计
        assertEquals(0, auditList(admin, "action=presence*").get("total").asInt());
    }

    @Test
    void presenceKeepsAtMost20RulesPerUserDroppingTheEarliest() {
        Api admin = admin();
        long project = newProject(admin, true);
        String svc = newService(admin, project);
        U a = newUser(admin, "MEMBER");
        U b = newUser(admin, "MEMBER");
        member(admin, project, a, "DEVELOPER");
        member(admin, project, b, "DEVELOPER");
        AtomicLong now = new AtomicLong(5_000_000L);
        presence.setClock(now::incrementAndGet);
        for (int i = 1; i <= 21; i++) {
            assertEquals(200, a.api().put("/api/v1/presence", target(svc, "R" + i)).status());
        }
        JsonNode list = b.api().get("/api/v1/presence?service=" + svc).json();
        assertEquals(20, list.size());
        for (JsonNode n : list) {
            assertFalse("R1".equals(n.get("ruleKey").asText()), "最早的被挤掉");
        }
    }

    // ---------- openChanges / overlaps ----------

    @Test
    void openChangesAndOverlapsOnlyCountUnfinishedChangesOfTheSameRule() {
        Api admin = admin();
        long project = newProject(admin, true);
        String svc = newService(admin, project);
        String other = newService(admin, project);
        U dev = newUser(admin, "MEMBER");
        member(admin, project, dev, "DEVELOPER");
        long c1 = changeId(createChange(dev.api(), project, item(svc, "O.key", 0L, rules("1"))));
        Api.Resp r2 = createChange(admin, project, item(svc, "O.key", 0L, rules("2")), item(svc, "O.second", 0L, rules("2")));
        long c2 = changeId(r2);
        changeId(createChange(admin, project, item(svc, "O.different", 0L, rules("3"))));
        changeId(createChange(admin, project, item(other, "O.key", 0L, rules("4"))));

        JsonNode item = dev.api().get("/api/v1/rules/" + svc + "/item?key=O.key&env=default").json();
        JsonNode open = item.get("openChanges");
        assertEquals(2, open.size());
        assertEquals(c1, open.get(0).get("id").asLong());
        assertEquals("DRAFT", open.get(0).get("status").asText());
        assertEquals(dev.id(), open.get(0).get("author").get("id").asLong());
        assertEquals("用户" + dev.username(), open.get(0).get("author").get("displayName").asText());
        assertNotNull(open.get(0).get("title"));
        assertTrue(open.get(0).get("updatedAt").asLong() > 0);
        assertEquals(c2, open.get(1).get("id").asLong());
        assertEquals(0, dev.api().get("/api/v1/rules/" + svc + "/item?key=O.nothing").json().get("openChanges").size());

        // overlaps:其它未完成修改单里同环境、同规则的条目
        JsonNode d1 = dev.api().get("/api/v1/changes/" + c1).json();
        JsonNode ov = d1.get("items").get(0).get("overlaps");
        assertEquals(1, ov.size());
        assertEquals(c2, ov.get(0).get("changeId").asLong());
        assertEquals("DRAFT", ov.get(0).get("status").asText());
        assertNotNull(ov.get(0).get("title"));
        JsonNode d2 = admin.get("/api/v1/changes/" + c2).json();
        assertEquals(1, d2.get("items").get(0).get("overlaps").size());
        assertEquals(0, d2.get("items").get(1).get("overlaps").size(), "O.second 没有重叠");

        // PENDING / CONFLICT 仍算;撤回、驳回、发布不算
        assertEquals(200, dev.api().post("/api/v1/changes/" + c1 + "/submit", null).status());
        assertEquals("PENDING", admin.get("/api/v1/changes/" + c2).json().get("items").get(0).get("overlaps").get(0).get("status").asText());
        assertEquals(2, dev.api().get("/api/v1/rules/" + svc + "/item?key=O.key").json().get("openChanges").size());
        assertEquals(200, admin.post("/api/v1/changes/" + c1 + "/reject", Map.of("note", "不要")).status());
        assertEquals(1, dev.api().get("/api/v1/rules/" + svc + "/item?key=O.key").json().get("openChanges").size());
        assertEquals(0, admin.get("/api/v1/changes/" + c2).json().get("items").get(0).get("overlaps").size());
        assertEquals(200, admin.post("/api/v1/changes/" + c2 + "/withdraw", null).status());
        assertEquals(0, dev.api().get("/api/v1/rules/" + svc + "/item?key=O.key").json().get("openChanges").size());
        // 只提示,不阻止:同时存在两张未完成单时都能提交
        long c5 = changeId(createChange(dev.api(), project, item(svc, "O.free", null, rules("5"))));
        long c6 = changeId(createChange(admin, project, item(svc, "O.free", null, rules("6"))));
        assertEquals(200, dev.api().post("/api/v1/changes/" + c5 + "/submit", null).status());
        assertEquals(200, admin.post("/api/v1/changes/" + c6 + "/submit", null).status());
        // 先发布的通过,后一张因版本比较冲突(真正的冲突仍由发布时的版本比较决定)
        assertEquals("PUBLISHED", admin.post("/api/v1/changes/" + c5 + "/approve", Map.of("note", "ok")).json().get("status").asText());
        assertEquals(409, admin.post("/api/v1/changes/" + c6 + "/approve", Map.of("note", "ok")).status());
        assertEquals(1, dev.api().get("/api/v1/rules/" + svc + "/item?key=O.free").json().get("openChanges").size(),
                "冲突的修改单(CONFLICT)仍算未完成,已发布的不算");
    }
}
