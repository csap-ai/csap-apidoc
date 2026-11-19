package ai.csap.apidoc.mcp.audit;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ai.csap.apidoc.database.DatabaseDialect;
import ai.csap.apidoc.database.DatabaseRuleStore;

/**
 * 把 MCP 审计写入数据库模式的库里的 {@code {prefix}mcp_audit} 表(表结构见 docs/design/team-integration.md 2.2)。
 *
 * <p>写入在一个后台守护线程里完成,有界队列满了就丢弃新记录并计数(不阻塞 MCP 请求);
 * 每 10 分钟按 {@code service} 删除超过 max-rows 的旧记录。
 *
 * @author ycf
 * @since 1.5.0
 */
public class JdbcAuditStore implements AuditSink {

    private static final Logger LOG = LoggerFactory.getLogger(JdbcAuditStore.class);
    private static final Pattern PREFIX = Pattern.compile("[A-Za-z0-9_]*");

    static final int DEFAULT_QUEUE_SIZE = 10000;
    static final long DEFAULT_PURGE_INTERVAL_MILLIS = TimeUnit.MINUTES.toMillis(10);
    private static final int BATCH = 100;
    /**
     * 写入连续失败时,第一次之后每隔多少次再 warn 一次
     */
    private static final int WARN_EVERY = 100;

    private final DataSource dataSource;
    private final DatabaseDialect dialect;
    private final String prefix;
    private final String table;
    private final String service;
    private final int maxRows;
    private final long purgeIntervalMillis;
    private final BlockingQueue<AuditEntry> queue;
    private final AtomicInteger pending = new AtomicInteger();
    private final AtomicLong dropped = new AtomicLong();
    private final AtomicLong writeFailures = new AtomicLong();
    private volatile Thread writer;
    private volatile boolean stopping;

    public JdbcAuditStore(DataSource dataSource, DatabaseDialect dialect, String tablePrefix, String service,
                          int maxRows) {
        this(dataSource, dialect, tablePrefix, service, maxRows, DEFAULT_QUEUE_SIZE, DEFAULT_PURGE_INTERVAL_MILLIS);
    }

