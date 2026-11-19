package ai.csap.apidoc.team.service;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import ai.csap.apidoc.team.auth.RequestContext;
import ai.csap.apidoc.team.auth.SessionUser;
import ai.csap.apidoc.team.auth.TokenPrincipal;
import ai.csap.apidoc.team.change.ChangeService;
import ai.csap.apidoc.team.change.Who;
import ai.csap.apidoc.team.common.ApiException;
import ai.csap.apidoc.team.config.TeamProperties;
import ai.csap.apidoc.team.rule.RuleReader;
import ai.csap.apidoc.team.user.UserService;

/** 开放接口(令牌鉴权):服务注册、规则读取、创建修改单。 */
@RestController
@RequestMapping("/api/open/v1")
public class OpenApiController {

    private final ServiceService services;
    private final RuleReader reader;
    private final ChangeService changes;
    private final UserService users;
    private final TeamProperties props;

    public OpenApiController(ServiceService services, RuleReader reader, ChangeService changes, UserService users,
                             TeamProperties props) {
        this.services = services;
        this.reader = reader;
        this.changes = changes;
        this.users = users;
        this.props = props;
    }

    private TokenPrincipal token(String scope) {
        TokenPrincipal t = RequestContext.token();
        if (t == null) {
            throw ApiException.unauthorized("缺少令牌");
        }
        if (!scope.equals(t.scope())) {
            throw ApiException.forbidden("令牌权限不足,需要 " + scope + " 范围");
        }
        return t;
    }

    @PostMapping("/services/register")
    public Map<String, Object> register(@RequestBody Map<String, Object> body) {
        services.register(token("REGISTER"), body);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("heartbeatSeconds", props.getHeartbeatSeconds());
        return m;
    }

    @PostMapping("/services/deregister")
    public Map<String, Object> deregister(@RequestBody Map<String, Object> body) {
        services.deregister(token("REGISTER"), body);
        return new LinkedHashMap<>();
    }

    @GetMapping("/rules/{service}")
    public Map<String, Object> rules(@PathVariable String service) {
        TokenPrincipal t = token("OPEN_API");
        if (t.projectId() != null) {
            ServiceService.Row r = services.get(service);
            if (r == null || r.projectId() == null || !r.projectId().equals(t.projectId())) {
                throw ApiException.forbidden("令牌只能访问其绑定项目的服务");
            }
        }
        return reader.itemsWithRules(service);
    }

    @PostMapping("/changes")
    public Map<String, Object> createChange(@RequestBody Map<String, Object> body) {
        TokenPrincipal t = token("OPEN_API");
        UserService.UserRow creator = users.byId(t.createdBy());
        if (creator == null || !"ACTIVE".equals(creator.status())) {
            throw ApiException.unauthorized("令牌创建者已停用");
        }
        Who who = Who.token(new SessionUser(creator.id(), creator.username(), creator.displayName(), creator.orgRole()), t.projectId());
        Long projectId = body.get("projectId") instanceof Number n ? n.longValue() : t.projectId();
        if (projectId == null) {
            throw ApiException.validation("projectId 必填");
        }
        Map<String, Object> change = changes.create(who, projectId, body.get("title") instanceof String s ? s : null,
                body.get("reason") instanceof String r ? r : null,
                ai.csap.apidoc.team.change.ChangeController.parseItemsPublic(body.get("items")), "EDIT");
        if (Boolean.TRUE.equals(body.get("submit"))) {
            return changes.submit(who, ((Number) change.get("id")).longValue());
        }
        return change;
    }
}
