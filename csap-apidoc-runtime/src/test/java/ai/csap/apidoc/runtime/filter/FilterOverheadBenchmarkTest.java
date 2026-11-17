package ai.csap.apidoc.runtime.filter;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.IntSupplier;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.alibaba.fastjson.JSON;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;

import ai.csap.apidoc.runtime.filter.model.Match;
import ai.csap.apidoc.runtime.filter.model.MatchPage;

/**
 * 过滤开销的性能检查(不是 JMH),默认跳过:
 * <pre>mvn -pl csap-apidoc-runtime test -DskipTests=false -Dtest=FilterOverheadBenchmarkTest -Dcsap.perf=true</pre>
 * 返回体:100 行 Match(含嵌套对象、Map),Jackson 输出约 17KB。
 * <ul>
 *   <li>无规则接口:只多两个 ResponseBodyAdvice 的判断(序列化不变),单独计时;</li>
 *   <li>规则放行全部字段:输出不变,差值就是过滤判定的开销;</li>
 *   <li>典型规则:只放行部分字段,输出变小。</li>
 * </ul>
 * 机器负载高、波动大时,绝对耗时没有意义,只看比值:同一个 JVM 内充分预热后,每一轮紧挨着各跑一批
 * "不过滤"和"过滤"(轮间交换先后顺序),得到这一轮的耗时比值;报告多轮比值的中位数和四分位范围。
 * 轮数、每批次数可用 {@code -Dcsap.perf.rounds}、{@code -Dcsap.perf.batch} 调整。
 */
@EnabledIfSystemProperty(named = "csap.perf", matches = "true")
class FilterOverheadBenchmarkTest {

    private static final int ROWS = 100;

    private static final int ROUNDS = Integer.getInteger("csap.perf.rounds", 41);

    private static final int BATCH = Integer.getInteger("csap.perf.batch", 400);

    private static final long WARMUP_NANOS = Long.getLong("csap.perf.warmupMs", 4000) * 1_000_000;

    private static final List<String> ALL_FIELDS = Arrays.asList("total", "size", "rows.id", "rows.leagueId",
            "rows.internal", "rows.home.id", "rows.home.name", "rows.home.secret", "rows.away.id", "rows.away.name",
            "rows.away.secret", "rows.byHalf.id", "rows.byHalf.name", "rows.byHalf.secret");

    private static final List<String> TYPICAL = Arrays.asList("total", "rows.id", "rows.leagueId", "rows.home.name",
            "rows.away.name", "rows.byHalf.name");

    private static final FieldMask ALL = FieldMask.compile(ALL_FIELDS, Set.of("rows.byHalf"));

    private static final FieldMask SOME = FieldMask.compile(TYPICAL, Set.of("rows.byHalf"));

    /**
     * csap 模式下与典型规则效果相同的类型规则
     */
    private static final TypeRules CSAP_TYPICAL = TypeRules.EMPTY
            .and(MatchPage.class, Set.of("rows", "total"), Set.of())
            .and(Match.class, Set.of("id", "league_id", "home", "away", "by_half"), Set.of())
            .and(ai.csap.apidoc.runtime.filter.model.Team.class, Set.of("name"), Set.of());

    private static MatchPage payload() {
        MatchPage page = new MatchPage();
        page.getRows().clear();
        for (int i = 0; i < ROWS; i++) {
            page.getRows().add(new Match());
        }
        return page;
    }

    @Test
    void jackson() throws Exception {
        MatchPage page = payload();
        ObjectMapper plain = new ObjectMapper();
        ObjectWriter converter = plain.writer();
        JacksonResponseFilterEngine engine = new JacksonResponseFilterEngine();
        engine.register(MappingJackson2HttpMessageConverter.class, plain.copy());
        Class<?> type = MappingJackson2HttpMessageConverter.class;
        System.out.printf("Jackson 输出 %d 字节%n", converter.writeValueAsBytes(page).length);
        // 引擎按规则序列化后,原消息转换器(这里用 converter 模拟)原样写出
        report("Jackson", Map.of(
                "不过滤", () -> bytes(() -> converter.writeValueAsBytes(page)),
                "规则放行全部", () -> bytes(() -> converter.writeValueAsBytes(
                        engine.apply(page, Engines.path(ALL, page), type))),
                "典型规则", () -> bytes(() -> converter.writeValueAsBytes(
                        engine.apply(page, Engines.path(SOME, page), type))),
                "csap 典型规则", () -> bytes(() -> converter.writeValueAsBytes(
                        engine.apply(page, Engines.csap(CSAP_TYPICAL, page), type)))));
    }

    @Test
    void fastjson() {
        MatchPage page = payload();
        FastjsonResponseFilterEngine engine = new FastjsonResponseFilterEngine();
        report("fastjson", Map.of(
                "不过滤", () -> JSON.toJSONBytes(page).length,
                "规则放行全部", () -> JSON.toJSONBytes(engine.apply(page, Engines.path(ALL, page), null)).length,
                "典型规则", () -> JSON.toJSONBytes(engine.apply(page, Engines.path(SOME, page), null)).length,
                "csap 典型规则", () -> JSON.toJSONBytes(engine.apply(page, Engines.csap(CSAP_TYPICAL, page), null))
                        .length));
    }

