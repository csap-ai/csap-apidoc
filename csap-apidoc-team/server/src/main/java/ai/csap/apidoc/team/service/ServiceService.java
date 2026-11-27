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
import ai.csap.apidoc.team.env.Env;
import ai.csap.apidoc.team.env.Environments;

/** 服务目录与实例注册。 */
@Service
public class ServiceService {

    public static final String DEFAULT_DOC_PATH = "/csap/apidoc";
    private static final Pattern NAME = Pattern.compile("[A-Za-z0-9._-]{1,128}");

    private final Db db;
    private final TeamProperties props;
    private final AuditService audit;
    private final Environments environments;

    public ServiceService(Db db, TeamProperties props, AuditService audit, Environments environments) {
        this.db = db;
        this.props = props;
        this.audit = audit;
        this.environments = environments;
    }

    /** 服务本身(项目归属不分环境)。 */
    public record Row(String service, Long projectId, String language, String frameworkVersion, long createdAt) {
    }

    /** 服务在某个环境的地址。 */
    public record EnvRow(String service, String env, String baseUrl, String docPath, String source, Long lastSeenAt, long createdAt) {
    }

    private static Row map(java.sql.ResultSet rs, int i) throws java.sql.SQLException {
        long pid = rs.getLong("project_id");
        Long project = rs.wasNull() ? null : pid;
        return new Row(rs.getString("service"), project, rs.getString("language"), rs.getString("framework_version"),
                rs.getLong("created_at"));
    }

    private static EnvRow mapEnv(java.sql.ResultSet rs, int i) throws java.sql.SQLException {
        long seen = rs.getLong("last_seen_at");
        Long lastSeen = rs.wasNull() ? null : seen;
        return new EnvRow(rs.getString("service"), rs.getString("env_key"), rs.getString("base_url"), rs.getString("doc_path"),
                rs.getString("source"), lastSeen, rs.getLong("created_at"));
    }

    private static final String COLS = "service, project_id, language, framework_version, created_at";
    private static final String ENV_COLS = "service, env_key, base_url, doc_path, source, last_seen_at, created_at";

    public Row get(String service) {
        return db.one("SELECT " + COLS + " FROM csap_team_project_service WHERE service = ?", ServiceService::map, service);
    }

    public List<Row> all() {
        return db.query("SELECT " + COLS + " FROM csap_team_project_service ORDER BY service", ServiceService::map);
    }

    public EnvRow envRow(String service, String env) {
        return db.one("SELECT " + ENV_COLS + " FROM csap_team_service_env WHERE service = ? AND env_key = ?", ServiceService::mapEnv,
                service, env);
    }

    public List<EnvRow> envRows(String service) {
        return db.query("SELECT " + ENV_COLS + " FROM csap_team_service_env WHERE service = ? ORDER BY env_key", ServiceService::mapEnv,
                service);
    }

