package ai.csap.apidoc.team.webhook;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import ai.csap.apidoc.team.common.Json;
import ai.csap.apidoc.team.config.TeamProperties;
import ai.csap.apidoc.team.db.Db;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.annotation.PreDestroy;

/** Webhook 推送:异步、失败重试(默认 10s、60s、300s)、记录投递结果;日志不打印 URL 和 secret。 */
@Service
public class WebhookService {

    private static final Logger log = LoggerFactory.getLogger(WebhookService.class);
    private static final int KEEP_DELIVERIES = 1000;

    private final Db db;
    private final TeamProperties props;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final ScheduledThreadPoolExecutor pool;

    public WebhookService(Db db, TeamProperties props) {
        this.db = db;
        this.props = props;
        this.pool = new ScheduledThreadPoolExecutor(4, r -> {
            Thread t = new Thread(r, "csap-team-webhook");
            t.setDaemon(true);
            return t;
        });
    }

    @PreDestroy
    void shutdown() {
        pool.shutdownNow();
    }

    public record Hook(long id, Long projectId, String name, String kind, String url, String secret, String events, boolean enabled,
                       long createdAt, long updatedAt) {
        public Map<String, Object> toMap() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", id);
            m.put("projectId", projectId);
            m.put("name", name);
            m.put("kind", kind);
            m.put("url", url);
            m.put("hasSecret", secret != null && !secret.isEmpty());
            m.put("events", events == null || events.isBlank() ? List.of() : List.of(events.split(",")));
            m.put("enabled", enabled);
            m.put("createdAt", createdAt);
            m.put("updatedAt", updatedAt);
            return m;
        }
    }

    static Hook map(java.sql.ResultSet rs, int i) throws java.sql.SQLException {
        long pid = rs.getLong("project_id");
        Long project = rs.wasNull() ? null : pid;
        return new Hook(rs.getLong("id"), project, rs.getString("name"), rs.getString("kind"), rs.getString("url"),
                rs.getString("secret"), rs.getString("events"), rs.getInt("enabled") == 1, rs.getLong("created_at"),
                rs.getLong("updated_at"));
    }

    static final String COLS = "id, project_id, name, kind, url, secret, events, enabled, created_at, updated_at";

    public Hook get(long id) {
        return db.one("SELECT " + COLS + " FROM csap_team_webhook WHERE id = ?", WebhookService::map, id);
    }

    /** 触发事件:匹配的 webhook 异步发送,不阻塞也不影响调用方。 */
    public void fire(WebhookEvent ev) {
        try {
            List<Hook> hooks = ev.projectId() == null
                    ? db.query("SELECT " + COLS + " FROM csap_team_webhook WHERE enabled = 1 AND project_id IS NULL", WebhookService::map)
                    : db.query("SELECT " + COLS + " FROM csap_team_webhook WHERE enabled = 1 AND (project_id IS NULL OR project_id = ?)",
                            WebhookService::map, ev.projectId());
            for (Hook h : hooks) {
                if (h.events() != null && (h.events().equals("*") || List.of(h.events().split(",")).contains(ev.event()))) {
                    pool.execute(() -> deliver(h, ev, 1, true));
                }
            }
        } catch (RuntimeException e) {
            log.warn("触发 webhook 事件 {} 失败: {}", ev.event(), e.getMessage());
        }
    }

    /** 同步发送一次测试消息(不重试),返回投递记录。 */
    public Map<String, Object> test(Hook h) {
        WebhookEvent ev = new WebhookEvent("webhook.test", System.currentTimeMillis(), h.projectId(), null, null, null, null,
                List.of(), "这是一条来自 CSAP Apidoc 团队版的测试消息");
        return deliver(h, ev, 1, false);
    }

    Map<String, Object> deliver(Hook h, WebhookEvent ev, int attempt, boolean retry) {
        Integer httpStatus = null;
        String error = null;
        boolean ok = false;
        try {
            HttpRequest req = buildRequest(h, ev);
            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
            httpStatus = res.statusCode();
            ok = res.statusCode() / 100 == 2;
            if (ok) {
                String bodyErr = platformError(h.kind(), res.body());
                if (bodyErr != null) {
                    ok = false;
                    error = bodyErr;
                }
            } else {
                error = "HTTP " + res.statusCode();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            error = "已中断";
        } catch (Exception e) {
            error = scrub(e.getClass().getSimpleName() + ": " + e.getMessage(), h);
        }
        Map<String, Object> rec = record(h.id(), ev.event(), attempt, ok, httpStatus, error);
        if (!ok) {
            log.warn("webhook #{} 事件 {} 第 {} 次投递失败: {}", h.id(), ev.event(), attempt, error);
            List<Integer> delays = props.getWebhook().getRetryDelaySeconds();
            if (retry && attempt - 1 < delays.size()) {
                int next = attempt + 1;
                pool.schedule(() -> deliver(h, ev, next, true), delays.get(attempt - 1), TimeUnit.SECONDS);
            }
        }
        return rec;
    }

    private static String scrub(String msg, Hook h) {
        String s = msg == null ? "" : msg;
        if (h.url() != null) {
            s = s.replace(h.url(), "<url>");
        }
        return s.length() > 500 ? s.substring(0, 500) : s;
    }

    /** 企业微信 / 钉钉 / 飞书在 HTTP 200 时用 body 里的错误码表示失败。 */
    private static String platformError(String kind, String body) {
        if ("GENERIC".equals(kind) || body == null) {
            return null;
        }
        JsonNode n = Json.read(body);
        if (n == null || !n.isObject()) {
            return null;
        }
        if (n.has("errcode") && n.get("errcode").asInt() != 0) {
            return "errcode=" + n.get("errcode").asInt();
        }
        if (n.has("code") && n.get("code").isInt() && n.get("code").asInt() != 0) {
            return "code=" + n.get("code").asInt();
        }
        if (n.has("StatusCode") && n.get("StatusCode").asInt() != 0) {
            return "StatusCode=" + n.get("StatusCode").asInt();
        }
        return null;
    }

    private Map<String, Object> record(long webhookId, String event, int attempt, boolean ok, Integer httpStatus, String error) {
        long now = System.currentTimeMillis();
        String status = ok ? "SUCCESS" : "FAILED";
        long id = 0;
        try {
            id = db.insert("INSERT INTO csap_team_webhook_delivery (webhook_id, event, at, attempt, status, http_status, error)"
                    + " VALUES (?, ?, ?, ?, ?, ?, ?)", webhookId, event, now, attempt, status, httpStatus, error);
            trim(webhookId);
        } catch (RuntimeException e) {
            log.warn("记录 webhook 投递失败: {}", e.getMessage());
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("webhookId", webhookId);
        m.put("event", event);
        m.put("at", now);
        m.put("attempt", attempt);
        m.put("status", status);
        m.put("httpStatus", httpStatus);
        m.put("error", error);
        return m;
    }

    /** 每个 webhook 只保留最近 1000 条投递记录。 */
    private void trim(long webhookId) {
        List<Long> ids = db.page("SELECT id FROM csap_team_webhook_delivery WHERE webhook_id = ? ORDER BY id DESC",
                (rs, i) -> rs.getLong(1), KEEP_DELIVERIES, 1, webhookId);
        if (!ids.isEmpty()) {
            db.update("DELETE FROM csap_team_webhook_delivery WHERE webhook_id = ? AND id <= ?", webhookId, ids.get(0));
        }
    }

    public List<Map<String, Object>> deliveries(long webhookId, int limit) {
        return db.page("SELECT id, event, at, attempt, status, http_status, error FROM csap_team_webhook_delivery"
                + " WHERE webhook_id = ? ORDER BY id DESC", (rs, i) -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", rs.getLong(1));
                    m.put("webhookId", webhookId);
                    m.put("event", rs.getString(2));
                    m.put("at", rs.getLong(3));
                    m.put("attempt", rs.getInt(4));
                    m.put("status", rs.getString(5));
                    int hs = rs.getInt(6);
                    m.put("httpStatus", rs.wasNull() ? null : hs);
                    m.put("error", rs.getString(7));
                    return m;
                }, 0, limit, webhookId);
    }

    // ---- 组装请求 ----

    public String link(WebhookEvent ev) {
        String base = props.getPublicUrl();
        if (base == null || base.isBlank() || ev.changeId() == null) {
            return null;
        }
        return base.replaceAll("/+$", "") + "/changes/" + ev.changeId();
    }

    public HttpRequest buildRequest(Hook h, WebhookEvent ev) {
        String link = link(ev);
        String text = ev.text() + (link == null ? "" : "\n" + link);
        String url = h.url();
        String body;
        HttpRequest.Builder b;
        long ts = System.currentTimeMillis();
        switch (h.kind()) {
            case "WECOM":
                body = Json.write(Map.of("msgtype", "markdown", "markdown", Map.of("content", text)));
                break;
            case "DINGTALK":
                body = Json.write(Map.of("msgtype", "markdown", "markdown", Map.of("title", "CSAP Apidoc 团队版", "text", text)));
                if (notEmpty(h.secret())) {
                    String sign = base64(hmac((ts + "\n" + h.secret()).getBytes(StandardCharsets.UTF_8), h.secret().getBytes(StandardCharsets.UTF_8)));
                    url = url + (url.contains("?") ? "&" : "?") + "timestamp=" + ts + "&sign=" + URLEncoder.encode(sign, StandardCharsets.UTF_8);
                }
                break;
            case "FEISHU": {
                Map<String, Object> m = new LinkedHashMap<>();
                if (notEmpty(h.secret())) {
                    long sec = ts / 1000;
                    m.put("timestamp", String.valueOf(sec));
                    m.put("sign", base64(hmac(new byte[0], (sec + "\n" + h.secret()).getBytes(StandardCharsets.UTF_8))));
                }
                m.put("msg_type", "text");
                m.put("content", Map.of("text", text));
                body = Json.write(m);
                break;
            }
            default: {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("event", ev.event());
                m.put("at", ev.at());
                m.put("project", ev.projectId() == null ? null : projectMap(ev));
                Map<String, Object> change = null;
                if (ev.changeId() != null) {
                    change = new LinkedHashMap<>();
                    change.put("id", ev.changeId());
                    change.put("title", ev.changeTitle());
                    change.put("author", ev.author());
                    change.put("url", link);
                }
                m.put("change", change);
                m.put("items", ev.items() == null ? List.of() : ev.items());
                m.put("text", ev.text());
                body = Json.write(m);
            }
        }
        b = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json; charset=utf-8")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
        if ("GENERIC".equals(h.kind())) {
            b.header("X-CSAP-Team-Event", ev.event());
            if (notEmpty(h.secret())) {
                b.header("X-CSAP-Team-Signature", "sha256=" + HexFormat.of().formatHex(
                        hmac(body.getBytes(StandardCharsets.UTF_8), h.secret().getBytes(StandardCharsets.UTF_8))));
            }
        }
        return b.build();
    }

    private static Map<String, Object> projectMap(WebhookEvent ev) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("id", ev.projectId());
        p.put("name", ev.projectName());
        return p;
    }

    private static boolean notEmpty(String s) {
        return s != null && !s.isEmpty();
    }

    private static String base64(byte[] b) {
        return Base64.getEncoder().encodeToString(b);
    }

    /** HMAC-SHA256(key, data) */
    static byte[] hmac(byte[] data, byte[] key) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key.length == 0 ? new byte[] {0} : key, "HmacSHA256"));
            return mac.doFinal(data);
        } catch (java.security.GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    public List<Hook> list(Long projectId, List<Long> restrictTo) {
        List<Hook> all = db.query("SELECT " + COLS + " FROM csap_team_webhook ORDER BY id", WebhookService::map);
        List<Hook> out = new ArrayList<>();
        for (Hook h : all) {
            if (projectId != null && !projectId.equals(h.projectId())) {
                continue;
            }
            if (restrictTo != null && (h.projectId() == null || !restrictTo.contains(h.projectId()))) {
                continue;
            }
            out.add(h);
        }
        return out;
    }
}
