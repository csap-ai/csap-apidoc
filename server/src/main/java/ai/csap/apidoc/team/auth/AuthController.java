package ai.csap.apidoc.team.auth;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import ai.csap.apidoc.team.audit.AuditService;
import ai.csap.apidoc.team.common.ApiException;
import ai.csap.apidoc.team.db.Db;
import ai.csap.apidoc.team.db.Settings;
import ai.csap.apidoc.team.user.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/** 初始化、登录、当前用户、改密码。 */
@RestController
@RequestMapping("/api/v1")
public class AuthController {

    private final UserService users;
    private final PasswordService passwords;
    private final SessionTokens sessions;
    private final LoginGuard guard;
    private final AuthFilter filter;
    private final AuditService audit;
    private final Settings settings;
    private final Access access;
    private final Db db;
    private final ai.csap.apidoc.team.license.LicenseService license;

    public AuthController(UserService users, PasswordService passwords, SessionTokens sessions, LoginGuard guard,
                          AuthFilter filter, AuditService audit, Settings settings, Access access, Db db, ai.csap.apidoc.team.license.LicenseService license) {
        this.license = license;
        this.users = users;
        this.passwords = passwords;
        this.sessions = sessions;
        this.guard = guard;
        this.filter = filter;
        this.audit = audit;
        this.settings = settings;
        this.access = access;
        this.db = db;
    }

    @GetMapping("/setup/status")
    public Map<String, Object> setupStatus() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("initialized", users.initialized());
        return m;
    }

    @PostMapping("/setup")
    public Map<String, Object> setup(@RequestBody Map<String, Object> body, HttpServletRequest req, HttpServletResponse res) {
        synchronized (this) {
            if (users.initialized()) {
                throw ApiException.conflict("系统已经初始化");
            }
            String org = str(body, "orgName");
            if (org == null || org.isBlank() || org.length() > 128) {
                throw ApiException.validation("组织名称不能为空且不超过 128 字");
            }
            UserService.UserRow u = users.create(str(body, "username"), str(body, "displayName"), str(body, "password"), "ADMIN", true);
            settings.set("org.name", org.trim());
            RequestContext.setActor(Actor.user(u.id(), u.username(), "CONSOLE", RequestContext.ip()));
            audit.log("setup.init").reason("组织 " + org.trim()).save();
            startSession(req, res, u);
            return meInfo(u);
        }
    }

    @PostMapping("/auth/login")
    public Map<String, Object> login(@RequestBody Map<String, Object> body, HttpServletRequest req, HttpServletResponse res) {
        String username = str(body, "username");
        String password = str(body, "password");
        long now = System.currentTimeMillis();
        String ip = RequestContext.ip();
        if (username == null || password == null) {
            throw ApiException.validation("用户名和密码不能为空");
        }
        Actor anon = new Actor(Actor.USER, null, username.length() > 128 ? username.substring(0, 128) : username, "CONSOLE", ip);
        if (guard.isLocked(username, now)) {
            audit.logAs(anon, "auth.login.failed").reason("账号已锁定").save();
            throw ApiException.unauthorized("登录失败次数过多,账号已锁定 15 分钟");
        }
        UserService.UserRow u = users.byUsername(username);
        // 用户不存在时也做一次哈希比较,避免通过耗时枚举用户名
        boolean ok = u != null && "ACTIVE".equals(u.status()) && passwords.matches(password, u.passwordHash());
        if (u == null) {
            passwords.burn(password);
        }
        if (!ok) {
            guard.fail(username, now);
            audit.logAs(anon, "auth.login.failed").reason(u != null && !"ACTIVE".equals(u.status()) ? "账号已停用" : "用户名或密码错误").save();
            throw ApiException.unauthorized("用户名或密码错误");
        }
        guard.success(username);
        startSession(req, res, u);
        audit.logAs(Actor.user(u.id(), u.username(), "CONSOLE", ip), "auth.login").save();
        return meInfo(u);
    }

    @PostMapping("/auth/logout")
    public Map<String, Object> logout(HttpServletRequest req, HttpServletResponse res) {
        filter.clearSessionCookie(req, res);
        return new LinkedHashMap<>();
    }

    @GetMapping("/auth/me")
    public Map<String, Object> me() {
        SessionUser s = access.user();
        return meInfo(users.byId(s.id()));
    }

    @PostMapping("/auth/password")
    public Map<String, Object> password(@RequestBody Map<String, Object> body, HttpServletRequest req, HttpServletResponse res) {
        SessionUser s = access.user();
        UserService.UserRow u = users.byId(s.id());
        if (!passwords.matches(str(body, "oldPassword"), u.passwordHash())) {
            throw ApiException.validation("原密码不正确");
        }
        String np = str(body, "newPassword");
        passwords.validate(np);
        String hash = passwords.hash(np);
        users.setPassword(u.id(), hash);
        audit.log("auth.password").save();
        long now = System.currentTimeMillis();
        filter.addSessionCookie(req, res, sessions.issue(u.id(), passwords.fingerprint(hash), now, filter.ttlMs()));
        return new LinkedHashMap<>();
    }

    private void startSession(HttpServletRequest req, HttpServletResponse res, UserService.UserRow u) {
        long now = System.currentTimeMillis();
        filter.addSessionCookie(req, res, sessions.issue(u.id(), passwords.fingerprint(u.passwordHash()), now, filter.ttlMs()));
    }

    private Map<String, Object> meInfo(UserService.UserRow u) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", u.id());
        m.put("username", u.username());
        m.put("displayName", u.displayName());
        m.put("orgRole", u.orgRole());
        List<Map<String, Object>> projects = new ArrayList<>();
        if ("ADMIN".equals(u.orgRole())) {
            for (Object[] r : db.query("SELECT p.id, p.name, m.role FROM csap_team_project p LEFT JOIN csap_team_member m"
                    + " ON m.project_id = p.id AND m.user_id = ? ORDER BY p.id",
                    (rs, i) -> new Object[] {rs.getLong(1), rs.getString(2), rs.getString(3)}, u.id())) {
                projects.add(project(r[0], r[1], r[2] == null ? "MAINTAINER" : r[2]));
            }
        } else {
            for (Object[] r : db.query("SELECT p.id, p.name, m.role FROM csap_team_member m JOIN csap_team_project p"
                    + " ON p.id = m.project_id WHERE m.user_id = ? ORDER BY p.id",
                    (rs, i) -> new Object[] {rs.getLong(1), rs.getString(2), rs.getString(3)}, u.id())) {
                projects.add(project(r[0], r[1], r[2]));
            }
        }
        m.put("projects", projects);
        ai.csap.apidoc.team.license.LicenseService.Info li = license.current();
        Map<String, Object> lm = new LinkedHashMap<>();
        lm.put("status", li.status());
        lm.put("maxMembers", li.maxMembers());
        lm.put("activeMembers", li.activeMembers());
        lm.put("expiresAt", li.expiresAt());
        m.put("license", lm);
        return m;
    }

    private static Map<String, Object> project(Object id, Object name, Object role) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("id", id);
        p.put("name", name);
        p.put("role", role);
        return p;
    }

    static String str(Map<String, Object> body, String k) {
        Object v = body.get(k);
        return v instanceof String s ? s : null;
    }
}
