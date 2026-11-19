package ai.csap.apidoc.team.audit;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

import ai.csap.apidoc.team.db.Db;

/** 读开源 Java 写入的 {prefix}mcp_audit(团队版不复制数据);表不存在时 available=false。 */
@Service
public class McpAuditService {

    private static final long CACHE_MS = 15_000;
    private static final String COLS = "id, service, at, caller, remote_address, tool, target, arguments, status, success, result,"
            + " duration_ms";

    private final Db db;
    private volatile long checkedAt;
    private volatile boolean available;

    public McpAuditService(Db db) {
        this.db = db;
    }

    /** 清除"表是否存在"的缓存(仅供测试)。 */
    public void resetCache() {
        checkedAt = 0;
    }

    public boolean available() {
        long now = System.currentTimeMillis();
        if (now - checkedAt > CACHE_MS) {
            available = db.tableExists(db.mcpAuditTable());
            checkedAt = now;
        }
        return available;
    }

    public static final class Filter {
        public Long from;
        public Long to;
        public String service;
        public String caller;
        public String tool;
        /** 可见服务;null = 不限(管理员) */
        public Set<String> visibleServices;
    }

    private static final RowMapper<Map<String, Object>> ROW = (rs, i) -> {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", rs.getLong("id"));
        m.put("service", rs.getString("service"));
        m.put("at", rs.getLong("at"));
        m.put("caller", rs.getString("caller"));
        m.put("remoteAddress", rs.getString("remote_address"));
        m.put("tool", rs.getString("tool"));
        m.put("target", rs.getString("target"));
        m.put("arguments", rs.getString("arguments"));
        m.put("status", rs.getInt("status"));
        m.put("success", rs.getInt("success") == 1);
        m.put("result", rs.getString("result"));
        m.put("durationMs", rs.getLong("duration_ms"));
        return m;
    };

    private String where(Filter f, List<Object> args) {
        StringBuilder w = new StringBuilder(" WHERE 1 = 1");
        if (f.from != null) { w.append(" AND at >= ?"); args.add(f.from); }
        if (f.to != null) { w.append(" AND at <= ?"); args.add(f.to); }
        if (f.service != null && !f.service.isBlank()) { w.append(" AND service = ?"); args.add(f.service); }
        if (f.caller != null && !f.caller.isBlank()) { w.append(" AND caller = ?"); args.add(f.caller); }
        if (f.tool != null && !f.tool.isBlank()) { w.append(" AND tool = ?"); args.add(f.tool); }
        if (f.visibleServices != null) {
            w.append(" AND service IN (");
            boolean first = true;
            for (String s : f.visibleServices) {
                w.append(first ? "?" : ", ?");
                args.add(s);
                first = false;
            }
            w.append(')');
        }
        return w.toString();
    }

    public Map<String, Object> list(Filter f, int page, int size) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (!available()) {
            out.put("available", false);
            out.put("items", List.of());
            out.put("total", 0);
            return out;
        }
        out.put("available", true);
        if (f.visibleServices != null && f.visibleServices.isEmpty()) {
            out.put("items", List.of());
            out.put("total", 0);
            return out;
        }
        List<Object> args = new ArrayList<>();
        String w = where(f, args);
        out.put("total", db.count("SELECT COUNT(*) FROM " + db.mcpAuditTable() + w, args.toArray()));
        out.put("items", db.page("SELECT " + COLS + " FROM " + db.mcpAuditTable() + w + " ORDER BY id DESC", ROW,
                (page - 1) * size, size, args.toArray()));
        return out;
    }

    public int stream(Filter f, int max, Consumer<Map<String, Object>> sink) {
        if (!available() || (f.visibleServices != null && f.visibleServices.isEmpty())) {
            return 0;
        }
        int sent = 0;
        long lastId = Long.MAX_VALUE;
        while (sent < max) {
            List<Object> args = new ArrayList<>();
            String w = where(f, args) + " AND id < ?";
            args.add(lastId);
            int batch = Math.min(1000, max - sent);
            List<Map<String, Object>> rows = db.page("SELECT " + COLS + " FROM " + db.mcpAuditTable() + w + " ORDER BY id DESC",
                    ROW, 0, batch, args.toArray());
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
