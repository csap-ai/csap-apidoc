package ai.csap.apidoc.team.token;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import ai.csap.apidoc.team.auth.TokenPrincipal;
import ai.csap.apidoc.team.common.ApiException;
import ai.csap.apidoc.team.db.Db;

/** 开放接口令牌:明文只在创建时返回一次,库里只存 SHA-256。 */
@Service
public class TokenService {

    private static final String BASE62 = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final Db db;

    public TokenService(Db db) {
        this.db = db;
    }

    public static String sha256(String s) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public static String generate() {
        StringBuilder sb = new StringBuilder("csapt_");
        for (int i = 0; i < 40; i++) {
            sb.append(BASE62.charAt(RANDOM.nextInt(BASE62.length())));
        }
        return sb.toString();
    }

    /** @return 含一次性明文 token 的 Map */
    public Map<String, Object> create(String name, String scope, Long projectId, long createdBy) {
        if (name == null || name.isBlank() || name.length() > 128) {
            throw ApiException.validation("令牌名称不能为空且不超过 128 字");
        }
        if (!"REGISTER".equals(scope) && !"OPEN_API".equals(scope)) {
            throw ApiException.validation("scope 只能是 REGISTER 或 OPEN_API");
        }
        if (projectId != null && db.count("SELECT COUNT(*) FROM csap_team_project WHERE id = ?", projectId) == 0) {
            throw ApiException.validation("项目不存在");
        }
        String raw = generate();
        long now = System.currentTimeMillis();
        long id = db.insert("INSERT INTO csap_team_token (name, token_hash, prefix, scope, project_id, created_by, created_at,"
                + " revoked) VALUES (?, ?, ?, ?, ?, ?, ?, 0)", name.trim(), sha256(raw), raw.substring(0, 8), scope, projectId,
                createdBy, now);
        Map<String, Object> m = get(id);
        m.put("token", raw);
        return m;
    }

    private static Map<String, Object> map(java.sql.ResultSet rs, int i) throws java.sql.SQLException {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", rs.getLong("id"));
        m.put("name", rs.getString("name"));
        m.put("prefix", rs.getString("prefix"));
        m.put("scope", rs.getString("scope"));
        long pid = rs.getLong("project_id");
        m.put("projectId", rs.wasNull() ? null : pid);
        m.put("createdBy", rs.getLong("created_by"));
        m.put("createdAt", rs.getLong("created_at"));
        long used = rs.getLong("last_used_at");
        m.put("lastUsedAt", rs.wasNull() ? null : used);
        m.put("revoked", rs.getInt("revoked") == 1);
        return m;
    }

    private static final String COLS = "id, name, prefix, scope, project_id, created_by, created_at, last_used_at, revoked";

    public Map<String, Object> get(long id) {
        return db.one("SELECT " + COLS + " FROM csap_team_token WHERE id = ?", TokenService::map, id);
    }

    public List<Map<String, Object>> list() {
        return db.query("SELECT " + COLS + " FROM csap_team_token ORDER BY id DESC", TokenService::map);
    }

    public Map<String, Object> revoke(long id) {
        if (db.update("UPDATE csap_team_token SET revoked = 1 WHERE id = ?", id) == 0) {
            throw ApiException.notFound("令牌不存在");
        }
        return get(id);
    }

    /** 校验明文令牌;无效或已撤销返回 null。 */
    public TokenPrincipal authenticate(String raw) {
        if (raw == null || raw.length() < 20 || raw.length() > 128) {
            return null;
        }
        String hash = sha256(raw);
        Object[] row = db.one("SELECT id, name, scope, project_id, created_by, revoked, last_used_at FROM csap_team_token"
                + " WHERE token_hash = ?", (rs, i) -> {
                    long pid = rs.getLong(4);
                    Long project = rs.wasNull() ? null : pid;
                    long used = rs.getLong(7);
                    return new Object[] {rs.getLong(1), rs.getString(2), rs.getString(3), project, rs.getLong(5), rs.getInt(6),
                            rs.wasNull() ? null : used};
                }, hash);
        if (row == null || (int) row[5] == 1) {
            return null;
        }
        long now = System.currentTimeMillis();
        Long used = (Long) row[6];
        if (used == null || now - used > 60_000) {
            db.update("UPDATE csap_team_token SET last_used_at = ? WHERE id = ?", now, row[0]);
        }
        return new TokenPrincipal((long) row[0], (String) row[1], (String) row[2], (Long) row[3], (long) row[4]);
    }
}
