package ai.csap.apidoc.team.it;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import ai.csap.apidoc.team.support.AbstractIT;
import ai.csap.apidoc.team.support.Api;
import ai.csap.apidoc.team.support.LocalHttp;
import com.fasterxml.jackson.databind.JsonNode;

/** 定时任务自己跑起来(不手动调用):history 同步、文档刷新都在首次延迟 + 间隔后生效(秒,不是毫秒)。 */
class SchedulerIT extends AbstractIT {

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        H2Db.register(r);
        r.add("csap.team.sync.history-seconds", () -> "1");
        r.add("csap.team.sync.settle-millis", () -> "200");
        r.add("csap.team.docs.refresh-seconds", () -> "1");
    }

    @Test
    void scheduledHistorySyncRecordsOutsideChangeWithoutManualCall() throws Exception {
        Api admin = admin();
        String svc = unique("sched");
        jdbc.update("INSERT INTO csap_apidoc_history (service, rule_key, before_rules, after_rules, version, updated_by, updated_at)"
                + " VALUES (?, 'K', NULL, '{\"request\":{},\"response\":{}}', 1, 'devtools', ?)", svc, System.currentTimeMillis());
        long end = System.currentTimeMillis() + 20_000;
        int total = 0;
        while (System.currentTimeMillis() < end && total == 0) {
            Thread.sleep(500);
            total = auditList(admin, "action=rule.changed_outside&service=" + svc).get("total").asInt();
        }
        assertEquals(1, total, "首次延迟 3 秒 + 间隔 1 秒内应出现审计");
        JsonNode a = auditList(admin, "action=rule.changed_outside&service=" + svc).get("items").get(0);
        assertEquals("devtools", a.get("actorName").asText());
    }

    @Test
    void scheduledDocsRefreshFetchesWithoutManualCall() throws Exception {
        admin();
        try (LocalHttp http = new LocalHttp()) {
            http.handler = r -> new LocalHttp.Res(200, "{\"data\":{\"apiList\":[]}}");
            String svc = unique("schedocs");
            jdbc.update("INSERT INTO csap_team_project_service (service, base_url, doc_path, source, created_at) VALUES (?, ?, '/csap/apidoc', 'MANUAL', ?)",
                    svc, http.url(), System.currentTimeMillis());
            jdbc.update("INSERT INTO csap_team_service_env (service, env_key, base_url, doc_path, source, created_at) VALUES (?, 'default', ?, '/csap/apidoc', 'MANUAL', ?)",
                    svc, http.url(), System.currentTimeMillis());
            assertTrue(http.await(r -> r.path().equals("/csap/apidoc"), 1, 20_000).size() >= 1, "定时刷新应在首次延迟 5 秒 + 间隔内拉取");
        }
    }
}
