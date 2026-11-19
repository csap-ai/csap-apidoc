package ai.csap.apidoc.team.user;

import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import ai.csap.apidoc.team.audit.AuditService;
import ai.csap.apidoc.team.auth.Access;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserService users;
    private final Access access;
    private final AuditService audit;

    public UserController(UserService users, Access access, AuditService audit) {
        this.users = users;
        this.access = access;
        this.audit = audit;
    }

    @GetMapping
    public List<Map<String, Object>> list() {
        access.admin();
        return users.list().stream().map(UserService.UserRow::toMap).toList();
    }

    @PostMapping
    public Map<String, Object> create(@RequestBody Map<String, Object> body) {
        access.admin();
        UserService.UserRow u = users.create(s(body, "username"), s(body, "displayName"), s(body, "password"), s(body, "orgRole"), false);
        audit.log("user.create").reason(u.username() + " (" + u.orgRole() + ")").save();
        return u.toMap();
    }

    @PatchMapping("/{id}")
    public Map<String, Object> update(@PathVariable long id, @RequestBody Map<String, Object> body) {
        access.admin();
        UserService.UserRow u = users.update(id, body);
        StringBuilder changed = new StringBuilder();
        for (String k : body.keySet()) {
            changed.append(changed.length() == 0 ? "" : ",").append(k);
        }
        audit.log("user.update").reason(u.username() + " 修改: " + changed).save();
        return u.toMap();
    }

    private static String s(Map<String, Object> m, String k) {
        return m.get(k) instanceof String v ? v : null;
    }
}
