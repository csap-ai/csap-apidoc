package ai.csap.apidoc.team.audit;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

import ai.csap.apidoc.team.auth.Actor;
import ai.csap.apidoc.team.auth.RequestContext;
import ai.csap.apidoc.team.db.Db;

/** 审计:只追加;唯一的 DELETE 在 {@link AuditRetention}。 */
@Service
public class AuditService {

    static final String COLS = "id, at, actor_type, actor_id, actor_name, source, ip, action, project_id, env_key, service, rule_key,"
            + " change_id, before_value, after_value, diff, reason, oss_history_id, prev_hash, hash";

    private final Db db;
    private final AuditChain chain;

    public AuditService(Db db, AuditChain chain) {
        this.db = db;
        this.chain = chain;
    }

    /** 一条审计的构造器,{@link #save()} 落库。 */
    public final class Entry {
        private final Actor actor;
        private final String action;
        private Long projectId;
        private String envKey;
        private String service;
        private String ruleKey;
        private Long changeId;
        private String before;
        private String after;
        private String diff;
        private String reason;
        private Long ossHistoryId;
        private Long at;

        private Entry(Actor actor, String action) {
            this.actor = actor;
            this.action = action;
        }

        public Entry project(Long id) { this.projectId = id; return this; }
        public Entry env(String key) { this.envKey = key; return this; }
        public Entry service(String s) { this.service = s; return this; }
        public Entry rule(String key) { this.ruleKey = key; return this; }
        public Entry change(Long id) { this.changeId = id; return this; }
        public Entry before(String v) { this.before = v; return this; }
        public Entry after(String v) { this.after = v; return this; }
        public Entry diff(String v) { this.diff = v; return this; }
        public Entry reason(String v) { this.reason = v == null || v.length() <= 1000 ? v : v.substring(0, 1000); return this; }
        public Entry ossHistory(Long id) { this.ossHistoryId = id; return this; }
        public Entry at(long ms) { this.at = ms; return this; }

        /**
         * 落库并接入哈希链:在同一个主库事务里锁住链头 → 插入(带 prev_hash)→ 算 hash 补写 → 更新链头。
         * 审计写入失败会让所在业务操作失败。
         */
        public long save() {
            long now = at != null ? at : System.currentTimeMillis();
            String b = blankToNull(before);
            String a = blankToNull(after);
            String d = blankToNull(diff);
            String r = blankToNull(reason);
            return db.inTx(() -> {
                AuditChain.Point head = chain.lockHead();
                long id = db.insert("INSERT INTO csap_team_audit (at, actor_type, actor_id, actor_name, source, ip, action, project_id,"
                                + " env_key, service, rule_key, change_id, before_value, after_value, diff, reason, oss_history_id,"
                                + " prev_hash) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                        now, actor.type(), actor.id(), actor.name(), actor.source(), actor.ip(), action, projectId, envKey, service,
                        ruleKey, changeId, b, a, d, r, ossHistoryId, head.hash());
                String hash = AuditChain.hash(head.hash(), AuditChain.values(id, now, actor.type(), actor.id(), actor.name(),
                        actor.source(), actor.ip(), action, projectId, envKey, service, ruleKey, changeId, b, a, d, r, ossHistoryId,
                        head.hash()));
                db.update("UPDATE csap_team_audit SET hash = ? WHERE id = ?", hash, id);
                chain.setHead(id, hash);
                return id;
            });
        }
    }

    /** 空串在个别库里等同 NULL,统一成 NULL,保证库里读出来的值与算 hash 时一致。 */
    private static String blankToNull(String s) {
        return s == null || s.isEmpty() ? null : s;
    }

    /** 以当前请求的身份记录;后台任务里为系统。 */
    public Entry log(String action) {
        return new Entry(RequestContext.actor(), action);
    }

    public Entry logAs(Actor actor, String action) {
        return new Entry(actor, action);
    }

    // ---- 查询 ----

    /** 查询条件;projectIds 为 null 表示不限制项目(管理员)。 */
    public static final class Filter {
        public Long from;
        public Long to;
        public String actor;
        public Long projectId;
        public String env;
        public String service;
        public String ruleKey;
        public String action;
        public String source;
        /** 可见项目;null = 全部 */
        public Set<Long> visibleProjects;
        /** 限制只读这些项目里的服务(用于 MCP 审计) */
        public boolean empty() {
            return visibleProjects != null && visibleProjects.isEmpty();
        }
    }

