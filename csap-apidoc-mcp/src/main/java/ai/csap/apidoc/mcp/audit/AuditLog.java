package ai.csap.apidoc.mcp.audit;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 审计日志:内存环形缓冲,可选异步写入 SQLite,或写入数据库({@link AuditSink},1.5.0 起)。
 * 写入数据库时 DevTools 的审计页仍从内存读取。
 *
 * @author ycf
 * @since 1.3.0
 */
public class AuditLog implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(AuditLog.class);

    static final String DDL = "CREATE TABLE IF NOT EXISTS mcp_audit (id INTEGER PRIMARY KEY AUTOINCREMENT,"
            + " time INTEGER, caller TEXT, remote_address TEXT, tool TEXT, target TEXT, arguments TEXT,"
            + " status INTEGER, success INTEGER, result TEXT, duration_ms INTEGER)";

    private final int capacity;
    private final Deque<AuditEntry> ring;
    private final AtomicLong seq = new AtomicLong();
    private final File sqliteFile;
    private final int maxRows;
    private final ExecutorService writer;
    private volatile boolean sqliteReady;
    private final AuditSink sink;

    /**
     * @param capacity   内存条数
     * @param sqliteFile SQLite 文件;null 表示不落盘
     * @param maxRows    SQLite 最多保留条数
     */
    public AuditLog(int capacity, File sqliteFile, int maxRows) {
        this.capacity = Math.max(1, capacity);
        this.ring = new ArrayDeque<>(this.capacity);
        this.sqliteFile = sqliteFile;
        this.maxRows = Math.max(100, maxRows);
        this.sink = null;
        if (sqliteFile != null) {
            this.writer = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>(10000),
                    r -> {
                        Thread t = new Thread(r, "csap-mcp-audit");
                        t.setDaemon(true);
                        return t;
                    }, new ThreadPoolExecutor.DiscardOldestPolicy());
            initSqlite();
        } else {
            this.writer = null;
        }
    }

    /**
     * 内存环形缓冲 + 写入数据库。
     *
     * @param capacity 内存条数
     * @param sink     落库目标(已初始化);null 表示只记录在内存
     * @since 1.5.0
     */
    public AuditLog(int capacity, AuditSink sink) {
        this.capacity = Math.max(1, capacity);
        this.ring = new ArrayDeque<>(this.capacity);
        this.sqliteFile = null;
        this.maxRows = 0;
        this.writer = null;
        this.sink = sink;
    }

    private void initSqlite() {
        try {
            File dir = sqliteFile.getAbsoluteFile().getParentFile();
            if (dir != null && !dir.isDirectory()) {
                dir.mkdirs();
            }
            try (Connection c = connect(); Statement st = c.createStatement()) {
                st.execute(DDL);
                st.execute("CREATE INDEX IF NOT EXISTS idx_mcp_audit_time ON mcp_audit(time)");
            }
            sqliteReady = true;
            try (Connection c = connect(); Statement st = c.createStatement();
                 ResultSet rs = st.executeQuery("SELECT MAX(id) FROM mcp_audit")) {
                if (rs.next()) {
                    seq.set(rs.getLong(1));
                }
            }
        } catch (SQLException | RuntimeException e) {
            LOG.warn("csap-apidoc MCP: 审计 SQLite 初始化失败({}),只记录在内存: {}", sqliteFile, e.getMessage());
        }
    }

    private Connection connect() throws SQLException {
        return DriverManager.getConnection("jdbc:sqlite:" + sqliteFile.getPath());
    }

    public boolean isPersistent() {
        return sqliteReady || sink != null;
    }

    public String getSqliteFile() {
        return sqliteFile == null ? null : sqliteFile.getPath();
    }

    /**
     * 记录
     *
     * @param entry 记录
     */
    public void record(AuditEntry entry) {
        entry.setId(seq.incrementAndGet());
        synchronized (ring) {
            if (ring.size() >= capacity) {
                ring.pollFirst();
            }
            ring.addLast(entry);
        }
        if (sqliteReady && writer != null) {
            writer.execute(() -> write(entry));
        }
        if (sink != null) {
            try {
                sink.offer(entry);
            } catch (RuntimeException e) {
                // 落库永远不能影响 MCP 请求
                LOG.debug("csap-apidoc MCP: 审计提交到数据库失败: {}", e.getMessage());
            }
        }
    }

    private void write(AuditEntry e) {
        try (Connection c = connect()) {
            try (PreparedStatement ps = c.prepareStatement("INSERT INTO mcp_audit(id, time, caller, remote_address,"
                    + " tool, target, arguments, status, success, result, duration_ms) VALUES (?,?,?,?,?,?,?,?,?,?,?)")) {
                ps.setLong(1, e.getId());
                ps.setLong(2, e.getTime());
                ps.setString(3, e.getCaller());
                ps.setString(4, e.getRemoteAddress());
                ps.setString(5, e.getTool());
                ps.setString(6, e.getTarget());
                ps.setString(7, e.getArguments());
                ps.setInt(8, e.getStatus());
                ps.setInt(9, e.isSuccess() ? 1 : 0);
                ps.setString(10, e.getResult());
                ps.setLong(11, e.getDurationMs());
                ps.executeUpdate();
            }
            if (e.getId() % 100 == 0) {
                try (PreparedStatement ps = c.prepareStatement("DELETE FROM mcp_audit WHERE id <= ?")) {
                    ps.setLong(1, e.getId() - maxRows);
                    ps.executeUpdate();
                }
            }
        } catch (SQLException ex) {
            LOG.warn("csap-apidoc MCP: 写审计 SQLite 失败: {}", ex.getMessage());
        }
    }

    /**
     * 查询,按时间倒序
     *
     * @param limit  条数
     * @param tool   工具名(精确,可空)
     * @param caller 调用方(精确,可空)
     * @return 记录
     */
    public List<AuditEntry> query(int limit, String tool, String caller) {
        int n = Math.max(1, Math.min(limit, 1000));
        if (sqliteReady) {
            flush();
            try {
                return querySqlite(n, tool, caller);
            } catch (SQLException e) {
                LOG.warn("csap-apidoc MCP: 查询审计 SQLite 失败,改用内存记录: {}", e.getMessage());
            }
        }
        List<AuditEntry> out = new ArrayList<>();
        synchronized (ring) {
            Iterator<AuditEntry> it = ring.descendingIterator();
            while (it.hasNext() && out.size() < n) {
                AuditEntry e = it.next();
                if ((isEmpty(tool) || tool.equals(e.getTool())) && (isEmpty(caller) || caller.equals(e.getCaller()))) {
                    out.add(e);
                }
            }
        }
        return out;
    }

    private List<AuditEntry> querySqlite(int limit, String tool, String caller) throws SQLException {
        StringBuilder sql = new StringBuilder("SELECT id, time, caller, remote_address, tool, target, arguments,"
                + " status, success, result, duration_ms FROM mcp_audit WHERE 1=1");
        List<String> args = new ArrayList<>();
        if (!isEmpty(tool)) {
            sql.append(" AND tool = ?");
            args.add(tool);
        }
        if (!isEmpty(caller)) {
            sql.append(" AND caller = ?");
            args.add(caller);
        }
        sql.append(" ORDER BY id DESC LIMIT ").append(limit);
        List<AuditEntry> out = new ArrayList<>();
        try (Connection c = connect(); PreparedStatement ps = c.prepareStatement(sql.toString())) {
            for (int i = 0; i < args.size(); i++) {
                ps.setString(i + 1, args.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    AuditEntry e = new AuditEntry();
                    e.setId(rs.getLong(1));
                    e.setTime(rs.getLong(2));
                    e.setCaller(rs.getString(3));
                    e.setRemoteAddress(rs.getString(4));
                    e.setTool(rs.getString(5));
                    e.setTarget(rs.getString(6));
                    e.setArguments(rs.getString(7));
                    e.setStatus(rs.getInt(8));
                    e.setSuccess(rs.getInt(9) != 0);
                    e.setResult(rs.getString(10));
                    e.setDurationMs(rs.getLong(11));
                    out.add(e);
                }
            }
        }
        return out;
    }

    /**
     * 等待已排队的写入完成(查询前调用,最多等 2 秒)
     */
    void flush() {
        if (writer == null) {
            return;
        }
        try {
            writer.submit(() -> {
            }).get(2, TimeUnit.SECONDS);
        } catch (Exception ignore) {
            // 查询以已写入的数据为准
        }
    }

    private static boolean isEmpty(String s) {
        return s == null || s.isEmpty();
    }

    @Override
    public void close() {
        if (sink != null) {
            sink.close();
        }
        if (writer != null) {
            writer.shutdown();
            try {
                writer.awaitTermination(2, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
