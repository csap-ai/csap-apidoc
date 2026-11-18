package ai.csap.apidoc.database;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;

import ai.csap.apidoc.ApiStrategyType;
import ai.csap.apidoc.annotation.ParamType;
import ai.csap.apidoc.autoconfigure.StrategyModel;
import ai.csap.apidoc.model.CsapDocMethod;
import ai.csap.apidoc.model.ParamGroupMethodProperty;
import ai.csap.apidoc.model.RuleChange;
import ai.csap.apidoc.strategy.store.RuleJson;
import ai.csap.apidoc.util.RuleStorageNotWritableException;
import ai.csap.validation.factory.IValidateFactory;
import ai.csap.validation.factory.Validate;

/**
 * 数据库模式的行为测试;H2、MySQL 8、PostgreSQL 16 各跑一遍(后两者需要 Docker)。
 */
abstract class AbstractDatabaseRuleTest {

    static final String CLASS_NAME = "com.example.UserController";
    private static final AtomicInteger SEQ = new AtomicInteger();

    abstract DataSource dataSource();

    abstract DatabaseDialect dialect();

    /**
     * 每个测试一个独立的表前缀
     */
    String newPrefix() {
        return "t" + System.nanoTime() % 1_000_000_000L + "_" + SEQ.incrementAndGet() + "_";
    }

    DatabaseRuleStore store(String prefix, String service) {
        return new DatabaseRuleStore(dataSource(), dialect(), prefix, service);
    }

    DatabaseParamGroupStrategy strategy(DatabaseRuleStore store, boolean readOnly, Duration poll) {
        return new DatabaseParamGroupStrategy(annotationFactory(), () -> store, true, readOnly, poll);
    }

    static ParamGroupMethodProperty property(String requestKey, String description) {
        ParamGroupMethodProperty p = new ParamGroupMethodProperty();
        ParamGroupMethodProperty.ParamDataValidate v = new ParamGroupMethodProperty.ParamDataValidate();
        v.setRequired(true);
        v.setInclude(true);
        v.setParamType(ParamType.QUERY);
        v.setDescription(description);
        v.getValidate().add(new Validate.ConstraintValidatorField().setDescr("长度").setLevel(2).setMessage("太长"));
        p.getRequest().put(requestKey, v);
        ParamGroupMethodProperty.ParamDataValidate r = new ParamGroupMethodProperty.ParamDataValidate();
        r.setDescription("响应 " + description);
        p.getResponse().put("data", r);
        return p;
    }

    static Map<String, Map<String, ParamGroupMethodProperty>> data(String cls, String method, ParamGroupMethodProperty p) {
        Map<String, ParamGroupMethodProperty> m = new java.util.HashMap<>();
        m.put(method, p);
        Map<String, Map<String, ParamGroupMethodProperty>> d = new java.util.HashMap<>();
        d.put(cls, m);
        return d;
    }

    static void save(DatabaseParamGroupStrategy s, String method, ParamGroupMethodProperty p) {
        s.writeAndMerge(model(), data(CLASS_NAME, method, p), ApiStrategyType.DATABASE, CLASS_NAME, method, true);
    }

    static StrategyModel model() {
        return StrategyModel.builder().id("db-test").paramType(ApiStrategyType.DATABASE).build();
    }

    static CsapDocMethod docMethod(String method) {
        CsapDocMethod m = new CsapDocMethod();
        m.setClassName(CLASS_NAME);
        m.setName(method);
        return m;
    }

