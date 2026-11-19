package ai.csap.apidoc.team.rule;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import ai.csap.apidoc.team.common.UnifiedDiff;
import ai.csap.apidoc.team.db.Db;

/** 读开源三张表:规则、历史、版本号。 */
@Service
public class RuleReader {

    private final Db db;

    public RuleReader(Db db) {
        this.db = db;
    }

    public record Current(String rules, long version, String updatedBy, long updatedAt) {
    }

    public record HistoryRow(long id, String service, String ruleKey, String before, String after, long version, String updatedBy,
                             long updatedAt) {
    }

    /** 服务的全局版本号,没有记录为 0。 */
    public long serviceVersion(String service) {
        Long v = db.one("SELECT version FROM " + db.metaTable() + " WHERE service = ?", (rs, i) -> rs.getLong(1), service);
        return v == null ? 0 : v;
    }

    public Map<String, Object> items(String service) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("service", service);
        out.put("version", serviceVersion(service));
        out.put("items", db.query("SELECT rule_key, version, updated_by, updated_at FROM " + db.ruleTable()
                + " WHERE service = ? ORDER BY rule_key", (rs, i) -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("ruleKey", rs.getString(1));
                    m.put("version", rs.getLong(2));
                    m.put("updatedBy", rs.getString(3));
                    m.put("updatedAt", rs.getLong(4));
                    return m;
                }, service));
        return out;
    }

    /** 含规则内容的列表(开放接口用)。 */
    public Map<String, Object> itemsWithRules(String service) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("service", service);
        out.put("version", serviceVersion(service));
        out.put("rules", db.query("SELECT rule_key, rules, version, updated_by, updated_at FROM " + db.ruleTable()
                + " WHERE service = ? ORDER BY rule_key", (rs, i) -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("ruleKey", rs.getString(1));
                    m.put("rules", rs.getString(2));
                    m.put("version", rs.getLong(3));
                    m.put("updatedBy", rs.getString(4));
                    m.put("updatedAt", rs.getLong(5));
                    return m;
                }, service));
        return out;
    }

    /** 当前规则,不存在返回 null。 */
    public Current current(String service, String ruleKey) {
        return db.one("SELECT rules, version, updated_by, updated_at FROM " + db.ruleTable() + " WHERE service = ? AND rule_key = ?",
                (rs, i) -> new Current(rs.getString(1), rs.getLong(2), rs.getString(3), rs.getLong(4)), service, ruleKey);
    }

    public long currentVersion(String service, String ruleKey) {
        Current c = current(service, ruleKey);
        return c == null ? 0 : c.version();
    }

    public HistoryRow historyById(String service, long id) {
        return db.one("SELECT id, service, rule_key, before_rules, after_rules, version, updated_by, updated_at FROM "
                + db.historyTable() + " WHERE id = ? AND service = ?", RuleReader::mapHistory, id, service);
    }

    static HistoryRow mapHistory(java.sql.ResultSet rs, int i) throws java.sql.SQLException {
        return new HistoryRow(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5), rs.getLong(6),
                rs.getString(7), rs.getLong(8));
    }

    /** 历史,最新在前;key 为空表示该服务全部规则。 */
    public List<Map<String, Object>> history(String service, String key, int limit) {
        boolean all = key == null || key.isEmpty();
        String sql = "SELECT id, service, rule_key, before_rules, after_rules, version, updated_by, updated_at FROM "
                + db.historyTable() + " WHERE service = ?" + (all ? "" : " AND rule_key = ?") + " ORDER BY id DESC";
        List<HistoryRow> rows = all ? db.page(sql, RuleReader::mapHistory, 0, limit, service)
                : db.page(sql, RuleReader::mapHistory, 0, limit, service, key);
        Map<Long, Long> changeIds = changeIdsOf(rows);
        List<Map<String, Object>> out = new ArrayList<>();
        for (HistoryRow r : rows) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", r.id());
            m.put("ruleKey", r.ruleKey());
            m.put("version", r.version());
            m.put("updatedBy", r.updatedBy());
            m.put("updatedAt", r.updatedAt());
            m.put("beforeRules", r.before());
            m.put("afterRules", r.after());
            m.put("changeId", changeIds.get(r.id()));
            m.put("diff", UnifiedDiff.ofRules(r.before(), r.after(), "before", "after"));
            out.add(m);
        }
        return out;
    }

    private Map<Long, Long> changeIdsOf(List<HistoryRow> rows) {
        Map<Long, Long> out = new java.util.HashMap<>();
        if (rows.isEmpty()) {
            return out;
        }
        StringBuilder in = new StringBuilder();
        List<Object> args = new ArrayList<>();
        for (HistoryRow r : rows) {
            in.append(args.isEmpty() ? "?" : ", ?");
            args.add(r.id());
        }
        db.jdbc().query("SELECT published_history_id, change_id FROM csap_team_change_item WHERE published_history_id IN (" + in + ")",
                rs -> {
                    out.put(rs.getLong(1), rs.getLong(2));
                }, args.toArray());
        return out;
    }
}
