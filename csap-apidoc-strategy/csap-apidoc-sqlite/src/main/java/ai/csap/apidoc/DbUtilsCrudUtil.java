package ai.csap.apidoc;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.dbutils.QueryRunner;
import org.apache.commons.dbutils.handlers.MapListHandler;
import org.apache.commons.dbutils.handlers.ScalarHandler;

import com.google.common.collect.Lists;

import ai.csap.apidoc.core.ApidocOptional;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.map.MapUtil;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

/**
 * Lightweight SQLite CRUD helpers using Apache Commons DbUtils.
 */
@Slf4j
public class DbUtilsCrudUtil {

    public static final String URL = "jdbc:sqlite:%s";

    /**
     * 获取链接
     *
     * @param path db路径
     * @return 结果
     */
    @SneakyThrows
    public static Connection getConnection(String path) {
        Files.createDirectories(Paths.get(path).getParent());
        Connection conn = DriverManager.getConnection(String.format(URL, path));
        try (Statement s = conn.createStatement()) {
            s.execute("PRAGMA journal_mode=WAL;");
            s.execute("PRAGMA busy_timeout=5000;");
        }
        return conn;
    }

    /**
     * 保存列表
     *
     * @param conn      数据源连接
     * @param tableName 表名
     * @param params    参数列表 key = Column,value = Column value
     * @return 操作结果
     */
    @SneakyThrows
    public static ApidocOptional<Integer> save(Connection conn, String tableName, Map<String, Object> params) {
        return saveList(conn, tableName, Lists.newArrayList(params));
    }

    /**
     * 保存列表
     *
     * @param conn      数据源连接
     * @param tableName 表名
     * @param params    参数列表 key = Column,value = Column value
     * @return 操作结果
     */
    @SneakyThrows
    public static ApidocOptional<Integer> saveList(Connection conn, String tableName, List<Map<String, Object>> params) {
        QueryRunner qr = new QueryRunner();
        int r = 0;
        for (Map<String, Object> param : params) {
            String k = CollectionUtil.join(param.keySet(), ",");
            String v = param.keySet().stream().map(i -> "?").collect(Collectors.joining(","));
            Object[] values = param.values().toArray();
            String format = String.format("INSERT INTO %s(%s) VALUES(%s)", tableName, k, v);
            if (log.isDebugEnabled()) {
                log.debug("saveList sql={} ", format);
                log.debug("saveList values={}", Arrays.toString(values));
            }
            r = r + qr.update(conn, format, values);
        }
        if (log.isDebugEnabled()) {
            log.debug("saveList result {}", r);
        }
        return ApidocOptional.ofNullable(r);
    }

    /**
     * 查询
     *
     * @param conn      链接
     * @param tableName 表名
     * @param select    select 字段
     * @param where     where 条件
     * @return 结果
     */
    @SneakyThrows
    public static ApidocOptional<List<Map<String, Object>>> queryList(Connection conn, String tableName, Set<String> select, Map<String, Object> where) {
        QueryRunner qr = new QueryRunner();
        String sql = "SELECT %s FROM %s ";
        sql = String.format(sql, CollectionUtil.join(select, ","), tableName);
        List<Object> values = new ArrayList<>();
        sql = formatWhereSql(sql, values, where);
        if (log.isDebugEnabled()) {
            log.debug("queryList sql: {} ", sql);
            log.debug("queryList where: {}", values);
        }
        return ApidocOptional.ofNullable(qr.query(conn, sql, new MapListHandler(), values.toArray()));
    }

    /**
     * 查询条数
     *
     * @param conn      链接
     * @param tableName 表名
     * @param where     where 条件
     * @return 结果
     */
    @SneakyThrows
    public static ApidocOptional<Integer> count(Connection conn, String tableName, Map<String, Object> where) {
        QueryRunner qr = new QueryRunner();
        String sql = "SELECT COUNT(*) FROM %s ";
        sql = String.format(sql, tableName);
        List<Object> values = new ArrayList<>();
        sql = formatWhereSql(sql, values, where);
        Integer result = qr.query(conn, sql, new ScalarHandler<Integer>(), values.toArray());
        if (log.isDebugEnabled()) {
            log.debug("count result {}", result);
        }
        return ApidocOptional.ofNullable(result);
    }

    /**
     * 根据条件修改
     *
     * @param conn      数据库连接
     * @param tableName 表名
     * @param set       修改数据
     * @param where     条件
     * @return 返回修改的条数
     */
    @SneakyThrows
    public static ApidocOptional<Integer> updateByWhere(Connection conn, String tableName, Map<String, Object> set, Map<String, Object> where) {
        QueryRunner qr = new QueryRunner();
        String sql = "UPDATE %s SET %s";
        sql = String.format(sql, tableName, set.keySet().stream().map(i -> i + " = ?").collect(Collectors.joining(",")));
        List<Object> values = new ArrayList<>(set.values());
        sql = formatWhereSql(sql, values, where);
        if (log.isDebugEnabled()) {
            log.debug("updateByWhere sql: {} ", sql);
            log.debug("updateByWhere where: {}", values);
        }
        return ApidocOptional.ofNullable(qr.update(conn, sql, values.toArray()))
                .when(i -> log.isDebugEnabled(), i -> log.debug("updateByWhere result {}", i));
    }

    /**
     * 格式化sql
     *
     * @param sql    sql
     * @param values 值
     * @param where  where条件
     * @return 结果
     */
    private static String formatWhereSql(String sql, List<Object> values, Map<String, Object> where) {
        if (MapUtil.isNotEmpty(where)) {
            String whereSql = where.keySet().stream().map(i -> i + " = ? ").collect(Collectors.joining(" and "));
            sql += " where " + whereSql;
            values.addAll(where.values());
        }
        return sql;
    }

    /**
     * 根据条件删除
     *
     * @param conn      数据库连接
     * @param tableName 表
     * @param where     条件
     * @return 删除条数
     */
    @SneakyThrows
    public static ApidocOptional<Integer> removeByWhere(Connection conn, String tableName, Map<String, Object> where) {
        QueryRunner qr = new QueryRunner();
        String sql = "DELETE FROM " + tableName;
        List<Object> values = new ArrayList<>();
        sql = formatWhereSql(sql, values, where);
        if (log.isDebugEnabled()) {
            log.debug("removeByWhere sql: {} ", sql);
            log.debug("removeByWhere where: {}", values);
        }
        return ApidocOptional.ofNullable(qr.update(conn, sql, values.toArray()))
                .when(i -> log.isDebugEnabled(), i -> log.debug("removeByWhere result {}", i));
    }
}
