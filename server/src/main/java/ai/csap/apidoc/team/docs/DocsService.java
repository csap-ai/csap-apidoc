package ai.csap.apidoc.team.docs;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import ai.csap.apidoc.team.common.Json;
import ai.csap.apidoc.team.config.TeamProperties;
import ai.csap.apidoc.team.db.Db;
import ai.csap.apidoc.team.service.ServiceService;
import ai.csap.apidoc.team.webhook.WebhookEvent;
import ai.csap.apidoc.team.webhook.WebhookService;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.annotation.PreDestroy;

/**
 * 文档中心:定时从在线服务拉取 {baseUrl}{docPath},缓存在内存;失败保留上次结果并标记 stale;内存搜索索引。
 * 服务状态与离线事件也在这里计算。
 */
@Service
public class DocsService {

    private static final Logger log = LoggerFactory.getLogger(DocsService.class);
    private static final long OFFLINE_EVENT_GAP_MS = 10 * 60 * 1000L;
    private static final long MAX_BYTES = 64L * 1024 * 1024;

    public record Cached(String json, long fetchedAt, boolean stale, List<Entry> index) {
    }

    public record Entry(String service, String method, String path, String title, String description, List<String[]> fields) {
    }

    private final ServiceService services;
    private final TeamProperties props;
    private final Db db;
    private final WebhookService webhooks;
    private final HttpClient http;
    private final ExecutorService pool = Executors.newFixedThreadPool(8, r -> {
        Thread t = new Thread(r, "csap-team-docs");
        t.setDaemon(true);
        return t;
    });
    private final Map<String, Cached> cache = new ConcurrentHashMap<>();
    /** 最近一次拉取是否失败 */
    private final Map<String, Boolean> failed = new ConcurrentHashMap<>();
    private final Map<String, String> lastStatus = new ConcurrentHashMap<>();
    private final Map<String, Long> lastOfflineEvent = new ConcurrentHashMap<>();

