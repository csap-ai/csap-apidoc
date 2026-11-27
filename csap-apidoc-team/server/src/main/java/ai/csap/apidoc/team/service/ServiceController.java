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
import ai.csap.apidoc.team.env.Env;
import ai.csap.apidoc.team.env.Environments;

@RestController
@RequestMapping("/api/v1/services")
public class ServiceController {

    private final ServiceService services;
    private final DocsService docs;
    private final Db db;
    private final Access access;
    private final AuditService audit;
    private final Environments environments;

    public ServiceController(ServiceService services, DocsService docs, Db db, Access access, AuditService audit,
                             Environments environments) {
        this.services = services;
        this.docs = docs;
        this.db = db;
        this.access = access;
        this.audit = audit;
        this.environments = environments;
    }

    private Map<String, Object> envMap(ServiceService.EnvRow e, long now) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("env", e.env());
        m.put("baseUrl", e.baseUrl());
        m.put("docPath", e.docPath());
        m.put("source", e.source());
        m.put("status", docs.status(e, now));
        m.put("instances", services.onlineInstances(e.env(), e.service(), now));
        m.put("lastSeenAt", e.lastSeenAt());
        DocsService.Cached c = docs.cached(e.env(), e.service());
        m.put("lastFetchedAt", c == null ? null : c.fetchedAt());
        return m;
    }

    /** 顶层字段取 env 所指环境(没有记录时为 null、status=OFFLINE);envs 列出该服务出现过的所有环境。 */
    Map<String, Object> toMap(ServiceService.Row r, List<ServiceService.EnvRow> envRows, Env env, Map<Long, String> projectNames,
                              long now) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("service", r.service());
        m.put("projectId", r.projectId());
        m.put("projectName", r.projectId() == null ? null : projectNames.get(r.projectId()));
        ServiceService.EnvRow cur = envRows.stream().filter(e -> e.env().equals(env.key())).findFirst().orElse(null);
        Map<String, Object> top = cur == null ? null : envMap(cur, now);
        m.put("baseUrl", cur == null ? null : cur.baseUrl());
        m.put("docPath", cur == null ? null : cur.docPath());
        m.put("language", r.language());
        m.put("frameworkVersion", r.frameworkVersion());
        m.put("source", cur == null ? null : cur.source());
        m.put("status", top == null ? "OFFLINE" : top.get("status"));
        m.put("instances", top == null ? 0 : top.get("instances"));
        m.put("lastSeenAt", cur == null ? null : cur.lastSeenAt());
        m.put("lastFetchedAt", top == null ? null : top.get("lastFetchedAt"));
        List<Map<String, Object>> envs = new ArrayList<>();
        for (Env e : environments.all()) {
            for (ServiceService.EnvRow er : envRows) {
                if (er.env().equals(e.key())) {
                    envs.add(envMap(er, now));
                }
            }
        }
        m.put("envs", envs);
        return m;
    }

    private Map<String, Object> one(String service, Env env) {
        return toMap(services.get(service), services.envRows(service), env, projectNames(), System.currentTimeMillis());
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
                                          @RequestParam(required = false) Boolean unassigned,
                                          @RequestParam(required = false) String env) {
        SessionUser u = access.user();
        Env e = environments.require(env);
        Set<Long> visible = access.projectsWith(u, Role.VIEWER);
        Map<Long, String> names = projectNames();
        Map<String, List<ServiceService.EnvRow>> byService = new java.util.HashMap<>();
        for (ServiceService.EnvRow er : services.allEnvRows()) {
            byService.computeIfAbsent(er.service(), k -> new ArrayList<>()).add(er);
        }
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
            out.add(toMap(r, byService.getOrDefault(r.service(), List.of()), e, names, now));
        }
        return out;
    }

    @PostMapping
    public Map<String, Object> add(@RequestBody Map<String, Object> body) {
        Env e = environments.require(str(body, "env"));
        String name = ServiceService.checkName(str(body, "service"));
        ServiceService.Row existing = services.get(name);
        Long projectId = existing != null ? existing.projectId()
                : (body.get("projectId") instanceof Number n ? n.longValue() : null);
        if (projectId == null) {
            access.admin();
        } else {
            access.requireRole(projectId, Role.MAINTAINER);
        }
        ServiceService.EnvRow r = services.addManual(name, str(body, "baseUrl"), str(body, "docPath"), projectId, e);
        audit.log("service.create").project(projectId).env(e.key()).service(r.service()).reason(r.baseUrl()).save();
        docs.refreshAsync(r);
        return one(r.service(), e);
    }

    @PatchMapping("/{service}")
    public Map<String, Object> update(@PathVariable String service, @RequestBody Map<String, Object> body,
                                      @RequestParam(required = false) String env) {
        ServiceService.Row r = load(service);
        Env e = environments.require(env);
        authorize(r);
        Long projectId = r.projectId();
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
        if (body.containsKey("baseUrl") || body.containsKey("docPath")) {
            ServiceService.EnvRow er = services.envRow(service, e.key());
            if (er == null) {
                throw ApiException.notFound("服务在环境「" + e.name() + "」没有地址,请先添加");
            }
            String baseUrl = er.baseUrl();
            String docPath = er.docPath();
            if (body.containsKey("baseUrl")) {
                baseUrl = ServiceService.checkBaseUrl(str(body, "baseUrl"));
                changed.append("baseUrl ");
            }
            if (body.containsKey("docPath")) {
                docPath = ServiceService.checkDocPath(str(body, "docPath"));
                changed.append("docPath ");
            }
            services.updateEnvRow(service, e.key(), baseUrl, docPath);
        }
        db.update("UPDATE csap_team_project_service SET project_id = ? WHERE service = ?", projectId, service);
        audit.log("service.update").project(projectId).env(e.key()).service(service)
                .before(r.projectId() == null ? null : String.valueOf(r.projectId()))
                .after(projectId == null ? null : String.valueOf(projectId)).reason(changed.toString().trim()).save();
        return one(service, e);
    }

    @DeleteMapping("/{service}")
    public Map<String, Object> delete(@PathVariable String service, @RequestParam(required = false) String env) {
        ServiceService.Row r = load(service);
        authorize(r);
        if (env != null && !env.isBlank()) {
            Env e = environments.require(env);
            services.deleteEnv(service, e.key());
            docs.forget(e.key(), service);
            audit.log("service.delete").project(r.projectId()).env(e.key()).service(service).save();
        } else {
            services.delete(service);
            docs.forgetAll(service);
            audit.log("service.delete").project(r.projectId()).service(service).save();
        }
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
