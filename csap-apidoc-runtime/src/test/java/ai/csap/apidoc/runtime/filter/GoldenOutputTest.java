package ai.csap.apidoc.runtime.filter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.http.converter.json.MappingJacksonValue;

import com.alibaba.fastjson.serializer.SerializerFeature;
import com.alibaba.fastjson2.JSONWriter;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.ser.impl.SimpleBeanPropertyFilter;
import com.fasterxml.jackson.databind.ser.impl.SimpleFilterProvider;

import ai.csap.apidoc.runtime.filter.model.ApiResult;
import ai.csap.apidoc.runtime.filter.model.Exotic;
import ai.csap.apidoc.runtime.filter.model.Match;
import ai.csap.apidoc.runtime.filter.model.MatchPage;
import ai.csap.apidoc.runtime.filter.model.Rich;
import ai.csap.apidoc.runtime.filter.model.Shared;
import ai.csap.apidoc.runtime.filter.model.Team;
import ai.csap.apidoc.runtime.filter.model.TeamWithExtra;
import ai.csap.apidoc.runtime.filter.model.TreeNode;
import ai.csap.apidoc.runtime.filter.model.Viewed;
import ai.csap.validation.factory.FilterClassParam;

/**
 * 黄金样本:引擎(含常见特性组合)× 模式 × 场景的输出逐字节比对。
 */
public class GoldenOutputTest {

    private static final ObjectMapper PLAIN = new ObjectMapper();

    private static final ObjectMapper SNAKE = new ObjectMapper()
            .setPropertyNamingStrategy(PropertyNamingStrategy.SNAKE_CASE)
            .setSerializationInclusion(JsonInclude.Include.NON_NULL);

