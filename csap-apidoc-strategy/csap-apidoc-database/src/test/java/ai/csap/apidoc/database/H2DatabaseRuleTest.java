package ai.csap.apidoc.database;

import javax.sql.DataSource;

class H2DatabaseRuleTest extends AbstractDatabaseRuleTest {

    private final DataSource dataSource = new SimpleDataSource(
            "jdbc:h2:mem:csap_apidoc_" + System.nanoTime() + ";DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=60000", "sa", "");

    @Override
    DataSource dataSource() {
        return dataSource;
    }

    @Override
    DatabaseDialect dialect() {
        return DatabaseDialect.H2;
    }
}
