package ai.csap.apidoc.mcp.audit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;

import ai.csap.apidoc.database.DatabaseDialect;

/**
 * 审计表的建表、写入、清理;每种数据库一个子类。每个用例用独立的表前缀,互不干扰。
 */
abstract class AbstractJdbcAuditStoreTest {

    private static final AtomicInteger SEQ = new AtomicInteger();

    abstract DataSource dataSource();

    abstract DatabaseDialect dialect();

    private static String newPrefix() {
        return "t" + System.nanoTime() % 1_000_000_000L + "_" + SEQ.incrementAndGet() + "_";
    }

    private JdbcAuditStore store(String prefix, String service, int maxRows) {
        return new JdbcAuditStore(dataSource(), dialect(), prefix, service, maxRows, 1000, 3_600_000L);
    }

    static AuditEntry entry(long at, String tool) {
        AuditEntry e = new AuditEntry();
        e.setTime(at);
        e.setCaller("token-a");
        e.setRemoteAddress("10.1.2.3");
        e.setTool(tool);
        e.setTarget("GET /orders/1?x=1");
        e.setArguments("{\"password\":\"***\",\"id\":1}");
        e.setStatus(200);
        e.setSuccess(true);
        e.setResult("{\"ok\":true,\"中文\":\"结果\"}");
        e.setDurationMs(42);
        return e;
    }

    private int count(String prefix, String service) throws SQLException {
        try (Connection c = dataSource().getConnection(); PreparedStatement ps = c.prepareStatement(
                "SELECT COUNT(*) FROM " + prefix + "mcp_audit WHERE service = ?")) {
            ps.setString(1, service);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    @Test
    void createsTableAndWritesEntries() throws Exception {
        String prefix = newPrefix();
        JdbcAuditStore s = store(prefix, "order-service", 10000);
        s.init(true);
        s.offer(entry(1_700_000_000_000L, "list_orders"));
        AuditEntry failed = entry(1_700_000_000_500L, "get_order");
        failed.setSuccess(false);
        failed.setStatus(500);
        failed.setResult("HTTP 500: boom");
        s.offer(failed);
        assertTrue(s.flush(5000));
        s.close();

        try (Connection c = dataSource().getConnection(); PreparedStatement ps = c.prepareStatement(
                "SELECT service, at, caller, remote_address, tool, target, arguments, status, success, result,"
                        + " duration_ms FROM " + prefix + "mcp_audit ORDER BY id")) {
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                assertEquals("order-service", rs.getString(1));
                assertEquals(1_700_000_000_000L, rs.getLong(2));
                assertEquals("token-a", rs.getString(3));
                assertEquals("10.1.2.3", rs.getString(4));
                assertEquals("list_orders", rs.getString(5));
                assertEquals("GET /orders/1?x=1", rs.getString(6));
                assertEquals("{\"password\":\"***\",\"id\":1}", rs.getString(7));
                assertEquals(200, rs.getInt(8));
                assertEquals(1, rs.getInt(9));
                assertEquals("{\"ok\":true,\"中文\":\"结果\"}", rs.getString(10));
                assertEquals(42L, rs.getLong(11));
                assertTrue(rs.next());
                assertEquals(0, rs.getInt(9));
                assertEquals(500, rs.getInt(8));
                assertEquals("HTTP 500: boom", rs.getString(10));
                assertFalse(rs.next());
            }
        }
    }

    @Test
    void initIsIdempotentAndSecondStoreReusesTable() throws Exception {
        String prefix = newPrefix();
        JdbcAuditStore a = store(prefix, "svc-a", 10000);
        a.init(true);
        JdbcAuditStore b = store(prefix, "svc-b", 10000);
        b.init(false); // 表已存在,不允许建表也可以
        a.offer(entry(1L, "t"));
        b.offer(entry(2L, "t"));
        assertTrue(a.flush(5000));
        assertTrue(b.flush(5000));
        a.close();
        b.close();
        assertEquals(1, count(prefix, "svc-a"));
        assertEquals(1, count(prefix, "svc-b"));
    }

    @Test
    void missingTableWithoutCreatePermissionFails() {
        String prefix = newPrefix();
        JdbcAuditStore s = store(prefix, "svc", 10000);
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> s.init(false));
        assertTrue(e.getMessage().contains(prefix + "mcp_audit"), e.getMessage());
    }

