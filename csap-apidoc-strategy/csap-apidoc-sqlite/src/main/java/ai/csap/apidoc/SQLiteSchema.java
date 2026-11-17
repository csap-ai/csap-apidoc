package ai.csap.apidoc;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * SQLite 参数分组库({@code paramType = SQL_LITE})的表结构。
 *
 * <p>早期版本依赖打包在 jar 里的二进制模板库 {@code sqlite-apidoc-example/apidoc-example.db},
 * 该文件已被删除;现在改为在首次使用时直接用 DDL 建表,不再依赖任何二进制模板。
 *
 * <p>{@link ai.csap.apidoc.strategy.SQLiteParamGroupStrategy} 实际只读写 {@link SQLiteHandle#API_METHOD}
 * 表的 {@code class_name / simple_name / name / request / response / create_time / update_time} 列,
 * 以 {@code (class_name, name)} 作为一条记录的业务主键。列定义沿用旧模板库的 api_method 表,
 * 但去掉了代码从不写入的列上的 {@code NOT NULL}(否则插入会违反约束),{@code key} 也不再要求唯一。
 * 所有语句都是幂等的,可以在每次打开连接时调用。
 *
 * @author yangchengfu
 */
public final class SQLiteSchema {

    static final String[] DDL = {
        "CREATE TABLE IF NOT EXISTS \"" + SQLiteHandle.API_METHOD + "\" (\n"
            + "  \"id\" INTEGER PRIMARY KEY AUTOINCREMENT,\n"
            + "  \"controller_id\" INTEGER NOT NULL DEFAULT 0,\n"
            + "  \"info_id\" INTEGER NOT NULL DEFAULT 0,\n"
            + "  \"value\" TEXT NOT NULL DEFAULT '',\n"
            + "  \"key\" TEXT,\n"
            + "  \"class_name\" TEXT NOT NULL DEFAULT '',\n"
            + "  \"simple_name\" TEXT NOT NULL DEFAULT '',\n"
            + "  \"name\" TEXT NOT NULL DEFAULT '',\n"
            + "  \"paths\" TEXT NOT NULL DEFAULT '',\n"
            + "  \"position\" INTEGER NOT NULL DEFAULT 0,\n"
            + "  \"api_path\" TEXT NOT NULL DEFAULT '',\n"
            + "  \"description\" TEXT NOT NULL DEFAULT '',\n"
            + "  \"methods\" TEXT NOT NULL DEFAULT '',\n"
            + "  \"search\" TEXT NOT NULL DEFAULT '',\n"
            + "  \"tags\" TEXT NOT NULL DEFAULT '',\n"
            + "  \"status\" TEXT NOT NULL DEFAULT 'DEFAULT',\n"
            + "  \"group\" TEXT NOT NULL DEFAULT '',\n"
            + "  \"version\" TEXT NOT NULL DEFAULT '',\n"
            + "  \"hidden\" INTEGER NOT NULL DEFAULT 0,\n"
            + "  \"create_time\" TEXT NOT NULL DEFAULT '',\n"
            + "  \"update_time\" TEXT NOT NULL DEFAULT '',\n"
            + "  \"create_by\" TEXT NOT NULL DEFAULT 'admin',\n"
            + "  \"update_by\" TEXT NOT NULL DEFAULT 'admin',\n"
            + "  \"request\" TEXT NOT NULL DEFAULT '{}',\n"
            + "  \"response\" TEXT NOT NULL DEFAULT '{}'\n"
            + ")",
        "CREATE INDEX IF NOT EXISTS \"index_class_name\" ON \"" + SQLiteHandle.API_METHOD
            + "\" (\"class_name\" ASC, \"name\")"
    };

    private SQLiteSchema() {
    }

    /**
     * 确保表结构存在(幂等)。
     *
     * @param connection 数据库连接
     * @throws SQLException 建表失败
     */
    public static void ensureSchema(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            for (String sql : DDL) {
                statement.execute(sql);
            }
        }
    }
}