    private static final ObjectMapper SORTED = new ObjectMapper()
            .configure(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY, true)
            .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true)
            .setSerializationInclusion(JsonInclude.Include.NON_EMPTY);

    private static final JacksonResponseFilterEngine PLAIN_ENGINE = new JacksonResponseFilterEngine();

    private static final JacksonResponseFilterEngine SNAKE_ENGINE = new JacksonResponseFilterEngine();

    private static final JacksonResponseFilterEngine SORTED_ENGINE = new JacksonResponseFilterEngine();

    static {
        // Exotic.Custom 上的自定义过滤器 id
        SimpleFilterProvider custom = new SimpleFilterProvider()
                .addFilter("customFilter", SimpleBeanPropertyFilter.serializeAllExcept("a"));
        PLAIN.setFilterProvider(custom);
        SNAKE.setFilterProvider(custom);
        SORTED.setFilterProvider(custom);
        PLAIN_ENGINE.register(MappingJackson2HttpMessageConverter.class, PLAIN.copy());
        SNAKE_ENGINE.register(MappingJackson2HttpMessageConverter.class, SNAKE.copy());
        SORTED_ENGINE.register(MappingJackson2HttpMessageConverter.class, SORTED.copy());
    }

    /**
     * 模拟 Spring 的 Jackson 消息转换器写出 MappingJacksonValue
     */
    private static String jackson(ObjectMapper mapper, JacksonResponseFilterEngine engine, Object body,
                                  FilterSession session) {
        try {
            Object out = engine.apply(body, session, MappingJackson2HttpMessageConverter.class);
            ObjectWriter writer = mapper.writer();
            if (out instanceof MappingJacksonValue) {
                MappingJacksonValue c = (MappingJacksonValue) out;
                if (c.getSerializationView() != null) {
                    writer = writer.withView(c.getSerializationView());
                }
                if (c.getFilters() != null) {
                    writer = writer.with(c.getFilters());
                }
                out = c.getValue();
            }
            return writer.writeValueAsString(out);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static String fastjson(Object body, FilterSession session, SerializerFeature... features) {
        return com.alibaba.fastjson.JSON.toJSONString(new FastjsonResponseFilterEngine().apply(body, session, null),
                features);
    }

    private static String fastjson2(Object body, FilterSession session, JSONWriter.Feature... features) {
        return com.alibaba.fastjson2.JSON.toJSONString(new Fastjson2ResponseFilterEngine().apply(body, session, null),
                features);
    }

    static Map<String, BiFunction<Object, FilterSession, String>> variants() {
        Map<String, BiFunction<Object, FilterSession, String>> v = new LinkedHashMap<>();
        v.put("jackson", (b, s) -> jackson(PLAIN, PLAIN_ENGINE, b, s));
        v.put("jacksonSnakeNonNull", (b, s) -> jackson(SNAKE, SNAKE_ENGINE, b, s));
        v.put("jacksonSorted", (b, s) -> jackson(SORTED, SORTED_ENGINE, b, s));
        v.put("fastjson", (b, s) -> fastjson(b, s));
        v.put("fastjsonNoRefNulls", (b, s) -> fastjson(b, s, SerializerFeature.DisableCircularReferenceDetect,
                SerializerFeature.WriteMapNullValue));
        v.put("fastjsonMisc", (b, s) -> fastjson(b, s, SerializerFeature.WriteNullListAsEmpty,
                SerializerFeature.WriteNullStringAsEmpty, SerializerFeature.WriteEnumUsingToString,
                SerializerFeature.BrowserCompatible, SerializerFeature.WriteDateUseDateFormat));
        v.put("fastjsonCommon", (b, s) -> fastjson(b, s, SerializerFeature.DisableCircularReferenceDetect,
                SerializerFeature.WriteMapNullValue, SerializerFeature.WriteNullListAsEmpty,
                SerializerFeature.WriteNullStringAsEmpty, SerializerFeature.WriteNullNumberAsZero,
                SerializerFeature.WriteNullBooleanAsFalse));
        v.put("fastjsonSorted", (b, s) -> fastjson(b, s, SerializerFeature.MapSortField,
                SerializerFeature.WriteNonStringKeyAsString, SerializerFeature.NotWriteDefaultValue));
        v.put("fastjson2", (b, s) -> fastjson2(b, s));
        v.put("fastjson2NullsRef", (b, s) -> fastjson2(b, s, JSONWriter.Feature.WriteNulls,
                JSONWriter.Feature.ReferenceDetection));
        v.put("fastjson2Misc", (b, s) -> fastjson2(b, s, JSONWriter.Feature.WriteNullListAsEmpty,
                JSONWriter.Feature.WriteNullStringAsEmpty, JSONWriter.Feature.BrowserCompatible,
                JSONWriter.Feature.WriteLongAsString));
        v.put("fastjson2Converter", (b, s) -> fastjson2(b, s, JSONWriter.Feature.WriteByteArrayAsBase64,
                JSONWriter.Feature.BrowserSecure));
        v.put("fastjson2Values", (b, s) -> fastjson2(b, s, JSONWriter.Feature.WriteNulls,
                JSONWriter.Feature.BrowserCompatible, JSONWriter.Feature.WriteNonStringValueAsString,
                JSONWriter.Feature.WriteBigDecimalAsPlain));
        v.put("fastjson2Sorted", (b, s) -> fastjson2(b, s, JSONWriter.Feature.MapSortField,
                JSONWriter.Feature.SortMapEntriesByKeys, JSONWriter.Feature.NotWriteDefaultValue,
                JSONWriter.Feature.WriteNonStringKeyAsString));
        return v;
    }

    /**
     * 一个场景:每次写出都重新构造值和会话
     */
    static final class Case {
        final String id;
        final Supplier<Object> body;
        final Function<Object, FilterSession> session;

        Case(String id, Supplier<Object> body, Function<Object, FilterSession> session) {
            this.id = id;
            this.body = body;
            this.session = session;
        }
    }

    private static FieldMask mask(List<String> include, String... maps) {
        return FieldMask.compile(include, new HashSet<>(Arrays.asList(maps)));
    }

    private static Function<Object, FilterSession> path(FieldMask mask) {
        return body -> Engines.path(mask, body);
    }

    private static Function<Object, FilterSession> csap(TypeRules rules) {
        return body -> Engines.csap(rules, body);
    }

    private static Set<String> set(String... s) {
        return new HashSet<>(Arrays.asList(s));
    }

    private static MatchPage page(int rows) {
        MatchPage page = new MatchPage();
        page.getRows().clear();
        for (int i = 0; i < rows; i++) {
            page.getRows().add(new Match());
        }
        return page;
    }

    static final List<String> ALL_FIELDS = Arrays.asList("total", "size", "rows.id", "rows.leagueId",
            "rows.internal", "rows.home.id", "rows.home.name", "rows.home.secret", "rows.away.id", "rows.away.name",
            "rows.away.secret", "rows.byHalf.id", "rows.byHalf.name", "rows.byHalf.secret");

    static final List<String> TYPICAL = Arrays.asList("total", "rows.id", "rows.leagueId", "rows.home.name",
            "rows.away.name", "rows.byHalf.name");

    static final List<String> RICH_TOP = Arrays.asList("nullInt", "nullStr", "text", "big", "amount", "color", "flag",
            "day", "longs", "teamArray", "nested", "groups", "byNumber", "teamSet", "anything", "shared",
            "sharedAgain", "nullTeam", "emptyList", "dates", "hidden", "nonNull", "loose");

    static final List<String> RICH_DEEP = Arrays.asList("text", "teamArray.id", "nested.name", "groups.id",
            "byNumber.name", "teamSet.id", "anything.extra", "shared.id", "sharedAgain.name", "nullTeam.id",
            "emptyList.id", "dates.x", "loose.team.id", "loose.list.id", "longs.x", "nullInt", "day");

    static List<Case> cases() {
        List<Case> list = new ArrayList<>();
        // ---- path 模式
        list.add(new Case("path/pageTypical", () -> page(2), path(mask(TYPICAL, "rows.byHalf"))));
        list.add(new Case("path/pageAll", () -> page(2), path(mask(ALL_FIELDS, "rows.byHalf"))));
        list.add(new Case("path/pageAllNoMapPath", () -> page(2), path(mask(ALL_FIELDS))));
        list.add(new Case("path/pageNothing", () -> page(1), path(mask(Collections.singletonList("zz")))));
        list.add(new Case("path/pageRowsWhole", () -> page(1), path(mask(Arrays.asList("rows", "total")))));
        list.add(new Case("path/pageHomeAwayDiffer", () -> page(2),
                path(mask(Arrays.asList("rows.home.id", "rows.away.name", "rows.byHalf.secret"), "rows.byHalf"))));
        list.add(new Case("path/resultInReturnType", () -> new ApiResult<>(page(1)),
                path(mask(Arrays.asList("code", "message", "language", "time", "data.total", "data.rows.id",
                        "data.rows.leagueId", "data.rows.home.id", "data.rows.away.name", "data.rows.byHalf.name"),
                        "data.rows.byHalf"))));
        list.add(new Case("path/richTop", Rich::new, path(mask(RICH_TOP))));
        list.add(new Case("path/richDeep", Rich::new, path(mask(RICH_DEEP, "groups", "byNumber"))));
        list.add(new Case("path/richLooseMap", Rich::new,
                path(mask(Arrays.asList("loose.id", "loose.secret", "groups.name"), "loose", "groups"))));
        list.add(new Case("path/richDeepNoMapPath", Rich::new, path(mask(RICH_DEEP))));
        list.add(new Case("path/tree", () -> TreeNode.of(3, 2),
                path(mask(Arrays.asList("name", "children.name", "children.children.level",
                        "children.children.children")))));
        list.add(new Case("path/rootList", () -> new ArrayList<>(Arrays.asList(new Team(1, "a"), new TeamWithExtra(),
                null)), path(mask(Arrays.asList("id", "extra")))));
        list.add(new Case("path/rootArray", () -> new Team[] {new Team(1, "a"), new Team(2, "b")},
                path(mask(Collections.singletonList("name")))));
        list.add(new Case("path/rootMap", () -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("a", new Team(1, "a"));
            m.put("b", new Team(2, "b"));
            m.put("c", 3);
            return m;
        }, path(mask(Arrays.asList("a.id", "c")))));
        list.add(new Case("path/viewed", Viewed::new, path(mask(Arrays.asList("a", "c", "team.name")))));
        list.add(new Case("path/exoticTop", Exotic::new, path(mask(Arrays.asList("unwrapped", "shape", "ident",
                "identAgain", "typed", "any", "custom", "pub", "fmtDates", "mixed")))));
        list.add(new Case("path/exoticDeep", Exotic::new, path(mask(Arrays.asList("unwrapped.name", "u_id",
                "shape.x", "shape.team.name", "ident.id", "identAgain.secret", "typed.extra", "typed.id",
                "any.name", "any.team.id", "custom.a", "custom.team.name", "pub.id", "pub.team.name", "fmtDates.x",
                "mixed.name", "@class", "@id")))));
        list.add(new Case("path/sharedTop", Shared::new, path(mask(Arrays.asList("team", "teamAgain", "list",
                "listAgain", "map", "mapAgain", "nested")))));
        list.add(new Case("path/sharedDeep", Shared::new, path(mask(Arrays.asList("team.id", "teamAgain.name",
                "list.id", "listAgain.name", "map.id", "mapAgain.name", "nested.id"), "map", "mapAgain"))));
        list.add(new Case("path/sharedRootList", () -> {
            Team t = new Team(33, "R");
            List<Object> l = new ArrayList<>(Arrays.asList(t, t, new ArrayList<>(Arrays.asList(t))));
            l.add(l.get(2));
            return l;
        }, path(mask(Collections.singletonList("name")))));
        for (Object[] parsed : parsedCases()) {
            list.add(new Case("path/parsed/" + parsed[0], () -> Engines.parse((String) parsed[1]),
                    path((FieldMask) parsed[2])));
        }
        // ---- 外层包装:只过滤返回值
        list.add(wrapped("path/wrappedPage", mask(Arrays.asList("total", "rows.id", "rows.home"))));
        list.add(wrapped("path/wrappedAll", mask(ALL_FIELDS, "rows.byHalf")));
        list.add(new Case("path/wrappedListInMap", () -> {
            List<MatchPage> pages = Arrays.asList(page(1), page(1));
            Map<String, Object> outer = new LinkedHashMap<>();
            outer.put("ok", true);
            outer.put("items", pages);
            outer.put("again", pages);
            return outer;
        }, outer -> Engines.path(mask(Collections.singletonList("total")),
                ((Map<?, ?>) outer).get("items"))));
        // ---- csap 模式
        list.add(new Case("csap/goSample", () -> new ApiResult<>(page(2)), csap(CsapModeTest.goSampleRules())));
        list.add(new Case("csap/emptyIncludes", TeamWithExtra::new,
                csap(TypeRules.EMPTY.and(Team.class, Collections.emptySet(), set("id", "name", "secret")))));
        list.add(new Case("csap/integration", () -> page(2), csap(integrationRules())));
        list.add(new Case("csap/objectExcludesSecret", Rich::new,
                csap(TypeRules.EMPTY.and(Object.class, Collections.emptySet(), set("secret", "level")))));
        list.add(new Case("csap/richMixed", Rich::new, csap(TypeRules.EMPTY
                .and(Team.class, set("id"), Collections.emptySet())
                .and(Rich.class, Collections.emptySet(), set("loose", "day", "nullInt"))
                .and(TeamWithExtra.class, set("id", "extra"), set("id")))));
        list.add(new Case("csap/notApplicable", Rich::new,
                csap(TypeRules.EMPTY.and(String.class, set("a"), Collections.emptySet()))));
        list.add(new Case("csap/wrapped", () -> new ApiResult<>(page(1)), outer -> Engines.csap(
                TypeRules.EMPTY.and(Match.class, set("id"), Collections.emptySet())
                        .and(ApiResult.class, Collections.emptySet(), set("time")),
                ((ApiResult<?>) outer).getData())));
        list.add(new Case("csap/tree", () -> TreeNode.of(2, 2),
                csap(TypeRules.EMPTY.and(TreeNode.class, Collections.emptySet(), set("level")))));
        list.add(new Case("csap/exotic", Exotic::new, csap(TypeRules.EMPTY
                .and(Team.class, set("id"), Collections.emptySet())
                .and(Exotic.Ident.class, Collections.emptySet(), set("secret"))
                .and(Exotic.Shape.class, Collections.emptySet(), set("secret"))
                .and(Exotic.Pub.class, Collections.emptySet(), set("secret"))
                .and(Exotic.WithAny.class, Collections.emptySet(), set("secret"))
                .and(Exotic.Custom.class, Collections.emptySet(), set("secret")))));
        list.add(new Case("csap/shared", Shared::new,
                csap(TypeRules.EMPTY.and(Team.class, set("id"), Collections.emptySet()))));
        list.add(new Case("csap/viewed", Viewed::new,
                csap(TypeRules.EMPTY.and(Team.class, set("name"), Collections.emptySet()))));
        return list;
    }

    private static Case wrapped(String id, FieldMask mask) {
        return new Case(id, () -> new ApiResult<>(page(2)), outer -> Engines.path(mask, ((ApiResult<?>) outer).getData()));
    }

    static TypeRules integrationRules() {
        FilterClassParam page = new FilterClassParam().type(MatchPage.class);
        page.addIncludes(Arrays.asList("rows", "total"));
        FilterClassParam match = new FilterClassParam().type(Match.class);
        match.addIncludes(Arrays.asList("id", "home"));
        FilterClassParam team = new FilterClassParam().type(Team.class);
        team.addExcludes("secret");
        return TypeRules.of(Arrays.asList(page, match, team));
    }

    private static List<Object[]> parsedCases() {
        List<Object[]> list = new ArrayList<>();
        list.add(new Object[] {"compact", "{\"code\":\"0\",\"success\":true,\"data\":{\"total\":2,\"size\":20,"
                + "\"rows\":[{\"id\":1,\"name\":\"a\"},{\"name\":\"b\",\"id\":2}]},\"time\":1}",
                mask(Arrays.asList("code", "data.rows.id", "data.total"))});
        list.add(new Object[] {"aliases", "{\"data\":{\"last_id\":1,\"rows\":[{\"a\":1,\"b\":{\"c\":2}}],\"extra\":3}}",
                mask(Arrays.asList("data", "data.lastId", "data.rows"))});
        list.add(new Object[] {"mapValues",
                "{\"stats\":{\"home\":{\"goals\":1,\"shots\":5},\"away\":{\"goals\":0,\"shots\":2}},\"x\":1}",
                mask(Collections.singletonList("stats.goals"), "stats")});
        list.add(new Object[] {"escaped", "{\"a\":\"x\\\"},{y\",\"b\":1}", mask(Collections.singletonList("a"))});
        list.add(new Object[] {"nestedArr", "{\"b\":[{\"a\":1}],\"a\":[[1,2]]}", mask(Collections.singletonList("a"))});
        list.add(new Object[] {"rootArr", "[{\"a\":1,\"b\":2},{\"b\":3}]", mask(Collections.singletonList("a"))});
        list.add(new Object[] {"empty", "{}", mask(Collections.singletonList("a"))});
        list.add(new Object[] {"deepArr", "{\"m\":[[{\"k\":1,\"x\":2}],[{\"k\":3}]],\"y\":0}",
                mask(Collections.singletonList("m.k"))});
        list.add(new Object[] {"nulls", "{\"a\":null,\"b\":{\"c\":null,\"d\":[null,{\"e\":null,\"f\":1}]},\"g\":null}",
                mask(Arrays.asList("a", "b.c", "b.d.e"))});
        return list;
    }

    /**
     * 日期输出与机器时区无关
     */
    @BeforeAll
    static void fixTimeZone() {
        TimeZone zone = TimeZone.getTimeZone("Asia/Shanghai");
        com.alibaba.fastjson.JSON.defaultTimeZone = zone;
        com.alibaba.fastjson2.JSON.configWriterZoneId(zone.toZoneId());
    }

    @Test
    void outputsMatchGolden() {
        Golden.verify("filter-outputs", compute());
    }

    /**
     * 用当前 classpath 上的实现生成黄金样本:{@code java ... GoldenOutputTest <输出文件>}。
     * 换用优化前的实现编译出的类即可得到"优化前"的样本。
     */
    public static void main(String[] args) throws Exception {
        fixTimeZone();
        List<String> lines = new ArrayList<>();
        compute().forEach((k, v) -> lines.add(k + '\t' + v));
        java.nio.file.Files.write(java.nio.file.Paths.get(args[0]), lines, java.nio.charset.StandardCharsets.UTF_8);
    }

    static Map<String, String> compute() {
        Map<String, String> actual = new LinkedHashMap<>();
        for (Map.Entry<String, BiFunction<Object, FilterSession, String>> v : variants().entrySet()) {
            for (Case c : cases()) {
                Object body = c.body.get();
                String out;
                try {
                    out = v.getValue().apply(body, c.session.apply(body));
                } catch (RuntimeException e) {
                    out = "ERROR " + e.getClass().getSimpleName();
                }
                actual.put(v.getKey() + "/" + c.id, out);
            }
        }
        // Jackson @JsonView:由 MappingJacksonValue 带入
        for (String id : Arrays.asList("viewPath", "viewCsap")) {
            Viewed viewed = new Viewed();
            MappingJacksonValue container = new MappingJacksonValue(viewed);
            container.setSerializationView(Viewed.Summary.class);
            FilterSession session = id.equals("viewPath")
                    ? Engines.path(mask(Arrays.asList("a", "c", "team.name")), viewed)
                    : Engines.csap(TypeRules.EMPTY.and(Team.class, set("name"), Collections.emptySet()), viewed);
            actual.put("jackson/" + id, jackson(PLAIN, PLAIN_ENGINE, container, session));
        }
        return actual;
    }
}
