package ai.csap.apidoc.runtime.filter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import ai.csap.apidoc.event.ApidocRulesRefreshedEvent;
import ai.csap.apidoc.model.CsapDocMethod;
import ai.csap.apidoc.model.CsapDocModel;
import ai.csap.apidoc.model.CsapDocModelController;
import ai.csap.apidoc.model.CsapDocParameter;
import ai.csap.apidoc.model.CsapDocResponse;
import ai.csap.apidoc.type.ModelType;
import ai.csap.validation.factory.FilterClassParam;

/**
 * 规则编译、按类刷新、并发读写。
 */
class ResponseRuleRegistryTest {

    static CsapDocParameter param(String name, CsapDocParameter... children) {
        return param(name, null, children);
    }

    static CsapDocParameter param(String name, String dataType, CsapDocParameter... children) {
        CsapDocModel child = children.length == 0 ? null
                : CsapDocModel.builder().modelType(ModelType.OBJECT)
                .parameters(new ArrayList<>(Arrays.asList(children))).build();
        return CsapDocParameter.builder().name(name).dataType(dataType).children(child).build();
    }

    static CsapDocMethod method(String className, String name, CsapDocParameter... params) {
        CsapDocModel root = CsapDocModel.builder().modelType(ModelType.OBJECT)
                .parameters(new ArrayList<>(Arrays.asList(params))).build();
        return CsapDocMethod.builder().className(className).name(name).key(className + "." + name)
                .response(new ArrayList<>(Collections.singletonList(root))).build();
    }

    static CsapDocResponse doc(CsapDocModelController... controllers) {
        CsapDocResponse response = new CsapDocResponse();
        response.getApiList().addAll(Arrays.asList(controllers));
        return response;
    }

    static CsapDocModelController controller(String name, CsapDocMethod... methods) {
        return CsapDocModelController.builder().name(name).methodList(new ArrayList<>(Arrays.asList(methods))).build();
    }

    @Test
    void compilesPathMaskFromDocTree() {
        CsapDocMethod m = method("a.UserController", "get",
                param("code"),
                param("data", param("rows", param("id"), param("leagueId")), param("total")),
                param("stats", "Map<String,Team>", param("goals")));
        FieldMask mask = EndpointRuleCompiler.pathMask(m);
        assertNotNull(mask);
        assertTrue(mask.allows("code"));
        assertFalse(mask.allows("message"));
        FieldMask rows = mask.lookup("data").lookup("rows");
        assertTrue(rows.allows("league_id"));
        assertTrue(rows.allows("leagueId"));
        assertFalse(rows.allows("name"));
        assertSame(FieldMask.PASS, mask.lookup("data").lookup("total"));
        FieldMask stats = mask.lookup("stats");
        assertNotNull(stats.mapValues());
        assertTrue(stats.lookup("anyKey").allows("goals"));
        assertFalse(stats.lookup("anyKey").allows("shots"));
    }

    @Test
    void baseTypeOrMissingResponseIsNotFiltered() {
        CsapDocMethod base = CsapDocMethod.builder().key("a.B.c").response(new ArrayList<>(Collections.singletonList(
                CsapDocModel.builder().modelType(ModelType.BASE_DATA).parameters(new ArrayList<>()).build()))).build();
        assertNull(EndpointRuleCompiler.pathMask(base));
        assertNull(EndpointRuleCompiler.pathMask(CsapDocMethod.builder().key("a.B.d").build()));
    }

    @Test
    void mapTypeDetection() {
        assertTrue(EndpointRuleCompiler.isMap(param("x", "Map<String,Team>")));
        assertTrue(EndpointRuleCompiler.isMap(param("x", "LinkedHashMap<String, List<Team>>")));
        assertTrue(EndpointRuleCompiler.isMap(param("x", "java.util.HashMap<String,Team>")));
        assertFalse(EndpointRuleCompiler.isMap(param("x", "List<Map<String,Team>>")));
        assertFalse(EndpointRuleCompiler.isMap(param("x", "Team")));
        assertFalse(EndpointRuleCompiler.isMap(param("x", (String) null)));
    }

