package ai.csap.apidoc.strategy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import ai.csap.apidoc.ApiStrategyType;
import ai.csap.apidoc.DbUtilsCrudUtil;
import ai.csap.apidoc.SQLiteHandle;
import ai.csap.apidoc.SQLiteSchema;
import ai.csap.apidoc.autoconfigure.StrategyModel;
import ai.csap.apidoc.model.ParamGroupMethodProperty;

import cn.hutool.core.io.FileUtil;

/**
 * 1.1.0 时期按旧模板建的 api_method 表(若干列 NOT NULL 无默认值、key 唯一索引)在打开连接时自动迁移。
 */
class SQLiteLegacySchemaMigrationTest {

    /**
     * 旧模板库 sqlite-apidoc-example/apidoc-example.db 中 api_method 的原始 DDL
     */
    private static final String LEGACY_DDL = "CREATE TABLE \"api_method\" (\n"
            + "  \"id\" INTEGER PRIMARY KEY AUTOINCREMENT,\n"
            + "  \"controller_id\" INTEGER NOT NULL,\n"
            + "  \"info_id\" INTEGER NOT NULL,\n"
            + "  \"value\" TEXT NOT NULL,\n"
            + "  \"key\" TEXT NOT NULL,\n"
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
            + "  \"group\" TEXT NOT NULL,\n"
            + "  \"version\" TEXT NOT NULL,\n"
            + "  \"hidden\" INTEGER NOT NULL DEFAULT 0,\n"
            + "  \"create_time\" TEXT NOT NULL DEFAULT '',\n"
            + "  \"update_time\" TEXT NOT NULL DEFAULT '',\n"
            + "  \"create_by\" TEXT NOT NULL DEFAULT 'admin',\n"
            + "  \"update_by\" TEXT NOT NULL DEFAULT 'admin',\n"
            + "  \"request\" TEXT NOT NULL DEFAULT '{}',\n"
            + "  \"response\" TEXT NOT NULL DEFAULT '{}'\n"
            + ")";

    private final String dir = "sqlite-legacy-test-" + System.nanoTime();

    @AfterEach
    void cleanUp() {
        FileUtil.del(new File(classpathRoot(), dir));
    }

    private static String classpathRoot() {
        return URLDecoder.decode(Thread.currentThread().getContextClassLoader().getResource("").getPath(),
                StandardCharsets.UTF_8);
    }

    private File dbFile() {
        return new File(new File(classpathRoot(), dir), SQLiteHandle.DEFAULT_DB_NAME + ".db");
    }

    private File createLegacyDb() throws Exception {
        File db = dbFile();
        try (Connection connection = DbUtilsCrudUtil.getConnection(db.getPath());
             Statement statement = connection.createStatement()) {
            statement.execute(LEGACY_DDL);
            statement.execute("CREATE UNIQUE INDEX \"index_key\" ON \"api_method\" (\"key\" ASC)");
            statement.execute("CREATE INDEX \"index_class_name\" ON \"api_method\" (\"class_name\" ASC, \"name\")");
            statement.execute("INSERT INTO api_method (controller_id, info_id, value, key, class_name, simple_name, name, "
                    + "\"group\", version, request, response) VALUES (1, 1, '旧接口', 'com.example.OldController.list', "
                    + "'com.example.OldController', 'OldController', 'list', 'default', 'default', "
                    + "'{\"name\":{\"required\":true,\"description\":\"旧规则\"}}', '{}')");
            // 迁移前确认:不写 key 等列的插入会失败
            assertThrows(SQLException.class, () -> statement.execute(
                    "INSERT INTO api_method (class_name, simple_name, name) VALUES ('a', 'a', 'b')"));
        }
        return db;
    }

