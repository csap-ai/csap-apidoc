package ai.csap.apidoc.database.autoconfigure;

import javax.sql.DataSource;

import ai.csap.apidoc.database.DatabaseDialect;

/**
 * 数据库模式使用的数据库:DataSource、方言、表前缀、服务标识,以及是否允许建表 / 是否只读。
 *
 * @author ycf
 * @since 1.5.0
 */
public final class DatabaseTarget {

    private final DataSource dataSource;
    private final DatabaseDialect dialect;
    private final String tablePrefix;
    private final String service;
    private final boolean initSchema;
    private final boolean readOnly;

    DatabaseTarget(DataSource dataSource, DatabaseDialect dialect, String tablePrefix, String service,
                   boolean initSchema, boolean readOnly) {
        this.dataSource = dataSource;
        this.dialect = dialect;
        this.tablePrefix = tablePrefix;
        this.service = service;
        this.initSchema = initSchema;
        this.readOnly = readOnly;
    }

    public DataSource getDataSource() {
        return dataSource;
    }

    public DatabaseDialect getDialect() {
        return dialect;
    }

    public String getTablePrefix() {
        return tablePrefix;
    }

    public String getService() {
        return service;
    }

    /**
     * csap.apidoc.database.init-schema:表不存在时是否自动建表
     */
    public boolean isInitSchema() {
        return initSchema;
    }

    /**
     * csap.apidoc.database.read-only:只读模式不建表
     */
    public boolean isReadOnly() {
        return readOnly;
    }
}