    public DocsService(ServiceService services, TeamProperties props, Db db, WebhookService webhooks) {
        this.services = services;
        this.props = props;
        this.db = db;
        this.webhooks = webhooks;
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(props.getDocs().getTimeoutSeconds())).build();
    }

    @PreDestroy
    void shutdown() {
        pool.shutdownNow();
    }

    public Cached cached(String service) {
        return cache.get(service);
    }

    @Scheduled(initialDelay = 5_000, fixedDelayString = "${csap.team.docs.refresh-seconds:60}", timeUnit = TimeUnit.SECONDS)
    public void refreshAll() {
        try {
            List<ServiceService.Row> rows = services.all();
            List<java.util.concurrent.Future<?>> fs = new ArrayList<>();
            for (ServiceService.Row r : rows) {
                fs.add(pool.submit(() -> refresh(r)));
            }
            for (java.util.concurrent.Future<?> f : fs) {
                try {
                    f.get(60, TimeUnit.SECONDS);
                } catch (Exception e) {
                    // 单个服务拉取异常不影响其他服务
                }
            }
            long now = System.currentTimeMillis();
            for (ServiceService.Row r : rows) {
                checkOffline(r, now);
            }
        } catch (RuntimeException e) {
            log.warn("刷新文档缓存失败: {}", e.getMessage());
        }
    }

    /** 后台拉取一次(手工添加服务后马上看),不阻塞请求。 */
    public void refreshAsync(ServiceService.Row r) {
        pool.submit(() -> refresh(r));
    }

    /** 立刻拉取一个服务。 */
    public boolean refresh(ServiceService.Row r) {
        long now = System.currentTimeMillis();
        String base = "REGISTERED".equals(r.source()) ? services.onlineInstanceUrl(r.service(), now) : r.baseUrl();
        if (base == null) {
            return false;
        }
        try {
            HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(base + r.docPath()))
                    .timeout(Duration.ofSeconds(props.getDocs().getTimeoutSeconds())).header("Accept", "application/json").GET();
            for (Map.Entry<String, String> h : props.getDocs().getHeaders().entrySet()) {
                b.header(h.getKey(), h.getValue());
            }
            HttpResponse<String> res = http.send(b.build(), HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() / 100 != 2) {
                throw new IllegalStateException("HTTP " + res.statusCode());
            }
            String body = res.body();
            if (body == null || body.length() > MAX_BYTES || Json.read(body) == null) {
                throw new IllegalStateException("返回内容不是有效的 JSON 文档");
            }
            cache.put(r.service(), new Cached(body, System.currentTimeMillis(), false, buildIndex(r.service(), Json.read(body))));
            failed.put(r.service(), false);
            if ("MANUAL".equals(r.source())) {
                db.update("UPDATE csap_team_project_service SET last_seen_at = ? WHERE service = ?", System.currentTimeMillis(), r.service());
            }
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } catch (Exception e) {
            failed.put(r.service(), true);
            Cached old = cache.get(r.service());
            if (old != null && !old.stale()) {
                cache.put(r.service(), new Cached(old.json(), old.fetchedAt(), true, old.index()));
            }
            log.warn("拉取服务 {} 的文档失败: {}", r.service(), e.getMessage());
            return false;
        }
    }

    /** ONLINE / STALE / OFFLINE。 */
    public String status(ServiceService.Row r, long now) {
        Cached c = cache.get(r.service());
        boolean failedLast = Boolean.TRUE.equals(failed.get(r.service()));
        if ("REGISTERED".equals(r.source())) {
            if (services.onlineInstances(r.service(), now) == 0) {
                return "OFFLINE";
            }
            return failedLast && c != null ? "STALE" : "ONLINE";
        }
        if (c == null) {
            return "OFFLINE";
        }
        return failedLast ? "STALE" : "ONLINE";
    }

    private void checkOffline(ServiceService.Row r, long now) {
        String s = status(r, now);
        String prev = lastStatus.put(r.service(), s);
        if ("OFFLINE".equals(s) && prev != null && !"OFFLINE".equals(prev)) {
            Long last = lastOfflineEvent.get(r.service());
            if (last == null || now - last >= OFFLINE_EVENT_GAP_MS) {
                lastOfflineEvent.put(r.service(), now);
                String pname = r.projectId() == null ? null
                        : db.one("SELECT name FROM csap_team_project WHERE id = ?", (rs, i) -> rs.getString(1), r.projectId());
                webhooks.fire(new WebhookEvent("service.offline", now, r.projectId(), pname, null, null, null,
                        List.of(WebhookEvent.item(r.service(), null)), "服务 " + r.service() + " 已离线"));
            }
        }
    }

    public void forget(String service) {
        cache.remove(service);
        failed.remove(service);
        lastStatus.remove(service);
    }

    // ---- 搜索 ----

    static List<Entry> buildIndex(String service, JsonNode root) {
        List<Entry> out = new ArrayList<>();
        JsonNode data = root.has("data") && root.get("data").isObject() ? root.get("data") : root;
        for (String listName : new String[] {"apiList", "globalApiList"}) {
            JsonNode controllers = data.get(listName);
            if (controllers == null || !controllers.isArray()) {
                continue;
            }
            for (JsonNode c : controllers) {
                JsonNode methods = c.get("methodList");
                if (methods == null || !methods.isArray()) {
                    continue;
                }
                for (JsonNode m : methods) {
                    addMethod(out, service, m);
                }
            }
        }
        return out;
    }

    private static void addMethod(List<Entry> out, String service, JsonNode m) {
        String title = firstText(m, "name", "simpleName", "value");
        String desc = text(m, "description");
        List<String[]> fields = new ArrayList<>();
        collectModels(m.get("request"), fields, 0);
        collectModels(m.get("response"), fields, 0);
        List<String> paths = strings(m.get("paths"));
        if (paths.isEmpty()) {
            paths = strings(m.get("apiPath"));
        }
        List<String> verbs = strings(m.get("methods"));
        if (verbs.isEmpty()) {
            verbs = List.of("ANY");
        }
        if (paths.isEmpty()) {
            paths = List.of("");
        }
        for (String p : paths) {
            for (String v : verbs) {
                out.add(new Entry(service, v, p, title, desc, fields));
            }
        }
    }

    private static void collectModels(JsonNode models, List<String[]> out, int depth) {
        if (models == null || !models.isArray() || depth > 6) {
            return;
        }
        for (JsonNode model : models) {
            collectParams(model.get("parameters"), out, depth);
        }
    }

    private static void collectParams(JsonNode params, List<String[]> out, int depth) {
        if (params == null || !params.isArray() || depth > 8) {
            return;
        }
        for (JsonNode p : params) {
            String name = firstText(p, "name", "keyName", "key");
            if (name != null && !name.isEmpty()) {
                out.add(new String[] {name, text(p, "description")});
            }
            collectParams(p.get("parameters"), out, depth + 1);
            JsonNode children = p.get("children");
            if (children != null && children.isObject()) {
                collectParams(children.get("parameters"), out, depth + 1);
            }
        }
    }

    private static List<String> strings(JsonNode n) {
        List<String> l = new ArrayList<>();
        if (n != null && n.isArray()) {
            n.forEach(x -> l.add(x.asText()));
        } else if (n != null && n.isTextual()) {
            l.add(n.asText());
        }
        return l;
    }

    private static String text(JsonNode n, String k) {
        JsonNode v = n.get(k);
        return v == null || v.isNull() ? "" : v.asText("");
    }

    private static String firstText(JsonNode n, String... ks) {
        for (String k : ks) {
            String t = text(n, k);
            if (!t.isEmpty()) {
                return t;
            }
        }
        return "";
    }

    public record Hit(String service, String method, String path, String title, String matchedIn, String snippet) {
        public Map<String, Object> toMap() {
            Map<String, Object> m = new java.util.LinkedHashMap<>();
            m.put("service", service);
            m.put("method", method);
            m.put("path", path);
            m.put("title", title);
            m.put("matchedIn", matchedIn);
            m.put("snippet", snippet);
            return m;
        }
    }

    /** 包含匹配(不区分大小写,中文直接子串),最多 100 条;services 为 null 表示全部。 */
    public List<Hit> search(String q, Set<String> only) {
        List<Hit> hits = new ArrayList<>();
        if (q == null || q.isBlank()) {
            return hits;
        }
        String needle = q.trim().toLowerCase(Locale.ROOT);
        List<String> names = new ArrayList<>(cache.keySet());
        java.util.Collections.sort(names);
        for (String svc : names) {
            if (only != null && !only.contains(svc)) {
                continue;
            }
            for (Entry e : cache.get(svc).index()) {
                Hit h = match(e, needle);
                if (h != null) {
                    hits.add(h);
                    if (hits.size() >= 100) {
                        return hits;
                    }
                }
            }
        }
        return hits;
    }

    private static Hit match(Entry e, String needle) {
        if (e.path().toLowerCase(Locale.ROOT).contains(needle) || e.method().toLowerCase(Locale.ROOT).contains(needle)) {
            return new Hit(e.service(), e.method(), e.path(), e.title(), "path", e.method() + " " + e.path());
        }
        if (e.title().toLowerCase(Locale.ROOT).contains(needle)) {
            return new Hit(e.service(), e.method(), e.path(), e.title(), "title", e.title());
        }
        if (e.description().toLowerCase(Locale.ROOT).contains(needle)) {
            return new Hit(e.service(), e.method(), e.path(), e.title(), "description", around(e.description(), needle));
        }
        for (String[] f : e.fields()) {
            if (f[0].toLowerCase(Locale.ROOT).contains(needle) || f[1].toLowerCase(Locale.ROOT).contains(needle)) {
                return new Hit(e.service(), e.method(), e.path(), e.title(), "field", f[1].isEmpty() ? f[0] : f[0] + ": " + f[1]);
            }
        }
        return null;
    }

    private static String around(String text, String needleLower) {
        int i = text.toLowerCase(Locale.ROOT).indexOf(needleLower);
        int from = Math.max(0, i - 30);
        int to = Math.min(text.length(), i + needleLower.length() + 60);
        return (from > 0 ? "…" : "") + text.substring(from, to) + (to < text.length() ? "…" : "");
    }
}