    int count(String sql, Object... args) throws SQLException {
        try (Connection c = dataSource().getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            for (int i = 0; i < args.length; i++) {
                ps.setObject(i + 1, args[i]);
            }
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    void exec(String sql) throws SQLException {
        try (Connection c = dataSource().getConnection(); Statement st = c.createStatement()) {
            st.execute(sql);
        }
    }

    // ------------------------------------------------------------------ 建表

    @Test
    void createsTablesAndIsIdempotent() throws Exception {
        String p = newPrefix();
        DatabaseRuleStore s = store(p, "svc");
        s.ensureSchema(true);
        s.ensureSchema(true);
        assertEquals(0, count("SELECT COUNT(*) FROM " + p + "rule"));
        assertEquals(0, count("SELECT COUNT(*) FROM " + p + "history"));
        assertEquals(1, count("SELECT COUNT(*) FROM " + p + "meta WHERE service = ?", "svc"));
        assertEquals(0L, s.version());
        // 与 Go 共用的列都在
        count("SELECT COUNT(service) + COUNT(rule_key) + COUNT(rules) + COUNT(version) + COUNT(updated_by) + COUNT(updated_at) FROM " + p + "rule");
        count("SELECT COUNT(id) + COUNT(before_rules) + COUNT(after_rules) FROM " + p + "history");
    }

    @Test
    void doesNotCreateTablesWhenInitSchemaIsOff() {
        DatabaseRuleStore s = store(newPrefix(), "svc");
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> s.ensureSchema(false));
        assertTrue(e.getMessage().contains("不存在"), e.getMessage());
        assertTrue(e.getMessage().contains(".sql"), e.getMessage());
    }

    @Test
    void oldGoTableWithoutServiceColumnFailsClearly() throws Exception {
        String p = newPrefix();
        // csap-apidoc-go 早期版本的表:没有 service 列
        exec("CREATE TABLE " + p + "rule (rule_key VARCHAR(255) NOT NULL PRIMARY KEY, rules VARCHAR(1000), version BIGINT)");
        DatabaseRuleStore s = store(p, "svc");
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> s.ensureSchema(true));
        assertTrue(e.getMessage().contains("service"), e.getMessage());
        assertTrue(e.getMessage().contains("csap-apidoc-go"), e.getMessage());
        // 只读检查同样报错
        assertThrows(IllegalStateException.class, () -> s.checkSchema());
        // 经策略使用时同样是明确的错误
        DatabaseParamGroupStrategy strategy = strategy(s, false, Duration.ZERO);
        assertThrows(IllegalStateException.class, () -> strategy.getHandle(model(), docMethod("x")));
    }

    // ------------------------------------------------------------------ 保存 / 删除 / 记录

    @Test
    void saveUpdateDeleteAndHistory() throws Exception {
        String p = newPrefix();
        DatabaseRuleStore s = store(p, "svc");
        s.ensureSchema(true);
        String key = CLASS_NAME + ".add";

        assertEquals(1L, s.save(key, "{\"request\":{\"a\":{}}}", "alice"));
        assertEquals(2L, s.save(key, "{\"request\":{\"a\":{\"required\":true}}}", "bob"));
        // 内容不变:不留记录、版本号不变
        assertEquals(-1L, s.save(key, "{\"request\":{\"a\":{\"required\":true}}}", "bob"));
        // 只有格式不同(空白、键顺序)也算不变
        assertEquals(-1L, s.save(key, "{ \"request\" : { \"a\" : { \"required\" : true } } }", "bob"));
        assertEquals(2L, s.version());
        assertEquals(3L, s.save(key, null, "carol"));
        // 删除一个不存在的:不变
        assertEquals(-1L, s.save(key, null, "carol"));
        assertEquals(3L, s.version());

        assertEquals(0, count("SELECT COUNT(*) FROM " + p + "rule"));
        List<RuleChange> history = s.history(key, 10);
        assertEquals(3, history.size());
        // 最新的在前
        assertEquals(3L, history.get(0).getVersion());
        assertEquals("carol", history.get(0).getUpdatedBy());
        assertEquals("{\"request\":{\"a\":{\"required\":true}}}", history.get(0).getBefore());
        assertNull(history.get(0).getAfter());
        assertEquals("bob", history.get(1).getUpdatedBy());
        assertEquals("{\"request\":{\"a\":{}}}", history.get(1).getBefore());
        assertNull(history.get(2).getBefore());
        assertEquals("alice", history.get(2).getUpdatedBy());
        assertTrue(history.get(2).getUpdatedAt() > 1_600_000_000_000L, "updated_at 应为毫秒时间戳");
        assertEquals(2, s.history(key, 2).size());
        assertEquals(3, s.history(null, 0).size());
    }

