package ai.csap.apidoc.team.env;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import ai.csap.apidoc.team.auth.Access;
import ai.csap.apidoc.team.auth.SessionUser;
import ai.csap.apidoc.team.common.ApiException;
import ai.csap.apidoc.team.common.UnifiedDiff;
import ai.csap.apidoc.team.db.Db;
import ai.csap.apidoc.team.rule.RuleReader;

/** 环境列表与环境差异对比。 */
@RestController
@RequestMapping("/api/v1/envs")
public class EnvController {

    private final Environments environments;
    private final EnvDiff envDiff;
    private final RuleReader reader;
    private final Access access;
    private final Db db;

    public EnvController(Environments environments, EnvDiff envDiff, RuleReader reader, Access access, Db db) {
        this.environments = environments;
        this.envDiff = envDiff;
        this.reader = reader;
        this.access = access;
        this.db = db;
    }

    /** 任何已登录用户;不返回数据源地址。连接失败的原文(可能含库地址)只给管理员,其他人只看到状态。 */
    @GetMapping
    public List<Map<String, Object>> list() {
        boolean admin = access.user().isAdmin();
        List<Map<String, Object>> out = new ArrayList<>();
        for (Env e : environments.all()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("key", e.key());
            m.put("name", e.name());
            m.put("order", e.order());
            m.put("protected", e.isProtected());
            m.put("readOnly", e.readOnly());
            m.put("status", e.isUp() ? "UP" : "DOWN");
            m.put("error", e.error() == null ? null : admin ? e.error() : "连接失败");
            m.put("default", e.order() == 0);
            out.add(m);
        }
        return out;
    }

    private Env[] pair(String from, String to) {
        if (from == null || to == null || from.isBlank() || to.isBlank()) {
            throw ApiException.validation("from 和 to 必填");
        }
        Env f = environments.require(from);
        Env t = environments.require(to);
        if (f.key().equals(t.key())) {
            throw ApiException.validation("from 和 to 不能是同一个环境");
        }
        return new Env[] {f, t};
    }

    @GetMapping("/diff")
    public Map<String, Object> diff(@RequestParam(required = false) String from, @RequestParam(required = false) String to,
                                    @RequestParam(required = false) Long projectId, @RequestParam(required = false) String service) {
        Env[] p = pair(from, to);
        if (projectId == null && (service == null || service.isBlank())) {
            throw ApiException.validation("projectId 与 service 至少要给一个");
        }
        SessionUser u = access.user();
        List<String> services;
        if (projectId != null) {
            if (access.roleIn(u, projectId) == null) {
                throw ApiException.forbidden("没有权限查看该项目");
            }
            services = db.query("SELECT service FROM csap_team_project_service WHERE project_id = ?", (rs, i) -> rs.getString(1), projectId);
            if (service != null && !service.isBlank()) {
                if (!services.contains(service)) {
                    throw ApiException.validation("服务 " + service + " 不属于该项目");
                }
                services = List.of(service);
            }
        } else {
            access.requireViewService(service);
            services = List.of(service);
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (EnvDiff.ServiceDiff sd : envDiff.diff(p[0], p[1], services)) {
            Map<String, Object> sm = new LinkedHashMap<>();
            sm.put("service", sd.service());
            List<Map<String, Object>> items = new ArrayList<>();
            for (EnvDiff.Item it : sd.items()) {
                Map<String, Object> im = new LinkedHashMap<>();
                im.put("ruleKey", it.ruleKey());
                im.put("status", it.status());
                im.put("fromVersion", it.fromVersion());
                im.put("toVersion", it.toVersion());
                items.add(im);
            }
            sm.put("items", items);
            out.add(sm);
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("from", p[0].key());
        m.put("to", p[1].key());
        m.put("services", out);
        return m;
    }

    /** diff 为 to → from 的统一差异,即"推广后目标会怎么变"。 */
    @GetMapping("/diff/item")
    public Map<String, Object> diffItem(@RequestParam(required = false) String from, @RequestParam(required = false) String to,
                                        @RequestParam String service, @RequestParam String key) {
        Env[] p = pair(from, to);
        access.requireViewService(service);
        RuleReader.Current f = reader.current(p[0], service, key);
        RuleReader.Current t = reader.current(p[1], service, key);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("from", p[0].key());
        m.put("to", p[1].key());
        m.put("service", service);
        m.put("ruleKey", key);
        m.put("fromRules", f == null ? null : f.rules());
        m.put("toRules", t == null ? null : t.rules());
        m.put("fromVersion", f == null ? null : f.version());
        m.put("toVersion", t == null ? null : t.version());
        m.put("diff", UnifiedDiff.ofRules(t == null ? null : t.rules(), f == null ? null : f.rules(), p[1].key(), p[0].key()));
        return m;
    }
}
