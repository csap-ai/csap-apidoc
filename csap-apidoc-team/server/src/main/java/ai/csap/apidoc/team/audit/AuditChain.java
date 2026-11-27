package ai.csap.apidoc.team.audit;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;

import ai.csap.apidoc.team.common.Json;
import ai.csap.apidoc.team.db.Db;
import ai.csap.apidoc.team.db.Settings;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * 审计哈希链(契约 6):{@code hash = hex(SHA-256(prev_hash + "\n" + canonical))},canonical 是 19 个值的紧凑 JSON 数组。
 * 本类负责算法、链头(setting {@code audit.chain.head})、保留期锚点({@code audit.chain.anchor})、升级回填与校验。
 *
 * <p>全代码里允许 UPDATE {@code csap_team_audit} 的只有:{@link AuditService.Entry#save()}(插入它的同一事务里补 hash)
 * 和本类的 {@link #backfill()}(升级时一次性封存旧记录);允许 DELETE 的只有 {@link AuditRetention}。
 */
@Component
public class AuditChain {

    public static final String ZERO = "0".repeat(64);
    public static final String HEAD = "audit.chain.head";
    public static final String ANCHOR = "audit.chain.anchor";
    public static final String SEALED_ACTION = "audit.chain.sealed";

    private static final int BATCH = 1000;

    /** canonical 数组里的 19 个字段,顺序固定。 */
    static final String[] FIELDS = {"id", "at", "actor_type", "actor_id", "actor_name", "source", "ip", "action", "project_id", "env_key",
            "service", "rule_key", "change_id", "before_value", "after_value", "diff", "reason", "oss_history_id", "prev_hash"};
    private static final String SELECT = String.join(", ", FIELDS) + ", hash";

    public record Point(long id, String hash) {
    }

    public record Result(boolean ok, long checked, Long headId, String headHash, Long firstBrokenId, String problem) {
    }

    private final Db db;
    private final Settings settings;

    public AuditChain(Db db, Settings settings) {
        this.db = db;
        this.settings = settings;
    }

    // ---- 算法 ----

    /** canonical:紧凑 JSON 数组,非 ASCII 原样输出,与 Python json.dumps(ensure_ascii=False, separators=(",", ":")) 一致。 */
    public static String canonical(Object[] values) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < values.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            Object v = values[i];
            if (v == null) {
                sb.append("null");
            } else if (v instanceof Number) {
                sb.append(v);
            } else {
                quote(sb, v.toString());
            }
        }
        return sb.append(']').toString();
    }

    private static void quote(StringBuilder sb, String s) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        sb.append('"');
    }

    public static String hash(String prevHash, Object[] values) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest((prevHash + "\n" + canonical(values)).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** 按契约顺序组 19 个值(prev_hash 为最后一个)。 */
    public static Object[] values(long id, long at, String actorType, Long actorId, String actorName, String source, String ip,
                                  String action, Long projectId, String envKey, String service, String ruleKey, Long changeId,
                                  String before, String after, String diff, String reason, Long ossHistoryId, String prevHash) {
        return new Object[] {id, at, actorType, actorId, actorName, source, ip, action, projectId, envKey, service, ruleKey, changeId,
                before, after, diff, reason, ossHistoryId, prevHash};
    }

    // ---- 链头 / 锚点 ----

    private static Point parse(String json) {
        JsonNode n = json == null ? null : Json.read(json);
        if (n == null || !n.has("id") || !n.has("hash")) {
            return null;
        }
        return new Point(n.get("id").asLong(), n.get("hash").asText());
    }

    private static String write(Point p) {
        return "{\"id\":" + p.id() + ",\"hash\":\"" + p.hash() + "\"}";
    }

    /** 在调用方的主库事务里锁住链头(SELECT ... FOR UPDATE)并返回;不存在时按现有审计初始化。 */
    public Point lockHead() {
        String sql = "SELECT setting_value FROM csap_team_setting WHERE name = ? FOR UPDATE";
        String v = db.one(sql, (rs, i) -> rs.getString(1), HEAD);
        if (v == null) {
            Point init = initialHead();
            try {
                db.update("INSERT INTO csap_team_setting (name, setting_value) VALUES (?, ?)", HEAD, write(init));
            } catch (DuplicateKeyException e) {
                // 别的事务刚插入
            }
            v = db.one(sql, (rs, i) -> rs.getString(1), HEAD);
        }
        Point p = parse(v);
        if (p == null) {
            throw new IllegalStateException("审计链头 " + HEAD + " 内容无效");
        }
        return p;
    }

    private Point initialHead() {
        Point last = db.one("SELECT id, hash FROM csap_team_audit ORDER BY id DESC", (rs, i) -> {
            String h = rs.getString(2);
            return h == null ? new Point(rs.getLong(1), null) : new Point(rs.getLong(1), h);
        });
        if (last == null) {
            return new Point(0, ZERO);
        }
        if (last.hash() == null) {
            throw new IllegalStateException("审计记录尚未封存哈希,请先完成 schema 升级");
        }
        return last;
    }

    public void setHead(long id, String hash) {
        settings.set(HEAD, write(new Point(id, hash)));
    }

    public Point head() {
        return parse(settings.get(HEAD));
    }

    public Point anchor() {
        return parse(settings.get(ANCHOR));
    }

    public void setAnchor(Point p) {
        settings.set(ANCHOR, write(p));
    }

    // ---- 升级回填 ----

    /** 给所有审计按 id 顺序计算并写入 prev_hash / hash,然后写链头。返回封存的条数。可重复执行。 */
    public long backfill() {
        long n = 0;
        long last = 0;
        String prev = ZERO;
        while (true) {
            List<Object[]> rows = db.page("SELECT " + String.join(", ", FIELDS) + " FROM csap_team_audit WHERE id > ? ORDER BY id",
                    (rs, i) -> readValues(rs, null), 0, BATCH, last);
            if (rows.isEmpty()) {
                break;
            }
            final String[] carry = {prev};
            db.inTx(() -> {
                for (Object[] r : rows) {
                    r[18] = carry[0];
                    String h = hash(carry[0], r);
                    db.update("UPDATE csap_team_audit SET prev_hash = ?, hash = ? WHERE id = ?", carry[0], h, r[0]);
                    carry[0] = h;
                }
            });
            prev = carry[0];
            last = (Long) rows.get(rows.size() - 1)[0];
            n += rows.size();
            if (rows.size() < BATCH) {
                break;
            }
        }
        db.inTx(() -> {
            lockHead();
        });
        setHead(last, prev);
        return n;
    }

    private static Long nl(java.sql.ResultSet rs, int col) throws java.sql.SQLException {
        long v = rs.getLong(col);
        return rs.wasNull() ? null : v;
    }

    /** 读一行里的 19 个值(第 19 个是库里的 prev_hash)。 */
    static Object[] readValues(java.sql.ResultSet rs, String[] hashOut) throws java.sql.SQLException {
        Object[] v = values(rs.getLong(1), rs.getLong(2), rs.getString(3), nl(rs, 4), rs.getString(5), rs.getString(6), rs.getString(7),
                rs.getString(8), nl(rs, 9), rs.getString(10), rs.getString(11), rs.getString(12), nl(rs, 13), rs.getString(14),
                rs.getString(15), rs.getString(16), rs.getString(17), nl(rs, 18), rs.getString(19));
        if (hashOut != null) {
            hashOut[0] = rs.getString(20);
        }
        return v;
    }

    // ---- 校验 ----

    /**
     * 校验链。不传范围 = 从锚点(或链首)到末尾,并核对链头;分批流式计算。
     * 判断以 prev_hash 为准:id 跳号(事务回滚)但 prev_hash 接得上不算错;尾部被删(链头对不上)报 GAP。
     */
    public Result verify(Long fromId, Long toId) {
        boolean full = fromId == null && toId == null;
        Point headAtStart = full ? head() : null;
        long cursor;
        String expectedPrev;
        if (fromId != null) {
            cursor = fromId - 1;
            Point before = db.one("SELECT id, hash FROM csap_team_audit WHERE id < ? ORDER BY id DESC",
                    (rs, i) -> new Point(rs.getLong(1), rs.getString(2)), fromId);
            expectedPrev = before == null ? null : before.hash();
        } else {
            Point anchor = anchor();
            cursor = anchor == null ? 0 : anchor.id();
            expectedPrev = anchor == null ? ZERO : anchor.hash();
        }
        long checked = 0;
        Long lastId = null;
        String lastHash = null;
        while (true) {
            final long from = cursor;
            List<Object[]> rows;
            List<String> hashes = new ArrayList<>();
            String sql = "SELECT " + SELECT + " FROM csap_team_audit WHERE id > ?" + (toId != null ? " AND id <= ?" : "") + " ORDER BY id";
            RowMapperHolder h = new RowMapperHolder(hashes);
            rows = toId != null ? db.page(sql, h::map, 0, BATCH, from, toId) : db.page(sql, h::map, 0, BATCH, from);
            if (rows.isEmpty()) {
                break;
            }
            for (int i = 0; i < rows.size(); i++) {
                Object[] r = rows.get(i);
                String storedHash = hashes.get(i);
                String storedPrev = (String) r[18];
                long id = (Long) r[0];
                checked++;
                if (storedHash == null || storedPrev == null) {
                    return new Result(false, checked, lastId, lastHash, id, "MISSING_HASH");
                }
                if (!hash(storedPrev, r).equals(storedHash)) {
                    return new Result(false, checked, lastId, lastHash, id, "HASH_MISMATCH");
                }
                if (expectedPrev != null && !storedPrev.equals(expectedPrev)) {
                    return new Result(false, checked, lastId, lastHash, id, "PREV_MISMATCH");
                }
                expectedPrev = storedHash;
                lastId = id;
                lastHash = storedHash;
                cursor = id;
            }
            if (rows.size() < BATCH) {
                break;
            }
        }
        if (full && headAtStart != null && headAtStart.id() > 0) {
            boolean tailMissing = lastId == null || lastId < headAtStart.id()
                    || (lastId.equals(headAtStart.id()) && !headAtStart.hash().equals(lastHash));
            if (tailMissing) {
                return new Result(false, checked, lastId, lastHash, headAtStart.id(), "GAP");
            }
        }
        if (lastId == null) {
            Point head = head();
            return new Result(true, 0, head == null || head.id() == 0 ? null : head.id(), head == null ? null : head.hash(), null, null);
        }
        return new Result(true, checked, lastId, lastHash, null, null);
    }

    private static final class RowMapperHolder {
        private final List<String> hashes;

        RowMapperHolder(List<String> hashes) {
            this.hashes = hashes;
        }

        Object[] map(java.sql.ResultSet rs, int i) throws java.sql.SQLException {
            String[] h = new String[1];
            Object[] v = readValues(rs, h);
            hashes.add(h[0]);
            return v;
        }
    }
}
