package ai.csap.apidoc.team.presence;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import ai.csap.apidoc.team.auth.Access;
import ai.csap.apidoc.team.auth.Role;
import ai.csap.apidoc.team.auth.SessionUser;
import ai.csap.apidoc.team.common.ApiException;
import ai.csap.apidoc.team.env.Environments;

@RestController
@RequestMapping("/api/v1/presence")
public class PresenceController {

    private final PresenceService presence;
    private final Environments environments;
    private final Access access;

    public PresenceController(PresenceService presence, Environments environments, Access access) {
        this.presence = presence;
        this.environments = environments;
        this.access = access;
    }

    private record Target(String env, String service, String ruleKey) {
    }

    private Target target(Map<String, Object> body) {
        String service = body.get("service") instanceof String s ? s : null;
        String key = body.get("ruleKey") instanceof String s ? s : null;
        if (service == null || service.isBlank() || key == null || key.isBlank()) {
            throw ApiException.validation("service 和 ruleKey 必填");
        }
        String env = environments.require(body.get("env") instanceof String s ? s : null).key();
        return new Target(env, service, key);
    }

    /** 能看该服务规则的人可以查看;只读用户不能上报。 */
    @PutMapping
    public Map<String, Object> put(@RequestBody Map<String, Object> body) {
        SessionUser u = access.user();
        Target t = target(body);
        access.requireViewService(t.service());
        Long pid = access.projectOfService(t.service());
        Role r = pid == null ? null : access.roleIn(u, pid);
        if (r == null || !r.atLeast(Role.DEVELOPER)) {
            throw ApiException.forbidden("只读成员不能上报编辑状态");
        }
        presence.touch(t.env(), t.service(), t.ruleKey(), u.id(), u.displayName());
        return new LinkedHashMap<>();
    }

    @DeleteMapping
    public Map<String, Object> delete(@RequestBody Map<String, Object> body) {
        SessionUser u = access.user();
        Target t = target(body);
        presence.leave(t.env(), t.service(), t.ruleKey(), u.id());
        return new LinkedHashMap<>();
    }

    @GetMapping
    public List<Map<String, Object>> list(@RequestParam(required = false) String env, @RequestParam String service) {
        SessionUser u = access.user();
        access.requireViewService(service);
        return presence.list(environments.require(env).key(), service, u.id());
    }
}
