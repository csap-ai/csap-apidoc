package ai.csap.apidoc.mcp.audit;

import javax.sql.DataSource;

import ai.csap.apidoc.database.DatabaseDialect;

class H2JdbcAuditStoreTest extends AbstractJdbcAuditStoreTest {

    private static final DataSource DS = new TestDataSource(
            "jdbc:h2:mem:mcp_audit_" + System.nanoTime() + ";DB_CLOSE_DELAY=-1", "sa", "");

    @Override
    DataSource dataSource() {
        return DS;
    }

    @Override
    DatabaseDialect dialect() {
        return DatabaseDialect.H2;
    }
}
