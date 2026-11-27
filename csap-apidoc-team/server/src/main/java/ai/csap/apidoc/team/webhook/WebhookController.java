package ai.csap.apidoc.team.webhook;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import ai.csap.apidoc.team.audit.AuditService;
import ai.csap.apidoc.team.auth.Access;
import ai.csap.apidoc.team.auth.Role;
import ai.csap.apidoc.team.auth.SessionUser;
import ai.csap.apidoc.team.common.ApiException;
import ai.csap.apidoc.team.db.Db;
import ai.csap.apidoc.team.env.Environments;

/** Webhook 管理:管理员全部;维护者只能管理所在项目的。secret 只写不回显。 */
@RestController
@RequestMapping("/api/v1/webhooks")
public class WebhookController {

    private static final Set<String> KINDS = Set.of("GENERIC", "WECOM", "DINGTALK", "FEISHU");

    private final WebhookService service;
    private final Db db;
    private final Access access;
    private final AuditService audit;
    private final Environments environments;

    public WebhookController(WebhookService service, Db db, Access access, AuditService audit, Environments environments) {
        this.service = service;
        this.db = db;
        this.access = access;
        this.audit = audit;
        this.environments = environments;
    }

    /** 订阅环境:空 / 省略 = 全部(存 null);否则逗号分隔的环境 key。 */
    private String envKeys(Object v) {
        if (v == null) {
            return null;
        }
        List<String> keys = new ArrayList<>();
        if (v instanceof List<?> l) {
            for (Object o : l) {
                keys.add(String.valueOf(o));
            }
        } else if (v instanceof String s) {
            for (String p : s.split(",")) {
                if (!p.isBlank()) {
                    keys.add(p.trim());
                }
            }
        } else {
            throw ApiException.validation("environments 必须是数组");
        }
        if (keys.isEmpty()) {
            return null;
        }
        for (String k : keys) {
            environments.require(k);
        }
        String joined = String.join(",", keys.stream().distinct().toList());
        if (joined.length() > 512) {
            throw ApiException.validation("environments 过长");
        }
        return joined;
    }

    @GetMapping
    public List<Map<String, Object>> list(@RequestParam(required = false) Long projectId) {
        SessionUser u = access.user();
        Set<Long> maintained = access.projectsWith(u, Role.MAINTAINER);
        if (maintained != null && maintained.isEmpty()) {
            throw ApiException.forbidden("没有权限管理 Webhook");
        }
        return service.list(projectId, maintained == null ? null : new ArrayList<>(maintained)).stream()
                .map(WebhookService.Hook::toMap).collect(Collectors.toList());
    }

    @PostMapping
    public Map<String, Object> create(@RequestBody Map<String, Object> body) {
        Long projectId = body.get("projectId") instanceof Number n ? n.longValue() : null;
        checkScope(projectId);
        String name = text(body, "name", 128, true);
        String kind = body.get("kind") instanceof String k ? k : "GENERIC";
        if (!KINDS.contains(kind)) {
            throw ApiException.validation("kind 只能是 GENERIC / WECOM / DINGTALK / FEISHU");
        }
        String url = url(body.get("url"));
        String secret = secret(body.get("secret"));
        String events = events(body.get("events"));
        boolean enabled = !body.containsKey("enabled") || Boolean.TRUE.equals(body.get("enabled"));
        String envKeys = envKeys(body.get("environments"));
        long now = System.currentTimeMillis();
        long id = db.insert("INSERT INTO csap_team_webhook (project_id, name, kind, url, secret, events, enabled, created_at,"
                + " updated_at, env_keys) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)", projectId, name, kind, url, secret, events,
                enabled ? 1 : 0, now, now, envKeys);
        audit.log("webhook.create").project(projectId).reason(name + " (" + kind + ")").save();
        return service.get(id).toMap();
    }

