package sqllite;

import java.sql.Connection;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.google.common.collect.Sets;

import ai.csap.apidoc.DbUtilsCrudUtil;
import ai.csap.apidoc.SQLiteHandle;

import cn.hutool.core.map.MapUtil;
import lombok.SneakyThrows;

/**
 * Demonstrates CRUD with Apache Commons DbUtils against SQLite.
 */
public class DbUtilsCrudTest implements SQLiteHandle {

    private static final String DB_PATH = "/Users/ycf/Documents/产品/csap/framework/csap-framework-apidoc/src/test/resources/apidoc.db";
    private static final String URL = "jdbc:sqlite:" + DB_PATH;

    @Test
    @SneakyThrows
    public void save() {
        try (Connection connection = DbUtilsCrudUtil.getConnection(DB_PATH)) {
            Map<String, Object> build = MapUtil.<String, Object>builder(new LinkedHashMap<>()).put("k", "name").put("v", "value").build();
            Map<String, Object> build2 = MapUtil.<String, Object>builder(new LinkedHashMap<>()).put("k", "name11").put("v", "value22").build();
            List<Map<String, Object>> data = new ArrayList<>();
            data.add(build);
            data.add(build2);
            int demo_kv = DbUtilsCrudUtil.saveList(connection, "demo_kv", data).orElse(0);
            System.out.println(demo_kv);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Test
    @SneakyThrows
    public void remove() {
        try (Connection connection = DbUtilsCrudUtil.getConnection(DB_PATH)) {
            Integer count = DbUtilsCrudUtil.removeByWhere(connection, "demo_kv",
                    MapUtil.<String, Object>builder("k", "name").build()).orElse(0);
            System.out.println(count);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Test
    @SneakyThrows
    public void update() {
        try (Connection connection = DbUtilsCrudUtil.getConnection(DB_PATH)) {
            Integer count = DbUtilsCrudUtil.updateByWhere(connection, "demo_kv",
                    MapUtil.<String, Object>builder("v", "name101").build(),
                    MapUtil.<String, Object>builder("k", "name").build()).orElse(0);
            System.out.println(count);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Test
    @SneakyThrows
    public void query() {
        try (Connection connection = DbUtilsCrudUtil.getConnection(DB_PATH)) {
            List<Map<String, Object>> maps = DbUtilsCrudUtil.queryList(connection, "demo_kv", Sets.newHashSet("k", "v"), MapUtil.<String, Object>builder("k", "name").build()).orElse(Collections.emptyList());
            System.out.println(maps);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Test
    @SneakyThrows
    public void count() {
        try (Connection connection = DbUtilsCrudUtil.getConnection(DB_PATH)) {
            Integer count = DbUtilsCrudUtil.count(connection, "demo_kv", MapUtil.<String, Object>builder("k", "name").build()).orElse(0);
            System.out.println(count);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    @Override
    public String fileName() {
        return "";
    }
}
