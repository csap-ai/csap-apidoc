package ai.csap.apidoc.team.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
import ai.csap.apidoc.team.docs.DocsService;

@RestController
@RequestMapping("/api/v1/services")
public class ServiceController {

    private final ServiceService services;
    private final DocsService docs;
    private final Db db;
    private final Access access;
    private final AuditService audit;

    public ServiceController(ServiceService services, DocsService docs, Db db, Access access, AuditService audit) {
        this.services = services;
        this.docs = docs;
        this.db = db;
        this.access = access;
        this.audit = audit;
    }

    Map<String, Object> toMap(ServiceService.Row r, Map<Long, String> projectNames, long now) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("service", r.service());
        m.put("projectId", r.projectId());
        m.put("projectName", r.projectId() == null ? null : projectNames.get(r.projectId()));
        m.put("baseUrl", r.baseUrl());
        m.put("docPath", r.docPath());
        m.put("language", r.language());
        m.put("frameworkVersion", r.frameworkVersion());
        m.put("source", r.source());
        m.put("status", docs.status(r, now));
        m.put("instances", services.onlineInstances(r.service(), now));
        m.put("lastSeenAt", r.lastSeenAt());
        DocsService.Cached c = docs.cached(r.service());
        m.put("lastFetchedAt", c == null ? null : c.fetchedAt());
        return m;
    }

    private Map<Long, String> projectNames() {
        Map<Long, String> names = new java.util.HashMap<>();
        db.jdbc().query("SELECT id, name FROM csap_team_project", rs -> {
            names.put(rs.getLong(1), rs.getString(2));
        });
        return names;
    }

    @GetMapping
    public List<Map<String, Object>> list(@RequestParam(required = false) Long projectId,
                                          @RequestParam(required = false) Boolean unassigned) {
        SessionUser u = access.user();
        Set<Long> visible = access.projectsWith(u, Role.VIEWER);
        Map<Long, String> names = projectNames();
        long now = System.currentTimeMillis();
        List<Map<String, Object>> out = new ArrayList<>();
        for (ServiceService.Row r : services.all()) {
            if (visible != null && (r.projectId() == null || !visible.contains(r.projectId()))) {
                continue;
            }
            if (projectId != null && !projectId.equals(r.projectId())) {
                continue;
            }
            if (Boolean.TRUE.equals(unassigned) && r.projectId() != null) {
                continue;
            }
            out.add(toMap(r, names, now));
        }
        return out;
    }

    @PostMapping
    public Map<String, Object> add(@RequestBody Map<String, Object> body) {
        Long projectId = body.get("projectId") instanceof Number n ? n.longValue() : null;
        if (projectId == null) {
            access.admin();
        } else {
            access.requireRole(projectId, Role.MAINTAINER);
        }
        ServiceService.Row r = services.addManual(str(body, "service"), str(body, "baseUrl"), str(body, "docPath"), projectId);
        audit.log("service.create").project(projectId).service(r.service()).reason(r.baseUrl()).save();
        docs.refreshAsync(r);
        return toMap(r, projectNames(), System.currentTimeMillis());
    }

    @PatchMapping("/{service}")
    public Map<String, Object> update(@PathVariable String service, @RequestBody Map<String, Object> body) {
        ServiceService.Row r = load(service);
        authorize(r);
        Long projectId = r.projectId();
        String baseUrl = r.baseUrl();
        String docPath = r.docPath();
        StringBuilder changed = new StringBuilder();
        if (body.containsKey("projectId")) {
            Object v = body.get("projectId");
            Long target = v instanceof Number n ? n.longValue() : null;
            if (v != null && target == null) {
                throw ApiException.validation("projectId 无效");
            }
            if (target == null) {
                access.admin();
            } else {
                if (db.count("SELECT COUNT(*) FROM csap_team_project WHERE id = ?", target) == 0) {
                    throw ApiException.validation("项目不存在");
                }
                access.requireRole(target, Role.MAINTAINER);
            }
            projectId = target;
            changed.append("projectId ");
        }
        if (body.containsKey("baseUrl")) {
            baseUrl = ServiceService.checkBaseUrl(str(body, "baseUrl"));
            changed.append("baseUrl ");
        }
        if (body.containsKey("docPath")) {
            docPath = ServiceService.checkDocPath(str(body, "docPath"));
            changed.append("docPath ");
        }
        db.update("UPDATE csap_team_project_service SET project_id = ?, base_url = ?, doc_path = ? WHERE service = ?", projectId,
                baseUrl, docPath, service);
        audit.log("service.update").project(projectId).service(service).before(r.projectId() == null ? null : String.valueOf(r.projectId()))
                .after(projectId == null ? null : String.valueOf(projectId)).reason(changed.toString().trim()).save();
        return toMap(services.get(service), projectNames(), System.currentTimeMillis());
    }

    @DeleteMapping("/{service}")
    public Map<String, Object> delete(@PathVariable String service) {
        ServiceService.Row r = load(service);
        authorize(r);
        services.delete(service);
        docs.forget(service);
        audit.log("service.delete").project(r.projectId()).service(service).save();
        return new LinkedHashMap<>();
    }

    private ServiceService.Row load(String service) {
        ServiceService.Row r = services.get(service);
        if (r == null) {
            throw ApiException.notFound("服务不存在");
        }
        return r;
    }

    private void authorize(ServiceService.Row r) {
        if (r.projectId() == null) {
            access.admin();
        } else {
            access.requireRole(r.projectId(), Role.MAINTAINER);
        }
    }

    private static String str(Map<String, Object> m, String k) {
        return m.get(k) instanceof String s ? s : null;
    }
}