    private static String tableSql(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("SELECT sql FROM sqlite_master WHERE type = 'table' AND name = 'api_method'")) {
            return rs.next() ? rs.getString(1) : null;
        }
    }

    private static int count(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement(); ResultSet rs = statement.executeQuery(sql)) {
            rs.next();
            return rs.getInt(1);
        }
    }

    @Test
    void legacyTableIsMigratedAndDataKept() throws Exception {
        File db = createLegacyDb();
        try (Connection connection = DbUtilsCrudUtil.getConnection(db.getPath())) {
            assertTrue(SQLiteSchema.needsMigration(connection));
            SQLiteSchema.ensureSchema(connection);
            assertFalse(SQLiteSchema.needsMigration(connection));

            // 旧数据保留,id 不变
            assertEquals(1, count(connection, "SELECT COUNT(*) FROM api_method WHERE id = 1 AND key = 'com.example.OldController.list'"
                    + " AND value = '旧接口' AND request LIKE '%旧规则%'"));
            // 不写 key / controller_id / group 等列也能插入,且 key 不再唯一
            try (Statement statement = connection.createStatement()) {
                statement.execute("INSERT INTO api_method (class_name, simple_name, name) VALUES ('a', 'a', 'b')");
                statement.execute("INSERT INTO api_method (class_name, simple_name, name) VALUES ('a', 'a', 'c')");
            }
            assertEquals(3, count(connection, "SELECT COUNT(*) FROM api_method"));
            assertEquals(2, count(connection, "SELECT COUNT(*) FROM api_method WHERE key IS NULL AND \"group\" = ''"));
            // 非唯一索引保留,唯一索引去掉,临时表不残留
            assertEquals(1, count(connection, "SELECT COUNT(*) FROM sqlite_master WHERE type = 'index' AND name = 'index_class_name'"));
            assertEquals(0, count(connection, "SELECT COUNT(*) FROM sqlite_master WHERE name = 'index_key'"));
            assertEquals(0, count(connection, "SELECT COUNT(*) FROM sqlite_master WHERE name LIKE '%migrating%'"));
            // 自增序列从旧数据之后继续
            assertEquals(1, count(connection, "SELECT COUNT(*) FROM api_method WHERE name = 'b' AND id = 2"));

            // 幂等:再次调用不再迁移,表结构不变
            String sql = tableSql(connection);
            SQLiteSchema.ensureSchema(connection);
            SQLiteSchema.ensureSchema(connection);
            assertEquals(sql, tableSql(connection));
            assertEquals(3, count(connection, "SELECT COUNT(*) FROM api_method"));
        }
    }

    @Test
    void extraColumnsOfLegacyTableAreKept() throws Exception {
        File db = dbFile();
        try (Connection connection = DbUtilsCrudUtil.getConnection(db.getPath());
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE api_method (id INTEGER PRIMARY KEY AUTOINCREMENT, class_name TEXT, name TEXT, "
                    + "request TEXT, response TEXT, remark TEXT NOT NULL, owner TEXT DEFAULT 'me')");
            statement.execute("INSERT INTO api_method (class_name, name, request, response, remark) VALUES ('c', 'm', NULL, '{}', 'r')");
            SQLiteSchema.ensureSchema(connection);
            // 旧值为 NULL 的列按新定义的默认值补齐;额外的列保留且去掉无默认值的 NOT NULL
            assertEquals(1, count(connection, "SELECT COUNT(*) FROM api_method WHERE request = '{}' AND remark = 'r' AND owner = 'me'"));
            statement.execute("INSERT INTO api_method (class_name, name) VALUES ('c', 'n')");
            assertFalse(SQLiteSchema.needsMigration(connection));
        }
    }

    @Test
    void writeThroughStrategyWorksOnLegacyDatabase() throws Exception {
        createLegacyDb();
        StrategyModel model = StrategyModel.builder()
                .id("legacy-" + dir)
                .path(dir)
                .fileName(SQLiteHandle.DEFAULT_DB_NAME)
                .paramType(ApiStrategyType.SQL_LITE)
                .build();
        SQLiteParamGroupStrategy strategy = new SQLiteParamGroupStrategy(null);
        Map<String, ParamGroupMethodProperty.ParamDataValidate> request = new HashMap<>();
        request.put("name", ParamGroupMethodProperty.ParamDataValidate.builder().required(true).build());
        ParamGroupMethodProperty property = new ParamGroupMethodProperty();
        property.setRequest(request);
        Map<String, Map<String, ParamGroupMethodProperty>> data = new HashMap<>();
        data.computeIfAbsent("com.example.UserController", k -> new HashMap<>()).put("add", property);

        strategy.writeAndMerge(model, data, ApiStrategyType.SQL_LITE, "com.example.UserController", "add", true);

        try (Connection connection = DbUtilsCrudUtil.getConnection(dbFile().getPath())) {
            assertEquals(1, count(connection, "SELECT COUNT(*) FROM api_method WHERE class_name = 'com.example.UserController' AND name = 'add'"));
            assertEquals(2, count(connection, "SELECT COUNT(*) FROM api_method"));
        }
    }
}
