package ai.csap.apidoc.strategy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import ai.csap.apidoc.ApiStrategyType;
import ai.csap.apidoc.SQLiteHandle;
import ai.csap.apidoc.SQLiteSchema;
import ai.csap.apidoc.annotation.ApiStatus;
import ai.csap.apidoc.autoconfigure.StrategyModel;
import ai.csap.apidoc.model.ParamGroupMethodProperty;

import cn.hutool.core.io.FileUtil;

/**
 * SQLite 模式存取 1.4.0 的接口状态:复用 api_method 已有的 status 列,备注用新加的 status_note 列;旧库自动补列。
 */
class SQLiteStatusTest {

    private final String dir = "sqlite-status-test-" + System.nanoTime();

    @AfterEach
    void cleanUp() {
        FileUtil.del(new File(classpathRoot(), dir));
    }

    private static String classpathRoot() {
        return URLDecoder.decode(Thread.currentThread().getContextClassLoader().getResource("").getPath(), StandardCharsets.UTF_8);
    }

    private StrategyModel model(String id) {
        return StrategyModel.builder().id(id).path(dir).fileName(SQLiteHandle.DEFAULT_DB_NAME)
                            .paramType(ApiStrategyType.SQL_LITE).build();
    }

    private static Map<String, Map<String, ParamGroupMethodProperty>> data(String method, ApiStatus status, String note) {
        ParamGroupMethodProperty p = new ParamGroupMethodProperty();
        p.getRequest().put("name", ParamGroupMethodProperty.ParamDataValidate.builder().required(true).description("名称")
                                                           .example("张三").template("name").mock("@oneOf(a,b)").build());
        p.setStatus(status);
        p.setStatusNote(note);
        Map<String, Map<String, ParamGroupMethodProperty>> d = new HashMap<>();
        d.computeIfAbsent("com.example.UserController", k -> new HashMap<>()).put(method, p);
        return d;
    }

    @Test
    void statusAndFieldExtrasSurviveSaveAndReload() {
        SQLiteParamGroupStrategy s = new SQLiteParamGroupStrategy(null);
        s.writeAndMerge(model("w-" + dir), data("add", ApiStatus.DEPRECATED, "请改用 /v2"), ApiStrategyType.SQL_LITE,
                "com.example.UserController", "add", true);
        s.writeAndMerge(model("w-" + dir), data("add", ApiStatus.DEPRECATED, "请改用 /v2"), ApiStrategyType.SQL_LITE,
                "com.example.UserController", "add", false);
        s.writeAndMerge(model("w-" + dir), data("list", null, null), ApiStrategyType.SQL_LITE,
                "com.example.UserController", "list", true);

        Map<String, Map<String, ParamGroupMethodProperty>> loaded = s.handle(model("r-" + dir), null, null, null);
        ParamGroupMethodProperty add = loaded.get("com.example.UserController").get("add");
        assertEquals(ApiStatus.DEPRECATED, add.getStatus());
        assertEquals("请改用 /v2", add.getStatusNote());
        ParamGroupMethodProperty.ParamDataValidate f = add.getRequest().get("name");
        assertEquals("张三", f.getExample());
        assertEquals("name", f.getTemplate());
        assertEquals("@oneOf(a,b)", f.getMock());
        ParamGroupMethodProperty list = loaded.get("com.example.UserController").get("list");
        assertNull(list.getStatus(), "没设状态(列默认值 DEFAULT)按没有覆盖处理");
        assertNull(list.getStatusNote());

        // 清除状态:写回 null,列回到默认
        s.writeAndMerge(model("w2-" + dir), data("add", null, null), ApiStrategyType.SQL_LITE,
                "com.example.UserController", "add", true);
        assertNull(s.handle(model("r2-" + dir), null, null, null).get("com.example.UserController").get("add").getStatus());
    }

    @Test
    void oldDatabaseWithoutStatusNoteColumnGetsItAdded() throws Exception {
        File db = new File(new File(classpathRoot(), dir), SQLiteHandle.DEFAULT_DB_NAME + ".db");
        FileUtil.mkdir(db.getParentFile());
        try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + db.getPath()); Statement st = c.createStatement()) {
            SQLiteSchema.ensureSchema(c);
            // 模拟 1.3.0 的库:没有 status_note 列
            st.execute("ALTER TABLE api_method DROP COLUMN status_note");
        }
        try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + db.getPath())) {
            SQLiteSchema.ensureSchema(c);
            boolean has = false;
            try (Statement st = c.createStatement(); java.sql.ResultSet rs = st.executeQuery("PRAGMA table_info(api_method)")) {
                while (rs.next()) {
                    has |= "status_note".equals(rs.getString("name"));
                }
            }
            assertTrue(has);
        }
    }
}