    @Test
    void rollbackRestoresPreviousContentAndLeavesARecord() throws Exception {
        DatabaseRuleStore s = store(newPrefix(), "svc");
        s.ensureSchema(true);
        String key = CLASS_NAME + ".add";
        s.save(key, "{\"request\":{\"a\":{}}}", "alice");
        s.save(key, "{\"request\":{\"b\":{}}}", "bob");
        long secondChange = s.history(key, 1).get(0).getId();

        assertEquals(3L, s.rollback(secondChange, "ops"));
        assertEquals("{\"request\":{\"a\":{}}}", s.load().rules().get(key));
        List<RuleChange> history = s.history(key, 10);
        assertEquals(3, history.size());
        assertEquals("ops", history.get(0).getUpdatedBy());
        // 回滚"新增"那条记录 = 删除
        long firstChange = history.get(2).getId();
        assertEquals(4L, s.rollback(firstChange, "ops"));
        assertFalse(s.load().rules().containsKey(key));
        assertThrows(IllegalArgumentException.class, () -> s.rollback(987654321L, "ops"));
    }

    @Test
    void servicesAreIsolated() throws Exception {
        String p = newPrefix();
        DatabaseRuleStore a = store(p, "svc-a");
        DatabaseRuleStore b = store(p, "svc-b");
        a.ensureSchema(true);
        b.ensureSchema(true);
        a.save(CLASS_NAME + ".add", "{\"request\":{}}", "x");
        b.save(CLASS_NAME + ".add", "{\"response\":{}}", "x");
        b.save(CLASS_NAME + ".del", "{\"response\":{}}", "x");
        assertEquals(1, a.load().rules().size());
        assertEquals(2, b.load().rules().size());
        assertEquals(1L, a.version());
        assertEquals(2L, b.version());
        assertEquals(1, a.history(null, 10).size());
    }

    @Test
    void concurrentSavesNeitherLoseNorRepeatVersions() throws Exception {
        String p = newPrefix();
        DatabaseRuleStore s = store(p, "svc");
        s.ensureSchema(true);
        int threads = 8;
        int each = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<List<Long>>> futures = new ArrayList<>();
        for (int t = 0; t < threads; t++) {
            int id = t;
            futures.add(pool.submit(() -> {
                start.await();
                List<Long> versions = new ArrayList<>();
                for (int i = 0; i < each; i++) {
                    versions.add(s.save(CLASS_NAME + ".m" + id + "_" + i, "{\"request\":{\"k\":{}}}", "t" + id));
                }
                return versions;
            }));
        }
        start.countDown();
        Set<Long> all = new HashSet<>();
        for (Future<List<Long>> f : futures) {
            for (Long v : f.get(120, TimeUnit.SECONDS)) {
                assertTrue(all.add(v), "版本号重复: " + v);
            }
        }
        pool.shutdown();
        int total = threads * each;
        assertEquals(total, all.size());
        for (long v = 1; v <= total; v++) {
            assertTrue(all.contains(v), "版本号缺失: " + v);
        }
        assertEquals(total, s.version());
        assertEquals(total, count("SELECT COUNT(*) FROM " + p + "rule"));
        assertEquals(total, count("SELECT COUNT(*) FROM " + p + "history"));
        assertEquals(total, count("SELECT COUNT(DISTINCT version) FROM " + p + "history"));
    }

    // ------------------------------------------------------------------ 策略

