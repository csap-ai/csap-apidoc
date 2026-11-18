package ai.csap.apidoc.runtime.filter;

import java.lang.invoke.CallSite;
import java.lang.invoke.LambdaMetafactory;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONFactory;
import com.alibaba.fastjson2.JSONWriter;
import com.alibaba.fastjson2.annotation.JSONType;
import com.alibaba.fastjson2.codec.FieldInfo;
import com.alibaba.fastjson2.writer.FieldWriter;
import com.alibaba.fastjson2.writer.ObjectWriter;
import com.alibaba.fastjson2.writer.ObjectWriterAdapter;
import com.alibaba.fastjson2.writer.ObjectWriterCreatorASM;
import com.alibaba.fastjson2.writer.ObjectWriterImplMap;
import com.alibaba.fastjson2.writer.ObjectWriterProvider;

/**
 * fastjson2 path 模式的快速路径:不在 JSONWriter 上挂过滤器,而是按 (掩码节点, 类型) 预编译只含放行字段的
 * ObjectWriter(由 fastjson2 自己的 ASM 生成器生成,与原生写出同样快)。
 *
 * <p>输出与原来的过滤器实现({@link Fastjson2ResponseFilterEngine} 的 Root / Scoped)逐字节一致:
 * <ul>
 *   <li>Bean:按原 ObjectWriter 的字段顺序,只保留掩码放行的字段;基础类型等不可能是容器的字段沿用原字段的
 *       名称、格式、特性;可能是容器的字段(对象、集合、Map、Object)取值后包成 {@link Masked},由
 *       {@link MaskedWriter} 用值的运行时类型继续写出——与原来 ValueFilter 把值包成 Scoped 的效果相同;</li>
 *   <li>集合、数组:逐个元素写出,元素沿用当前掩码;</li>
 *   <li>Map:与 fastjson2 带过滤器时写 Map 的逻辑相同(键按掩码放行,值套用子掩码);</li>
 *   <li>自定义 ObjectWriter、带特殊字段配置的类等无法预编译的值:临时挂上原来的过滤器写出这一段。</li>
 * </ul>
 * 只在 {@link #eligible} 成立时使用(没有其他过滤器、没有会改变空值 / 引用 / 类型信息写法的特性),
 * 其余情况整次请求走原来的实现。
 *
 * @author ycf
 * @since 1.2.0
 */
final class Fastjson2FastPath {

    /**
     * 这些特性下原来的过滤器实现与原生写出的空值、引用、类型信息、顺序等写法不同,不走快速路径
     */
    private static final long UNSUPPORTED_FEATURES = mask(
            JSONWriter.Feature.ReferenceDetection,
            JSONWriter.Feature.WriteClassName,
            JSONWriter.Feature.BeanToArray,
            JSONWriter.Feature.NotWriteDefaultValue,
            JSONWriter.Feature.WriteNullListAsEmpty,
            JSONWriter.Feature.WriteNullStringAsEmpty,
            JSONWriter.Feature.WriteNullNumberAsZero,
            JSONWriter.Feature.WriteNullBooleanAsFalse,
            JSONWriter.Feature.NullAsDefaultValue,
            JSONWriter.Feature.IgnoreNonFieldGetter,
            JSONWriter.Feature.FieldBased,
            JSONWriter.Feature.IgnoreErrorGetter,
            JSONWriter.Feature.IgnoreNoneSerializable,
            JSONWriter.Feature.ErrorOnNoneSerializable,
            JSONWriter.Feature.MapSortField,
            JSONWriter.Feature.SortMapEntriesByKeys,
            JSONWriter.Feature.PrettyFormat);

    /**
     * 字段上允许出现的内部标记(其余标记或字段级特性说明字段有特殊写法,该类不预编译)
     */
    private static final long SAFE_FIELD_FEATURES = FieldInfo.FIELD_MASK | FieldInfo.JIT | FieldInfo.DISABLE_SMART_MATCH
            | FieldInfo.DISABLE_UNSAFE | FieldInfo.READ_ONLY | FieldInfo.DISABLE_REFERENCE_DETECT
            | FieldInfo.DISABLE_ARRAY_MAPPING | FieldInfo.DISABLE_AUTO_TYPE | FieldInfo.DISABLE_JSONB
            | FieldInfo.READ_USING_MASK;

