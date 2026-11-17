package ai.csap.apidoc.runtime.filter;

import static ai.csap.apidoc.runtime.filter.Engines.parse;
import static ai.csap.apidoc.runtime.filter.Engines.path;
import static ai.csap.apidoc.runtime.filter.Engines.tree;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import ai.csap.apidoc.runtime.filter.Engines.Engine;
import ai.csap.apidoc.runtime.filter.model.ApiResult;
import ai.csap.apidoc.runtime.filter.model.MatchPage;

/**
 * path 模式。用例改写自 Go 版 filter/filter_test.go 与 mask_test.go,三种引擎结果必须一致。
 */
class PathModeTest {

    private static void check(Engine engine, FieldMask mask, String src, String want) {
        Object value = parse(src);
        String got = engine.write(value, path(mask, value));
        assertEquals(tree(want), tree(got), engine + " got " + got);
    }

    private static FieldMask compile(String... include) {
        return FieldMask.compile(Arrays.asList(include), null);
    }

    @ParameterizedTest
    @EnumSource(Engine.class)
    void compact(Engine engine) {
        check(engine, compile("code", "data.rows.id", "data.total"),
                "{\"code\":\"0\",\"success\":true,\"data\":{\"total\":2,\"size\":20,"
                        + "\"rows\":[{\"id\":1,\"name\":\"a\"},{\"name\":\"b\",\"id\":2}]},\"time\":1}",
                "{\"code\":\"0\",\"data\":{\"total\":2,\"rows\":[{\"id\":1},{\"id\":2}]}}");
    }

    @ParameterizedTest
    @EnumSource(Engine.class)
    void aliasesAndWholeValue(Engine engine) {
        // 规则写驼峰,JSON 是下划线;没有下级规则的 data.rows 整段输出
        check(engine, compile("data", "data.lastId", "data.rows"),
                "{\"data\":{\"last_id\":1,\"rows\":[{\"a\":1,\"b\":{\"c\":2}}],\"extra\":3}}",
                "{\"data\":{\"last_id\":1,\"rows\":[{\"a\":1,\"b\":{\"c\":2}}]}}");
    }

    @ParameterizedTest
    @EnumSource(Engine.class)
    void mapValues(Engine engine) {
        FieldMask mask = FieldMask.compile(Collections.singletonList("stats.goals"), Set.of("stats"));
        check(engine, mask,
                "{\"stats\":{\"home\":{\"goals\":1,\"shots\":5},\"away\":{\"goals\":0,\"shots\":2}},\"x\":1}",
                "{\"stats\":{\"home\":{\"goals\":1},\"away\":{\"goals\":0}}}");
    }

    @ParameterizedTest
    @EnumSource(Engine.class)
    void edgeCases(Engine engine) {
        FieldMask a = compile("a");
        check(engine, a, "{\"a\":\"x\\\"},{y\",\"b\":1}", "{\"a\":\"x\\\"},{y\"}");
        check(engine, a, "{\"b\":[{\"a\":1}],\"a\":[[1,2]]}", "{\"a\":[[1,2]]}");
        check(engine, a, "[{\"a\":1,\"b\":2},{\"b\":3}]", "[{\"a\":1},{}]");
        check(engine, a, "{\"a\":1,\"b\":2}", "{\"a\":1}");
        check(engine, a, "{}", "{}");
        // 一个字段都没放行:根节点仍是白名单
        check(engine, compile("zz"), "{\"a\":1,\"b\":{\"c\":2}}", "{}");
        // 嵌套数组
        check(engine, compile("m.k"), "{\"m\":[[{\"k\":1,\"x\":2}],[{\"k\":3}]],\"y\":0}",
                "{\"m\":[[{\"k\":1}],[{\"k\":3}]]}");
    }

    /**
     * 外层包装 + 驼峰/下划线 + Map 值 + force 字段(force 字段在文档树里,编译时就是放行路径)
     */
    @ParameterizedTest
    @EnumSource(Engine.class)
    void beansWithWrapperInReturnType(Engine engine) {
        List<String> include = Arrays.asList("code", "message", "language", "time", "data.total",
                "data.rows.id", "data.rows.leagueId", "data.rows.home.id", "data.rows.away.name",
                "data.rows.byHalf.name");
        FieldMask mask = FieldMask.compile(include, Set.of("data.rows.byHalf"));
        ApiResult<MatchPage> value = new ApiResult<>(new MatchPage());
        String got = engine.write(value, path(mask, value));
        assertEquals(tree("{\"code\":\"0\",\"message\":\"成功\",\"data\":{\"rows\":[{\"id\":7,\"league_id\":3,"
                + "\"home\":{\"id\":1},\"away\":{\"name\":\"B\"},\"by_half\":{\"h1\":{\"name\":\"A\"}}}],"
                + "\"total\":1},\"language\":\"ZH\",\"time\":1}"), tree(got), engine + " got " + got);
    }

    /**
     * 返回值被别的 ResponseBodyAdvice 包进外层结构:只过滤返回值那一段,外层原样输出
     */
    @ParameterizedTest
    @EnumSource(Engine.class)
    void wrappedByAnotherAdvice(Engine engine) {
        MatchPage page = new MatchPage();
        FieldMask mask = FieldMask.compile(Arrays.asList("total", "rows.id"), null);
        ApiResult<MatchPage> wrapper = new ApiResult<>(page);
        String got = engine.write(wrapper, path(mask, page));
        assertEquals(tree("{\"code\":\"0\",\"message\":\"成功\",\"data\":{\"rows\":[{\"id\":7}],\"total\":1},"
                + "\"language\":\"ZH\",\"time\":1,\"success\":true}"), tree(got), engine + " got " + got);

        // 返回值是集合、被包进 Map
        List<MatchPage> list = Arrays.asList(new MatchPage(), new MatchPage());
        FieldMask m2 = FieldMask.compile(Collections.singletonList("total"), null);
        Map<String, Object> outer = new java.util.LinkedHashMap<>();
        outer.put("ok", true);
        outer.put("items", list);
        got = engine.write(outer, path(m2, list));
        assertEquals(tree("{\"ok\":true,\"items\":[{\"total\":1},{\"total\":1}]}"), tree(got), engine + " got " + got);
    }

    @ParameterizedTest
    @EnumSource(Engine.class)
    void sessionIsReusableAcrossWrites(Engine engine) {
        FieldMask mask = compile("total");
        MatchPage page = new MatchPage();
        FilterSession session = path(mask, page);
        String first = engine.write(page, session);
        String second = engine.write(page, session);
        assertEquals(first, second);
        assertEquals(tree("{\"total\":1}"), tree(first));
    }
}