    @Test
    void strategyLoadsSavesAndLooksUp() throws Exception {
        String p = newPrefix();
        DatabaseParamGroupStrategy s = strategy(store(p, "svc"), false, Duration.ZERO);
        assertTrue(s.getHandle(model(), docMethod("add")).isEmpty());

        save(s, "add", property("name", "姓名"));
        ParamGroupMethodProperty.ParamDataValidate hit = s.paramDataValidate(model(), "name", docMethod("add"), true);
        assertNotNull(hit);
        assertEquals("姓名", hit.getDescription());
        assertEquals(ParamType.QUERY, hit.getParamType());
        assertEquals(1, hit.getValidate().size());
        assertEquals("太长", hit.getValidate().get(0).getMessage());
        assertEquals("响应 姓名", s.paramDataValidate(model(), "data", docMethod("add"), false).getDescription());
        assertNull(s.paramDataValidate(model(), "nothing", docMethod("add"), true));

        // 重启(新策略实例)后仍在
        DatabaseParamGroupStrategy again = strategy(store(p, "svc"), false, Duration.ZERO);
        assertEquals("姓名", again.paramDataValidate(model(), "name", docMethod("add"), true).getDescription());

        // 规则清空 = 删除这一行
        save(s, "add", new ParamGroupMethodProperty());
        assertTrue(s.getHandle(model(), docMethod("add")).isEmpty());
        List<RuleChange> history = again.store().history(null, 10);
        assertEquals(2, history.size());
        assertNull(history.get(0).getAfter());
    }

    @Test
    void unchangedSaveLeavesNoRecord() throws Exception {
        DatabaseParamGroupStrategy s = strategy(store(newPrefix(), "svc"), false, Duration.ZERO);
        save(s, "add", property("name", "姓名"));
        long version = s.store().history(null, 10).get(0).getVersion();
        save(s, "add", property("name", "姓名"));
        assertEquals(1, s.store().history(null, 10).size());
        assertEquals(version, s.loadedVersion());
    }

    @Test
    void annotationRulesAreNotAccumulatedIntoTheCache() throws Exception {
        DatabaseParamGroupStrategy s = strategy(store(newPrefix(), "svc"), false, Duration.ZERO);
        save(s, "add", property("name", "姓名"));
        Field field = AbstractDatabaseRuleTest.class.getDeclaredField("name");
        for (int i = 0; i < 3; i++) {
            ParamGroupMethodProperty.ParamDataValidate r = s.paramRequestGroup(model(), "name", docMethod("add"), null, field);
            assertEquals(2, r.getValidate().size(), "库里一条 + 注解一条,不随调用次数增加");
            assertEquals("annotationNotNull", r.getValidate().get(0).getDescr());
            assertEquals("长度", r.getValidate().get(1).getDescr());
        }
        assertEquals(1, s.paramDataValidate(model(), "name", docMethod("add"), true).getValidate().size());
    }

    @SuppressWarnings("unused")
    private String name;

    @Test
    void handleReturnsACopyAndFailedSaveLeavesCacheUntouched() {
        DatabaseParamGroupStrategy s = strategy(store(newPrefix(), "svc"), false, Duration.ZERO);
        save(s, "add", property("name", "姓名"));
        Map<String, Map<String, ParamGroupMethodProperty>> copy = s.handle(model(), ApiStrategyType.DATABASE, null, null);
        copy.get(CLASS_NAME).get("add").getRequest().get("name").setDescription("被改了");
        assertNotSame(copy, s.getHandle(model(), docMethod("add")));
        assertEquals("姓名", s.paramDataValidate(model(), "name", docMethod("add"), true).getDescription());
    }

