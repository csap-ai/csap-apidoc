package ai.csap.apidoc.team.rule;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import ai.csap.apidoc.strategy.store.RuleJson;
import ai.csap.apidoc.team.db.Db;

/**
 * 发布:对每条规则做比较并交换,写入结果与开源 {@code DatabaseRuleStore.save} 一致
 * (同一事务内:meta.version 加 1、写 rule、写 history,updated_by 为团队版用户名)。
 *
 * <p>并发安全:先对涉及服务的 {prefix}meta 行 {@code SELECT ... FOR UPDATE}(与开源 save 先 UPDATE meta 行锁的顺序一致,
 * 服务侧的保存会被串行化),再 {@code SELECT ... FOR UPDATE} 读取规则行比较版本。必须在调用方的事务里调用。
 */
@Service
public class RulePublisher {

    private final Db db;

    public RulePublisher(Db db) {
        this.db = db;
    }

    /** 待发布的一条规则。afterRules 为 null 表示删除。 */
    public record Pub(long itemId, String service, String ruleKey, long baseVersion, String afterRules) {
    }

    public record Done(long itemId, String service, String ruleKey, String before, String after, Long publishedVersion,
                       Long historyId) {
    }

    public record Conflict(String service, String ruleKey, long baseVersion, long currentVersion, String currentRules) {
    }

    public record Result(List<Done> done, List<Conflict> conflicts) {
    }

    /** 检查是否过期(不加锁,提交时的预检)。内容与目标相同的条目不算冲突。 */
    public List<Conflict> check(List<Pub> items) {
        List<Conflict> out = new ArrayList<>();
        for (Pub p : items) {
            RuleReader.Current c = current(p.service(), p.ruleKey(), false);
            long v = c == null ? 0 : c.version();
            String rules = c == null ? null : c.rules();
            if (v != p.baseVersion() && !RuleJson.sameJson(rules, p.afterRules())) {
                out.add(new Conflict(p.service(), p.ruleKey(), p.baseVersion(), v, rules));
            }
        }
        return out;
    }

    /**
     * 确保这些服务在 {prefix}meta 里有版本号行(与开源 ensureMetaRow 一致,version=0)。
     * 必须在发布事务<b>之外</b>调用:并发插入同一行时,PostgreSQL 的唯一键冲突会让所在事务进入中止状态,
     * 放在自动提交里就只是一条失败的语句。
     */
    public void ensureMeta(java.util.Collection<String> services) {
        for (String service : new TreeSet<>(services)) {
            Long v = db.one("SELECT version FROM " + db.metaTable() + " WHERE service = ?", (rs, i) -> rs.getLong(1), service);
            if (v == null) {
                try {
                    db.update("INSERT INTO " + db.metaTable() + " (service, version) VALUES (?, ?)", service, 0L);
                } catch (DuplicateKeyException e) {
                    // 别的请求刚插入
                }
            }
        }
    }

    public Result publish(List<Pub> items, String updatedBy) {
        String by = updatedBy == null || updatedBy.isEmpty() ? "unknown" : updatedBy;
        // 固定加锁顺序,避免两个发布互相等待
        for (String service : new TreeSet<>(items.stream().map(Pub::service).toList())) {
            lockMeta(service);
        }
        List<Conflict> conflicts = new ArrayList<>();
        List<RuleReader.Current> currents = new ArrayList<>();
        for (Pub p : items) {
            RuleReader.Current c = current(p.service(), p.ruleKey(), true);
            currents.add(c);
            long v = c == null ? 0 : c.version();
            String rules = c == null ? null : c.rules();
            if (v != p.baseVersion() && !RuleJson.sameJson(rules, p.afterRules())) {
                conflicts.add(new Conflict(p.service(), p.ruleKey(), p.baseVersion(), v, rules));
            }
        }
        if (!conflicts.isEmpty()) {
            return new Result(List.of(), conflicts);
        }
        List<Done> done = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            Pub p = items.get(i);
            RuleReader.Current c = currents.get(i);
            String before = c == null ? null : c.rules();
            if (RuleJson.sameJson(before, p.afterRules())) {
                done.add(new Done(p.itemId(), p.service(), p.ruleKey(), before, p.afterRules(), null, null));
                continue;
            }
            long v = bump(p.service());
            long now = System.currentTimeMillis();
            if (p.afterRules() == null) {
                db.update("DELETE FROM " + db.ruleTable() + " WHERE service = ? AND rule_key = ?", p.service(), p.ruleKey());
            } else if (c != null) {
                db.update("UPDATE " + db.ruleTable() + " SET rules = ?, version = ?, updated_by = ?, updated_at = ?"
                        + " WHERE service = ? AND rule_key = ?", p.afterRules(), v, by, now, p.service(), p.ruleKey());
            } else {
                db.update("INSERT INTO " + db.ruleTable() + " (service, rule_key, rules, version, updated_by, updated_at)"
                        + " VALUES (?, ?, ?, ?, ?, ?)", p.service(), p.ruleKey(), p.afterRules(), v, by, now);
            }
            long historyId = db.insert("INSERT INTO " + db.historyTable() + " (service, rule_key, before_rules, after_rules,"
                    + " version, updated_by, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?)", p.service(), p.ruleKey(), before,
                    p.afterRules(), v, by, now);
            done.add(new Done(p.itemId(), p.service(), p.ruleKey(), before, p.afterRules(), v, historyId));
        }
        return new Result(done, List.of());
    }

    private RuleReader.Current current(String service, String ruleKey, boolean forUpdate) {
        return db.one("SELECT rules, version, updated_by, updated_at FROM " + db.ruleTable() + " WHERE service = ? AND rule_key = ?"
                + (forUpdate ? " FOR UPDATE" : ""),
                (rs, i) -> new RuleReader.Current(rs.getString(1), rs.getLong(2), rs.getString(3), rs.getLong(4)), service, ruleKey);
    }

    /** 锁住服务的 meta 行,没有就先插入 version=0 的行(与开源 ensureMetaRow 一致)。 */
    private void lockMeta(String service) {
        String sql = "SELECT version FROM " + db.metaTable() + " WHERE service = ? FOR UPDATE";
        Long v = db.one(sql, (rs, i) -> rs.getLong(1), service);
        if (v == null) {
            try {
                db.update("INSERT INTO " + db.metaTable() + " (service, version) VALUES (?, ?)", service, 0L);
            } catch (DuplicateKeyException e) {
                // 别的事务刚插入
            }
            v = db.one(sql, (rs, i) -> rs.getLong(1), service);
            if (v == null) {
                throw new IllegalStateException("版本号行丢失: " + db.metaTable() + " service=" + service);
            }
        }
    }

    /** meta.version 加 1,返回新版本号。行已被 lockMeta 锁住。 */
    private long bump(String service) {
        db.update("UPDATE " + db.metaTable() + " SET version = version + 1 WHERE service = ?", service);
        Long v = db.one("SELECT version FROM " + db.metaTable() + " WHERE service = ?", (rs, i) -> rs.getLong(1), service);
        if (v == null) {
            throw new IllegalStateException("版本号行丢失: " + db.metaTable() + " service=" + service);
        }
        return v;
    }
}
