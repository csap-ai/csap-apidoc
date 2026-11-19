package ai.csap.apidoc.team.db;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.regex.Pattern;

import javax.sql.DataSource;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCreator;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import ai.csap.apidoc.database.DatabaseDialect;
import ai.csap.apidoc.team.config.TeamProperties;

/**
 * 数据访问入口:JdbcTemplate、事务、方言、开源三张表的表名。所有 SQL 都用 {@code ?} 参数化,
 * 表名只由本类校验过的前缀拼出。
 */
@Component
public class Db {

    private static final Pattern PREFIX = Pattern.compile("[A-Za-z0-9_]*");

    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final DatabaseDialect dialect;
    private final String prefix;

    public Db(DataSource dataSource, PlatformTransactionManager txManager, TeamProperties props) {
        this.jdbc = new JdbcTemplate(dataSource);
        this.tx = new TransactionTemplate(txManager);
        this.prefix = props.getRuleTablePrefix() == null ? "csap_apidoc_" : props.getRuleTablePrefix();
        if (!PREFIX.matcher(prefix).matches()) {
            throw new IllegalArgumentException("csap.team.rule-table-prefix 只能包含字母、数字和下划线");
        }
        DatabaseDialect d = DatabaseDialect.parse(props.getDatabase().getDialect());
        if (d == null) {
            d = detect(dataSource);
        }
        this.dialect = d;
    }

    private static DatabaseDialect detect(DataSource ds) {
        try (Connection c = ds.getConnection()) {
            String url = c.getMetaData().getURL();
            DatabaseDialect d = DatabaseDialect.fromJdbcUrl(url);
            if (d == null) {
                throw new IllegalStateException("无法从 JDBC URL 识别数据库方言,请设置 csap.team.database.dialect"
                        + "(mysql / postgresql / dm / kingbase / h2)");
            }
            return d;
        } catch (SQLException e) {
            throw new IllegalStateException("无法连接数据库: " + e.getMessage(), e);
        }
    }

    public JdbcTemplate jdbc() { return jdbc; }
    public DatabaseDialect dialect() { return dialect; }
    public String prefix() { return prefix; }
    public String ruleTable() { return prefix + "rule"; }
    public String historyTable() { return prefix + "history"; }
    public String metaTable() { return prefix + "meta"; }
    public String mcpAuditTable() { return prefix + "mcp_audit"; }

    /** 在事务里执行。 */
    public <T> T inTx(Supplier<T> body) {
        return tx.execute(status -> body.get());
    }

    public void inTx(Runnable body) {
        tx.executeWithoutResult(status -> body.run());
    }

    /** INSERT 并返回自增 id。 */
    public long insert(String sql, Object... args) {
        GeneratedKeyHolder holder = new GeneratedKeyHolder();
        boolean named = dialect == DatabaseDialect.POSTGRESQL || dialect == DatabaseDialect.KINGBASE;
        PreparedStatementCreator psc = con -> {
            PreparedStatement ps = named
                    ? con.prepareStatement(sql, new String[] {"id"})
                    : con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            for (int i = 0; i < args.length; i++) {
                ps.setObject(i + 1, args[i]);
            }
            return ps;
        };
        jdbc.update(psc, holder);
        List<Map<String, Object>> keys = holder.getKeyList();
        if (keys.isEmpty() || keys.get(0).isEmpty()) {
            throw new IllegalStateException("INSERT 没有返回自增 id");
        }
        Object v = keys.get(0).values().iterator().next();
        return ((Number) v).longValue();
    }

    public <T> List<T> query(String sql, RowMapper<T> mapper, Object... args) {
        return jdbc.query(sql, mapper, args);
    }

    public <T> T one(String sql, RowMapper<T> mapper, Object... args) {
        List<T> l = jdbc.query(sql, mapper, args);
        return l.isEmpty() ? null : l.get(0);
    }

    public int update(String sql, Object... args) {
        return jdbc.update(sql, args);
    }

    public long count(String sql, Object... args) {
        Long n = jdbc.queryForObject(sql, Long.class, args);
        return n == null ? 0 : n;
    }

    /** 最多取 limit 行,跳过前 skip 行(不依赖 LIMIT/OFFSET 方言差异)。 */
    public <T> List<T> page(String sql, RowMapper<T> mapper, int skip, int limit, Object... args) {
        return jdbc.query(con -> {
            PreparedStatement ps = con.prepareStatement(sql);
            ps.setMaxRows(skip + limit);
            for (int i = 0; i < args.length; i++) {
                ps.setObject(i + 1, args[i]);
            }
            return ps;
        }, rs -> {
            List<T> out = new java.util.ArrayList<>();
            int n = 0;
            while (rs.next()) {
                if (n++ < skip) {
                    continue;
                }
                out.add(mapper.mapRow(rs, out.size()));
            }
            return out;
        });
    }

    /** 表是否存在(探测一条空查询)。 */
    public boolean tableExists(String table) {
        try {
            jdbc.query("SELECT 1 FROM " + table + " WHERE 1 = 0", rs -> null);
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** 创建 / 查询用的原始连接回调,仅供特殊场景。 */
    public <T> T withConnection(java.util.function.Function<Connection, T> fn) {
        return jdbc.execute((Connection c) -> fn.apply(c));
    }
}
