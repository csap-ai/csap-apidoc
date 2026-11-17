package ai.csap.apidoc.runtime.filter;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.serializer.ASMSerializerFactory;
import com.alibaba.fastjson.serializer.CollectionCodec;
import com.alibaba.fastjson.serializer.JSONSerializer;
import com.alibaba.fastjson.serializer.JavaBeanSerializer;
import com.alibaba.fastjson.serializer.ListSerializer;
import com.alibaba.fastjson.serializer.MapSerializer;
import com.alibaba.fastjson.serializer.ObjectArrayCodec;
import com.alibaba.fastjson.serializer.ObjectSerializer;
import com.alibaba.fastjson.serializer.SerializeBeanInfo;
import com.alibaba.fastjson.serializer.SerializeConfig;
import com.alibaba.fastjson.serializer.SerializeFilterable;
import com.alibaba.fastjson.serializer.SerializerFeature;
import com.alibaba.fastjson.util.FieldInfo;

/**
 * fastjson 1.x path 模式的快速路径。
 *
 * <p>原来的实现在 JSONSerializer 上挂全局 PropertyPreFilter / ValueFilter:打开过滤器后,fastjson 所有 ASM
 * 序列化器都退回逐字段调用过滤器的慢路径。快速路径不挂过滤器:
 * <ul>
 *   <li>Bean:按 (掩码节点, 类型) 预编译只含放行字段的序列化器(原序列化器是 ASM 生成的,子集也用 ASM 生成);</li>
 *   <li>被过滤的值由 {@link Masked} 写出——它是 JSONSerializer 的子类,复制原 JSONSerializer 的全部状态、
 *       共用同一个输出,只改写 {@link JSONSerializer#getObjectWriter}:在过滤范围内把容器类型(对象、集合、Map)
 *       交给 {@link Dispatcher},按当前位置算出子掩码,再用对应的预编译序列化器写出。这与原来 ValueFilter
 *       把容器值包成 Scoped 的效果相同(包括 Scoped 写出时不带字段名、引用检测路径);集合、Map 仍由 fastjson
 *       自己的序列化器写出;</li>
 *   <li>PASS 子树用原序列化器;自定义序列化器、带格式或 unwrapped 的容器字段等无法预编译的值,
 *       临时挂上原来的过滤器写出这一段。</li>
 * </ul>
 * 只在 {@link #eligible} 成立时使用(JSONSerializer 是 fastjson 自带的类型、没有其他过滤器、
 * 没有 WriteClassName / BeanToArray / PrettyFormat),输出与原来的实现逐字节一致。
 *
 * <p>共享的原序列化器只在"原样输出"的位置使用,不会把 {@link Dispatcher} 缓存进全局的序列化器;
 * 含容器字段的 Bean 总是使用自己的预编译副本。
 *
 * @author ycf
 * @since 1.2.0
 */
final class FastjsonFastPath {

    private static final int UNSUPPORTED_FEATURES = SerializerFeature.WriteClassName.mask
            | SerializerFeature.BeanToArray.mask | SerializerFeature.PrettyFormat.mask;

    private static final Field[] FILTER_FIELDS = filterFields();

    /**
     * JSONSerializer 及其父类上需要复制的实例字段(out、config 由构造器传入)
     */
    private static final Field[] STATE_FIELDS = stateFields();

    private static final Field BEAN_INFO = field(JavaBeanSerializer.class, "beanInfo");

    private static final Field BI_TYPE = field(SerializeBeanInfo.class, "beanType");

    private static final Field BI_JSON_TYPE = field(SerializeBeanInfo.class, "jsonType");

    private static final Field BI_TYPE_NAME = field(SerializeBeanInfo.class, "typeName");

    private static final Field BI_TYPE_KEY = field(SerializeBeanInfo.class, "typeKey");

    private static final Field BI_FEATURES = field(SerializeBeanInfo.class, "features");

