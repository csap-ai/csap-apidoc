package ai.csap.apidoc.runtime.filter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import ai.csap.apidoc.model.CsapDocResponse;
import ai.csap.apidoc.runtime.filter.Engines.Engine;
import ai.csap.apidoc.runtime.filter.model.MatchPage;

/**
 * 预编译的序列化计划缓存在掩码 / 规则上,多个线程同时首次编译、同时使用;规则热更新时整体替换。
 */
class ConcurrencyTest {

    private static final int THREADS = 8;

    @BeforeAll
    static void fixTimeZone() {
        GoldenOutputTest.fixTimeZone();
    }

    /**
     * 所有黄金样本场景在多个线程里同时从冷缓存开始写出,结果与黄金样本一致
     */
    @Test
    void concurrentFirstUseMatchesGolden() throws Exception {
        Map<String, String> golden = Golden.load("filter-outputs");
        assertTrue(!golden.isEmpty());
        // 同一批场景对象(同一批掩码、规则)在所有线程间共享,计划缓存从空开始
        List<GoldenOutputTest.Case> cases = GoldenOutputTest.cases();
        Map<String, BiFunction<Object, FilterSession, String>> variants = GoldenOutputTest.variants();
        List<Object[]> work = new ArrayList<>();
        for (Map.Entry<String, BiFunction<Object, FilterSession, String>> v : variants.entrySet()) {
            for (GoldenOutputTest.Case c : cases) {
                work.add(new Object[] {v.getKey() + "/" + c.id, v.getValue(), c});
            }
        }
        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        CountDownLatch start = new CountDownLatch(1);
        AtomicReference<String> failure = new AtomicReference<>();
        AtomicInteger checked = new AtomicInteger();
        for (int t = 0; t < THREADS; t++) {
            long seed = t;
            pool.execute(() -> {
                List<Object[]> mine = new ArrayList<>(work);
                Collections.shuffle(mine, new Random(seed));
                try {
                    start.await();
                    for (int round = 0; round < 3 && failure.get() == null; round++) {
                        for (Object[] w : mine) {
                            @SuppressWarnings("unchecked")
                            BiFunction<Object, FilterSession, String> writer = (BiFunction<Object, FilterSession, String>) w[1];
                            GoldenOutputTest.Case c = (GoldenOutputTest.Case) w[2];
                            Object body = c.body.get();
                            String out;
                            try {
                                out = writer.apply(body, c.session.apply(body));
                            } catch (RuntimeException e) {
                                out = "ERROR " + e.getClass().getSimpleName();
                                if (Boolean.getBoolean("csap.debug")) {
                                    e.printStackTrace();
                                }
                            }
                            String want = golden.get((String) w[0]);
                            if (!out.equals(want)) {
                                failure.compareAndSet(null, w[0] + "\n  want " + want + "\n  got  " + out);
                                return;
                            }
                            checked.incrementAndGet();
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
        }
        start.countDown();
        pool.shutdown();
        assertTrue(pool.awaitTermination(5, TimeUnit.MINUTES));
        assertNull(failure.get(), failure.get());
        assertEquals(work.size() * 3 * THREADS, checked.get());
    }

    /**
     * 规则热更新期间,每次写出要么完全按旧规则、要么完全按新规则
     */
    @Test
    void hotUpdateNeverMixesVersions() throws Exception {
        String key = "c.C.page";
        ResponseRuleRegistry registry = new ResponseRuleRegistry(k -> true, k -> Collections.emptyList());
        CsapDocResponse v1 = ResponseRuleRegistryTest.doc(ResponseRuleRegistryTest.controller("c.C",
                ResponseRuleRegistryTest.method("c.C", "page", ResponseRuleRegistryTest.param("total"),
                        ResponseRuleRegistryTest.param("rows", ResponseRuleRegistryTest.param("id"),
                                ResponseRuleRegistryTest.param("home", ResponseRuleRegistryTest.param("name")),
                                ResponseRuleRegistryTest.param("byHalf", "Map<String,Team>",
                                        ResponseRuleRegistryTest.param("id"))))));
        CsapDocResponse v2 = ResponseRuleRegistryTest.doc(ResponseRuleRegistryTest.controller("c.C",
                ResponseRuleRegistryTest.method("c.C", "page", ResponseRuleRegistryTest.param("size"),
                        ResponseRuleRegistryTest.param("rows", ResponseRuleRegistryTest.param("leagueId"),
                                ResponseRuleRegistryTest.param("away")))));
        for (Engine engine : Engine.values()) {
            registry.refresh(v1, Collections.emptySet());
            String want1 = write(engine, registry, key);
            registry.refresh(v2, Collections.emptySet());
            String want2 = write(engine, registry, key);
            assertNotEquals(want1, want2);
            AtomicBoolean stop = new AtomicBoolean();
            AtomicReference<String> failure = new AtomicReference<>();
            ExecutorService pool = Executors.newFixedThreadPool(THREADS);
            CountDownLatch done = new CountDownLatch(THREADS);
            for (int t = 0; t < THREADS; t++) {
                pool.execute(() -> {
                    try {
                        while (!stop.get() && failure.get() == null) {
                            String got = write(engine, registry, key);
                            if (!got.equals(want1) && !got.equals(want2)) {
                                failure.set(engine + " got " + got);
                            }
                        }
                    } finally {
                        done.countDown();
                    }
                });
            }
            for (int i = 0; i < 300; i++) {
                registry.refresh(i % 2 == 0 ? v1 : v2, i % 3 == 0 ? Collections.emptySet()
                        : Collections.singleton("c.C"));
            }
            stop.set(true);
            assertTrue(done.await(1, TimeUnit.MINUTES));
            pool.shutdownNow();
            assertNull(failure.get(), failure.get());
        }
    }

    private static String write(Engine engine, ResponseRuleRegistry registry, String key) {
        MatchPage page = new MatchPage();
        return engine.write(page, new FilterSession(FilterMode.PATH, registry.get(key), page));
    }
}