    JdbcAuditStore(DataSource dataSource, DatabaseDialect dialect, String tablePrefix, String service, int maxRows,
                   int queueSize, long purgeIntervalMillis) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
        this.dialect = Objects.requireNonNull(dialect, "dialect");
        this.prefix = tablePrefix == null ? "csap_apidoc_" : tablePrefix;
        if (!PREFIX.matcher(this.prefix).matches()) {
            throw new IllegalArgumentException("csap.apidoc.database.table-prefix 只能包含字母、数字和下划线: " + tablePrefix);
        }
        if (service == null || service.trim().isEmpty()) {
            throw new IllegalArgumentException("service 不能为空");
        }
        this.service = service.trim();
        this.table = this.prefix + "mcp_audit";
        this.maxRows = Math.max(100, maxRows);
        this.queue = new LinkedBlockingQueue<>(Math.max(1, queueSize));
        this.purgeIntervalMillis = purgeIntervalMillis;
    }

    /**
     * 检查表,不存在时按方言建表,然后启动后台写入线程。
     *
     * @param createTables 是否允许建表(csap.apidoc.database.init-schema);只读模式传 false
     * @throws SQLException          连接 / 建表失败
     * @throws IllegalStateException 表不存在且不允许建表
     */
    public void init(boolean createTables) throws SQLException {
        try (Connection conn = dataSource.getConnection()) {
            boolean autoCommit = conn.getAutoCommit();
            conn.setAutoCommit(true);
            try {
                if (!probe(conn)) {
                    if (!createTables) {
                        throw new IllegalStateException("审计表 " + table + " 不存在。请先执行建表 SQL(jar 内 META-INF/csap-apidoc/database/"
                                + dialect.name().toLowerCase(Locale.ROOT) + ".sql 里的 " + table + "),"
                                + "或设置 csap.apidoc.database.init-schema=true 让应用自动建表(只读模式不建表)。");
                    }
                    try (Statement st = conn.createStatement()) {
                        for (String sql : DatabaseRuleStore.mcpAuditSchema(dialect, prefix)) {
                            st.execute(sql);
                        }
                    } catch (SQLException e) {
                        // 多个实例同时启动:别的实例刚建好,忽略
                        if (!probe(conn)) {
                            throw e;
                        }
                    }
                }
            } finally {
                conn.setAutoCommit(autoCommit);
            }
        }
        Thread t = new Thread(this::run, "csap-mcp-audit-db");
        t.setDaemon(true);
        writer = t;
        t.start();
    }

    private boolean probe(Connection conn) {
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT service, at, caller, status, success, duration_ms FROM " + table
                     + " WHERE 1 = 0")) {
            return true;
        } catch (SQLException e) {
            return false;
        }
    }

    @Override
    public String describe() {
        return "dialect=" + dialect.name().toLowerCase(Locale.ROOT) + " table=" + table + " service=" + service;
    }

    @Override
    public void offer(AuditEntry entry) {
        if (entry == null || stopping) {
            return;
        }
        pending.incrementAndGet();
        if (!queue.offer(entry)) {
            pending.decrementAndGet();
            long n = dropped.incrementAndGet();
            if (n == 1 || n % 1000 == 0) {
                LOG.warn("csap-apidoc MCP: 审计写库队列已满,已丢弃 {} 条记录(内存审计不受影响)", n);
            }
        }
    }

    /**
     * 因队列满而丢弃的记录数
     */
    public long droppedCount() {
        return dropped.get();
    }

    /**
     * 等待已提交的记录写完(测试和关闭时用)。
     *
     * @param timeoutMillis 最长等待
     * @return 是否已全部写完
     */
    public boolean flush(long timeoutMillis) {
        long end = System.currentTimeMillis() + timeoutMillis;
        while (pending.get() > 0 && System.currentTimeMillis() < end) {
            try {
                Thread.sleep(5);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        return pending.get() == 0;
    }

    private void run() {
        long lastPurge = 0L;
        while (true) {
            AuditEntry first;
            try {
                first = queue.poll(500, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                break;
            }
            if (first != null) {
                List<AuditEntry> batch = new ArrayList<>(BATCH);
                batch.add(first);
                queue.drainTo(batch, BATCH - 1);
                try {
                    insert(batch);
                } catch (SQLException | RuntimeException e) {
                    long n = writeFailures.incrementAndGet();
                    if (n == 1 || n % WARN_EVERY == 0) {
                        LOG.warn("csap-apidoc MCP: 审计写库失败(累计 {} 次,这批 {} 条已丢弃,内存审计不受影响): {}",
                                n, batch.size(), e.getMessage());
                    }
                } finally {
                    pending.addAndGet(-batch.size());
                }
            }
            long now = System.currentTimeMillis();
            if (now - lastPurge >= purgeIntervalMillis) {
                lastPurge = now;
                try {
                    purge();
                } catch (SQLException | RuntimeException e) {
                    LOG.warn("csap-apidoc MCP: 清理旧审计记录失败: {}", e.getMessage());
                }
            }
            if (stopping && queue.isEmpty()) {
                break;
            }
        }
    }

    private void insert(List<AuditEntry> batch) throws SQLException {
        try (Connection conn = dataSource.getConnection()) {
            boolean autoCommit = conn.getAutoCommit();
            conn.setAutoCommit(false);
            try (PreparedStatement ps = conn.prepareStatement("INSERT INTO " + table
                    + " (service, at, caller, remote_address, tool, target, arguments, status, success, result,"
                    + " duration_ms) VALUES (?,?,?,?,?,?,?,?,?,?,?)")) {
                for (AuditEntry e : batch) {
                    ps.setString(1, service);
                    ps.setLong(2, e.getTime());
                    ps.setString(3, cut(e.getCaller(), 128));
                    ps.setString(4, cut(e.getRemoteAddress(), 64));
                    ps.setString(5, cut(e.getTool(), 128));
                    ps.setString(6, cut(e.getTarget(), 512));
                    ps.setString(7, e.getArguments());
                    ps.setInt(8, e.getStatus());
                    ps.setInt(9, e.isSuccess() ? 1 : 0);
                    ps.setString(10, e.getResult());
                    ps.setLong(11, e.getDurationMs());
                    ps.addBatch();
                }
                ps.executeBatch();
                conn.commit();
            } catch (SQLException | RuntimeException ex) {
                try {
                    conn.rollback();
                } catch (SQLException suppressed) {
                    ex.addSuppressed(suppressed);
                }
                throw ex;
            } finally {
                conn.setAutoCommit(autoCommit);
            }
        }
    }

    /**
     * 删除本服务超出 max-rows 的最旧记录。不用 LIMIT / OFFSET(各库写法不同):先数行数,
     * 超出时只取"多出来的那几行"里最大的 id 作为界限。
     *
     * @return 删除的行数
     */
    public int purge() throws SQLException {
        try (Connection conn = dataSource.getConnection()) {
            long count;
            try (PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) FROM " + table + " WHERE service = ?")) {
                ps.setString(1, service);
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    count = rs.getLong(1);
                }
            }
            long excess = count - maxRows;
            if (excess <= 0) {
                return 0;
            }
            long cutoff = -1L;
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT id FROM " + table + " WHERE service = ? ORDER BY id ASC")) {
                ps.setMaxRows((int) Math.min(excess, Integer.MAX_VALUE));
                ps.setString(1, service);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        cutoff = rs.getLong(1);
                    }
                }
            }
            if (cutoff < 0) {
                return 0;
            }
            try (PreparedStatement ps = conn.prepareStatement("DELETE FROM " + table + " WHERE service = ? AND id <= ?")) {
                ps.setString(1, service);
                ps.setLong(2, cutoff);
                return ps.executeUpdate();
            }
        }
    }

    /**
     * 停止接收并尽量把队列写完(最多等 2 秒)。
     */
    @Override
    public void close() {
        stopping = true;
        Thread t = writer;
        if (t == null) {
            return;
        }
        try {
            t.join(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        if (t.isAlive()) {
            t.interrupt();
        }
    }

    private static String cut(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max);
    }
}