    private static final Field BI_FIELDS = field(SerializeBeanInfo.class, "fields");

    private static final Field BI_SORTED = field(SerializeBeanInfo.class, "sortedFields");

    private static final boolean AVAILABLE = FILTER_FIELDS != null && STATE_FIELDS != null && BEAN_INFO != null
            && BI_TYPE != null && BI_JSON_TYPE != null && BI_TYPE_NAME != null && BI_TYPE_KEY != null
            && BI_FEATURES != null && BI_FIELDS != null && BI_SORTED != null;

    private FastjsonFastPath() {
    }

    private static Field field(Class<?> type, String name) {
        try {
            Field f = type.getDeclaredField(name);
            f.setAccessible(true);
            return f;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    private static Field[] filterFields() {
        String[] names = {"beforeFilters", "afterFilters", "propertyFilters", "valueFilters", "nameFilters",
            "propertyPreFilters", "labelFilters", "contextValueFilters"};
        Field[] out = new Field[names.length];
        for (int i = 0; i < names.length; i++) {
            out[i] = field(SerializeFilterable.class, names[i]);
            if (out[i] == null) {
                return null;
            }
        }
        return out;
    }

    private static Field[] stateFields() {
        try {
            List<Field> list = new ArrayList<>();
            for (Class<?> c = JSONSerializer.class; c != Object.class; c = c.getSuperclass()) {
                for (Field f : c.getDeclaredFields()) {
                    if (Modifier.isStatic(f.getModifiers()) || "out".equals(f.getName())
                            || "config".equals(f.getName())) {
                        continue;
                    }
                    f.setAccessible(true);
                    list.add(f);
                }
            }
            return list.toArray(new Field[0]);
        } catch (RuntimeException e) {
            return null;
        }
    }

    /**
     * 本次写出能否走快速路径
     */
    static boolean eligible(JSONSerializer serializer) {
        if (!AVAILABLE || serializer.getClass() != JSONSerializer.class
                || serializer.out.isEnabled(UNSUPPORTED_FEATURES)) {
            return false;
        }
        try {
            for (Field f : FILTER_FIELDS) {
                List<?> list = (List<?>) f.get(serializer);
                if (list != null && !list.isEmpty()) {
                    return false;
                }
            }
            return true;
        } catch (IllegalAccessException | RuntimeException e) {
            return false;
        }
    }

    /**
     * 快速写出被过滤的值,与原来"在过滤器下 {@code serializer.write(value)}"等价
     *
     * @param serializer JSONSerializer(满足 {@link #eligible})
     * @param value      值(非 null)
     * @param mask       值适用的掩码(不是 OUTSIDE)
     * @param session    过滤状态
     */
    static void writeRoot(JSONSerializer serializer, Object value, FieldMask mask, FilterSession session)
            throws IOException {
        Masked masked = new Masked(serializer, session);
        try {
            masked.writeValue(value, mask, null, null, 0);
        } finally {
            masked.copyTo(serializer);
        }
    }

    /**
     * csap 模式快速写出(与原来"挂上按类型判断的 PropertyPreFilter 后 {@code serializer.write(value)}"等价):
     * 类型规则只与对象的类型有关,{@link Masked} 对每个 Bean / Map 类型直接返回按规则预编译的序列化器
     *
     * @param serializer JSONSerializer(满足 {@link #eligible})
     * @param value      值(非 null)
     * @param rules      接口的类型规则
     * @param session    过滤状态
     */
    static void writeCsap(JSONSerializer serializer, Object value, TypeRules rules, FilterSession session)
            throws IOException {
        Masked masked = new Masked(serializer, session, rules);
        try {
            masked.write(value);
        } finally {
            masked.copyTo(serializer);
        }
    }

    /**
     * 一段正在写出的值:掩码和写法
     */
    static final class Frame {
        static final int BEAN = 0;
        static final int MAP = 1;
        static final int SEQUENCE = 2;
        static final int NATIVE = 3;

        private static final String[] NO_NAMES = new String[0];
        private static final Slot[] NO_SLOTS = new Slot[0];

        final FieldMask mask;
        final int kind;

        /**
         * Bean 的容器字段名及其子掩码
         */
        private final String[] names;
        private final Slot[] slots;

        /**
         * 集合元素(当前掩码)或 map 节点的值(mapValues)
         */
        final Slot all;

        Frame(FieldMask mask, int kind) {
            this(mask, kind, NO_NAMES, NO_SLOTS);
        }

        Frame(FieldMask mask, int kind, String[] names, Slot[] slots) {
            this.mask = mask;
            this.kind = kind;
            this.names = names;
            this.slots = slots;
            if (kind == SEQUENCE) {
                this.all = new Slot(mask);
            } else if (kind == MAP && mask.mapValues() != null) {
                this.all = new Slot(mask.mapValues());
            } else {
                this.all = null;
            }
        }

        /**
         * Bean 字段的子掩码(字段名通常就是同一个字符串实例)
         */
        Slot slot(String name) {
            String[] n = names;
            for (int i = 0; i < n.length; i++) {
                if (n[i] == name) {
                    return slots[i];
                }
            }
            for (int i = 0; i < n.length; i++) {
                if (n[i].equals(name)) {
                    return slots[i];
                }
            }
            return null;
        }
    }

    /**
     * 一个位置的子掩码,以及按值的类型缓存的计划(同一位置的值大多是同一类型)
     */
    static final class Slot {
        final FieldMask mask;
        private Cached cached;

        Slot(FieldMask mask) {
            this.mask = mask;
        }

        Plan plan(Masked masked, Class<?> type) {
            Cached c = cached;
            SerializeConfig config = masked.getMapping();
            if (c != null && c.type == type && c.config == config) {
                return c.plan;
            }
            Plan p = masked.plan(type, mask);
            cached = new Cached(type, config, p);
            return p;
        }
    }

    private static final class Cached {
        final Class<?> type;
        final SerializeConfig config;
        final Plan plan;

        Cached(Class<?> type, SerializeConfig config, Plan plan) {
            this.type = type;
            this.config = config;
            this.plan = plan;
        }
    }

    private static final Frame NATIVE_FRAME = new Frame(FieldMask.PASS, Frame.NATIVE);

    /**
     * 写出被过滤的值用的 JSONSerializer:复制原 JSONSerializer 的状态,共用同一个输出
     */
    static final class Masked extends JSONSerializer {
        private final FilterSession session;

        /**
         * csap 模式的类型规则;path 模式为 null
         */
        private final TypeRules rules;
        private Frame[] frames = new Frame[16];
        private int size;

        Masked(JSONSerializer source, FilterSession session) {
            this(source, session, null);
        }

        Masked(JSONSerializer source, FilterSession session, TypeRules rules) {
            super(source.out, source.getMapping());
            this.session = session;
            this.rules = rules;
            copy(source, this);
        }

        private static void copy(JSONSerializer from, JSONSerializer to) {
            try {
                for (Field f : STATE_FIELDS) {
                    f.set(to, f.get(from));
                }
            } catch (IllegalAccessException e) {
                throw new IllegalStateException(e);
            }
        }

        /**
         * 写完后把引用表、上下文等状态写回原 JSONSerializer
         */
        void copyTo(JSONSerializer target) {
            copy(this, target);
        }

        Frame top() {
            return size == 0 ? NATIVE_FRAME : frames[size - 1];
        }

        private void push(Frame frame) {
            if (size == frames.length) {
                frames = Arrays.copyOf(frames, size * 2);
            }
            frames[size++] = frame;
        }

        private void pop() {
            frames[--size] = null;
        }

        @Override
        public ObjectSerializer getObjectWriter(Class<?> clazz) {
            if (rules != null) {
                return csapWriter(clazz);
            }
            Frame top = size == 0 ? NATIVE_FRAME : frames[size - 1];
            if (top.kind == Frame.NATIVE || !FilterSession.isContainerType(clazz)) {
                return super.getObjectWriter(clazz);
            }
            return Dispatcher.INSTANCE;
        }

        /**
         * csap 模式:Bean 用按规则预编译的副本(总是副本:其字段缓存的序列化器来自本类,不能放进共享的原序列化器);
         * 规则作用于该类型的 Map 用只输出放行键的 MapSerializer;其他类型用原序列化器
         */
        private ObjectSerializer csapWriter(Class<?> clazz) {
            ObjectSerializer serializer = super.getObjectWriter(clazz);
            if (!(serializer instanceof JavaBeanSerializer) && !(serializer instanceof MapSerializer)) {
                Class<?> type = serializer == null ? null : serializer.getClass();
                if (!FilterSession.isContainerType(clazz) || type == ListSerializer.class
                        || type == CollectionCodec.class || type == ObjectArrayCodec.class) {
                    return serializer;
                }
                // 其他容器序列化器(如带固定元素序列化器的数组、自定义序列化器)可能绕过本类直接用原序列化器
                return serializer == null ? null
                        : rules.plan(PlanCache.FASTJSON, new ConfigKey(getMapping(), clazz), k -> new LegacyWriter(serializer));
            }
            SerializeConfig config = getMapping();
            Object key = config == SerializeConfig.globalInstance ? clazz : new ConfigKey(config, clazz);
            return rules.plan(PlanCache.FASTJSON, key, k -> csapPlan(serializer, clazz));
        }

        private ObjectSerializer csapPlan(ObjectSerializer serializer, Class<?> clazz) {
            TypeRules r = rules;
            if (serializer instanceof MapSerializer) {
                if (serializer.getClass() != MapSerializer.class || !r.appliesTo(clazz)) {
                    return serializer.getClass() == MapSerializer.class ? serializer : new LegacyWriter(serializer);
                }
                MapSerializer map = new MapSerializer();
                map.getPropertyPreFilters().add((s, object, name) -> r.allows(object.getClass(), name));
                return map;
            }
            try {
                ObjectSerializer subset = Plan.copy((JavaBeanSerializer) serializer, f -> r.allows(clazz, f.name),
                        true);
                return subset != null ? subset : new LegacyWriter(serializer);
            } catch (Exception | LinkageError e) {
                return new LegacyWriter(serializer);
            }
        }

        /**
         * 按掩码写出一个值;参数与原来写出该值时相同
         */
        void writeValue(Object value, FieldMask mask, Object fieldName, Type fieldType, int features)
                throws IOException {
            if (mask == FieldMask.PASS || !FilterSession.isContainer(value)) {
                writeNative(value, fieldName, fieldType, features);
                return;
            }
            write(value, plan(value.getClass(), mask), mask, fieldName, fieldType, features);
        }

        /**
         * 按某个位置的子掩码写出一个值
         */
        void writeValue(Object value, Slot slot, Object fieldName, Type fieldType, int features)
                throws IOException {
            if (slot.mask == FieldMask.PASS || !FilterSession.isContainer(value)) {
                writeNative(value, fieldName, fieldType, features);
                return;
            }
            write(value, slot.plan(this, value.getClass()), slot.mask, fieldName, fieldType, features);
        }

        private void write(Object value, Plan plan, FieldMask mask, Object fieldName, Type fieldType, int features)
                throws IOException {
            if (plan.sealed) {
                // 字段都是基础类型 / String:写出期间不会再查序列化器,不必压栈
                plan.serializer.write(this, value, fieldName, fieldType, features);
                return;
            }
            if (plan.serializer == null) {
                writeLegacy(value, mask, fieldName, fieldType, features);
                return;
            }
            push(plan.frame);
            try {
                plan.serializer.write(this, value, fieldName, fieldType, features);
            } finally {
                pop();
            }
        }

        /**
         * 原样输出(PASS 子树):只用原序列化器
         */
        void writeNative(Object value, Object fieldName, Type fieldType, int features) throws IOException {
            push(NATIVE_FRAME);
            try {
                super.getObjectWriter(value.getClass()).write(this, value, fieldName, fieldType, features);
            } finally {
                pop();
            }
        }

        Plan plan(Class<?> type, FieldMask mask) {
            SerializeConfig config = getMapping();
            Object key = config == SerializeConfig.globalInstance ? type : new ConfigKey(config, type);
            return mask.plan(PlanCache.FASTJSON, key, k -> Plan.create(super.getObjectWriter(type), type, mask));
        }

        /**
         * 无法预编译的值:临时挂上原来的全局过滤器写出这一段(其中只用原序列化器)
         */
        private void writeLegacy(Object value, FieldMask mask, Object fieldName, Type fieldType, int features)
                throws IOException {
            FastjsonResponseFilterEngine.Filters filters = new FastjsonResponseFilterEngine.Filters(session, false);
            getPropertyPreFilters().add(filters);
            getValueFilters().add(filters);
            session.push(mask);
            push(NATIVE_FRAME);
            try {
                super.getObjectWriter(value.getClass()).write(this, value, fieldName, fieldType, features);
            } finally {
                pop();
                session.pop();
                getPropertyPreFilters().remove(filters);
                getValueFilters().remove(filters);
            }
        }
    }

    /**
     * 过滤范围内所有容器类型的序列化器:按当前位置算出子掩码再写出。
     * <ul>
     *   <li>Bean 的字段 / Map 的值:子掩码是当前掩码下该键的掩码,写法同原来的 Scoped
     *       ({@code serializer.write(value)},不带字段名);</li>
     *   <li>集合的元素:沿用当前掩码,参数原样传递(与集合序列化器调用元素序列化器相同)。</li>
     * </ul>
     */
    static final class Dispatcher implements ObjectSerializer {
        static final Dispatcher INSTANCE = new Dispatcher();

        @Override
        public void write(JSONSerializer serializer, Object object, Object fieldName, Type fieldType, int features)
                throws IOException {
            if (!(serializer instanceof Masked)) {
                // 防御:不应被缓存到过滤范围之外使用
                if (object == null) {
                    serializer.writeNull();
                } else {
                    serializer.getMapping().getObjectWriter(object.getClass())
                            .write(serializer, object, fieldName, fieldType, features);
                }
                return;
            }
            Masked masked = (Masked) serializer;
            if (object == null) {
                serializer.writeNull();
                return;
            }
            Frame top = masked.top();
            if (top.kind == Frame.SEQUENCE) {
                masked.writeValue(object, top.all, fieldName, fieldType, features);
                return;
            }
            if (top.kind == Frame.BEAN && fieldName instanceof String) {
                Slot slot = top.slot((String) fieldName);
                if (slot != null) {
                    masked.writeValue(object, slot, null, null, 0);
                    return;
                }
            } else if (top.all != null && fieldName != null && !(fieldName instanceof Map)
                    && !(fieldName instanceof Collection)) {
                // map 节点:所有键的值都套用 mapValues
                masked.writeValue(object, top.all, null, null, 0);
                return;
            }
            String key;
            if (fieldName instanceof String) {
                key = (String) fieldName;
            } else if (top.kind == Frame.MAP && fieldName != null && !(fieldName instanceof Map)
                    && !(fieldName instanceof Collection)) {
                key = JSON.toJSONString(fieldName);
            } else {
                // Map 的键是集合时原来不包装,原样输出(Bean 的容器字段都带字段名,带格式的字段不预编译)
                masked.writeNative(object, fieldName, fieldType, features);
                return;
            }
            FieldMask child = top.mask.lookup(key);
            masked.writeValue(object, child == FieldMask.DROP ? FieldMask.PASS : child, null, null, 0);
        }
    }

    /**
     * csap 模式下无法预编译的序列化器:写出期间挂上原来的按类型判断的 PropertyPreFilter
     */
    static final class LegacyWriter implements ObjectSerializer {
        private final ObjectSerializer target;

        LegacyWriter(ObjectSerializer target) {
            this.target = target;
        }

        @Override
        public void write(JSONSerializer serializer, Object object, Object fieldName, Type fieldType, int features)
                throws IOException {
            FastjsonResponseFilterEngine.Filters filters =
                    new FastjsonResponseFilterEngine.Filters(((Masked) serializer).session, false);
            serializer.getPropertyPreFilters().add(filters);
            try {
                target.write(serializer, object, fieldName, fieldType, features);
            } finally {
                serializer.getPropertyPreFilters().remove(filters);
            }
        }
    }

    private static final class ConfigKey {
        private final SerializeConfig config;
        private final Class<?> type;

        ConfigKey(SerializeConfig config, Class<?> type) {
            this.config = config;
            this.type = type;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof ConfigKey && ((ConfigKey) o).config == config && ((ConfigKey) o).type == type;
        }

        @Override
        public int hashCode() {
            return Objects.hash(System.identityHashCode(config), type);
        }
    }

    /**
     * 某个类型在某个掩码节点下的写法;serializer 为 null 表示无法预编译(临时挂回原来的过滤器)
     */
    static final class Plan {
        private static final Plan LEGACY = new Plan(NATIVE_FRAME, null);

        final Frame frame;
        final ObjectSerializer serializer;

        /**
         * 原序列化器写出、字段只有基础类型和 String
         */
        final boolean sealed;

        Plan(Frame frame, ObjectSerializer serializer) {
            this(frame, serializer, false);
        }

        Plan(Frame frame, ObjectSerializer serializer, boolean sealed) {
            this.frame = frame;
            this.serializer = serializer;
            this.sealed = sealed;
        }

        static Plan create(ObjectSerializer serializer, Class<?> type, FieldMask mask) {
            Class<?> serializerType = serializer == null ? null : serializer.getClass();
            if (serializerType == ListSerializer.class && List.class.isAssignableFrom(type)
                    || serializerType == CollectionCodec.class && Collection.class.isAssignableFrom(type)
                    || serializerType == ObjectArrayCodec.class && Object[].class.isAssignableFrom(type)) {
                return new Plan(new Frame(mask, Frame.SEQUENCE), serializer);
            }
            if (serializerType == MapSerializer.class) {
                MapSerializer map = (MapSerializer) serializer;
                if (mask.mapValues() == null) {
                    // 只输出放行的键
                    map = new MapSerializer();
                    map.getPropertyPreFilters().add((s, object, name) -> mask.allows(name));
                }
                return new Plan(new Frame(mask, Frame.MAP), map);
            }
            if (serializer instanceof JavaBeanSerializer) {
                try {
                    return bean((JavaBeanSerializer) serializer, mask);
                } catch (Exception | LinkageError e) {
                    return LEGACY;
                }
            }
            return LEGACY;
        }

        /**
         * 原序列化器的副本,只含 keep 放行的字段(原序列化器是 ASM 的,副本也用 ASM);不是 fastjson 自带的
         * Bean 序列化器时返回 null
         */
        static JavaBeanSerializer copy(JavaBeanSerializer serializer, java.util.function.Predicate<FieldInfo> keep,
                                       boolean asmAllowed) throws Exception {
            Class<?> serializerType = serializer.getClass();
            boolean asm = serializerType != JavaBeanSerializer.class;
            if (asm && !serializerType.getSimpleName().startsWith("ASMSerializer_")) {
                return null;
            }
            SerializeBeanInfo info = (SerializeBeanInfo) BEAN_INFO.get(serializer);
            FieldInfo[] fields = (FieldInfo[]) BI_FIELDS.get(info);
            FieldInfo[] sorted = (FieldInfo[]) BI_SORTED.get(info);
            List<FieldInfo> kept = new ArrayList<>(fields.length);
            for (FieldInfo f : fields) {
                if (keep.test(f)) {
                    kept.add(f);
                }
            }
            FieldInfo[] keptFields = kept.toArray(new FieldInfo[0]);
            FieldInfo[] keptSorted;
            if (sorted == fields) {
                keptSorted = keptFields;
            } else {
                List<FieldInfo> list = new ArrayList<>(sorted.length);
                for (FieldInfo f : sorted) {
                    if (keep.test(f)) {
                        list.add(f);
                    }
                }
                keptSorted = list.toArray(new FieldInfo[0]);
            }
            SerializeBeanInfo subset = new SerializeBeanInfo((Class<?>) BI_TYPE.get(info),
                    (com.alibaba.fastjson.annotation.JSONType) BI_JSON_TYPE.get(info), (String) BI_TYPE_NAME.get(info),
                    (String) BI_TYPE_KEY.get(info), BI_FEATURES.getInt(info), keptFields, keptSorted);
            if (asm && asmAllowed) {
                try {
                    return new ASMSerializerFactory().createJavaBeanSerializer(subset);
                } catch (Exception | LinkageError e) {
                    // 退回非 ASM
                }
            }
            return new JavaBeanSerializer(subset);
        }

        private static Plan bean(JavaBeanSerializer serializer, FieldMask mask) throws Exception {
            Class<?> serializerType = serializer.getClass();
            boolean asm = serializerType != JavaBeanSerializer.class;
            if (asm && !serializerType.getSimpleName().startsWith("ASMSerializer_")) {
                return LEGACY;
            }
            SerializeBeanInfo info = (SerializeBeanInfo) BEAN_INFO.get(serializer);
            FieldInfo[] fields = (FieldInfo[]) BI_FIELDS.get(info);
            FieldInfo[] sorted = (FieldInfo[]) BI_SORTED.get(info);
            List<FieldInfo> kept = new ArrayList<>(fields.length);
            boolean flat = true;
            boolean sealed = true;
            boolean inlineList = false;
            for (FieldInfo f : fields) {
                if (mask.lookup(f.name) == FieldMask.DROP) {
                    continue;
                }
                if (List.class.isAssignableFrom(f.fieldClass)) {
                    inlineList = true;
                }
                if (!isSealed(f.fieldClass)) {
                    sealed = false;
                }
                if (!FilterSession.isLeafType(f.fieldClass)) {
                    // 带格式或 unwrapped 的容器字段写出时不带字段名,无法按字段算子掩码
                    if (f.getFormat() != null || f.unwrapped) {
                        return LEGACY;
                    }
                    flat = false;
                }
                kept.add(f);
            }
            List<String> names = new ArrayList<>();
            List<Slot> slots = new ArrayList<>();
            for (FieldInfo f : kept) {
                if (!FilterSession.isLeafType(f.fieldClass)) {
                    names.add(f.name);
                    slots.add(new Slot(mask.lookup(f.name)));
                }
            }
            Frame frame = new Frame(mask, Frame.BEAN, names.toArray(new String[0]), slots.toArray(new Slot[0]));
            // 原序列化器是共享的,只有不含容器字段的 ASM 序列化器才能直接使用:它只按字段的声明类型缓存序列化器,
            // 不会缓存 Dispatcher(非 ASM 的会按值的运行时类型缓存)
            if (flat && asm && kept.size() == fields.length) {
                return new Plan(frame, serializer, sealed);
            }
            FieldInfo[] keptFields = kept.toArray(new FieldInfo[0]);
            FieldInfo[] keptSorted;
            if (sorted == fields) {
                keptSorted = keptFields;
            } else {
                List<FieldInfo> list = new ArrayList<>(sorted.length);
                for (FieldInfo f : sorted) {
                    if (mask.lookup(f.name) != FieldMask.DROP) {
                        list.add(f);
                    }
                }
                keptSorted = list.toArray(new FieldInfo[0]);
            }
            SerializeBeanInfo subset = new SerializeBeanInfo((Class<?>) BI_TYPE.get(info),
                    (com.alibaba.fastjson.annotation.JSONType) BI_JSON_TYPE.get(info), (String) BI_TYPE_NAME.get(info),
                    (String) BI_TYPE_KEY.get(info), BI_FEATURES.getInt(info), keptFields, keptSorted);
            JavaBeanSerializer created = null;
            // ASM 序列化器自己逐个写出 List 字段的元素(不经过集合的序列化器),原来是整个 List 交给
            // ListSerializer 按子掩码写出:含 List 字段时用非 ASM 的 JavaBeanSerializer
            if (asm && !inlineList) {
                try {
                    created = new ASMSerializerFactory().createJavaBeanSerializer(subset);
                } catch (Exception | LinkageError e) {
                    created = null;
                }
            }
            if (created == null) {
                created = new JavaBeanSerializer(subset);
                sealed = false;
            } else {
                seed(created, keptFields, frame);
            }
            return new Plan(frame, created, sealed);
        }

        /**
         * 字段的值只能是基础类型或 String(这些类型不能有子类)
         */
        private static boolean isSealed(Class<?> type) {
            return type.isPrimitive() || type == String.class || type == Long.class || type == Integer.class
                    || type == Boolean.class || type == Double.class || type == Float.class || type == Short.class
                    || type == Byte.class || type == Character.class;
        }

        /**
         * ASM 序列化器在字段值正好是声明类型时使用字段上缓存的序列化器({@code <字段名>_asm_ser_}):
         * 预置为该字段的 {@link SlotWriter},省去按字段名查找子掩码
         */
        private static void seed(JavaBeanSerializer created, FieldInfo[] fields, Frame frame) {
            for (FieldInfo f : fields) {
                Slot slot = frame.slot(f.name);
                if (slot == null) {
                    continue;
                }
                try {
                    Field cache = created.getClass().getField(f.name + "_asm_ser_");
                    if (cache.getType() == ObjectSerializer.class) {
                        cache.set(created, new SlotWriter(slot, f.fieldClass));
                    }
                } catch (ReflectiveOperationException | RuntimeException e) {
                    // 没有该字段缓存:写出时由 Dispatcher 处理
                }
            }
        }
    }

    /**
     * 某个 Bean 字段的序列化器:与 {@link Dispatcher} 处理 Bean 字段相同
     */
    static final class SlotWriter implements ObjectSerializer {
        private final Slot slot;
        private final Class<?> type;

        /**
         * 字段声明类型的计划是"原序列化器、只有基础类型字段"时,直接用该序列化器
         */
        private volatile ObjectSerializer sealed;

        SlotWriter(Slot slot, Class<?> type) {
            this.slot = slot;
            this.type = type;
        }

        @Override
        public void write(JSONSerializer serializer, Object object, Object fieldName, Type fieldType, int features)
                throws IOException {
            if (object == null) {
                serializer.writeNull();
                return;
            }
            ObjectSerializer direct = sealed;
            if (direct != null && object.getClass() == type) {
                direct.write(serializer, object, null, null, 0);
                return;
            }
            Masked masked = (Masked) serializer;
            if (slot.mask != FieldMask.PASS && object.getClass() == type && FilterSession.isContainer(object)) {
                Plan plan = slot.plan(masked, type);
                if (plan.sealed) {
                    sealed = plan.serializer;
                }
            }
            masked.writeValue(object, slot, null, null, 0);
        }
    }
}