    @Test
    void onlyEndpointsWithResponseRulesAndPartialRefresh() {
        Set<String> withRules = ConcurrentHashMap.newKeySet();
        withRules.addAll(Arrays.asList("a.A.get", "b.B.get"));
        Map<String, List<FilterClassParam>> typeRules = new ConcurrentHashMap<>();
        ResponseRuleRegistry registry = new ResponseRuleRegistry(withRules::contains,
                k -> typeRules.getOrDefault(k, Collections.emptyList()));
        assertFalse(registry.isReady());

        CsapDocResponse v1 = doc(controller("a.A", method("a.A", "get", param("x")), method("a.A", "list", param("y"))),
                controller("b.B", method("b.B", "get", param("z"))));
        registry.onApplicationEvent(new ApidocRulesRefreshedEvent(v1, Collections.emptySet()));
        assertTrue(registry.isReady());
        assertNotNull(registry.get("a.A.get"));
        assertNull(registry.get("a.A.list"), "没有返回规则的接口不过滤");
        assertNotNull(registry.get("b.B.get"));
        assertTrue(registry.get("a.A.get").getPathMask().allows("x"));

        // 热更新:只刷新 a.A,b.B 的编译结果原样保留
        CompiledEndpoint bBefore = registry.get("b.B.get");
        withRules.add("a.A.list");
        CsapDocResponse v2 = doc(controller("a.A", method("a.A", "get", param("x2")), method("a.A", "list", param("y"))),
                controller("b.B", method("b.B", "get", param("changed"))));
        registry.refresh(v2, Collections.singleton("a.A"));
        assertTrue(registry.get("a.A.get").getPathMask().allows("x2"));
        assertFalse(registry.get("a.A.get").getPathMask().allows("x"));
        assertNotNull(registry.get("a.A.list"));
        assertSame(bBefore, registry.get("b.B.get"));

        // 规则被删除
        withRules.remove("a.A.get");
        registry.refresh(v2, Collections.singleton("a.A"));
        assertNull(registry.get("a.A.get"));
    }

    @Test
    void concurrentReadsAlwaysSeeACompleteVersion() throws Exception {
        ResponseRuleRegistry registry = new ResponseRuleRegistry(k -> true, k -> Collections.emptyList());
        int methods = 50;
        registry.refresh(version(0, methods), Collections.emptySet());
        AtomicBoolean stop = new AtomicBoolean();
        AtomicReference<String> failure = new AtomicReference<>();
        ExecutorService pool = Executors.newFixedThreadPool(5);
        CountDownLatch readers = new CountDownLatch(4);
        for (int t = 0; t < 4; t++) {
            pool.execute(() -> {
                try {
                    while (!stop.get() && failure.get() == null) {
                        Map<String, CompiledEndpoint> snap = registry.snapshot();
                        String version = null;
                        for (int i = 0; i < methods; i++) {
                            CompiledEndpoint e = snap.get("c.C.m" + i);
                            if (e == null) {
                                failure.set("missing m" + i);
                                return;
                            }
                            String v = e.getPathMask().fields().keySet().iterator().next();
                            if (version == null) {
                                version = v;
                            } else if (!version.equals(v)) {
                                failure.set("mixed versions " + version + " / " + v);
                                return;
                            }
                        }
                        if (registry.get("c.C.m0") == null) {
                            failure.set("get returned null");
                        }
                    }
                } finally {
                    readers.countDown();
                }
            });
        }
        for (int v = 1; v <= 200; v++) {
            registry.refresh(version(v, methods), v % 2 == 0 ? Collections.emptySet() : Collections.singleton("c.C"));
        }
        stop.set(true);
        assertTrue(readers.await(10, TimeUnit.SECONDS));
        pool.shutdownNow();
        assertNull(failure.get(), failure.get());
        assertTrue(registry.get("c.C.m0").getPathMask().allows("v200"));
    }

    private static CsapDocResponse version(int v, int methods) {
        CsapDocMethod[] list = new CsapDocMethod[methods];
        for (int i = 0; i < methods; i++) {
            list[i] = method("c.C", "m" + i, param("v" + v));
        }
        return doc(controller("c.C", list));
    }

    @Test
    void endpointFiltersByMode() {
        CompiledEndpoint pathOnly = new CompiledEndpoint("k", "c", FieldMask.compile(List.of("a"), null), null);
        assertTrue(pathOnly.filters(FilterMode.PATH));
        assertFalse(pathOnly.filters(FilterMode.CSAP));
        assertFalse(pathOnly.filters(FilterMode.OFF));
        CompiledEndpoint csapOnly = new CompiledEndpoint("k", "c", null,
                TypeRules.EMPTY.and(String.class, Set.of("a"), Set.of()));
        assertFalse(csapOnly.filters(FilterMode.PATH));
        assertTrue(csapOnly.filters(FilterMode.CSAP));
        assertEquals("k", csapOnly.getKey());
    }
}