    private static final Set<String> SEQUENCE_WRITERS = new HashSet<>(Arrays.asList(
            "com.alibaba.fastjson2.writer.ObjectWriterImplList",
            "com.alibaba.fastjson2.writer.ObjectWriterImplCollection",
            "com.alibaba.fastjson2.writer.ObjectWriterImplListStr",
            "com.alibaba.fastjson2.writer.ObjectWriterArray",
            "com.alibaba.fastjson2.writer.ObjectWriterArrayFinal"));

    /**
     * 当前线程正在快速写出的过滤状态(无法预编译的值临时挂回原来的过滤器时使用)
     */
    private static final ThreadLocal<FilterSession> CURRENT = new ThreadLocal<>();

    private static final Field MAP_FEATURES = mapFeaturesField();

    private Fastjson2FastPath() {
    }

    private static long mask(JSONWriter.Feature... features) {
        long m = 0;
        for (JSONWriter.Feature f : features) {
            m |= f.mask;
        }
        return m;
    }

    private static Field mapFeaturesField() {
        try {
            Field f = ObjectWriterImplMap.class.getDeclaredField("features");
            f.setAccessible(true);
            return f;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    /**
     * 本次写出能否走快速路径
     *
     * @param writer   JSONWriter
     * @param features 写出根对象时传入的特性
     */
    static boolean eligible(JSONWriter writer, long features) {
        if (features != 0 || writer.jsonb) {
            return false;
        }
        JSONWriter.Context c = writer.getContext();
        return c.provider == JSONFactory.getDefaultObjectWriterProvider()
                && (c.getFeatures() & UNSUPPORTED_FEATURES) == 0
                && c.getPropertyPreFilter() == null && c.getValueFilter() == null && c.getNameFilter() == null
                && c.getPropertyFilter() == null && c.getBeforeFilter() == null && c.getAfterFilter() == null
                && c.getContextValueFilter() == null && c.getContextNameFilter() == null
                && c.getLabelFilter() == null;
    }

    /**
     * 快速写出被过滤的值
     *
     * @param writer  JSONWriter
     * @param value   值(非 null)
     * @param scope   path 模式为值适用的掩码(不是 OUTSIDE),csap 模式为接口的类型规则
     * @param session 过滤状态
     */
    static void writeRoot(JSONWriter writer, Object value, Object scope, FilterSession session) {
        FilterSession previous = CURRENT.get();
        CURRENT.set(session);
        try {
            write(writer, value, scope);
        } finally {
            CURRENT.set(previous);
        }
    }

    /**
     * 键在该范围内的子范围:path 模式为子掩码,csap 模式仍是同一份类型规则;不输出时返回 null
     */
    private static Object child(Object scope, Class<?> owner, String name) {
        if (scope instanceof FieldMask) {
            FieldMask mask = (FieldMask) scope;
            if (mask == FieldMask.PASS) {
                return mask;
            }
            FieldMask child = mask.lookup(name);
            return child == FieldMask.DROP ? null : child;
        }
        return ((TypeRules) scope).allows(owner, name) ? scope : null;
    }

    /**
     * 与原来"值的运行时类型的 ObjectWriter 在过滤器下写出"等价
     */
    static void write(JSONWriter writer, Object value, Object scope) {
        if (!FilterSession.isContainer(value)) {
            writer.getObjectWriter(value.getClass()).write(writer, value, null, null, 0);
            return;
        }
        write(writer, value, plan(writer, value, scope), scope);
    }

    private static Plan plan(JSONWriter writer, Object value, Object scope) {
        Class<?> type = value.getClass();
        if (scope instanceof FieldMask) {
            return ((FieldMask) scope).plan(PlanCache.FASTJSON2, type, t -> Plan.create(writer, t, scope, value));
        }
        return ((TypeRules) scope).plan(PlanCache.FASTJSON2, type, t -> Plan.create(writer, t, scope, value));
    }

    /**
     * 写出容器值(已确定是容器,计划已查好)
     */
    static void write(JSONWriter writer, Object value, Plan plan, Object scope) {
        switch (plan.kind) {
            case Plan.NATIVE:
                plan.writer.write(writer, value, null, null, 0);
                break;
            case Plan.SEQUENCE:
                writeSequence(writer, value, scope);
                break;
            case Plan.MAP:
                writeMap(writer, (Map<?, ?>) value, scope);
                break;
            default:
                writeLegacy(writer, value, scope);
                break;
        }
    }

    /**
     * 集合、数组:元素沿用当前范围(同一类型的元素沿用上一个元素的计划)
     */
    private static void writeSequence(JSONWriter writer, Object value, Object scope) {
        writer.startArray();
        Class<?> lastType = null;
        Plan lastPlan = null;
        if (value instanceof List) {
            List<?> list = (List<?>) value;
            for (int i = 0, n = list.size(); i < n; i++) {
                if (i != 0) {
                    writer.writeComma();
                }
                Object item = list.get(i);
                if (item == null) {
                    writer.writeNull();
                } else if (item.getClass() == lastType) {
                    write(writer, item, lastPlan, scope);
                } else if (FilterSession.isContainer(item)) {
                    lastType = item.getClass();
                    lastPlan = plan(writer, item, scope);
                    write(writer, item, lastPlan, scope);
                } else {
                    writer.getObjectWriter(item.getClass()).write(writer, item, i, null, 0);
                }
            }
        } else if (value instanceof Collection) {
            int i = 0;
            for (Object item : (Collection<?>) value) {
                if (i != 0) {
                    writer.writeComma();
                }
                writeElement(writer, item, i++, scope);
            }
        } else {
            for (int i = 0, n = Array.getLength(value); i < n; i++) {
                if (i != 0) {
                    writer.writeComma();
                }
                writeElement(writer, Array.get(value, i), i, scope);
            }
        }
        writer.endArray();
    }

    private static void writeElement(JSONWriter writer, Object item, int index, Object scope) {
        if (item == null) {
            writer.writeNull();
        } else if (FilterSession.isContainer(item)) {
            write(writer, item, scope);
        } else {
            writer.getObjectWriter(item.getClass()).write(writer, item, index, null, 0);
        }
    }

    /**
     * 与 {@code ObjectWriterImplMap#writeWithFilter} 相同,PropertyPreFilter / ValueFilter 换成掩码 / 类型规则判断
     */
    private static void writeMap(JSONWriter writer, Map<?, ?> map, Object scope) {
        writer.startObject();
        boolean writeNulls = writer.isWriteNulls();
        Object lastScope = null;
        Class<?> lastType = null;
        Plan lastPlan = null;
        Class<?> mapType = map.getClass();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            Object value = entry.getValue();
            if (value == null && !writeNulls) {
                continue;
            }
            Object entryKey = entry.getKey();
            String key = entryKey == null ? null : entryKey.toString();
            Object child = child(scope, mapType, key);
            if (child == null) {
                continue;
            }
            writer.writeName(key);
            writer.writeColon();
            if (value == null) {
                writer.writeNull();
            } else if (FilterSession.isContainer(value)) {
                if (child != lastScope || value.getClass() != lastType) {
                    lastScope = child;
                    lastType = value.getClass();
                    lastPlan = plan(writer, value, child);
                }
                write(writer, value, lastPlan, child);
            } else {
                writer.getObjectWriter(value.getClass()).write(writer, value, null, null, 0);
            }
        }
        writer.endObject();
    }

    /**
     * 无法预编译的值:临时挂上原来的过滤器写出(其中再遇到放行的容器值时回到快速路径)
     */
    private static void writeLegacy(JSONWriter writer, Object value, Object scope) {
        FilterSession session = CURRENT.get();
        JSONWriter.Context context = writer.getContext();
        boolean path = scope instanceof FieldMask;
        if (path) {
            session.push((FieldMask) scope);
        }
        Fastjson2ResponseFilterEngine.installFilters(context, session, null, null, true);
        try {
            writer.getObjectWriter(value.getClass()).write(writer, value, null, null, 0);
        } finally {
            context.setPropertyPreFilter(null);
            context.setValueFilter(null);
            if (path) {
                session.pop();
            }
        }
    }

    /**
     * 放行的容器值:写出时继续按掩码过滤
     */
    @JSONType(serializer = MaskedWriter.class)
    static final class Masked {
        final Object value;
        final Slot slot;

        Masked(Object value, Slot slot) {
            this.value = value;
            this.slot = slot;
        }
    }

    /**
     * {@link Masked} 的写出
     */
    public static final class MaskedWriter implements ObjectWriter<Object> {
        @Override
        public void write(JSONWriter writer, Object object, Object fieldName, Type fieldType, long features) {
            Masked masked = (Masked) object;
            Slot slot = masked.slot;
            Fastjson2FastPath.write(writer, masked.value, slot.plan(writer, masked.value), slot.scope);
        }
    }

    /**
     * 一个容器字段:子掩码,以及按值的类型缓存的计划(大多数字段的值总是同一类型)
     */
    static final class Slot {
        final Object scope;
        private Cached cached;

        Slot(Object scope) {
            this.scope = scope;
        }

        Plan plan(JSONWriter writer, Object value) {
            Cached c = cached;
            Class<?> type = value.getClass();
            if (c != null && c.type == type) {
                return c.plan;
            }
            Plan p = Fastjson2FastPath.plan(writer, value, scope);
            cached = new Cached(type, p);
            return p;
        }
    }

    private static final class Cached {
        final Class<?> type;
        final Plan plan;

        Cached(Class<?> type, Plan plan) {
            this.type = type;
            this.plan = plan;
        }
    }

    /**
     * 某个类型在某个掩码节点下的写法
     */
    static final class Plan {
        static final int NATIVE = 0;
        static final int SEQUENCE = 1;
        static final int MAP = 2;
        static final int LEGACY = 3;

        private static final Plan SEQUENCE_PLAN = new Plan(SEQUENCE, null);
        private static final Plan MAP_PLAN = new Plan(MAP, null);
        private static final Plan LEGACY_PLAN = new Plan(LEGACY, null);

        final int kind;
        final ObjectWriter writer;

        Plan(int kind, ObjectWriter writer) {
            this.kind = kind;
            this.writer = writer;
        }

        static Plan create(JSONWriter jsonWriter, Class<?> type, Object scope, Object sample) {
            ObjectWriter writer = jsonWriter.getObjectWriter(type);
            if (writer == null) {
                return LEGACY_PLAN;
            }
            if (Map.class.isAssignableFrom(type)) {
                return writer.getClass() == ObjectWriterImplMap.class && mapFeatures(writer) == 0 ? MAP_PLAN
                        : LEGACY_PLAN;
            }
            if (Collection.class.isAssignableFrom(type) || type.isArray()) {
                return SEQUENCE_WRITERS.contains(writer.getClass().getName()) ? SEQUENCE_PLAN : LEGACY_PLAN;
            }
            if (!(writer instanceof ObjectWriterAdapter)) {
                return LEGACY_PLAN;
            }
            try {
                return bean(jsonWriter, type, scope, writer, sample);
            } catch (RuntimeException | LinkageError e) {
                return LEGACY_PLAN;
            }
        }

        private static long mapFeatures(ObjectWriter writer) {
            try {
                return MAP_FEATURES == null ? -1 : MAP_FEATURES.getLong(writer);
            } catch (IllegalAccessException | RuntimeException e) {
                return -1;
            }
        }

        @SuppressWarnings({"unchecked", "rawtypes"})
        private static Plan bean(JSONWriter jsonWriter, Class<?> type, Object scope, ObjectWriter writer,
                                 Object sample) {
            boolean csap = scope instanceof TypeRules;
            List<FieldWriter> all = writer.getFieldWriters();
            if (all == null) {
                return LEGACY_PLAN;
            }
            ObjectWriterProvider provider = jsonWriter.getContext().provider;
            ObjectWriterCreatorASM creator = new ObjectWriterCreatorASM(JSON.class.getClassLoader());
            // 字段上的自定义写法(serializeUsing 等)只能在新建的、尚未使用过的字段上看出来
            // (使用过的字段会缓存值的 ObjectWriter)
            ObjectWriter fresh = provider.getCreator() instanceof ObjectWriterCreatorASM
                    ? creator.createObjectWriter(type, 0, provider)
                    : provider.getCreator().createObjectWriter(type, 0, provider);
            List<FieldWriter> freshFields = fresh.getFieldWriters();
            if (freshFields == null || freshFields.size() != all.size()) {
                return LEGACY_PLAN;
            }
            boolean flat = true;
            for (int i = 0; i < all.size(); i++) {
                FieldWriter fw = all.get(i);
                FieldWriter check = freshFields.get(i);
                if ((fw.features & ~SAFE_FIELD_FEATURES) != 0 || check.getInitWriter() != null || fw.locale != null
                        || !fw.fieldName.equals(check.fieldName)) {
                    return LEGACY_PLAN;
                }
                if (!FilterSession.isLeafType(fw.fieldClass)) {
                    flat = false;
                    // csap 模式原来由字段自己写出容器值(格式生效),这里改由值的类型写出,带格式时不预编译
                    if (csap && fw.format != null) {
                        return LEGACY_PLAN;
                    }
                }
            }
            List<FieldWriter> kept = new ArrayList<>(all.size());
            List<Object> children = new ArrayList<>(all.size());
            for (FieldWriter fw : all) {
                Object child = child(scope, type, fw.fieldName);
                if (child != null) {
                    kept.add(fw);
                    children.add(child);
                }
            }
            // 没有容器字段、全部放行:与原生写出相同
            if (flat && kept.size() == all.size()) {
                return new Plan(NATIVE, writer);
            }
            List<FieldWriter> fields = new ArrayList<>(kept.size());
            for (int i = 0; i < kept.size(); i++) {
                FieldWriter fw = kept.get(i);
                Function getter = getter(type, fw, sample);
                if (FilterSession.isLeafType(fw.fieldClass)) {
                    Class<?> boxed = box(fw.fieldClass);
                    Type fieldType = fw.fieldClass.isPrimitive() ? boxed : fw.fieldType;
                    fields.add(creator.createFieldWriter(provider, (Class) type, fw.fieldName, fw.ordinal, fw.features,
                            fw.format, fw.label, fieldType, (Class) boxed, null, null, getter));
                } else {
                    Slot slot = new Slot(children.get(i));
                    Function wrap = o -> {
                        Object v = getter.apply(o);
                        return FilterSession.isContainer(v) ? new Masked(v, slot) : v;
                    };
                    fields.add(creator.createFieldWriter(provider, (Class) type, fw.fieldName, fw.ordinal, fw.features,
                            null, fw.label, Object.class, Object.class, null, null, wrap));
                }
            }
            return new Plan(NATIVE, creator.createObjectWriter(fields));
        }
    }

    private static Class<?> box(Class<?> type) {
        return type.isPrimitive() ? MethodType.methodType(type).wrap().returnType() : type;
    }

    /**
     * 字段取值:public getter 用 LambdaMetafactory 生成(与 fastjson2 生成的取值一样快),其余沿用原字段的取值
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Function getter(Class<?> type, FieldWriter fw, Object sample) {
        Function own = fw.getFunction();
        if (own != null) {
            return own;
        }
        Method method = fw.method;
        if (method != null && method.getParameterCount() == 0 && Modifier.isPublic(method.getModifiers())
                && Modifier.isPublic(method.getDeclaringClass().getModifiers()) && Modifier.isPublic(type.getModifiers())) {
            try {
                MethodHandles.Lookup lookup = MethodHandles.lookup();
                MethodHandle handle = lookup.unreflect(method);
                CallSite site = LambdaMetafactory.metafactory(lookup, "apply", MethodType.methodType(Function.class),
                        MethodType.methodType(Object.class, Object.class), handle,
                        MethodType.methodType(box(method.getReturnType()), type));
                Function function = (Function) site.getTarget().invoke();
                // 试取一次:类型对本类的类加载器不可见时在这里失败
                function.apply(sample);
                return function;
            } catch (Throwable e) {
                // 例如类型不在本类的类加载器可见范围内:改用原字段的取值
            }
        }
        return fw::getFieldValue;
    }
}
