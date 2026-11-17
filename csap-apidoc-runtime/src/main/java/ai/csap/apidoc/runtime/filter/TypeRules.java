package ai.csap.apidoc.runtime.filter;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import ai.csap.validation.factory.FilterClassParam;

/**
 * csap 模式的规则:按"类型"挂白名单 / 黑名单,与 csap-framework 使用的 fastjson
 * {@code SimplePropertyPreFilter} 判定一致:
 * <ol>
 *   <li>规则作用于该类型及其子类型的对象({@code type.isInstance(obj)});</li>
 *   <li>先查黑名单,命中则不输出;</li>
 *   <li>白名单非空时只输出白名单里的字段;白名单为空时输出黑名单之外的全部字段;</li>
 *   <li>多条规则同时作用于一个对象时,全部通过才输出。</li>
 * </ol>
 * 实例不可变,可在线程间共享。
 *
 * @author ycf
 * @since 1.2.0
 */
public final class TypeRules {

    private static final Rule[] NONE = new Rule[0];

    /**
     * 没有任何规则
     */
    public static final TypeRules EMPTY = new TypeRules(Collections.emptyList());

    private final Rule[] rules;

    /**
     * 每个运行时类型适用的规则(按需计算)
     */
    private final ConcurrentMap<Class<?>, Rule[]> applicable = new ConcurrentHashMap<>();

    private TypeRules(List<Rule> rules) {
        this.rules = rules.toArray(NONE);
    }

    /**
     * 由文档生成时登记的 {@link FilterClassParam} 构建(复制一份,之后与原对象无关)
     *
     * @param params 过滤参数
     * @return 规则
     */
    public static TypeRules of(Collection<FilterClassParam> params) {
        if (params == null || params.isEmpty()) {
            return EMPTY;
        }
        List<Rule> list = new ArrayList<>(params.size());
        for (FilterClassParam p : params) {
            if (p == null || p.getType() == null) {
                continue;
            }
            list.add(new Rule(p.getType(), new HashSet<>(p.getIncludes()), new HashSet<>(p.getExcludes())));
        }
        return list.isEmpty() ? EMPTY : new TypeRules(list);
    }

    /**
     * 手工构造(测试与扩展用)
     *
     * @param type     类型
     * @param includes 白名单
     * @param excludes 黑名单
     * @return 追加了一条规则的新实例
     */
    public TypeRules and(Class<?> type, Set<String> includes, Set<String> excludes) {
        List<Rule> list = new ArrayList<>(List.of(rules));
        list.add(new Rule(type, new HashSet<>(includes), new HashSet<>(excludes)));
        return new TypeRules(list);
    }

    /**
     * 是否没有任何规则
     */
    public boolean isEmpty() {
        return rules.length == 0;
    }

    /**
     * 该类型对象的字段是否输出
     *
     * @param type 对象的运行时类型
     * @param name JSON 键
     * @return 是否输出
     */
    public boolean allows(Class<?> type, String name) {
        if (rules.length == 0 || type == null) {
            return true;
        }
        for (Rule rule : applicable(type)) {
            if (rule.excludes.contains(name)) {
                return false;
            }
            if (!rule.includes.isEmpty() && !rule.includes.contains(name)) {
                return false;
            }
        }
        return true;
    }

    private Rule[] applicable(Class<?> type) {
        Rule[] found = applicable.get(type);
        if (found != null) {
            return found;
        }
        List<Rule> list = new ArrayList<>(2);
        for (Rule rule : rules) {
            if (rule.type.isAssignableFrom(type)) {
                list.add(rule);
            }
        }
        found = list.isEmpty() ? NONE : list.toArray(NONE);
        Rule[] raced = applicable.putIfAbsent(type, found);
        return raced != null ? raced : found;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("[");
        for (Rule r : rules) {
            if (sb.length() > 1) {
                sb.append(", ");
            }
            sb.append(r.type.getSimpleName()).append("{+").append(r.includes).append(" -").append(r.excludes).append('}');
        }
        return sb.append(']').toString();
    }

    private static final class Rule {
        private final Class<?> type;
        private final Set<String> includes;
        private final Set<String> excludes;

        Rule(Class<?> type, Set<String> includes, Set<String> excludes) {
            this.type = type;
            this.includes = includes;
            this.excludes = excludes;
        }
    }
}
