package ai.csap.apidoc.redis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import ai.csap.apidoc.ApiStrategyType;
import ai.csap.apidoc.annotation.ParamType;
import ai.csap.apidoc.autoconfigure.StrategyModel;
import ai.csap.apidoc.model.CsapDocMethod;
import ai.csap.apidoc.model.ParamGroupMethodProperty;
import ai.csap.apidoc.model.RuleChange;
import ai.csap.apidoc.util.RuleStorageNotWritableException;

/**
 * Redis 模式(Testcontainers Redis 7);没有 Docker 时跳过。
 */
class RedisRuleStoreTest {

    private static final String CLASS_NAME = "com.example.UserController";
    private static final AtomicInteger SEQ = new AtomicInteger();
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static GenericContainer<?> container;
    private static LettuceConnectionFactory factory;
    private static StringRedisTemplate template;

    @BeforeAll
    static void start() {
        boolean docker;
        try {
            docker = DockerClientFactory.instance().isDockerAvailable();
        } catch (Throwable e) {
            docker = false;
        }
        Assumptions.assumeTrue(docker, "没有 Docker,跳过 Redis 测试");
        container = new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);
        container.start();
        factory = new LettuceConnectionFactory(container.getHost(), container.getMappedPort(6379));
        factory.afterPropertiesSet();
        template = new StringRedisTemplate(factory);
        template.afterPropertiesSet();
    }

    @AfterAll
    static void stop() {
        if (factory != null) {
            factory.destroy();
        }
        if (container != null) {
            container.stop();
        }
    }

    private static String newService() {
        return "svc" + System.nanoTime() % 100000 + "-" + SEQ.incrementAndGet();
    }

    private static RedisRuleStore store(String service, int historyLimit, boolean subscribe) {
        return new RedisRuleStore(template, "csap:apidoc:", service, historyLimit, subscribe);
    }

    private static RedisParamGroupStrategy strategy(RedisRuleStore store, boolean readOnly, Duration poll) {
        return new RedisParamGroupStrategy(null, () -> store, readOnly, poll);
    }

    private static String json(String field, String description) {
        return "{\"request\":{\"" + field + "\":{\"required\":true,\"description\":\"" + description + "\"}}}";
    }

    private static StrategyModel model() {
        return StrategyModel.builder().id("redis-test").paramType(ApiStrategyType.REDIS).build();
    }

    private static CsapDocMethod docMethod(String method) {
        CsapDocMethod m = new CsapDocMethod();
        m.setClassName(CLASS_NAME);
        m.setName(method);
        return m;
    }

    private static ParamGroupMethodProperty property(String description) {
        ParamGroupMethodProperty p = new ParamGroupMethodProperty();
        ParamGroupMethodProperty.ParamDataValidate v = new ParamGroupMethodProperty.ParamDataValidate();
        v.setRequired(true);
        v.setParamType(ParamType.QUERY);
        v.setDescription(description);
        p.getRequest().put("name", v);
        return p;
    }

    private static void save(RedisParamGroupStrategy s, String method, ParamGroupMethodProperty p) {
        s.writeAndMerge(model(), Map.of(CLASS_NAME, Map.of(method, p)), ApiStrategyType.REDIS, CLASS_NAME, method, true);
    }

    private static boolean waitFor(java.util.function.BooleanSupplier c, long ms) throws InterruptedException {
        long end = System.currentTimeMillis() + ms;
        while (System.currentTimeMillis() < end) {
            if (c.getAsBoolean()) {
                return true;
            }
            Thread.sleep(50);
        }
        return c.getAsBoolean();
    }

    // ------------------------------------------------------------------ 脚本与设计文档一致

    @Test
    void scriptFollowsTheStepsOfTheDesignDocument() {
        String lua = RedisRuleStore.saveScript();
        String[] steps = {
                "redis.call('HGET', KEYS[1], ruleKey)",        // 1 读修改前
                "if before == afterValue then",                 // 2 相同返回 -1
                "return -1",
                "redis.call('INCR', KEYS[2])",                  // 3 版本号加 1
                "redis.call('HDEL', KEYS[1], ruleKey)",         // 4 删除 / 写入
                "redis.call('HSET', KEYS[1], ruleKey, afterValue)",
                "cjson.encode(",                                // 5 记录
                "redis.call('LPUSH', KEYS[3], record)",
                "redis.call('LRANGE', KEYS[3], limit, -1)",
                "redis.call('HDEL', KEYS[4], tostring(oldId))",
                "redis.call('LTRIM', KEYS[3], 0, limit - 1)",
                "redis.call('HSET', KEYS[4], tostring(v), ruleKey)",  // 6 索引
                "redis.call('PUBLISH', channel, tostring(v))",  // 7 通知
                "return v"};
        int from = 0;
        for (String step : steps) {
            int at = lua.indexOf(step, from);
            assertTrue(at >= 0, "脚本缺少或顺序不对: " + step);
            from = at;
        }
        for (String field : new String[]{"id =", "ruleKey =", "before =", "after =", "version =", "updatedBy =", "updatedAt ="}) {
            assertTrue(lua.contains(field), "记录缺少字段 " + field);
        }
    }

    // ------------------------------------------------------------------ 保存 / 删除 / 记录

    @Test
    void saveUpdateDeleteAndUnchanged() throws Exception {
        RedisRuleStore s = store(newService(), 200, false);
        String key = CLASS_NAME + ".add";
        assertEquals(0L, s.version());
        assertEquals(1L, s.save(key, json("a", "一"), "alice"));
        assertEquals(2L, s.save(key, json("a", "二"), "bob"));
        // 内容不变(含只有格式不同):不加版本号、不留记录
        assertEquals(-1L, s.save(key, json("a", "二"), "bob"));
        assertEquals(-1L, s.save(key, "{ \"request\" : { \"a\" : { \"description\" : \"二\", \"required\" : true } } }", "bob"));
        assertEquals(2L, s.version());
        assertEquals(3L, s.save(key, null, "carol"));
        assertEquals(-1L, s.save(key, null, "carol"));
        assertEquals(3L, s.version());
        assertTrue(s.load().rules().isEmpty());

        List<RuleChange> history = s.history(key, 10);
        assertEquals(3, history.size());
        assertEquals(3L, history.get(0).getId());
        assertEquals(3L, history.get(0).getVersion());
        assertEquals("carol", history.get(0).getUpdatedBy());
        assertEquals(json("a", "二"), history.get(0).getBefore());
        assertNull(history.get(0).getAfter());
        assertNull(history.get(2).getBefore());
        assertEquals(json("a", "一"), history.get(2).getAfter());
        assertTrue(history.get(2).getUpdatedAt() > 1_600_000_000_000L);
    }

    @Test
    void recordIsStoredAsTheDesignDocumentSays() throws Exception {
        RedisRuleStore s = store(newService(), 200, false);
        String key = CLASS_NAME + ".add";
        s.save(key, json("a", "一"), "");
        String raw = template.opsForList().index(s.historyKey(key), 0);
        JsonNode node = MAPPER.readTree(raw);
        assertEquals(1, node.get("id").asInt());
        assertEquals(key, node.get("ruleKey").asText());
        assertTrue(node.has("before") && node.get("before").isNull(), "新增时 before 为 null,键不省略: " + raw);
        assertEquals(json("a", "一"), node.get("after").asText());
        assertEquals(1, node.get("version").asInt());
        assertEquals("unknown", node.get("updatedBy").asText());
        // 键的形式
        assertTrue(s.rulesKey().contains("{" + s.service() + "}:rules"));
        assertEquals(key, template.opsForHash().get(s.changesKey(), "1"));
        assertEquals("1", template.opsForValue().get(s.versionKey()));
    }

    @Test
    void historyIsTrimmedAndChangesIndexFollows() {
        RedisRuleStore s = store(newService(), 3, false);
        String key = CLASS_NAME + ".add";
        for (int i = 1; i <= 6; i++) {
            s.save(key, json("a", "v" + i), "u");
        }
        List<RuleChange> history = s.history(key, 100);
        assertEquals(3, history.size());
        assertEquals(6L, history.get(0).getId());
        assertEquals(4L, history.get(2).getId());
        assertEquals(3L, template.opsForHash().size(s.changesKey()));
        assertNull(template.opsForHash().get(s.changesKey(), "3"), "被裁掉的记录同时从 changes 索引删除");
        assertNotNull(template.opsForHash().get(s.changesKey(), "4"));
        // 被裁掉的记录不能再回滚
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> s.rollback(3L, "x"));
        assertTrue(e.getMessage().contains("不存在"), e.getMessage());
    }

    @Test
    void historyWithoutKeyMergesAllKeysNewestFirst() {
        RedisRuleStore s = store(newService(), 200, false);
        s.save(CLASS_NAME + ".a", json("x", "1"), "u");
        s.save(CLASS_NAME + ".b", json("x", "2"), "u");
        s.save(CLASS_NAME + ".a", json("x", "3"), "u");
        List<RuleChange> all = s.history(null, 100);
        assertEquals(3, all.size());
        assertEquals(List.of(3L, 2L, 1L), List.of(all.get(0).getId(), all.get(1).getId(), all.get(2).getId()));
        assertEquals(2, s.history("", 2).size());
    }

    @Test
    void rollbackRestoresPreviousContentByChangeId() {
        RedisRuleStore s = store(newService(), 200, false);
        String key = CLASS_NAME + ".add";
        s.save(key, json("a", "一"), "alice");
        s.save(key, json("a", "二"), "bob");
        assertEquals(3L, s.rollback(2L, "ops"));
        assertEquals(json("a", "一"), s.load().rules().get(key));
        List<RuleChange> history = s.history(key, 10);
        assertEquals(3, history.size());
        assertEquals("ops", history.get(0).getUpdatedBy());
        // 回滚"新增"那条 = 删除
        assertEquals(4L, s.rollback(1L, "ops"));
        assertFalse(s.load().rules().containsKey(key));
        assertThrows(IllegalArgumentException.class, () -> s.rollback(999L, "ops"));
    }

    @Test
    void servicesAreIsolated() {
        RedisRuleStore a = store(newService(), 200, false);
        RedisRuleStore b = store(newService(), 200, false);
        a.save(CLASS_NAME + ".add", json("x", "a"), "u");
        b.save(CLASS_NAME + ".add", json("x", "b"), "u");
        b.save(CLASS_NAME + ".del", json("x", "b"), "u");
        assertEquals(1, a.load().rules().size());
        assertEquals(2, b.load().rules().size());
        assertEquals(1L, a.version());
        assertEquals(2L, b.version());
    }

    @Test
    void concurrentSavesNeitherLoseNorRepeatVersions() throws Exception {
        RedisRuleStore s = store(newService(), 1000, false);
        int threads = 8;
        int each = 25;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<List<Long>>> futures = new ArrayList<>();
        for (int t = 0; t < threads; t++) {
            int id = t;
            futures.add(pool.submit(() -> {
                start.await();
                List<Long> versions = new ArrayList<>();
                for (int i = 0; i < each; i++) {
                    versions.add(s.save(CLASS_NAME + ".m" + id + "_" + i, json("k", "v"), "t" + id));
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
        for (long v = 1; v <= total; v++) {
            assertTrue(all.contains(v), "版本号缺失: " + v);
        }
        assertEquals(total, s.version());
        assertEquals(total, s.load().rules().size());
        assertEquals(total, template.opsForHash().size(s.changesKey()));
    }

    // ------------------------------------------------------------------ 同步 / 只读

    @Test
    void secondInstanceSyncsBySubscription() throws Exception {
        String service = newService();
        // 不轮询,只靠订阅
        RedisParamGroupStrategy a = strategy(store(service, 200, true), false, Duration.ZERO);
        RedisParamGroupStrategy b = strategy(store(service, 200, true), false, Duration.ZERO);
        List<Set<String>> seen = new java.util.concurrent.CopyOnWriteArrayList<>();
        b.setReloadListener(seen::add);
        assertTrue(b.getHandle(model(), docMethod("add")).isEmpty());
        save(a, "add", property("姓名"));
        assertTrue(waitFor(() -> b.paramDataValidate(model(), "name", docMethod("add"), true) != null, 10_000),
                "订阅应让 b 即时同步");
        assertEquals(Set.of(CLASS_NAME), seen.get(0));
        a.close();
        b.close();
    }

    @Test
    void pollingCoversForABrokenSubscription() throws Exception {
        String service = newService();
        RedisParamGroupStrategy a = strategy(store(service, 200, false), false, Duration.ZERO);
        // b 订阅被关掉(subscribe=false 等价于订阅断开),只靠轮询兜底
        RedisParamGroupStrategy b = strategy(store(service, 200, false), false, Duration.ofMillis(100));
        b.getHandle(model(), docMethod("add"));
        save(a, "add", property("姓名"));
        assertTrue(waitFor(() -> b.paramDataValidate(model(), "name", docMethod("add"), true) != null, 15_000),
                "轮询应兜底同步");
        a.close();
        b.close();
    }

    @Test
    void killedSubscriberConnectionIsCoveredByPolling() throws Exception {
        String service = newService();
        RedisParamGroupStrategy a = strategy(store(service, 200, true), false, Duration.ZERO);
        RedisParamGroupStrategy b = strategy(store(service, 200, true), false, Duration.ofMillis(200));
        b.getHandle(model(), docMethod("add"));
        // 杀掉所有订阅连接
        try (RedisConnection c = factory.getConnection()) {
            c.execute("CLIENT", "KILL".getBytes(), "TYPE".getBytes(), "pubsub".getBytes());
        } catch (RuntimeException ignore) {
            // 杀掉的可能包括自己以外的连接,错误无关紧要
        }
        save(a, "add", property("姓名"));
        assertTrue(waitFor(() -> b.paramDataValidate(model(), "name", docMethod("add"), true) != null, 20_000),
                "订阅断开后轮询兜底");
        a.close();
        b.close();
    }

    @Test
    void readOnlyModeReadsButRefusesToSaveAndRollback() throws Exception {
        String service = newService();
        RedisParamGroupStrategy writer = strategy(store(service, 200, false), false, Duration.ZERO);
        save(writer, "add", property("姓名"));
        RedisRuleStore roStore = store(service, 200, false);
        RedisParamGroupStrategy ro = strategy(roStore, true, Duration.ZERO);
        assertEquals("姓名", ro.paramDataValidate(model(), "name", docMethod("add"), true).getDescription());
        RuleStorageNotWritableException e = assertThrows(RuleStorageNotWritableException.class,
                () -> save(ro, "add", property("改")));
        assertTrue(e.getMessage().contains("read-only") && e.getMessage().contains("csap.apidoc.redis.read-only"), e.getMessage());
        assertThrows(RuleStorageNotWritableException.class, () -> ro.rollback(1, "x"));
        assertEquals(1L, roStore.version());
    }

    @Test
    void strategySavesWholeMethodAndKeepsHistory() {
        RedisRuleStore st = store(newService(), 200, false);
        RedisParamGroupStrategy s = strategy(st, false, Duration.ZERO);
        save(s, "add", property("姓名"));
        assertEquals("姓名", s.paramDataValidate(model(), "name", docMethod("add"), true).getDescription());
        List<RuleChange> h = s.history(null, 10);
        assertEquals(1, h.size());
        assertEquals("devtools", h.get(0).getUpdatedBy());
        long v = s.rollback(h.get(0).getId(), "devtools-rollback");
        assertEquals(2L, v);
        assertNull(s.paramDataValidate(model(), "name", docMethod("add"), true));
    }
}
