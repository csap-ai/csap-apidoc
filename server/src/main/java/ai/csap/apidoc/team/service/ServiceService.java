package ai.csap.apidoc.team.service;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import ai.csap.apidoc.team.audit.AuditService;
import ai.csap.apidoc.team.auth.TokenPrincipal;
import ai.csap.apidoc.team.common.ApiException;
import ai.csap.apidoc.team.config.TeamProperties;
import ai.csap.apidoc.team.db.Db;

/** 服务目录与实例注册。 */
@Service
public class ServiceService {

    public static final String DEFAULT_DOC_PATH = "/csap/apidoc";
    private static final Pattern NAME = Pattern.compile("[A-Za-z0-9._-]{1,128}");

    private final Db db;
    private final TeamProperties props;
    private final AuditService audit;

    public ServiceService(Db db, TeamProperties props, AuditService audit) {
        this.db = db;
        this.props = props;
        this.audit = audit;
    }

    public record Row(String service, Long projectId, String baseUrl, String docPath, String language, String frameworkVersion,
                      String source, Long lastSeenAt, long createdAt) {
    }

    private static Row map(java.sql.ResultSet rs, int i) throws java.sql.SQLException {
        long pid = rs.getLong("project_id");
        Long project = rs.wasNull() ? null : pid;
        long seen = rs.getLong("last_seen_at");
        Long lastSeen = rs.wasNull() ? null : seen;
        return new Row(rs.getString("service"), project, rs.getString("base_url"), rs.getString("doc_path"),
                rs.getString("language"), rs.getString("framework_version"), rs.getString("source"), lastSeen,
                rs.getLong("created_at"));
    }

    private static final String COLS = "service, project_id, base_url, doc_path, language, framework_version, source,"
            + " last_seen_at, created_at";

    public Row get(String service) {
        return db.one("SELECT " + COLS + " FROM csap_team_project_service WHERE service = ?", ServiceService::map, service);
    }

    public List<Row> all() {
        return db.query("SELECT " + COLS + " FROM csap_team_project_service ORDER BY service", ServiceService::map);
    }

    public static String checkName(String service) {
        if (service == null || !NAME.matcher(service.trim()).matches()) {
            throw ApiException.validation("service 只能包含字母、数字和 . _ -,长度 1–128");
        }
        return service.trim();
    }