    public List<EnvRow> allEnvRows() {
        return db.query("SELECT " + ENV_COLS + " FROM csap_team_service_env ORDER BY service, env_key", ServiceService::mapEnv);
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

    /**
     * 手工添加:服务不存在就新建;服务已存在而该环境没有地址时,给该环境加地址;该环境已有则 CONFLICT。
     */
    public EnvRow addManual(String service, String baseUrl, String docPath, Long projectId, Env env) {
        String name = checkName(service);
        String url = checkBaseUrl(baseUrl);
        String path = checkDocPath(docPath);
        if (projectId != null && db.count("SELECT COUNT(*) FROM csap_team_project WHERE id = ?", projectId) == 0) {
            throw ApiException.validation("项目不存在");
        }
        long now = System.currentTimeMillis();
        // 不放进事务:PostgreSQL 里唯一键冲突会让所在事务中止,后面的语句全部失败;这里每条语句各自提交即可
        {
            if (get(name) == null) {
                try {
                    db.update("INSERT INTO csap_team_project_service (service, project_id, doc_path, source, created_at)"
                            + " VALUES (?, ?, ?, 'MANUAL', ?)", name, projectId, path, now);
                } catch (DuplicateKeyException e) {
                    // 并发添加,按已存在处理
                }
            }
            try {
                db.update("INSERT INTO csap_team_service_env (service, env_key, base_url, doc_path, source, created_at)"
                        + " VALUES (?, ?, ?, ?, 'MANUAL', ?)", name, env.key(), url, path, now);
            } catch (DuplicateKeyException e) {
                throw ApiException.conflict("服务在环境「" + env.name() + "」已存在");
            }
        }
        return envRow(name, env.key());
    }

    public long onlineWindowMs() {
        return 3L * props.getHeartbeatSeconds() * 1000L;
    }

    public int onlineInstances(String env, String service, long now) {
        return (int) db.count("SELECT COUNT(*) FROM csap_team_env_instance WHERE env_key = ? AND service = ? AND last_seen_at >= ?",
                env, service, now - onlineWindowMs());
    }

    /** 最近上报的在线实例地址,没有返回 null。 */
    public String onlineInstanceUrl(String env, String service, long now) {
        return db.one("SELECT base_url FROM csap_team_env_instance WHERE env_key = ? AND service = ? AND last_seen_at >= ?"
                + " ORDER BY last_seen_at DESC", (rs, i) -> rs.getString(1), env, service, now - onlineWindowMs());
    }

    /** 注册 / 心跳(同一请求重复发送)。注册令牌绑定了环境就落到该环境,否则默认环境;请求体里的 environment 忽略。 */
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
        Env env = tokenEnv(token);
        long now = System.currentTimeMillis();
        // 不放进事务:同一服务在多个环境(或多个实例)同时首次注册时会并发插入同一行,
        // PostgreSQL 里唯一键冲突会让所在事务中止;这里都是幂等的 upsert,每条语句各自提交即可
        boolean created;
        {
            Row existing = get(service);
            if (existing != null && token.projectId() != null && existing.projectId() != null
                    && !existing.projectId().equals(token.projectId())) {
                throw ApiException.forbidden("令牌绑定的项目与该服务所属项目不一致");
            }
            boolean createdService = existing == null;
            if (existing == null) {
                try {
                    db.update("INSERT INTO csap_team_project_service (service, project_id, doc_path, language,"
                            + " framework_version, source, last_seen_at, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                            service, token.projectId(), docPath, language, version, "REGISTERED", now, now);
                } catch (DuplicateKeyException e) {
                    // 并发注册,按更新处理
                    updateService(service, language, version, token.projectId());
                    createdService = false;
                }
            } else {
                updateService(service, language, version, token.projectId());
            }
            boolean createdEnv = upsertEnv(service, env.key(), baseUrl, docPath, now);
            upsertInstance(env.key(), service, instanceId, baseUrl, now);
            created = createdService || createdEnv;
        }
        if (created) {
            audit.log("service.register").project(token.projectId()).env(env.key()).service(service)
                    .reason(language + " " + version + " " + instanceId).save();
        }
    }

    private Env tokenEnv(TokenPrincipal token) {
        if (token.envKey() == null) {
            return environments.defaultEnv();
        }
        Env e = environments.find(token.envKey());
        if (e == null) {
            throw ApiException.validation("令牌绑定的环境 " + token.envKey() + " 已不在配置中");
        }
        return e;
    }

    private void updateService(String service, String language, String version, Long tokenProject) {
        db.update("UPDATE csap_team_project_service SET language = ?, framework_version = ? WHERE service = ?", language, version,
                service);
        if (tokenProject != null) {
            db.update("UPDATE csap_team_project_service SET project_id = ? WHERE service = ? AND project_id IS NULL", tokenProject, service);
        }
    }

    /** @return 是否新建了该环境的地址行 */
    private boolean upsertEnv(String service, String env, String baseUrl, String docPath, long now) {
        if (db.update("UPDATE csap_team_service_env SET base_url = ?, doc_path = ?, source = 'REGISTERED', last_seen_at = ?"
                + " WHERE service = ? AND env_key = ?", baseUrl, docPath, now, service, env) > 0) {
            return false;
        }
        try {
            db.update("INSERT INTO csap_team_service_env (service, env_key, base_url, doc_path, source, last_seen_at, created_at)"
                    + " VALUES (?, ?, ?, ?, 'REGISTERED', ?, ?)", service, env, baseUrl, docPath, now, now);
            return true;
        } catch (DuplicateKeyException e) {
            db.update("UPDATE csap_team_service_env SET base_url = ?, doc_path = ?, source = 'REGISTERED', last_seen_at = ?"
                    + " WHERE service = ? AND env_key = ?", baseUrl, docPath, now, service, env);
            return false;
        }
    }

    private void upsertInstance(String env, String service, String instanceId, String baseUrl, long now) {
        if (db.update("UPDATE csap_team_env_instance SET base_url = ?, last_seen_at = ? WHERE env_key = ? AND service = ?"
                + " AND instance_id = ?", baseUrl, now, env, service, instanceId) == 0) {
            try {
                db.update("INSERT INTO csap_team_env_instance (env_key, service, instance_id, base_url, last_seen_at)"
                        + " VALUES (?, ?, ?, ?, ?)", env, service, instanceId, baseUrl, now);
            } catch (DuplicateKeyException e) {
                db.update("UPDATE csap_team_env_instance SET base_url = ?, last_seen_at = ? WHERE env_key = ? AND service = ?"
                        + " AND instance_id = ?", baseUrl, now, env, service, instanceId);
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
        db.update("DELETE FROM csap_team_env_instance WHERE env_key = ? AND service = ? AND instance_id = ?",
                tokenEnv(token).key(), service, instanceId);
    }

    /** 改某个环境的地址 / 文档路径。 */
    public void updateEnvRow(String service, String env, String baseUrl, String docPath) {
        db.update("UPDATE csap_team_service_env SET base_url = ?, doc_path = ? WHERE service = ? AND env_key = ?", baseUrl, docPath,
                service, env);
    }

    /** 不带环境:删整个服务(1.0 行为)。 */
    public void delete(String service) {
        db.inTx(() -> {
            db.update("DELETE FROM csap_team_env_instance WHERE service = ?", service);
            db.update("DELETE FROM csap_team_service_env WHERE service = ?", service);
            db.update("DELETE FROM csap_team_project_service WHERE service = ?", service);
        });
    }

    /** 只删该环境的地址和实例。 */
    public void deleteEnv(String service, String env) {
        db.inTx(() -> {
            db.update("DELETE FROM csap_team_env_instance WHERE env_key = ? AND service = ?", env, service);
            db.update("DELETE FROM csap_team_service_env WHERE env_key = ? AND service = ?", env, service);
        });
    }

    static String str(Map<String, Object> m, String k) {
        return m.get(k) instanceof String s ? s : null;
    }
}