    @Test
    void purgeKeepsNewestMaxRowsPerService() throws Exception {
        String prefix = newPrefix();
        // maxRows 最小为 100
        JdbcAuditStore s = store(prefix, "svc-a", 100);
        s.init(true);
        JdbcAuditStore other = store(prefix, "svc-b", 100);
        other.init(false);
        for (int i = 0; i < 130; i++) {
            s.offer(entry(1000 + i, "t" + i));
        }
        for (int i = 0; i < 20; i++) {
            other.offer(entry(i, "o" + i));
        }
        assertTrue(s.flush(10000));
        assertTrue(other.flush(10000));
        assertEquals(130, count(prefix, "svc-a"));

        assertEquals(30, s.purge());
        assertEquals(100, count(prefix, "svc-a"));
        assertEquals(20, count(prefix, "svc-b"), "清理只作用于本服务");
        assertEquals(0, s.purge());
        // 留下的是最新的 100 条
        try (Connection c = dataSource().getConnection(); PreparedStatement ps = c.prepareStatement(
                "SELECT MIN(at) FROM " + prefix + "mcp_audit WHERE service = ?")) {
            ps.setString(1, "svc-a");
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                assertEquals(1030L, rs.getLong(1));
            }
        }
        s.close();
        other.close();
    }

    @Test
    void overlongFieldsAreTruncatedInsteadOfFailing() throws Exception {
        String prefix = newPrefix();
        JdbcAuditStore s = store(prefix, "svc", 10000);
        s.init(true);
        AuditEntry e = entry(1L, "t");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 1000; i++) {
            sb.append('x');
        }
        e.setCaller(sb.toString());
        e.setRemoteAddress(sb.toString());
        e.setTool(sb.toString());
        e.setTarget(sb.toString());
        s.offer(e);
        assertTrue(s.flush(5000));
        s.close();
        List<Integer> lens = new ArrayList<>();
        try (Connection c = dataSource().getConnection(); PreparedStatement ps = c.prepareStatement(
                "SELECT caller, remote_address, tool, target FROM " + prefix + "mcp_audit")) {
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                for (int i = 1; i <= 4; i++) {
                    lens.add(rs.getString(i).length());
                }
            }
        }
        assertEquals(List.of(128, 64, 128, 512), lens);
        assertEquals(0, s.droppedCount());
    }

    @Test
    void indexesExist() throws Exception {
        String prefix = newPrefix();
        JdbcAuditStore s = store(prefix, "svc", 10000);
        s.init(true);
        s.close();
        List<String> names = new ArrayList<>();
        try (Connection c = dataSource().getConnection();
             ResultSet rs = c.getMetaData().getIndexInfo(null, null, tableName(c, prefix), false, false)) {
            while (rs.next()) {
                String n = rs.getString("INDEX_NAME");
                if (n != null) {
                    names.add(n.toLowerCase());
                }
            }
        }
        assertTrue(names.contains(prefix + "mcp_audit_service_at"), names.toString());
        assertTrue(names.contains(prefix + "mcp_audit_at"), names.toString());
    }

    /**
     * 不同数据库对表名大小写的存放不同(H2 存大写),按元数据找到真实表名。
     */
    private static String tableName(Connection c, String prefix) throws SQLException {
        try (ResultSet rs = c.getMetaData().getTables(null, null, "%", new String[]{"TABLE"})) {
            while (rs.next()) {
                String n = rs.getString("TABLE_NAME");
                if (n.equalsIgnoreCase(prefix + "mcp_audit")) {
                    return n;
                }
            }
        }
        throw new SQLException("table not found " + prefix + "mcp_audit");
    }
}
