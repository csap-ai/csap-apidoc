package ai.csap.apidoc.team.user;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;

import ai.csap.apidoc.team.auth.PasswordService;
import ai.csap.apidoc.team.common.ApiException;
import ai.csap.apidoc.team.db.Db;
import ai.csap.apidoc.team.license.LicenseService;

@Service
public class UserService {

    private static final Pattern USERNAME = Pattern.compile("[A-Za-z0-9._@-]{2,64}");

    private final Db db;
    private final PasswordService passwords;
    private final LicenseService license;
    private volatile boolean initialized;

    public UserService(Db db, PasswordService passwords, LicenseService license) {
        this.db = db;
        this.passwords = passwords;
        this.license = license;
    }

    public record UserRow(long id, String username, String displayName, String passwordHash, String status, String orgRole,
                          long createdAt, long updatedAt) {
        public Map<String, Object> toMap() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", id);
            m.put("username", username);
            m.put("displayName", displayName);
            m.put("status", status);
            m.put("orgRole", orgRole);
            m.put("createdAt", createdAt);
            m.put("updatedAt", updatedAt);
            return m;
        }
    }

    private static final String COLS = "id, username, display_name, password_hash, status, org_role, created_at, updated_at";

    private static UserRow map(java.sql.ResultSet rs, int i) throws java.sql.SQLException {
        return new UserRow(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5), rs.getString(6),
                rs.getLong(7), rs.getLong(8));
    }

    public boolean initialized() {
        if (initialized) {
            return true;
        }
        boolean has = db.count("SELECT COUNT(*) FROM csap_team_user") > 0;
        initialized = has;
        return has;
    }

    public UserRow byId(long id) {
        return db.one("SELECT " + COLS + " FROM csap_team_user WHERE id = ?", UserService::map, id);
    }

    public UserRow byUsername(String username) {
        return db.one("SELECT " + COLS + " FROM csap_team_user WHERE username = ?", UserService::map, username);
    }

    public List<UserRow> list() {
        return db.query("SELECT " + COLS + " FROM csap_team_user ORDER BY id", UserService::map);
    }

    public UserRow create(String username, String displayName, String password, String orgRole, boolean skipLicense) {
        username = username == null ? null : username.trim();
        if (username == null || !USERNAME.matcher(username).matches()) {
            throw ApiException.validation("用户名只能包含字母、数字和 . _ @ -,长度 2–64");
        }
        String role = orgRole == null || orgRole.isBlank() ? "MEMBER" : orgRole;
        if (!"ADMIN".equals(role) && !"MEMBER".equals(role)) {
            throw ApiException.validation("orgRole 只能是 ADMIN 或 MEMBER");
        }
        passwords.validate(password);
        String dn = displayName == null || displayName.isBlank() ? username : displayName.trim();
        if (dn.length() > 128) {
            throw ApiException.validation("显示名过长");
        }
        if (byUsername(username) != null) {
            throw ApiException.conflict("用户名已存在");
        }
        if (!skipLicense) {
            license.checkCanAddActive();
        }
        long now = System.currentTimeMillis();
        try {
            long id = db.insert("INSERT INTO csap_team_user (username, display_name, password_hash, status, org_role, created_at,"
                    + " updated_at) VALUES (?, ?, ?, 'ACTIVE', ?, ?, ?)", username, dn, passwords.hash(password), role, now, now);
            initialized = true;
            return byId(id);
        } catch (org.springframework.dao.DuplicateKeyException e) {
            throw ApiException.conflict("用户名已存在");
        }
    }

    /** body 里出现的字段才修改。 */
    public UserRow update(long id, Map<String, Object> body) {
        UserRow u = byId(id);
        if (u == null) {
            throw ApiException.notFound("用户不存在");
        }
        String dn = u.displayName();
        String status = u.status();
        String role = u.orgRole();
        String hash = u.passwordHash();
        if (body.containsKey("displayName")) {
            Object v = body.get("displayName");
            if (!(v instanceof String s) || s.isBlank() || s.length() > 128) {
                throw ApiException.validation("显示名不能为空且不超过 128 字");
            }
            dn = s.trim();
        }
        if (body.containsKey("status")) {
            Object v = body.get("status");
            if (!"ACTIVE".equals(v) && !"DISABLED".equals(v)) {
                throw ApiException.validation("status 只能是 ACTIVE 或 DISABLED");
            }
            status = (String) v;
        }
        if (body.containsKey("orgRole")) {
            Object v = body.get("orgRole");
            if (!"ADMIN".equals(v) && !"MEMBER".equals(v)) {
                throw ApiException.validation("orgRole 只能是 ADMIN 或 MEMBER");
            }
            role = (String) v;
        }
        if (body.containsKey("password")) {
            Object v = body.get("password");
            passwords.validate(v instanceof String s ? s : null);
            hash = passwords.hash((String) v);
        }
        boolean wasActiveAdmin = "ACTIVE".equals(u.status()) && "ADMIN".equals(u.orgRole());
        boolean stillActiveAdmin = "ACTIVE".equals(status) && "ADMIN".equals(role);
        if (wasActiveAdmin && !stillActiveAdmin
                && db.count("SELECT COUNT(*) FROM csap_team_user WHERE status = 'ACTIVE' AND org_role = 'ADMIN'") <= 1) {
            throw ApiException.validation("不能停用或降级最后一个管理员");
        }
        if ("DISABLED".equals(u.status()) && "ACTIVE".equals(status)) {
            license.checkCanAddActive();
        }
        db.update("UPDATE csap_team_user SET display_name = ?, status = ?, org_role = ?, password_hash = ?, updated_at = ?"
                + " WHERE id = ?", dn, status, role, hash, System.currentTimeMillis(), id);
        return byId(id);
    }

    public void setPassword(long id, String newHash) {
        db.update("UPDATE csap_team_user SET password_hash = ?, updated_at = ? WHERE id = ?", newHash, System.currentTimeMillis(), id);
    }
}
