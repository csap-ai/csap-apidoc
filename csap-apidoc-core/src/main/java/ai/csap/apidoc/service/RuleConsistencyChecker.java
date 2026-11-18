package ai.csap.apidoc.service;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ai.csap.apidoc.ApiStrategyType;
import ai.csap.apidoc.autoconfigure.StrategyModel;
import ai.csap.apidoc.model.CsapDocMethod;
import ai.csap.apidoc.model.CsapDocModelController;
import ai.csap.apidoc.model.CsapDocResponse;
import ai.csap.apidoc.model.ParamGroupMethodProperty;

/**
 * 字段规则过期检查(只打 WARN,不改变任何行为)。
 *
 * <p>生成文档时,每次按字段路径(keyName)查规则都会记录下来(见 {@code ParamGroupStrategy#paramDataValidate});
 * 全量扫描完成后,拿已加载的 YAML / SQLite 规则与之对照:
 * <ol>
 *   <li>规则里的方法在类中已不存在(例如方法改名)→ WARN,附相似的现有方法名;</li>
 *   <li>规则里的路径在当前请求参数 / 返回结构中没有被查到 → WARN,列出路径。</li>
 * </ol>
 * 只在上级路径被查到(或是顶层路径)时才判定为"找不到",避免把未展开的子树误报。每个方法汇总成一条日志。
 *
 * @author ycf
 * @since 1.3.0
 */
public final class RuleConsistencyChecker {

    private static final Logger LOG = LoggerFactory.getLogger(RuleConsistencyChecker.class);

    /**
     * 类名.方法名 → 本轮文档生成中查过的请求 / 返回规则路径
     */
    private static final Map<String, Set<String>> REQUEST_LOOKUPS = new ConcurrentHashMap<>();
    private static final Map<String, Set<String>> RESPONSE_LOOKUPS = new ConcurrentHashMap<>();
    /**
     * 上一次检查的结果:结果没变时(例如启动扫描后又 flush)不重复打日志
     */
    private static volatile List<String> lastResult = Collections.emptyList();

    private RuleConsistencyChecker() {
    }

    /**
     * 记录一次规则查询
     *
     * @param method  文档方法
     * @param request 是否请求规则
     * @param keyName 字段路径
     */
    public static void recordLookup(CsapDocMethod method, boolean request, String keyName) {
        if (method == null || keyName == null || method.getClassName() == null || method.getName() == null) {
            return;
        }
        (request ? REQUEST_LOOKUPS : RESPONSE_LOOKUPS)
                .computeIfAbsent(method.getClassName() + "." + method.getName(), k -> ConcurrentHashMap.newKeySet())
                .add(keyName);
    }

    /**
     * 全量扫描开始前清空记录
     */
    public static void reset() {
        REQUEST_LOOKUPS.clear();
        RESPONSE_LOOKUPS.clear();
    }

    /**
     * 检查并打 WARN
     *
     * @param models 扫描使用的策略模型
     * @param doc    生成的文档
     * @param rules  取某个策略模型已加载的规则(类名 → 方法名 → 规则)
     * @return 每个有问题的方法一条消息
     */
    public static List<String> check(List<StrategyModel> models, CsapDocResponse doc,
                                     Function<StrategyModel, Map<String, Map<String, ParamGroupMethodProperty>>> rules) {
        Map<String, CsapDocMethod> documented = new HashMap<>();
        if (doc != null && doc.getApiList() != null) {
            for (CsapDocModelController c : doc.getApiList()) {
                if (c.getMethodList() == null) {
                    continue;
                }
                for (CsapDocMethod m : c.getMethodList()) {
                    if (m.getClassName() != null && m.getName() != null) {
                        documented.putIfAbsent(m.getClassName() + "." + m.getName(), m);
                    }
                }
            }
        }
        List<String> messages = new ArrayList<>();
        Set<String> reported = new HashSet<>();
        for (StrategyModel model : models) {
            if (model.getParamType() != ApiStrategyType.YAML && model.getParamType() != ApiStrategyType.SQL_LITE
                    && model.getParamType() != ApiStrategyType.DATABASE
                    && model.getParamType() != ApiStrategyType.REDIS) {
                continue;
            }
            Map<String, Map<String, ParamGroupMethodProperty>> loaded;
            try {
                loaded = rules.apply(model);
            } catch (RuntimeException e) {
                LOG.debug("rule check: load rules failed {}", e.getMessage());
                continue;
            }
            if (loaded == null || loaded.isEmpty()) {
                continue;
            }
            Map<String, Class<?>> scanned = new HashMap<>();
            if (model.getClazz() != null) {
                for (Class<?> c : model.getClazz()) {
                    scanned.put(c.getName(), c);
                }
            }
            String source = model.getParamType().getName() + (model.getPath() == null ? "" : ": " + model.getPath());
            for (Map.Entry<String, Map<String, ParamGroupMethodProperty>> byClass : new TreeMap<>(loaded).entrySet()) {
                String className = byClass.getKey();
                Class<?> type = scanned.get(className);
                if (type == null || byClass.getValue() == null) {
                    // 不属于本策略扫描的类(可能由其他 @EnableApidoc 配置负责),不检查
                    continue;
                }
                for (Map.Entry<String, ParamGroupMethodProperty> byMethod : new TreeMap<>(byClass.getValue()).entrySet()) {
                    String key = className + "." + byMethod.getKey();
                    if (!reported.add(key)) {
                        continue;
                    }
                    String msg = checkMethod(type, byMethod.getKey(), byMethod.getValue(), documented.get(key), source);
                    if (msg != null) {
                        messages.add(msg);
                    }
                }
            }
        }
        if (!messages.equals(lastResult)) {
            messages.forEach(LOG::warn);
            lastResult = Collections.unmodifiableList(new ArrayList<>(messages));
        }
        return messages;
    }