    @Test
    void parsesRulesWrittenByOtherLanguages() throws Exception {
        String p = newPrefix();
        DatabaseRuleStore st = store(p, "svc");
        st.ensureSchema(true);
        // Go 写入的 JSON:带未知字段、未知枚举值
        st.save(CLASS_NAME + ".go", "{\"request\":{\"id\":{\"required\":true,\"paramType\":\"FROM_THE_FUTURE\","
                + "\"extra\":1,\"validate\":[{\"descr\":\"必填\",\"type\":\"Nothing\",\"level\":3,\"unknown\":true}]}},"
                + "\"response\":{\"data\":{\"include\":false}},\"somethingNew\":{}}", "go");
        st.save(CLASS_NAME + ".broken", "{not json", "go");
        st.save("noDotKey", "{}", "go");
        DatabaseParamGroupStrategy s = strategy(st, false, Duration.ZERO);
        ParamGroupMethodProperty.ParamDataValidate id = s.paramDataValidate(model(), "id", docMethod("go"), true);
        assertEquals(Boolean.TRUE, id.getRequired());
        assertNull(id.getParamType());
        assertEquals("必填", id.getValidate().get(0).getDescr());
        assertEquals(Boolean.FALSE, s.paramDataValidate(model(), "data", docMethod("go"), false).getInclude());
        // 解析不了的行被忽略,不影响其他规则
        assertNull(s.paramDataValidate(model(), "x", docMethod("broken"), true));
    }

    // ------------------------------------------------------------------ 多实例同步 / 只读

    @Test
    void secondInstanceSeesChangesByPolling() throws Exception {
        String p = newPrefix();
        DatabaseParamGroupStrategy a = strategy(store(p, "svc"), false, Duration.ZERO);
        DatabaseParamGroupStrategy b = strategy(store(p, "svc"), false, Duration.ZERO);
        assertTrue(b.getHandle(model(), docMethod("add")).isEmpty());
        assertFalse(b.pollOnce(), "版本没变不重新加载");

        save(a, "add", property("name", "姓名"));
        assertNull(b.paramDataValidate(model(), "name", docMethod("add"), true), "没轮询前还是旧的");
        assertTrue(b.pollOnce());
        assertEquals("姓名", b.paramDataValidate(model(), "name", docMethod("add"), true).getDescription());
        assertEquals(a.loadedVersion(), b.loadedVersion());

        // b 保存后 a 也能同步;a 自己保存之后版本号对齐,再轮询不重复加载
        save(b, "add", property("name", "名字"));
        assertFalse(b.pollOnce());
        assertTrue(a.pollOnce());
        assertEquals("名字", a.paramDataValidate(model(), "name", docMethod("add"), true).getDescription());
        assertFalse(a.pollOnce());
    }

    @Test
    void backgroundPollerReloadsAndStopsOnClose() throws Exception {
        String p = newPrefix();
        DatabaseParamGroupStrategy a = strategy(store(p, "svc"), false, Duration.ZERO);
        DatabaseParamGroupStrategy b = strategy(store(p, "svc"), false, Duration.ofMillis(100));
        AtomicInteger reloads = new AtomicInteger();
        java.util.List<Set<String>> seen = new java.util.concurrent.CopyOnWriteArrayList<>();
        b.setReloadListener(changed -> {
            seen.add(changed);
            reloads.incrementAndGet();
        });
        b.getHandle(model(), docMethod("add"));
        save(a, "add", property("name", "姓名"));
        long deadline = System.currentTimeMillis() + 20_000;
        while (b.paramDataValidate(model(), "name", docMethod("add"), true) == null && System.currentTimeMillis() < deadline) {
            Thread.sleep(50);
        }
        assertNotNull(b.paramDataValidate(model(), "name", docMethod("add"), true), "后台轮询应已同步");
        assertTrue(reloads.get() >= 1);
        assertEquals(Set.of(CLASS_NAME), seen.get(0));
        assertTrue(Thread.getAllStackTraces().keySet().stream()
                         .anyMatch(t -> t.getName().equals("csap-apidoc-database-poller") && t.isDaemon()));
        b.close();
        a.close();
    }