    @PatchMapping("/{id}")
    public Map<String, Object> update(@PathVariable long id, @RequestBody Map<String, Object> body) {
        WebhookService.Hook h = load(id);
        String name = h.name();
        String kind = h.kind();
        String url = h.url();
        String secret = h.secret();
        String events = h.events();
        boolean enabled = h.enabled();
        String envKeys = h.envKeys();
        if (body.containsKey("environments")) { envKeys = envKeys(body.get("environments")); }
        if (body.containsKey("name")) { name = text(body, "name", 128, true); }
        if (body.containsKey("kind")) {
            kind = body.get("kind") instanceof String k ? k : null;
            if (!KINDS.contains(kind)) {
                throw ApiException.validation("kind 只能是 GENERIC / WECOM / DINGTALK / FEISHU");
            }
        }
        if (body.containsKey("url")) { url = url(body.get("url")); }
        if (body.containsKey("secret")) { secret = secret(body.get("secret")); }
        if (body.containsKey("events")) { events = events(body.get("events")); }
        if (body.containsKey("enabled")) { enabled = Boolean.TRUE.equals(body.get("enabled")); }
        db.update("UPDATE csap_team_webhook SET name = ?, kind = ?, url = ?, secret = ?, events = ?, enabled = ?, updated_at = ?,"
                + " env_keys = ? WHERE id = ?", name, kind, url, secret, events, enabled ? 1 : 0, System.currentTimeMillis(), envKeys, id);
        audit.log("webhook.update").project(h.projectId()).reason(name).save();
        return service.get(id).toMap();
    }

    @DeleteMapping("/{id}")
    public Map<String, Object> delete(@PathVariable long id) {
        WebhookService.Hook h = load(id);
        db.inTx(() -> {
            db.update("DELETE FROM csap_team_webhook_delivery WHERE webhook_id = ?", id);
            db.update("DELETE FROM csap_team_webhook WHERE id = ?", id);
        });
        audit.log("webhook.delete").project(h.projectId()).reason(h.name()).save();
        return new LinkedHashMap<>();
    }

    @PostMapping("/{id}/test")
    public Map<String, Object> test(@PathVariable long id) {
        return service.test(load(id));
    }

    @GetMapping("/{id}/deliveries")
    public List<Map<String, Object>> deliveries(@PathVariable long id, @RequestParam(defaultValue = "50") int limit) {
        load(id);
        return service.deliveries(id, Math.max(1, Math.min(limit, 200)));
    }

    private WebhookService.Hook load(long id) {
        WebhookService.Hook h = service.get(id);
        if (h == null) {
            throw ApiException.notFound("Webhook 不存在");
        }
        checkScope(h.projectId());
        return h;
    }

    private void checkScope(Long projectId) {
        if (projectId == null) {
            access.admin();
        } else {
            if (db.count("SELECT COUNT(*) FROM csap_team_project WHERE id = ?", projectId) == 0) {
                throw ApiException.validation("项目不存在");
            }
            access.requireRole(projectId, Role.MAINTAINER);
        }
    }

    private static String text(Map<String, Object> b, String k, int max, boolean required) {
        Object v = b.get(k);
        if (!(v instanceof String s) || (required && s.isBlank()) || s.length() > max) {
            throw ApiException.validation(k + " 不能为空且不超过 " + max + " 字");
        }
        return s.trim();
    }

    private static String url(Object v) {
        if (!(v instanceof String s) || s.length() > 1000) {
            throw ApiException.validation("url 无效");
        }
        try {
            URI u = URI.create(s.trim());
            if (u.getHost() == null || !("http".equals(u.getScheme()) || "https".equals(u.getScheme()))) {
                throw new IllegalArgumentException();
            }
        } catch (IllegalArgumentException e) {
            throw ApiException.validation("url 必须是 http 或 https 地址");
        }
        return s.trim();
    }

    private static String secret(Object v) {
        if (v == null || "".equals(v)) {
            return null;
        }
        if (!(v instanceof String s) || s.length() > 256) {
            throw ApiException.validation("secret 不超过 256 字");
        }
        return s;
    }

    private static String events(Object v) {
        List<String> list = new ArrayList<>();
        if (v instanceof List<?> l) {
            for (Object o : l) {
                list.add(String.valueOf(o));
            }
        } else if (v instanceof String s) {
            for (String p : s.split(",")) {
                if (!p.isBlank()) {
                    list.add(p.trim());
                }
            }
        }
        if (list.isEmpty()) {
            throw ApiException.validation("events 至少选择一个事件");
        }
        for (String e : list) {
            if (!WebhookEvent.ALL.contains(e)) {
                throw ApiException.validation("未知事件: " + e);
            }
        }
        return String.join(",", list.stream().distinct().toList());
    }
}
