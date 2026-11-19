package ai.csap.apidoc.mcp.audit;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import javax.sql.DataSource;

import ai.csap.apidoc.database.DatabaseDialect;

/**
 * SQLite 方言(数据库模式的库是 SQLite 时,审计也能写入)
 */
class SqliteJdbcAuditStoreTest extends AbstractJdbcAuditStoreTest {

    private static final DataSource DS;

    static {
        try {
            File f = Files.createTempFile("mcp-audit-sqlite", ".db").toFile();
            f.deleteOnExit();
            DS = new TestDataSource("jdbc:sqlite:" + f.getPath(), null, null);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    DataSource dataSource() {
        return DS;
    }

    @Override
    DatabaseDialect dialect() {
        return DatabaseDialect.SQLITE;
    }
}