    /**
     * 最近一次检查的结果
     *
     * @return 每个有问题的方法一条消息
     */
    public static List<String> lastResult() {
        return lastResult;
    }

    static String checkMethod(Class<?> type, String methodName, ParamGroupMethodProperty rule, CsapDocMethod docMethod,
                              String source) {
        String where = type.getName() + "." + methodName;
        Set<String> methodNames = new TreeSet<>();
        for (Method m : type.getMethods()) {
            methodNames.add(m.getName());
        }
        for (Method m : type.getDeclaredMethods()) {
            methodNames.add(m.getName());
        }
        if (!methodNames.contains(methodName)) {
            List<String> similar = similar(methodName, methodNames);
            return "csap-apidoc 字段规则可能已过期: " + where + " —— 类中已没有这个方法(规则来源 " + source + ")"
                    + (similar.isEmpty() ? "" : ";相似的现有方法: " + String.join(", ", similar))
                    + "。方法改名后请把规则移到新方法名下,否则新方法没有规则";
        }
        if (docMethod == null || rule == null) {
            // 方法存在但没有生成文档(隐藏 / 不在扫描范围),无法判断路径
            return null;
        }
        String key = docMethod.getClassName() + "." + docMethod.getName();
        List<String> badRequest = missing(rule.getRequest() == null ? null : rule.getRequest().keySet(),
                REQUEST_LOOKUPS.getOrDefault(key, Collections.emptySet()), true,
                docMethod.getParamNames());
        List<String> badResponse = missing(rule.getResponse() == null ? null : rule.getResponse().keySet(),
                RESPONSE_LOOKUPS.getOrDefault(key, Collections.emptySet()), false, null);
        if (badRequest.isEmpty() && badResponse.isEmpty()) {
            return null;
        }
        StringBuilder sb = new StringBuilder("csap-apidoc 字段规则可能已过期: ").append(where)
                .append(" —— 以下路径在当前的参数 / 返回结构中找不到(规则来源 ").append(source).append(")");
        if (!badResponse.isEmpty()) {
            sb.append(";返回: ").append(String.join(", ", badResponse));
        }
        if (!badRequest.isEmpty()) {
            sb.append(";请求: ").append(String.join(", ", badRequest));
        }
        sb.append("。返回结构改过(例如改成分页)时,白名单会把新结构的字段排除,文档看起来像泛型没解析");
        return sb.toString();
    }

    /**
     * 规则路径中"上级被查到(或是顶层)但自身没被查到"的路径
     */
    static List<String> missing(Set<String> ruleKeys, Set<String> looked, boolean request, List<String> paramNames) {
        List<String> out = new ArrayList<>();
        if (ruleKeys == null || ruleKeys.isEmpty()) {
            return out;
        }
        Set<String> params = paramNames == null ? Collections.emptySet() : new HashSet<>(paramNames);
        for (String k : new TreeSet<>(ruleKeys)) {
            if (k == null || looked.contains(k)) {
                continue;
            }
            int dot = k.lastIndexOf('.');
            boolean flag;
            if (dot < 0) {
                flag = true;
            } else if (looked.contains(k.substring(0, dot))) {
                flag = true;
            } else {
                // 请求规则的第一段是方法参数名:参数已经不存在
                flag = request && !params.isEmpty() && !params.contains(k.substring(0, k.indexOf('.')));
            }
            if (flag) {
                out.add(k);
            }
        }
        return out;
    }

    /**
     * 编辑距离最近的至多 3 个方法名
     */
    static List<String> similar(String name, Set<String> candidates) {
        String lower = name.toLowerCase();
        List<String[]> scored = new ArrayList<>();
        for (String c : candidates) {
            if (OBJECT_METHODS.contains(c)) {
                continue;
            }
            String cl = c.toLowerCase();
            int d = distance(lower, cl);
            boolean related = cl.contains(lower) || lower.contains(cl) || d <= Math.max(2, Math.max(name.length(), c.length()) / 2);
            if (related) {
                scored.add(new String[]{c, String.valueOf(d)});
            }
        }
        scored.sort(Comparator.comparingInt((String[] a) -> Integer.parseInt(a[1])).thenComparing(a -> a[0]));
        Set<String> out = new LinkedHashSet<>();
        for (String[] s : scored) {
            if (out.size() == 3) {
                break;
            }
            out.add(s[0]);
        }
        return new ArrayList<>(out);
    }

    private static final Set<String> OBJECT_METHODS = new HashSet<>(java.util.Arrays.asList("equals", "hashCode",
            "toString", "getClass", "notify", "notifyAll", "wait", "clone", "finalize"));

    static int distance(String a, String b) {
        int[] prev = new int[b.length() + 1];
        int[] cur = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) {
            prev[j] = j;
        }
        for (int i = 1; i <= a.length(); i++) {
            cur[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                cur[j] = Math.min(Math.min(cur[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
            }
            int[] t = prev;
            prev = cur;
            cur = t;
        }
        return prev[b.length()];
    }
}