    private static String inList(Set<Long> ids, List<Object> args) {
        StringBuilder sb = new StringBuilder("(");
        boolean first = true;
        for (Long id : ids) {
            sb.append(first ? "?" : ", ?");
            args.add(id);
            first = false;
        }
        return sb.append(')').toString();
    }

    private String where(Filter f, List<Object> args) {
        StringBuilder w = new StringBuilder(" WHERE 1 = 1");
        if (f.from != null) { w.append(" AND at >= ?"); args.add(f.from); }
        if (f.to != null) { w.append(" AND at <= ?"); args.add(f.to); }
        if (notBlank(f.actor)) { w.append(" AND actor_name = ?"); args.add(f.actor); }
        if (f.projectId != null) { w.append(" AND project_id = ?"); args.add(f.projectId); }
        if (notBlank(f.env)) { w.append(" AND env_key = ?"); args.add(f.env); }
        if (notBlank(f.service)) { w.append(" AND service = ?"); args.add(f.service); }
        if (notBlank(f.ruleKey)) { w.append(" AND rule_key = ?"); args.add(f.ruleKey); }
        if (notBlank(f.action)) {
            if (f.action.endsWith("*")) {
                w.append(" AND action LIKE ?");
                args.add(f.action.substring(0, f.action.length() - 1).replace("%", "").replace("_", "") + "%");
            } else {
                w.append(" AND action = ?");
                args.add(f.action);
            }
        }
        if (notBlank(f.source)) { w.append(" AND source = ?"); args.add(f.source); }
        if (f.visibleProjects != null) {
            w.append(" AND project_id IN ").append(inList(f.visibleProjects, args));
        }
        return w.toString();
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    private static final RowMapper<Map<String, Object>> ROW = (rs, i) -> {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", rs.getLong("id"));
        m.put("at", rs.getLong("at"));
        m.put("actorType", rs.getString("actor_type"));
        m.put("actorId", nl(rs, "actor_id"));
        m.put("actorName", rs.getString("actor_name"));
        m.put("source", rs.getString("source"));
        m.put("ip", rs.getString("ip"));
        m.put("action", rs.getString("action"));
        m.put("projectId", nl(rs, "project_id"));
        m.put("env", rs.getString("env_key"));
        m.put("service", rs.getString("service"));
        m.put("ruleKey", rs.getString("rule_key"));
        m.put("changeId", nl(rs, "change_id"));
        m.put("beforeValue", rs.getString("before_value"));
        m.put("afterValue", rs.getString("after_value"));
        m.put("diff", rs.getString("diff"));
        m.put("reason", rs.getString("reason"));
        m.put("ossHistoryId", nl(rs, "oss_history_id"));
        m.put("prevHash", rs.getString("prev_hash"));
        m.put("hash", rs.getString("hash"));
        return m;
    };

    static Long nl(java.sql.ResultSet rs, String col) throws java.sql.SQLException {
        long v = rs.getLong(col);
        return rs.wasNull() ? null : v;
    }

    public Map<String, Object> get(long id) {
        return db.one("SELECT " + COLS + " FROM csap_team_audit WHERE id = ?", ROW, id);
    }

    public Map<String, Object> list(Filter f, int page, int size) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (f.empty()) {
            out.put("items", List.of());
            out.put("total", 0);
            return out;
        }
        List<Object> args = new ArrayList<>();
        String w = where(f, args);
        long total = db.count("SELECT COUNT(*) FROM csap_team_audit" + w, args.toArray());
        List<Map<String, Object>> items = db.page("SELECT " + COLS + " FROM csap_team_audit" + w + " ORDER BY id DESC", ROW,
                (page - 1) * size, size, args.toArray());
        out.put("items", items);
        out.put("total", total);
        return out;
    }

    /** 按 id 倒序分批(keyset)读取,最多 max 条,逐条回调,用于流式导出。 */
    public int stream(Filter f, int max, Consumer<Map<String, Object>> sink) {
        if (f.empty()) {
            return 0;
        }
        int sent = 0;
        long lastId = Long.MAX_VALUE;
        while (sent < max) {
            List<Object> args = new ArrayList<>();
            String w = where(f, args) + " AND id < ?";
            args.add(lastId);
            int batch = Math.min(1000, max - sent);
            List<Map<String, Object>> rows = db.page("SELECT " + COLS + " FROM csap_team_audit" + w + " ORDER BY id DESC", ROW,
                    0, batch, args.toArray());
            if (rows.isEmpty()) {
                break;
            }
            for (Map<String, Object> r : rows) {
                sink.accept(r);
                lastId = (Long) r.get("id");
            }
            sent += rows.size();
            if (rows.size() < batch) {
                break;
            }
        }
        return sent;
    }
}