    public static String checkBaseUrl(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank() || baseUrl.length() > 512) {
            throw ApiException.validation("baseUrl 不能为空且不超过 512 字");
        }
        String u = baseUrl.trim().replaceAll("/+$", "");
        try {
            java.net.URI uri = java.net.URI.create(u);
            if (uri.getHost() == null || !("http".equals(uri.getScheme()) || "https".equals(uri.getScheme()))) {
                throw new IllegalArgumentException();
            }
        } catch (IllegalArgumentException e) {
            throw ApiException.validation("baseUrl 必须是 http 或 https 地址");
        }
        return u;
    }

    public static String checkDocPath(String docPath) {
        if (docPath == null || docPath.isBlank()) {
            return DEFAULT_DOC_PATH;
        }
        String p = docPath.trim();
        if (!p.startsWith("/") || p.length() > 256 || p.contains("..") || p.contains("://")) {
            throw ApiException.validation("docPath 必须以 / 开头");
        }
        return p;
    }

    /** 手工添加。 */
    public Row addManual(String service, String baseUrl, String docPath, Long projectId) {
        String name = checkName(service);
        String url = checkBaseUrl(baseUrl);
        String path = checkDocPath(docPath);
        if (projectId != null && db.count("SELECT COUNT(*) FROM csap_team_project WHERE id = ?", projectId) == 0) {
            throw ApiException.validation("项目不存在");
        }
        try {
            db.update("INSERT INTO csap_team_project_service (service, project_id, base_url, doc_path, source, created_at)"
                    + " VALUES (?, ?, ?, ?, 'MANUAL', ?)", name, projectId, url, path, System.currentTimeMillis());
        } catch (DuplicateKeyException e) {
            throw ApiException.conflict("服务已存在");
        }
        return get(name);
    }

    public long onlineWindowMs() {
        return 3L * props.getHeartbeatSeconds() * 1000L;
    }

    public int onlineInstances(String service, long now) {
        return (int) db.count("SELECT COUNT(*) FROM csap_team_service_instance WHERE service = ? AND last_seen_at >= ?", service,
                now - onlineWindowMs());
    }

    /** 最近上报的在线实例地址,没有返回 null。 */
    public String onlineInstanceUrl(String service, long now) {
        return db.one("SELECT base_url FROM csap_team_service_instance WHERE service = ? AND last_seen_at >= ?"
                + " ORDER BY last_seen_at DESC", (rs, i) -> rs.getString(1), service, now - onlineWindowMs());
    }

    /** 注册 / 心跳(同一请求重复发送)。 */
    public void register(TokenPrincipal token, Map<String, Object> body) {
        String service = checkName(str(body, "service"));
        String instanceId = str(body, "instanceId");
        if (instanceId == null || instanceId.isBlank() || instanceId.length() > 128) {
            throw ApiException.validation("instanceId 不能为空且不超过 128 字");
        }
        String baseUrl = checkBaseUrl(str(body, "baseUrl"));
        String docPath = checkDocPath(str(body, "docPath"));
        String language = str(body, "language");
        String version = str(body, "frameworkVersion");
        if (language == null || language.isBlank() || language.length() > 32 || version == null || version.isBlank()
                || version.length() > 64) {
            throw ApiException.validation("language、frameworkVersion 必填");
        }
        long now = System.currentTimeMillis();
        boolean created = db.inTx(() -> {
            Row existing = get(service);
            if (existing != null && token.projectId() != null && existing.projectId() != null
                    && !existing.projectId().equals(token.projectId())) {
                throw ApiException.forbidden("令牌绑定的项目与该服务所属项目不一致");
            }
            if (existing == null) {
                try {
                    db.update("INSERT INTO csap_team_project_service (service, project_id, base_url, doc_path, language,"
                            + " framework_version, source, last_seen_at, created_at) VALUES (?, ?, ?, ?, ?, ?, 'REGISTERED', ?, ?)",
                            service, token.projectId(), baseUrl, docPath, language, version, now, now);
                } catch (DuplicateKeyException e) {
                    // 并发注册,按更新处理
                    updateService(service, baseUrl, docPath, language, version, now, token.projectId());
                    existing = get(service);
                }
            } else {
                updateService(service, baseUrl, docPath, language, version, now, token.projectId());
            }
            upsertInstance(service, instanceId, baseUrl, now);
            return existing == null;
        });
        if (created) {
            audit.log("service.register").project(token.projectId()).service(service)
                    .reason(language + " " + version + " " + instanceId).save();
        }
    }

    private void updateService(String service, String baseUrl, String docPath, String language, String version, long now,
                               Long tokenProject) {
        db.update("UPDATE csap_team_project_service SET base_url = ?, doc_path = ?, language = ?, framework_version = ?,"
                + " source = 'REGISTERED', last_seen_at = ? WHERE service = ?", baseUrl, docPath, language, version, now, service);
        if (tokenProject != null) {
            db.update("UPDATE csap_team_project_service SET project_id = ? WHERE service = ? AND project_id IS NULL", tokenProject, service);
        }
    }

    private void upsertInstance(String service, String instanceId, String baseUrl, long now) {
        if (db.update("UPDATE csap_team_service_instance SET base_url = ?, last_seen_at = ? WHERE service = ? AND instance_id = ?",
                baseUrl, now, service, instanceId) == 0) {
            try {
                db.update("INSERT INTO csap_team_service_instance (service, instance_id, base_url, last_seen_at) VALUES (?, ?, ?, ?)",
                        service, instanceId, baseUrl, now);
            } catch (DuplicateKeyException e) {
                db.update("UPDATE csap_team_service_instance SET base_url = ?, last_seen_at = ? WHERE service = ? AND instance_id = ?",
                        baseUrl, now, service, instanceId);
            }
        }
    }

    public void deregister(TokenPrincipal token, Map<String, Object> body) {
        String service = checkName(str(body, "service"));
        String instanceId = str(body, "instanceId");
        if (instanceId == null || instanceId.isBlank()) {
            throw ApiException.validation("instanceId 不能为空");
        }
        Row r = get(service);
        if (r != null && token.projectId() != null && r.projectId() != null && !r.projectId().equals(token.projectId())) {
            throw ApiException.forbidden("令牌绑定的项目与该服务所属项目不一致");
        }
        db.update("DELETE FROM csap_team_service_instance WHERE service = ? AND instance_id = ?", service, instanceId);
    }

    public void delete(String service) {
        db.inTx(() -> {
            db.update("DELETE FROM csap_team_service_instance WHERE service = ?", service);
            db.update("DELETE FROM csap_team_project_service WHERE service = ?", service);
        });
    }

    static String str(Map<String, Object> m, String k) {
        return m.get(k) instanceof String s ? s : null;
    }
}
