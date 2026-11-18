package ai.csap.apidoc;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;

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
 * <p>1.1.0 时期按旧模板建的库,{@code api_method} 表有若干 {@code NOT NULL} 且没有默认值的列,
 * {@code key} 上还有唯一索引,导致插入失败。{@link #ensureSchema(Connection)} 会检测这种旧表,
 * 按 SQLite 推荐的方式迁移:建新表、拷贝数据、删除旧表、改名、重建索引,整个过程在一个事务里完成。
 * 迁移后的表不再满足检测条件,因此重复调用不会再次迁移。
 *
 * @author yangchengfu
 */
@Slf4j
public final class SQLiteSchema {

    private static final String TABLE = SQLiteHandle.API_METHOD;
    private static final String MIGRATING_TABLE = TABLE + "__migrating";

    /**
     * api_method 的列定义(列名 -> 定义),顺序即建表顺序
     */
    private static final Map<String, String> COLUMNS = new LinkedHashMap<>();

    /**
     * NOT NULL 列迁移时使用的默认值(旧数据里为 NULL 时用它补齐)
     */
    private static final Map<String, String> DEFAULTS = new LinkedHashMap<>();

    static {
        column("controller_id", "INTEGER", "0");
        column("info_id", "INTEGER", "0");
        column("value", "TEXT", "''");
        COLUMNS.put("key", "TEXT");
        column("class_name", "TEXT", "''");
        column("simple_name", "TEXT", "''");
        column("name", "TEXT", "''");
        column("paths", "TEXT", "''");
        column("position", "INTEGER", "0");
        column("api_path", "TEXT", "''");
        column("description", "TEXT", "''");
        column("methods", "TEXT", "''");
        column("search", "TEXT", "''");
        column("tags", "TEXT", "''");
        column("status", "TEXT", "'DEFAULT'");
        column("group", "TEXT", "''");
        column("version", "TEXT", "''");
        column("hidden", "INTEGER", "0");
        column("create_time", "TEXT", "''");
        column("update_time", "TEXT", "''");
        column("create_by", "TEXT", "'admin'");
        column("update_by", "TEXT", "'admin'");
        column("request", "TEXT", "'{}'");
        column("response", "TEXT", "'{}'");
        // 1.4.0:接口状态备注(状态本身用上面已有的 status 列)
        column("status_note", "TEXT", "''");
    }

    static final String[] DDL = {
        createTable(TABLE),
        "CREATE INDEX IF NOT EXISTS \"index_class_name\" ON \"" + TABLE + "\" (\"class_name\" ASC, \"name\")"
    };

    private SQLiteSchema() {
    }

    private static void column(String name, String type, String defaultValue) {
        COLUMNS.put(name, type + " NOT NULL DEFAULT " + defaultValue);
        DEFAULTS.put(name, defaultValue);
    }

    private static String quote(String identifier) {
        return "\"" + identifier.replace("\"", "\"\"") + "\"";
    }

    private static String createTable(String table) {
        return createTable(table, new LinkedHashMap<>());
    }

    private static String createTable(String table, Map<String, String> extraColumns) {
        StringBuilder sql = new StringBuilder("CREATE TABLE IF NOT EXISTS ").append(quote(table)).append(" (\n");
        sql.append("  \"id\" INTEGER PRIMARY KEY AUTOINCREMENT");
        COLUMNS.forEach((name, def) -> sql.append(",\n  ").append(quote(name)).append(' ').append(def));
        extraColumns.forEach((name, def) -> sql.append(",\n  ").append(quote(name)).append(' ').append(def));
        return sql.append("\n)").toString();
    }

    /**
     * 确保表结构存在且可写(幂等):表不存在时建表;是旧模板建的表时迁移。
     *
     * @param connection 数据库连接
     * @throws SQLException 建表或迁移失败
     */
    public static void ensureSchema(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute(DDL[0]);
        }
        if (needsMigration(connection)) {
            migrate(connection);
        }
        addMissingColumns(connection);
        try (Statement statement = connection.createStatement()) {
            for (int i = 1; i < DDL.length; i++) {
                statement.execute(DDL[i]);
            }
        }
    }

    /**
     * 旧库缺少新版本加的列时补上(如 1.4.0 的 status_note)
     */
    private static void addMissingColumns(Connection connection) throws SQLException {
        java.util.Set<String> existing = new java.util.HashSet<>();
        for (ColumnInfo c : columns(connection)) {
            existing.add(c.name);
        }
        try (Statement statement = connection.createStatement()) {
            for (Map.Entry<String, String> e : COLUMNS.entrySet()) {
                if (!existing.contains(e.getKey())) {
                    statement.execute("ALTER TABLE " + quote(TABLE) + " ADD COLUMN " + quote(e.getKey()) + " " + e.getValue());
                }
            }
        }
    }

    /**
     * 表信息(PRAGMA table_info 的一行)
     */
    static final class ColumnInfo {
        final String name;
        final String type;
        final boolean notNull;
        final String defaultValue;
        final boolean primaryKey;

        ColumnInfo(String name, String type, boolean notNull, String defaultValue, boolean primaryKey) {
            this.name = name;
            this.type = type;
            this.notNull = notNull;
            this.defaultValue = defaultValue;
            this.primaryKey = primaryKey;
        }
    }

    static List<ColumnInfo> columns(Connection connection) throws SQLException {
        List<ColumnInfo> columns = new ArrayList<>();
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("PRAGMA table_info(" + quote(TABLE) + ")")) {
            while (rs.next()) {
                columns.add(new ColumnInfo(rs.getString("name"), rs.getString("type"), rs.getInt("notnull") == 1,
                        rs.getString("dflt_value"), rs.getInt("pk") > 0));
            }
        }
        return columns;
    }

    /**
     * 唯一索引(不含主键)覆盖的列,按索引名分组
     */
    static Map<String, List<String>> uniqueIndexes(Connection connection) throws SQLException {
        List<String> names = new ArrayList<>();
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("PRAGMA index_list(" + quote(TABLE) + ")")) {
            while (rs.next()) {
                if (rs.getInt("unique") == 1 && !"pk".equals(rs.getString("origin"))) {
                    names.add(rs.getString("name"));
                }
            }
        }
        Map<String, List<String>> result = new LinkedHashMap<>();
        for (String name : names) {
            List<String> cols = new ArrayList<>();
            try (Statement statement = connection.createStatement();
                 ResultSet rs = statement.executeQuery("PRAGMA index_info(" + quote(name) + ")")) {
                while (rs.next()) {
                    cols.add(rs.getString("name"));
                }
            }
            result.put(name, cols);
        }
        return result;
    }

    /**
     * 是否是旧模板建的表:存在 NOT NULL 且没有默认值的非主键列,或 key 上有唯一约束
     *
     * @param connection 连接
     * @return true 表示需要迁移
     * @throws SQLException 读取表结构失败
     */
    public static boolean needsMigration(Connection connection) throws SQLException {
        for (ColumnInfo column : columns(connection)) {
            if (column.notNull && column.defaultValue == null && !column.primaryKey) {
                return true;
            }
        }
        return uniqueIndexes(connection).values().stream().anyMatch(cols -> cols.contains("key"));
    }

    private static void migrate(Connection connection) throws SQLException {
        List<ColumnInfo> oldColumns = columns(connection);
        // 旧表里有、新定义里没有的列原样保留(去掉无默认值的 NOT NULL)
        Map<String, String> extra = new LinkedHashMap<>();
        for (ColumnInfo column : oldColumns) {
            if (column.primaryKey || "id".equals(column.name) || COLUMNS.containsKey(column.name)) {
                continue;
            }
            String def = column.type == null || column.type.isEmpty() ? "" : column.type;
            if (column.defaultValue != null) {
                def += (column.notNull ? " NOT NULL" : "") + " DEFAULT " + column.defaultValue;
            }
            extra.put(column.name, def.trim());
        }
        // 旧表上的非唯一索引(index_key 这类唯一索引不再重建)
        Map<String, List<String>> unique = uniqueIndexes(connection);
        List<String> indexSql = new ArrayList<>();
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("SELECT name, sql FROM sqlite_master WHERE type = 'index' AND tbl_name = '"
                     + TABLE + "' AND sql IS NOT NULL")) {
            while (rs.next()) {
                if (!unique.containsKey(rs.getString("name"))) {
                    indexSql.add(rs.getString("sql"));
                }
            }
        }

        List<String> targets = new ArrayList<>();
        List<String> sources = new ArrayList<>();
        for (ColumnInfo column : oldColumns) {
            targets.add(quote(column.name));
            String source = quote(column.name);
            if (DEFAULTS.containsKey(column.name)) {
                source = "COALESCE(" + source + ", " + DEFAULTS.get(column.name) + ")";
            }
            sources.add(source);
        }

        boolean autoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try (Statement statement = connection.createStatement()) {
            statement.execute("DROP TABLE IF EXISTS " + quote(MIGRATING_TABLE));
            statement.execute(createTable(MIGRATING_TABLE, extra));
            statement.execute("INSERT INTO " + quote(MIGRATING_TABLE) + " (" + String.join(", ", targets) + ") SELECT "
                    + String.join(", ", sources) + " FROM " + quote(TABLE));
            statement.execute("DROP TABLE " + quote(TABLE));
            statement.execute("ALTER TABLE " + quote(MIGRATING_TABLE) + " RENAME TO " + quote(TABLE));
            for (String sql : indexSql) {
                statement.execute(sql);
            }
            connection.commit();
            log.info("sqlite table {} migrated from legacy schema, columns: {}", TABLE,
                    oldColumns.stream().map(c -> c.name).collect(Collectors.joining(",")));
        } catch (SQLException e) {
            connection.rollback();
            throw e;
        } finally {
            connection.setAutoCommit(autoCommit);
        }
    }
}
