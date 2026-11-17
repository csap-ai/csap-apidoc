package ai.csap.apidoc.strategy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import ai.csap.apidoc.ApiStrategyType;
import ai.csap.apidoc.DbUtilsCrudUtil;
import ai.csap.apidoc.SQLiteHandle;
import ai.csap.apidoc.autoconfigure.StrategyModel;
import ai.csap.apidoc.model.ParamGroupMethodProperty;

import cn.hutool.core.io.FileUtil;

/**
 * paramType=SQL_LITE:首次写入时库文件不存在,必须用 DDL 自动建库,不依赖已删除的二进制模板库。
 */
class SQLiteParamGroupStrategySchemaTest {

    private final String dir = "sqlite-schema-test-" + System.nanoTime();

    @AfterEach
    void cleanUp() {
        FileUtil.del(new File(classpathRoot(), dir));
    }

    private static String classpathRoot() {
        return URLDecoder.decode(Thread.currentThread().getContextClassLoader().getResource("").getPath(),
                StandardCharsets.UTF_8);
    }

    private StrategyModel model(String id) {
        return StrategyModel.builder()
                .id(id)
                .path(dir)
                .fileName(SQLiteHandle.DEFAULT_DB_NAME)
                .paramType(ApiStrategyType.SQL_LITE)
                .build();
    }

    @Test
    void firstWriteCreatesDatabaseFromDdlAndCanBeReadBack() throws Exception {
        File db = new File(new File(classpathRoot(), dir), SQLiteHandle.DEFAULT_DB_NAME + ".db");
        assertFalse(db.exists());

        SQLiteParamGroupStrategy strategy = new SQLiteParamGroupStrategy(null);
        Map<String, ParamGroupMethodProperty.ParamDataValidate> request = new HashMap<>();
        request.put("name", ParamGroupMethodProperty.ParamDataValidate.builder().required(true).description("名称").build());
        ParamGroupMethodProperty property = new ParamGroupMethodProperty();
        property.setRequest(request);
        Map<String, Map<String, ParamGroupMethodProperty>> data = new HashMap<>();
        data.computeIfAbsent("com.example.UserController", k -> new HashMap<>()).put("add", property);

        // 第一次写:插入
        strategy.writeAndMerge(model("write-" + dir), data, ApiStrategyType.SQL_LITE,
                "com.example.UserController", "add", true);
        assertTrue(db.isFile(), "首次写入应自动建库");

        // 第二次写:同一 (class_name, name) 走更新,不新增
        request.put("age", ParamGroupMethodProperty.ParamDataValidate.builder().required(false).build());
        strategy.writeAndMerge(model("write-" + dir), data, ApiStrategyType.SQL_LITE,
                "com.example.UserController", "add", true);

        try (Connection connection = DbUtilsCrudUtil.getConnection(db.getPath())) {
            List<Map<String, Object>> rows = DbUtilsCrudUtil.queryList(connection, SQLiteHandle.API_METHOD,
                    Set.of("class_name", "simple_name", "name", "request", "response"), new HashMap<>()).get();
            assertEquals(1, rows.size());
            assertEquals("com.example.UserController", rows.get(0).get("class_name"));
            assertEquals("add", rows.get(0).get("name"));
            assertTrue(String.valueOf(rows.get(0).get("request")).contains("age"));
        }

        // 用另一个缓存 key 读取,确认是从库里读出来的
        Map<String, Map<String, ParamGroupMethodProperty>> loaded = strategy.handle(model("read-" + dir), null, null, null);
        assertNotNull(loaded);
        ParamGroupMethodProperty readBack = loaded.get("com.example.UserController").get("add");
        assertTrue(readBack.getRequest().get("name").getRequired());
        assertEquals("名称", readBack.getRequest().get("name").getDescription());
        assertEquals(2, readBack.getRequest().size());
    }
}
