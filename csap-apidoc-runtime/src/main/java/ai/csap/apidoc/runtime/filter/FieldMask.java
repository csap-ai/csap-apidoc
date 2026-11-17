package ai.csap.apidoc.runtime.filter;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import cn.hutool.core.util.StrUtil;

/**
 * 编译好的返回字段掩码(path 模式),语义与 Go 版 {@code filter.Node} 一致。
 *
 * <ul>
 *   <li>{@link #PASS}:原样输出整个值,不再过滤下级;</li>
 *   <li>普通节点:只输出 {@link #fields()} 里的键,值套用对应的子掩码;</li>
 *   <li>map 节点({@link #mapValues()} 不为空):所有键都输出,每个值套用 mapValues。</li>
 * </ul>
 * 对数组 / 集合,掩码作用于每个元素。实例不可变,可在线程间共享。
 *
 * @author ycf
 * @since 1.2.0
 */
public final class FieldMask {

    /**
     * 原样输出整个值
     */
    public static final FieldMask PASS = new FieldMask(Collections.emptyMap(), null, "PASS");

    /**
     * {@link #lookup(String)} 的结果:该键不输出
     */
    public static final FieldMask DROP = new FieldMask(Collections.emptyMap(), null, "DROP");

    /**
     * 过滤过程中的位置标记:当前对象不在要过滤的返回值内(如外层统一包装)
     */
    static final FieldMask OUTSIDE = new FieldMask(Collections.emptyMap(), null, "OUTSIDE");

    private final Map<String, FieldMask> fields;

    private final FieldMask mapValues;

    private final String label;

    private FieldMask(Map<String, FieldMask> fields, FieldMask mapValues, String label) {
        this.fields = fields;
        this.mapValues = mapValues;
        this.label = label;
    }

    /**
     * 查找键:不输出时返回 {@link #DROP};原样输出时返回 {@link #PASS};否则返回子掩码。
     *
     * @param key JSON 键
     * @return 结果
     */
    public FieldMask lookup(String key) {
        if (this == PASS || this == OUTSIDE) {
            return PASS;
        }
        if (mapValues != null) {
            return mapValues;
        }
        FieldMask child = fields.get(key);
        return child == null ? DROP : child;
    }

    /**
     * 是否输出该键
     */
    public boolean allows(String key) {
        return lookup(key) != DROP;
    }

    /**
     * 允许输出的键及子掩码(不可修改)
     */
    public Map<String, FieldMask> fields() {
        return fields;
    }

    /**
     * map 节点每个值套用的掩码;不是 map 节点时为 null
     */
    public FieldMask mapValues() {
        return mapValues;
    }

    @Override
    public String toString() {
        if (label != null) {
            return label;
        }
        if (mapValues != null) {
            return "{*:" + mapValues + "}";
        }
        StringBuilder sb = new StringBuilder("{");
        Map<FieldMask, String> printed = new IdentityHashMap<>();
        fields.forEach((k, v) -> {
            if (sb.length() > 1) {
                sb.append(',');
            }
            sb.append(k);
            if (v != PASS) {
                String seen = printed.get(v);
                sb.append(':').append(seen != null ? "=" + seen : v.toString());
                printed.putIfAbsent(v, k);
            }
        });
        return sb.append('}').toString();
    }

