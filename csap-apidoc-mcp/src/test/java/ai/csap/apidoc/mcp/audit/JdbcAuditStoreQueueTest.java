package ai.csap.apidoc.mcp.audit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;

import ai.csap.apidoc.database.DatabaseDialect;

/**
 * 队列满时不阻塞调用线程,只丢弃并计数;写库失败不影响内存审计。
 */
class JdbcAuditStoreQueueTest {

    /**
     * 第一次 getConnection 之后(init 用)的连接全部卡住,直到 release
     */
    private static final class BlockingDataSource implements DataSource {
        private final DataSource delegate = new TestDataSource("jdbc:h2:mem:queue_" + System.nanoTime()
                + ";DB_CLOSE_DELAY=-1", "sa", "");
        private final CountDownLatch release = new CountDownLatch(1);
        private volatile boolean block;

        @Override
        public Connection getConnection() throws SQLException {
            if (block) {
                try {
                    release.await(30, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new SQLException("interrupted");
                }
            }
            return delegate.getConnection();
        }

        @Override
        public Connection getConnection(String u, String p) throws SQLException {
            return getConnection();
        }

        @Override
        public PrintWriter getLogWriter() {
            return null;
        }

        @Override
        public void setLogWriter(PrintWriter out) {
        }

        @Override
        public void setLoginTimeout(int seconds) {
        }

        @Override
        public int getLoginTimeout() {
            return 0;
        }

        @Override
        public Logger getParentLogger() throws SQLFeatureNotSupportedException {
            throw new SQLFeatureNotSupportedException();
        }

        @Override
        public <T> T unwrap(Class<T> iface) throws SQLException {
            throw new SQLException("not a wrapper");
        }

        @Override
        public boolean isWrapperFor(Class<?> iface) {
            return false;
        }
    }

    @Test
    void fullQueueDropsWithoutBlocking() throws Exception {
        BlockingDataSource ds = new BlockingDataSource();
        JdbcAuditStore s = new JdbcAuditStore(ds, DatabaseDialect.H2, "q_", "svc", 10000, 5, 3_600_000L);
        s.init(true);
        ds.block = true;

        long t0 = System.nanoTime();
        for (int i = 0; i < 2000; i++) {
            s.offer(AbstractJdbcAuditStoreTest.entry(i, "t"));
        }
        long ms = (System.nanoTime() - t0) / 1_000_000L;
        assertTrue(ms < 2000, "提交 2000 条必须不阻塞,实际 " + ms + "ms");
        // 队列 5 + 写线程手里的一批(最多 100)之外的都被丢弃
        assertTrue(s.droppedCount() >= 2000 - 5 - 100, "dropped=" + s.droppedCount());
        assertFalse(s.flush(50), "写库被卡住时 flush 应超时");

        ds.release.countDown();
        assertTrue(s.flush(10000));
        s.close();
    }

    @Test
    void writeFailureDoesNotAffectMemoryAudit() throws Exception {
        BlockingDataSource ds = new BlockingDataSource();
        JdbcAuditStore s = new JdbcAuditStore(ds, DatabaseDialect.H2, "w_", "svc", 10000, 100, 3_600_000L);
        s.init(true);
        // 表被删除:写库失败,但 AuditLog 的内存记录和查询不受影响
        try (Connection c = ds.delegate.getConnection(); java.sql.Statement st = c.createStatement()) {
            st.execute("DROP TABLE w_mcp_audit");
        }
        AuditLog log = new AuditLog(10, s);
        log.record(AbstractJdbcAuditStoreTest.entry(1L, "tool-1"));
        log.record(AbstractJdbcAuditStoreTest.entry(2L, "tool-2"));
        assertTrue(s.flush(5000));
        assertEquals(2, log.query(10, null, null).size());
        assertEquals("tool-2", log.query(10, null, null).get(0).getTool());
        assertTrue(log.isPersistent());
        log.close();
    }

    @Test
    void auditLogWritesBothMemoryAndDatabase() throws Exception {
        BlockingDataSource ds = new BlockingDataSource();
        JdbcAuditStore s = new JdbcAuditStore(ds, DatabaseDialect.H2, "b_", "svc", 10000, 100, 3_600_000L);
        s.init(true);
        AuditLog log = new AuditLog(10, s);
        log.record(AbstractJdbcAuditStoreTest.entry(5L, "tool-x"));
        assertTrue(s.flush(5000));
        assertEquals(1, log.query(10, "tool-x", null).size());
        try (Connection c = ds.delegate.getConnection(); java.sql.Statement st = c.createStatement();
             java.sql.ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM b_mcp_audit")) {
            rs.next();
            assertEquals(1, rs.getInt(1));
        }
        log.close();
    }
}