    @Test
    void fastjson2() {
        MatchPage page = payload();
        Fastjson2ResponseFilterEngine engine = new Fastjson2ResponseFilterEngine();
        report("fastjson2", Map.of(
                "不过滤", () -> com.alibaba.fastjson2.JSON.toJSONBytes(page).length,
                "规则放行全部", () -> com.alibaba.fastjson2.JSON.toJSONBytes(
                        engine.apply(page, Engines.path(ALL, page), null)).length,
                "典型规则", () -> com.alibaba.fastjson2.JSON.toJSONBytes(
                        engine.apply(page, Engines.path(SOME, page), null)).length,
                "csap 典型规则", () -> com.alibaba.fastjson2.JSON.toJSONBytes(
                        engine.apply(page, Engines.csap(CSAP_TYPICAL, page), null)).length));
    }

    /**
     * 没有规则的接口:两个 advice 的额外耗时
     */
    @Test
    void adviceWithoutRules() throws Exception {
        ResponseRuleRegistry registry = new ResponseRuleRegistry(k -> false, k -> Collections.emptyList());
        registry.refresh(null, Collections.emptySet());
        ResponseFilterCaptureAdvice capture = new ResponseFilterCaptureAdvice(registry, k -> FilterMode.PATH);
        ResponseFilterAdvice apply = new ResponseFilterAdvice(Collections.emptyList(), FilterEngine.AUTO);
        Method method = FilterOverheadBenchmarkTest.class.getDeclaredMethod("payload");
        MethodParameter returnType = new MethodParameter(method, -1);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest()));
        MatchPage page = payload();
        try {
            report("无规则接口的 advice", Map.of("两个 advice", () -> {
                Object body = capture.beforeBodyWrite(page, returnType, MediaType.APPLICATION_JSON,
                        MappingJackson2HttpMessageConverter.class, null, null);
                return apply.beforeBodyWrite(body, returnType, MediaType.APPLICATION_JSON,
                        MappingJackson2HttpMessageConverter.class, null, null) == page ? 1 : 0;
            }));
        } finally {
            RequestContextHolder.resetRequestAttributes();
        }
    }

    private interface Call {
        byte[] run() throws Exception;
    }

    private static int bytes(Call call) {
        try {
            return call.run().length;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static long sink;

    private static long batch(IntSupplier task, int n) {
        long start = System.nanoTime();
        for (int i = 0; i < n; i++) {
            sink += task.getAsInt();
        }
        return System.nanoTime() - start;
    }

    private static double percentile(double[] sorted, double p) {
        double idx = p * (sorted.length - 1);
        int lo = (int) Math.floor(idx);
        int hi = (int) Math.ceil(idx);
        return sorted[lo] + (sorted[hi] - sorted[lo]) * (idx - lo);
    }

    private static String time(double nanos) {
        return nanos < 10_000 ? String.format("%.0fns", nanos) : String.format("%.1fµs", nanos / 1000);
    }

    private static void report(String name, Map<String, IntSupplier> cases) {
        Map<String, IntSupplier> ordered = new LinkedHashMap<>();
        for (String key : Arrays.asList("不过滤", "规则放行全部", "典型规则", "csap 典型规则", "两个 advice")) {
            if (cases.containsKey(key)) {
                ordered.put(key, cases.get(key));
            }
        }
        // 预热:轮流执行直到总时长
        long warmEnd = System.nanoTime() + WARMUP_NANOS;
        while (System.nanoTime() < warmEnd) {
            for (IntSupplier task : ordered.values()) {
                batch(task, BATCH / 4 + 1);
            }
        }
        IntSupplier base = ordered.get("不过滤");
        StringBuilder sb = new StringBuilder(name).append(':');
        if (base == null) {
            ordered.forEach((k, task) -> {
                double[] per = new double[ROUNDS];
                for (int r = 0; r < ROUNDS; r++) {
                    per[r] = batch(task, BATCH) / (double) BATCH;
                }
                Arrays.sort(per);
                sb.append(' ').append(k).append(" 中位 ").append(time(percentile(per, 0.5))).append(" |");
            });
            System.out.println(sb);
            return;
        }
        for (Map.Entry<String, IntSupplier> e : ordered.entrySet()) {
            if (e.getValue() == base) {
                continue;
            }
            double[] ratios = new double[ROUNDS];
            double[] baseTimes = new double[ROUNDS];
            double[] filterTimes = new double[ROUNDS];
            for (int r = 0; r < ROUNDS; r++) {
                long b;
                long f;
                if ((r & 1) == 0) {
                    b = batch(base, BATCH);
                    f = batch(e.getValue(), BATCH);
                } else {
                    f = batch(e.getValue(), BATCH);
                    b = batch(base, BATCH);
                }
                ratios[r] = (double) f / b;
                baseTimes[r] = b / (double) BATCH;
                filterTimes[r] = f / (double) BATCH;
            }
            Arrays.sort(ratios);
            Arrays.sort(baseTimes);
            Arrays.sort(filterTimes);
            sb.append(String.format(" %s 开销 %+.1f%% (IQR %+.1f%% ~ %+.1f%%, 不过滤 %s / 过滤 %s) |", e.getKey(),
                    (percentile(ratios, 0.5) - 1) * 100, (percentile(ratios, 0.25) - 1) * 100,
                    (percentile(ratios, 0.75) - 1) * 100, time(percentile(baseTimes, 0.5)),
                    time(percentile(filterTimes, 0.5))));
        }
        System.out.println(sb + (sink == 42 ? " " : ""));
    }
}
