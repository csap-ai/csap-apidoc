package ai.csap.apidoc.team.token;

import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import ai.csap.apidoc.team.audit.AuditService;
import ai.csap.apidoc.team.auth.Access;
import ai.csap.apidoc.team.auth.SessionUser;

@RestController
@RequestMapping("/api/v1/tokens")
public class TokenController {

    private final TokenService tokens;
    private final Access access;
    private final AuditService audit;

    public TokenController(TokenService tokens, Access access, AuditService audit) {
        this.tokens = tokens;
        this.access = access;
        this.audit = audit;
    }

    @GetMapping
    public List<Map<String, Object>> list() {
        access.admin();
        return tokens.list();
    }

    /** 返回一次性明文 token;审计里只记名称和前缀。 */
    @PostMapping
    public Map<String, Object> create(@RequestBody Map<String, Object> body) {
        SessionUser u = access.admin();
        Long projectId = body.get("projectId") instanceof Number n ? n.longValue() : null;
        Map<String, Object> t = tokens.create(body.get("name") instanceof String s ? s : null,
                body.get("scope") instanceof String s ? s : null, projectId, u.id());
        audit.log("token.create").project(projectId).reason(t.get("name") + " (" + t.get("prefix") + "…, " + t.get("scope") + ")").save();
        return t;
    }

    @DeleteMapping("/{id}")
    public Map<String, Object> revoke(@PathVariable long id) {
        access.admin();
        Map<String, Object> t = tokens.revoke(id);
        audit.log("token.revoke").reason(t.get("name") + " (" + t.get("prefix") + "…)").save();
        return t;
    }
}