    @Test
    void reloadReportsChangedClassesOnly() throws Exception {
        String p = newPrefix();
        DatabaseRuleStore other = store(p, "svc");
        DatabaseParamGroupStrategy b = strategy(store(p, "svc"), false, Duration.ZERO);
        List<Set<String>> seen = new ArrayList<>();
        b.setReloadListener(seen::add);
        b.getHandle(model(), docMethod("add"));

        other.save("a.One.m", RuleJson.write(property("x", "第一版")), "o");
        other.save("a.Two.m", RuleJson.write(property("x", "第一版")), "o");
        assertTrue(b.pollOnce());
        assertEquals(Set.of("a.One", "a.Two"), seen.get(0));

        // 只改一个类
        other.save("a.One.m", RuleJson.write(property("x", "第二版")), "o");
        assertTrue(b.pollOnce());
        assertEquals(Set.of("a.One"), seen.get(1));
        // 删除
        other.save("a.Two.m", null, "o");
        assertTrue(b.pollOnce());
        assertEquals(Set.of("a.Two"), seen.get(2));
        // 回滚也会通知
        long id = other.history("a.One.m", 1).get(0).getId();
        b.rollback(id, "devtools-rollback");
        assertEquals(Set.of("a.One"), seen.get(3));
        assertEquals("第一版", b.paramDataValidate(model(), "x", docMethodOf("a.One", "m"), true).getDescription());
    }

    static CsapDocMethod docMethodOf(String cls, String method) {
        CsapDocMethod m = new CsapDocMethod();
        m.setClassName(cls);
        m.setName(method);
        return m;
    }

    @Test
    void readOnlyModeReadsButRefusesToSave() throws Exception {
        String p = newPrefix();
        DatabaseParamGroupStrategy writer = strategy(store(p, "svc"), false, Duration.ZERO);
        save(writer, "add", property("name", "姓名"));

        DatabaseParamGroupStrategy ro = strategy(store(p, "svc"), true, Duration.ZERO);
        assertEquals("姓名", ro.paramDataValidate(model(), "name", docMethod("add"), true).getDescription());
        RuleStorageNotWritableException e = assertThrows(RuleStorageNotWritableException.class,
                () -> save(ro, "add", property("name", "改")));
        assertTrue(e.getMessage().contains("read-only"), e.getMessage());
        assertTrue(e.getMessage().contains("只读"), e.getMessage());
        assertThrows(RuleStorageNotWritableException.class, () -> ro.rollback(1, "x"));
        assertEquals(1, count("SELECT COUNT(*) FROM " + p + "history"));
        assertEquals("姓名", ro.paramDataValidate(model(), "name", docMethod("add"), true).getDescription());
    }

    @Test
    void readOnlyModeDoesNotCreateTables() throws Exception {
        DatabaseParamGroupStrategy ro = strategy(store(newPrefix(), "svc"), true, Duration.ZERO);
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> ro.getHandle(model(), docMethod("add")));
        assertTrue(e.getMessage().contains("不存在"), e.getMessage() + " / " + e.getCause());
    }

    // ------------------------------------------------------------------ 工具

    private static Validate.ConstraintValidatorField rule(String descr, int level) {
        return new Validate.ConstraintValidatorField().setDescr(descr).setLevel(level);
    }

    /**
     * 每次调用都返回一条新的注解规则,和真实工厂一致
     */
    static IValidateFactory annotationFactory() {
        return (IValidateFactory) Proxy.newProxyInstance(IValidateFactory.class.getClassLoader(),
                new Class<?>[]{IValidateFactory.class}, (proxy, method, args) -> {
                    if ("getAllFieldConstraintValidator".equals(method.getName())) {
                        List<Validate.ConstraintValidatorField> list = new ArrayList<>();
                        list.add(rule("annotationNotNull", 1));
                        return list;
                    }
                    if ("toString".equals(method.getName())) {
                        return "annotationFactory";
                    }
                    return null;
                });
    }
}
