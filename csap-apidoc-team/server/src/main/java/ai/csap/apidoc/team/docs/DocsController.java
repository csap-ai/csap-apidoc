package ai.csap.apidoc.team.docs;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import ai.csap.apidoc.team.auth.Access;
import ai.csap.apidoc.team.auth.Role;
import ai.csap.apidoc.team.auth.SessionUser;
import ai.csap.apidoc.team.common.ApiException;
import ai.csap.apidoc.team.env.Env;
import ai.csap.apidoc.team.env.Environments;
import ai.csap.apidoc.team.service.ServiceService;

@RestController
@RequestMapping("/api/v1/docs")
public class DocsController {

    private final DocsService docs;
    private final ServiceService services;
    private final Access access;
    private final Environments environments;

    public DocsController(DocsService docs, ServiceService services, Access access, Environments environments) {
        this.docs = docs;
        this.services = services;
        this.access = access;
        this.environments = environments;
    }

    @GetMapping("/search")
    public List<Map<String, Object>> search(@RequestParam(required = false, defaultValue = "") String q,
                                            @RequestParam(required = false) Long projectId,
                                            @RequestParam(required = false) String env) {
        Env e = environments.require(env);
        SessionUser u = access.user();
        Set<Long> visible = access.projectsWith(u, Role.VIEWER);
        Set<String> names = new HashSet<>();
        for (ServiceService.Row r : services.all()) {
            boolean seen = visible == null || (r.projectId() != null && visible.contains(r.projectId()));
            if (seen && (projectId == null || projectId.equals(r.projectId()))) {
                names.add(r.service());
            }
        }
        return docs.search(e.key(), q, names).stream().map(DocsService.Hit::toMap).toList();
    }

    /** 缓存的 /csap/apidoc 原样 JSON,外加 X-CSAP-Team-Fetched-At、X-CSAP-Team-Stale。 */
    @GetMapping("/{service}")
    public ResponseEntity<String> get(@PathVariable String service, @RequestParam(required = false) String env) {
        access.requireViewService(service);
        Env e = environments.require(env);
        ServiceService.EnvRow row = services.envRow(service, e.key());
        if (row == null) {
            throw ApiException.notFound("服务在环境「" + e.name() + "」没有记录");
        }
        DocsService.Cached c = docs.cached(e.key(), service);
        if (c == null) {
            docs.refresh(row);
            c = docs.cached(e.key(), service);
        }
        if (c == null) {
            throw ApiException.notFound("文档尚未拉取到(服务离线或地址不可达)");
        }
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON)
                .header("X-CSAP-Team-Fetched-At", String.valueOf(c.fetchedAt()))
                .header("X-CSAP-Team-Stale", String.valueOf(c.stale()))
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(c.json());
    }
}
