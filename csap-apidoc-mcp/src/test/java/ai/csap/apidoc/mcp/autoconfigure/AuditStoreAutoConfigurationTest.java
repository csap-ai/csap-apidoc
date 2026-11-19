package ai.csap.apidoc.mcp.autoconfigure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import ai.csap.apidoc.ApiStrategyType;
import ai.csap.apidoc.autoconfigure.EnableApidocConfig;
import ai.csap.apidoc.mcp.audit.AuditEntry;
import ai.csap.apidoc.mcp.audit.AuditLog;
import ai.csap.apidoc.mcp.audit.TestDataSource;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

/**
 * csap.apidoc.mcp.audit.store 的选择与退回。
 */
class AuditStoreAutoConfigurationTest {

    private static final String H2 = "jdbc:h2:mem:audit_cfg_%d;DB_CLOSE_DELAY=-1";

    @Configuration(proxyBeanMethods = false)
    static class DatabaseModeApp {
        @Bean
        EnableApidocConfig enableApidocConfig() {
            EnableApidocConfig c = new EnableApidocConfig();
            c.setParamType(ApiStrategyType.DATABASE);
            return c;
        }

        @Bean
        DataSource dataSource() {
            return new TestDataSource(String.format(H2, System.nanoTime()), "sa", "");
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class YamlModeApp {
        @Bean
        EnableApidocConfig enableApidocConfig() {
            EnableApidocConfig c = new EnableApidocConfig();
            c.setParamType(ApiStrategyType.YAML);
            return c;
        }

        @Bean
        DataSource dataSource() {
            return new TestDataSource(String.format(H2, System.nanoTime()), "sa", "");
        }
    }

    private WebApplicationContextRunner runner() {
        return new WebApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(McpAutoConfiguration.class))
                .withPropertyValues("csap.apidoc.mcp.enabled=true");
    }

    private ListAppender<ILoggingEvent> captureLogs() {
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        ((Logger) LoggerFactory.getLogger("ai.csap.apidoc.mcp.autoconfigure")).addAppender(appender);
        return appender;
    }

    private static List<String> warns(ListAppender<ILoggingEvent> a) {
        List<String> out = new ArrayList<>();
        for (ILoggingEvent e : new ArrayList<>(a.list)) {
            if (e.getLevel().toString().equals("WARN")) {
                out.add(e.getFormattedMessage());
            }
        }
        return out;
    }

    private static boolean tableExists(DataSource ds, String table) {
        try (Connection c = ds.getConnection(); Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM " + table)) {
            return rs.next();
        } catch (Exception e) {
            return false;
        }
    }

    @Test
    void defaultIsMemory() {
        runner().run(ctx -> assertFalse(ctx.getBean(AuditLog.class).isPersistent()));
        runner().withPropertyValues("csap.apidoc.mcp.audit.store=memory")
                .run(ctx -> assertFalse(ctx.getBean(AuditLog.class).isPersistent()));
    }

    @Test
    void persistTrueIsSqliteAndStoreOverridesIt() throws Exception {
        File dir = Files.createTempDirectory("audit-cfg").toFile();
        String file = new File(dir, "a.db").getPath();
        runner().withPropertyValues("csap.apidoc.mcp.audit.persist=true", "csap.apidoc.mcp.audit.sqlite-file=" + file)
                .run(ctx -> assertTrue(ctx.getBean(AuditLog.class).isPersistent()));
        runner().withPropertyValues("csap.apidoc.mcp.audit.store=sqlite", "csap.apidoc.mcp.audit.sqlite-file=" + file)
                .run(ctx -> assertTrue(ctx.getBean(AuditLog.class).isPersistent()));
        // 显式 store=memory 优先于 persist
        runner().withPropertyValues("csap.apidoc.mcp.audit.store=memory", "csap.apidoc.mcp.audit.persist=true",
                      "csap.apidoc.mcp.audit.sqlite-file=" + file)
                .run(ctx -> assertFalse(ctx.getBean(AuditLog.class).isPersistent()));
    }

    @Test
    void databaseStoreWritesToDatabaseModeDataSourceWithPrefix() {
        runner().withUserConfiguration(DatabaseModeApp.class)
                .withPropertyValues("csap.apidoc.mcp.audit.store=database", "csap.apidoc.database.table-prefix=app_",
                        "csap.apidoc.database.service=order-service", "csap.apidoc.database.dialect=h2")
                .run(ctx -> {
                    AuditLog log = ctx.getBean(AuditLog.class);
                    assertTrue(log.isPersistent());
                    DataSource ds = ctx.getBean(DataSource.class);
                    assertTrue(tableExists(ds, "app_mcp_audit"));
                    AuditEntry e = new AuditEntry();
                    e.setTime(123L);
                    e.setTool("t");
                    log.record(e);
                    log.close();
                    try (Connection c = ds.getConnection(); Statement st = c.createStatement();
                         ResultSet rs = st.executeQuery("SELECT service, at FROM app_mcp_audit")) {
                        assertTrue(rs.next());
                        assertEquals("order-service", rs.getString(1));
                        assertEquals(123L, rs.getLong(2));
                    }
                });
    }

    @Test
    void databaseStoreFallsBackToMemoryWhenDatabaseModeNotEnabled() {
        ListAppender<ILoggingEvent> logs = captureLogs();
        runner().withUserConfiguration(YamlModeApp.class)
                .withPropertyValues("csap.apidoc.mcp.audit.store=database")
                .run(ctx -> {
                    assertFalse(ctx.getBean(AuditLog.class).isPersistent());
                    assertFalse(tableExists(ctx.getBean(DataSource.class), "csap_apidoc_mcp_audit"));
                });
        assertTrue(warns(logs).stream().anyMatch(s -> s.contains("需要启用数据库模式")), warns(logs).toString());
        // 完全没有 @EnableApidoc 也一样
        runner().withPropertyValues("csap.apidoc.mcp.audit.store=database")
                .run(ctx -> assertFalse(ctx.getBean(AuditLog.class).isPersistent()));
    }

    @Test
    void databaseStoreFallsBackWhenTableMissingAndInitSchemaOff() {
        ListAppender<ILoggingEvent> logs = captureLogs();
        runner().withUserConfiguration(DatabaseModeApp.class)
                .withPropertyValues("csap.apidoc.mcp.audit.store=database", "csap.apidoc.database.init-schema=false")
                .run(ctx -> {
                    assertFalse(ctx.getBean(AuditLog.class).isPersistent());
                    assertFalse(tableExists(ctx.getBean(DataSource.class), "csap_apidoc_mcp_audit"));
                });
        assertTrue(warns(logs).stream().anyMatch(s -> s.contains("审计写入数据库不可用")), warns(logs).toString());
    }

    @Test
    void databaseStoreFallsBackWhenReadOnlyAndTableMissing() {
        runner().withUserConfiguration(DatabaseModeApp.class)
                .withPropertyValues("csap.apidoc.mcp.audit.store=database", "csap.apidoc.database.read-only=true")
                .run(ctx -> {
                    assertFalse(ctx.getBean(AuditLog.class).isPersistent());
                    assertFalse(tableExists(ctx.getBean(DataSource.class), "csap_apidoc_mcp_audit"));
                });
    }

    @Test
    void auditLogBeanIsAlwaysCreated() {
        runner().withUserConfiguration(DatabaseModeApp.class)
                .withPropertyValues("csap.apidoc.mcp.audit.store=database")
                .run(ctx -> assertNotNull(ctx.getBean(AuditLog.class)));
    }
}