    /**
     * 按路径白名单编译掩码(与 Go 版 {@code filter.Compile} 一致)。
     *
     * <ul>
     *   <li>include 是要输出的字段路径(点号分隔,数组不写下标),上级路径自动放行;</li>
     *   <li>某个路径没有任何下级出现在 include 里时,它的值原样输出;</li>
     *   <li>mapPaths 是值为 map 的路径:该节点的所有键都保留,下级路径作用于每个值;</li>
     *   <li>每一段同时放行驼峰和下划线两种写法。</li>
     * </ul>
     * 根节点始终是白名单:include 为空时输出 {@code {}}。
     *
     * @param include  要输出的路径
     * @param mapPaths 值为 map 的路径,可为 null
     * @return 掩码
     */
    public static FieldMask compile(Collection<String> include, Collection<String> mapPaths) {
        Set<String> maps = mapPaths == null ? Collections.emptySet() : new HashSet<>(mapPaths);
        Builder root = new Builder();
        for (String path : include) {
            if (path == null || path.isEmpty()) {
                continue;
            }
            Builder node = root;
            String[] segs = path.split("\\.");
            StringBuilder prefix = new StringBuilder();
            for (int i = 0; i < segs.length; i++) {
                if (i > 0) {
                    prefix.append('.');
                }
                prefix.append(segs[i]);
                node = node.child(segs[i]);
                if (node.mapValues == null && maps.contains(prefix.toString())) {
                    node.mapValues = new Builder();
                }
                if (node.mapValues != null && i < segs.length - 1) {
                    // 下一段在 map 的值里继续
                    node = node.mapValues;
                }
            }
        }
        Map<Builder, FieldMask> done = new IdentityHashMap<>();
        Map<String, FieldMask> rootFields = new LinkedHashMap<>();
        root.fields.forEach((k, v) -> rootFields.put(k, v.freeze(done)));
        return new FieldMask(Collections.unmodifiableMap(rootFields), null, null);
    }

    /**
     * 手工构造掩码(测试与扩展用)
     *
     * @param fields 键及子掩码,子掩码为 null 表示原样输出
     * @return 掩码
     */
    public static FieldMask of(Map<String, FieldMask> fields) {
        Map<String, FieldMask> copy = new LinkedHashMap<>();
        fields.forEach((k, v) -> copy.put(k, v == null ? PASS : v));
        return new FieldMask(Collections.unmodifiableMap(copy), null, null);
    }

    /**
     * 手工构造 map 节点:所有键都输出,值套用 values
     */
    public static FieldMask mapOf(FieldMask values) {
        return values == null || values == PASS ? PASS : new FieldMask(Collections.emptyMap(), values, null);
    }

    /**
     * 一段路径的两种写法(原样、下划线、驼峰,去重)
     */
    static List<String> aliases(String seg) {
        List<String> out = new ArrayList<>(3);
        out.add(seg);
        String under = StrUtil.toUnderlineCase(seg);
        if (!out.contains(under)) {
            out.add(under);
        }
        String camel = StrUtil.toCamelCase(seg);
        if (!out.contains(camel)) {
            out.add(camel);
        }
        return out;
    }

    /**
     * 编译期的可变节点
     */
    private static final class Builder {
        private final Map<String, Builder> fields = new LinkedHashMap<>();
        private Builder mapValues;

        Builder child(String seg) {
            Builder existing = fields.get(seg);
            if (existing != null) {
                return existing;
            }
            List<String> names = aliases(seg);
            Builder c = null;
            for (String name : names) {
                c = fields.get(name);
                if (c != null) {
                    break;
                }
            }
            if (c == null) {
                c = new Builder();
            }
            for (String name : names) {
                fields.put(name, c);
            }
            return c;
        }

        /**
         * 没有下级的节点变成 PASS(原样输出);别名共用同一个子掩码
         */
        FieldMask freeze(Map<Builder, FieldMask> done) {
            FieldMask cached = done.get(this);
            if (cached != null) {
                return cached;
            }
            FieldMask result;
            if (mapValues != null) {
                result = mapOf(mapValues.freeze(done));
            } else if (fields.isEmpty()) {
                result = PASS;
            } else {
                Map<String, FieldMask> frozen = new HashMap<>(fields.size() * 2);
                fields.forEach((k, v) -> frozen.put(k, v.freeze(done)));
                result = new FieldMask(Collections.unmodifiableMap(frozen), null, null);
            }
            done.put(this, result);
            return result;
        }
    }
}
