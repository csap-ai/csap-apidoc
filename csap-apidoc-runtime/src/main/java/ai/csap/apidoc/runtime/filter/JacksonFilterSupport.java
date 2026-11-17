package ai.csap.apidoc.runtime.filter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.AnnotationIntrospector;
import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.BeanProperty;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.SerializationConfig;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.introspect.Annotated;
import com.fasterxml.jackson.databind.introspect.AnnotatedClass;
import com.fasterxml.jackson.databind.introspect.AnnotationIntrospectorPair;
import com.fasterxml.jackson.databind.introspect.NopAnnotationIntrospector;
import com.fasterxml.jackson.databind.jsonFormatVisitors.JsonFormatVisitorWrapper;
import com.fasterxml.jackson.databind.jsontype.TypeSerializer;
import com.fasterxml.jackson.databind.ser.BeanPropertyFilter;
import com.fasterxml.jackson.databind.ser.BeanPropertyWriter;
import com.fasterxml.jackson.databind.ser.BeanSerializer;
import com.fasterxml.jackson.databind.ser.BeanSerializerModifier;
import com.fasterxml.jackson.databind.ser.ContextualSerializer;
import com.fasterxml.jackson.databind.ser.DefaultSerializerProvider;
import com.fasterxml.jackson.databind.ser.FilterProvider;
import com.fasterxml.jackson.databind.ser.PropertyFilter;
import com.fasterxml.jackson.databind.ser.PropertyWriter;
import com.fasterxml.jackson.databind.ser.ResolvableSerializer;
import com.fasterxml.jackson.databind.ser.impl.SimpleBeanPropertyFilter;
import com.fasterxml.jackson.databind.ser.std.BeanSerializerBase;
import com.fasterxml.jackson.databind.ser.std.MapProperty;
import com.fasterxml.jackson.databind.util.NameTransformer;

/**
 * Jackson 过滤的基础设施。
 *
 * <ul>
 *   <li>{@link MaskingBeanSerializer}:包住每个 Bean 序列化器,按当前掩码(path)或类型规则(csap)选用预编译的
 *       {@link SubsetBeanSerializer}——只含放行属性,放行属性写出期间压入子掩码;按 (序列化器, 掩码节点) 缓存在掩码上;</li>
 *   <li>{@link Introspector}:没有自定义 {@code @JsonFilter} 的 Map 使用过滤器 {@link #FILTER_ID}(Map 按键过滤);</li>
 *   <li>{@link Provider}:{@link #FILTER_ID} 返回本次请求的过滤器,其他过滤器 id 交给原有的 FilterProvider;</li>
 *   <li>{@link SessionFilter}:每个请求新建,按 {@link FilterSession} 决定 Map 的键(以及无法预编译的 Bean,
 *       如带 {@code @JsonAnyGetter}、数组形状、{@code @JsonUnwrapped})是否输出;</li>
 *   <li>{@link FilteredValue}:消息转换器写出它时,改用已安装过滤支持的 ObjectWriter 写入同一个输出流。</li>
 * </ul>
 * 只安装在消息转换器 ObjectMapper 的副本上,该副本只用于有返回规则的接口;
 * 应用里共享的 ObjectMapper 和没有规则的接口都不受影响。副本在启动时配置一次,之后只读,线程安全。
 *
 * @author ycf
 * @since 1.2.0
 */
public final class JacksonFilterSupport {

    /**
     * 过滤器 id
     */
    public static final String FILTER_ID = "csapApidocResponseFilter";

    private static final PropertyFilter SERIALIZE_ALL = SimpleBeanPropertyFilter.serializeAll();

    private JacksonFilterSupport() {
    }

    /**
     * 是否已安装
     */
    public static boolean isInstalled(ObjectMapper mapper) {
        return mapper.getSerializationConfig().getAnnotationIntrospector() instanceof Introspector;
    }

    /**
     * 为 ObjectMapper 安装过滤支持并清空已缓存的序列化器。重复调用无副作用。
     * 安装后该 ObjectMapper 序列化任何对象都会经过过滤器,只应安装在专用的副本上。
     *
     * @param mapper ObjectMapper
     */
    public static void install(ObjectMapper mapper) {
        if (isInstalled(mapper)) {
            return;
        }
        AnnotationIntrospector serializer = mapper.getSerializationConfig().getAnnotationIntrospector();
        AnnotationIntrospector deserializer = mapper.getDeserializationConfig().getAnnotationIntrospector();
        mapper.setAnnotationIntrospectors(new Introspector(serializer), deserializer);
        FilterProvider original = mapper.getSerializationConfig().getFilterProvider();
        mapper.setFilterProvider(new Provider(null, original, null));
        mapper.setSerializerFactory(mapper.getSerializerFactory().withSerializerModifier(new MaskingModifier()));
        if (mapper.getSerializerProvider() instanceof DefaultSerializerProvider) {
            ((DefaultSerializerProvider) mapper.getSerializerProvider()).flushCachedSerializers();
        }
    }

    /**
     * 当前线程正在写出的过滤状态(只在 {@link FilteredValueSerializer} 写出期间有值)
     */
    private static final ThreadLocal<FilterSession> CURRENT = new ThreadLocal<>();

    /**
     * 本次序列化的过滤状态
     */
    static FilterSession session() {
        return CURRENT.get();
    }

    /**
     * 本次请求使用的 FilterProvider
     *
     * @param mapper   已安装过滤支持的 ObjectMapper
     * @param session  过滤状态
     * @param existing 响应体上已有的 FilterProvider(如用户返回的 MappingJacksonValue),可为 null
     */
    public static FilterProvider requestProvider(ObjectMapper mapper, FilterSession session, FilterProvider existing) {
        FilterProvider installed = mapper.getSerializationConfig().getFilterProvider();
        FilterProvider original = installed instanceof Provider ? ((Provider) installed).original : installed;
        return new Provider(new SessionFilter(session), existing instanceof Provider ? null : existing, original);
    }

    /**
     * 没有自定义过滤器 id 的 Map 统一使用 {@link #FILTER_ID}
     */
    public static final class Introspector extends AnnotationIntrospectorPair {
        private static final long serialVersionUID = 1L;

        Introspector(AnnotationIntrospector delegate) {
            super(delegate == null ? NopAnnotationIntrospector.instance : delegate,
                    NopAnnotationIntrospector.instance);
        }

        @Override
        public Object findFilterId(Annotated a) {
            Object id = super.findFilterId(a);
            if (id == null && a instanceof AnnotatedClass
                    && Map.class.isAssignableFrom(((AnnotatedClass) a).getRawType())) {
                return FILTER_ID;
            }
            return id;
        }
    }

    /**
     * {@link #FILTER_ID} 返回本次请求的过滤器(没有时全部输出),其他 id 依次交给响应体上的、ObjectMapper 原有的 FilterProvider
     */
    public static final class Provider extends FilterProvider {
        private final SessionFilter filter;
        private final FilterProvider original;
        private final List<FilterProvider> delegates = new ArrayList<>(2);

        Provider(SessionFilter filter, FilterProvider first, FilterProvider second) {
            this.filter = filter;
            this.original = second == null ? first : second;
            if (first != null) {
                delegates.add(first);
            }
            if (second != null && second != first) {
                delegates.add(second);
            }
        }

        @Override
        @Deprecated
        public BeanPropertyFilter findFilter(Object filterId) {
            throw new UnsupportedOperationException("Access to deprecated filters not supported");
        }

        @Override
        public PropertyFilter findPropertyFilter(Object filterId, Object valueToFilter) {
            if (FILTER_ID.equals(filterId)) {
                if (filter == null) {
                    return SERIALIZE_ALL;
                }
                // 不需要过滤时返回 null:Jackson 改走不过滤的序列化(输出相同)
                return filter.isNoop(valueToFilter) ? null : filter;
            }
            for (FilterProvider delegate : delegates) {
                try {
                    PropertyFilter found = delegate.findPropertyFilter(filterId, valueToFilter);
                    if (found != null) {
                        return found;
                    }
                } catch (IllegalArgumentException ignore) {
                    // SimpleFilterProvider 找不到 id 时抛异常,继续找下一个
                }
            }
            return null;
        }
    }

    /**
     * 每个请求新建的过滤器
     */
    static final class SessionFilter extends SimpleBeanPropertyFilter {
        private final FilterSession session;
        private final boolean pathMode;

        SessionFilter(FilterSession session) {
            this.session = session;
            this.pathMode = session.isPathMode();
        }

        /**
         * 当前对象的字段全部原样输出:path 模式掩码为 PASS,或 csap 模式没有适用于该类型的规则
         */
        boolean isNoop(Object value) {
            if (pathMode) {
                return session.current() == FieldMask.PASS;
            }
            return value != null && !session.getEndpoint().getTypeRules().appliesTo(value.getClass());
        }

        @Override
        public void serializeAsField(Object pojo, JsonGenerator gen, SerializerProvider provider,
                                     PropertyWriter writer) throws Exception {
            if (!pathMode) {
                if (session.typeAllows(pojo, writer.getName())) {
                    writer.serializeAsField(pojo, gen, provider);
                } else if (!gen.canOmitFields()) {
                    writer.serializeAsOmittedField(pojo, gen, provider);
                }
                return;
            }
            FieldMask current = session.current();
            if (current == FieldMask.PASS) {
                writer.serializeAsField(pojo, gen, provider);
                return;
            }
            FieldMask next;
            if (current == FieldMask.OUTSIDE) {
                next = session.enter(writer.getName(), valueOf(pojo, writer));
                if (next == null) {
                    writer.serializeAsField(pojo, gen, provider);
                    return;
                }
            } else {
                next = current.lookup(writer.getName());
                if (next == FieldMask.DROP) {
                    if (!gen.canOmitFields()) {
                        writer.serializeAsOmittedField(pojo, gen, provider);
                    }
                    return;
                }
            }
            session.push(next);
            try {
                writer.serializeAsField(pojo, gen, provider);
            } finally {
                session.pop();
            }
        }

        private static Object valueOf(Object pojo, PropertyWriter writer) {
            try {
                if (writer instanceof BeanPropertyWriter) {
                    return ((BeanPropertyWriter) writer).get(pojo);
                }
                if (writer instanceof MapProperty) {
                    return ((MapProperty) writer).getValue();
                }
            } catch (Exception ignore) {
                // 取不到值时按外层字段处理
            }
            return null;
        }
    }

    /**
     * 包住所有 Bean 序列化器
     */
    static final class MaskingModifier extends BeanSerializerModifier {
        private static final long serialVersionUID = 1L;

        @Override
        public JsonSerializer<?> modifySerializer(SerializationConfig config, BeanDescription beanDesc,
                                                  JsonSerializer<?> serializer) {
            return MaskingBeanSerializer.wrap(serializer);
        }
    }

    /**
     * Bean 序列化器的包装:按本次请求的掩码选用预编译的序列化器。
     * <ul>
     *   <li>没有过滤状态(未经过滤入口)或掩码为 PASS:原序列化器;</li>
     *   <li>类上有自定义 {@code @JsonFilter}:原序列化器(与之前一致,只应用自定义过滤器);</li>
     *   <li>普通 Bean:{@link SubsetBeanSerializer},按掩码节点 / 类型规则缓存;</li>
     *   <li>其他(数组形状、{@code @JsonUnwrapped}、{@code @JsonAnyGetter}、非标准 Bean 序列化器):
     *       原序列化器加上 {@link #FILTER_ID},由 {@link SessionFilter} 逐属性判断。</li>
     * </ul>
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    static final class MaskingBeanSerializer extends JsonSerializer<Object>
            implements ContextualSerializer, ResolvableSerializer {

        private final BeanSerializerBase delegate;

        /**
         * 原序列化器的特征,首次使用时探测
         */
        private volatile Probe probe;

        private volatile JsonSerializer<Object> legacy;

        MaskingBeanSerializer(BeanSerializerBase delegate) {
            this.delegate = delegate;
        }

        static JsonSerializer<?> wrap(JsonSerializer<?> serializer) {
            return serializer instanceof BeanSerializerBase
                    ? new MaskingBeanSerializer((BeanSerializerBase) serializer) : serializer;
        }

        private JsonSerializer<?> rewrap(JsonSerializer<?> serializer) {
            return serializer == delegate ? this : wrap(serializer);
        }

        private Probe probe() {
            Probe p = probe;
            if (p == null) {
                p = new Probe(delegate);
                probe = p;
            }
            return p;
        }

        private JsonSerializer<Object> legacy() {
            JsonSerializer<Object> l = legacy;
            if (l == null) {
                l = (JsonSerializer<Object>) delegate.withFilterId(FILTER_ID);
                legacy = l;
            }
            return l;
        }

        private JsonSerializer<Object> select(FilterSession session, Object value) {
            boolean pathMode = session.isPathMode();
            if (pathMode && session.current() == FieldMask.PASS) {
                return (JsonSerializer<Object>) (JsonSerializer) delegate;
            }
            Probe p = probe();
            if (p.customFilter) {
                return (JsonSerializer<Object>) (JsonSerializer) delegate;
            }
            if (!pathMode) {
                if (!p.plain || value.getClass() != delegate.handledType()) {
                    return legacy();
                }
                TypeRules rules = session.getEndpoint().getTypeRules();
                Class<?> type = value.getClass();
                return rules.plan(this, k -> subset(SubsetBeanSerializer.CSAP, w -> rules.allows(type, w.getName()) ? w : null,
                        null));
            }
            FieldMask mask = session.current();
            if (!p.plain) {
                return legacy();
            }
            if (mask == FieldMask.OUTSIDE) {
                return mask.plan(this, k -> subset(SubsetBeanSerializer.OUTSIDE, w -> w, null));
            }
            return mask.plan(this, k -> subset(SubsetBeanSerializer.PATH,
                    w -> mask.lookup(w.getName()) == FieldMask.DROP ? null : w, w -> mask.lookup(w.getName())));
        }

        private JsonSerializer<Object> subset(int mode, Function<BeanPropertyWriter, BeanPropertyWriter> keep,
                                              Function<BeanPropertyWriter, FieldMask> child) {
            try {
                return new SubsetBeanSerializer(delegate, mode, keep, child);
            } catch (RuntimeException | LinkageError e) {
                return legacy();
            }
        }

        @Override
        public void serialize(Object value, JsonGenerator gen, SerializerProvider provider) throws IOException {
            FilterSession session = CURRENT.get();
            if (session == null) {
                delegate.serialize(value, gen, provider);
                return;
            }
            JsonSerializer<Object> target = select(session, value);
            if (target instanceof SubsetBeanSerializer) {
                SubsetBeanSerializer subset = (SubsetBeanSerializer) target;
                if (subset.full) {
                    session.push(FieldMask.PASS);
                    try {
                        delegate.serialize(value, gen, provider);
                    } finally {
                        session.pop();
                    }
                } else {
                    subset.serialize(value, gen, provider, session);
                }
            } else {
                target.serialize(value, gen, provider);
            }
        }

        @Override
        public void serializeWithType(Object value, JsonGenerator gen, SerializerProvider provider,
                                      TypeSerializer typeSer) throws IOException {
            FilterSession session = CURRENT.get();
            if (session == null) {
                delegate.serializeWithType(value, gen, provider, typeSer);
            } else {
                select(session, value).serializeWithType(value, gen, provider, typeSer);
            }
        }

        @Override
        public JsonSerializer<?> createContextual(SerializerProvider prov, BeanProperty property)
                throws JsonMappingException {
            return rewrap(delegate.createContextual(prov, property));
        }

        @Override
        public void resolve(SerializerProvider provider) throws JsonMappingException {
            delegate.resolve(provider);
        }

        @Override
        public JsonSerializer<Object> unwrappingSerializer(NameTransformer unwrapper) {
            return (JsonSerializer<Object>) rewrap(delegate.unwrappingSerializer(unwrapper));
        }

        @Override
        public JsonSerializer<?> withFilterId(Object filterId) {
            return rewrap(delegate.withFilterId(filterId));
        }

        @Override
        public boolean isUnwrappingSerializer() {
            return delegate.isUnwrappingSerializer();
        }

        @Override
        public boolean usesObjectId() {
            return delegate.usesObjectId();
        }

        @Override
        public Class<Object> handledType() {
            return (Class<Object>) delegate.handledType();
        }

        @Override
        public boolean isEmpty(SerializerProvider provider, Object value) {
            return delegate.isEmpty(provider, value);
        }

        @Override
        public Iterator<PropertyWriter> properties() {
            return delegate.properties();
        }

        @Override
        public JsonSerializer<?> getDelegatee() {
            return delegate;
        }

        @Override
        public void acceptJsonFormatVisitor(JsonFormatVisitorWrapper visitor, JavaType type)
                throws JsonMappingException {
            delegate.acceptJsonFormatVisitor(visitor, type);
        }
    }

    /**
     * 读取原 Bean 序列化器的受保护状态
     */
    static final class Probe extends BeanSerializer {
        private static final long serialVersionUID = 1L;

        /**
         * 类上的自定义过滤器
         */
        final boolean customFilter;

        /**
         * 可以预编译(标准 BeanSerializer,没有 any-getter)
         */
        final boolean plain;

        Probe(BeanSerializerBase src) {
            super(src);
            this.customFilter = _propertyFilterId != null;
            this.plain = src.getClass() == BeanSerializer.class && _anyGetterWriter == null && _propertyFilterId == null;
        }
    }

    /**
     * 只含放行属性的 Bean 序列化器(由原序列化器复制,保留 view、类型信息、对象 id 等设置)。
     * path 模式下每个属性写出期间压入它的子掩码;外层包装(OUTSIDE)只在属性值就是被过滤的返回值时压入根掩码。
     */
    static final class SubsetBeanSerializer extends BeanSerializer {
        private static final long serialVersionUID = 1L;

        static final int PATH = 0;

        static final int CSAP = 1;

        static final int OUTSIDE = 2;

        private final int mode;

        private final boolean objectId;

        private final BeanPropertyWriter[] props;

        private final transient FieldMask[] children;

        private final BeanPropertyWriter[] viewProps;

        private final transient FieldMask[] viewChildren;

        /**
         * path 模式下全部属性放行且子掩码都是 PASS:等价于压入 PASS 后用原序列化器写出
         */
        final boolean full;

        SubsetBeanSerializer(BeanSerializerBase src, int mode, Function<BeanPropertyWriter, BeanPropertyWriter> keep,
                             Function<BeanPropertyWriter, FieldMask> child) {
            super(src);
            this.mode = mode;
            this.objectId = _objectIdWriter != null;
            List<BeanPropertyWriter> list = new ArrayList<>(_props.length);
            List<FieldMask> masks = new ArrayList<>(_props.length);
            select(_props, keep, child, list, masks);
            this.props = list.toArray(new BeanPropertyWriter[0]);
            this.children = child == null ? null : masks.toArray(new FieldMask[0]);
            if (_filteredProps == null) {
                this.viewProps = null;
                this.viewChildren = null;
            } else {
                list.clear();
                masks.clear();
                select(_filteredProps, keep, child, list, masks);
                this.viewProps = list.toArray(new BeanPropertyWriter[0]);
                this.viewChildren = child == null ? null : masks.toArray(new FieldMask[0]);
            }
            this.full = mode == PATH && allPass(props, children, _props)
                    && (viewProps == null || allPass(viewProps, viewChildren, _filteredProps));
        }

        private static boolean allPass(BeanPropertyWriter[] kept, FieldMask[] masks, BeanPropertyWriter[] all) {
            int count = 0;
            for (BeanPropertyWriter w : all) {
                if (w != null) {
                    count++;
                }
            }
            if (kept.length != count) {
                return false;
            }
            for (FieldMask m : masks) {
                if (m != FieldMask.PASS) {
                    return false;
                }
            }
            return true;
        }

        private static void select(BeanPropertyWriter[] all, Function<BeanPropertyWriter, BeanPropertyWriter> keep,
                                   Function<BeanPropertyWriter, FieldMask> child, List<BeanPropertyWriter> out,
                                   List<FieldMask> masks) {
            for (BeanPropertyWriter w : all) {
                BeanPropertyWriter kept = w == null ? null : keep.apply(w);
                if (kept != null) {
                    out.add(kept);
                    if (child != null) {
                        masks.add(child.apply(w));
                    }
                }
            }
        }

        /**
         * 与 {@link BeanSerializer#serialize} 相同,只是直接带上过滤状态
         */
        void serialize(Object bean, JsonGenerator gen, SerializerProvider provider, FilterSession session)
                throws IOException {
            if (objectId) {
                serialize(bean, gen, provider);
                return;
            }
            gen.writeStartObject(bean);
            serializeFields(bean, gen, provider, session);
            gen.writeEndObject();
        }

        @Override
        protected void serializeFields(Object bean, JsonGenerator gen, SerializerProvider provider)
                throws IOException {
            serializeFields(bean, gen, provider, CURRENT.get());
        }

        @Override
        protected void serializeFieldsFiltered(Object bean, JsonGenerator gen, SerializerProvider provider)
                throws IOException {
            serializeFields(bean, gen, provider, CURRENT.get());
        }

        private void serializeFields(Object bean, JsonGenerator gen, SerializerProvider provider,
                                     FilterSession session) throws IOException {
            final boolean view = viewProps != null && provider.getActiveView() != null;
            final BeanPropertyWriter[] use = view ? viewProps : props;
            int i = 0;
            try {
                final int len = use.length;
                if (mode == CSAP) {
                    for (; i < len; ++i) {
                        use[i].serializeAsField(bean, gen, provider);
                    }
                } else if (mode == PATH) {
                    final FieldMask[] masks = view ? viewChildren : children;
                    for (; i < len; ++i) {
                        session.push(masks[i]);
                        try {
                            use[i].serializeAsField(bean, gen, provider);
                        } finally {
                            session.pop();
                        }
                    }
                } else {
                    for (; i < len; ++i) {
                        serializeOutside(use[i], bean, gen, provider, session);
                    }
                }
            } catch (Exception e) {
                wrapAndThrow(provider, e, bean, i == use.length ? "[anySetter]" : use[i].getName());
            } catch (StackOverflowError e) {
                JsonMappingException mapE = new JsonMappingException(gen, "Infinite recursion (StackOverflowError)", e);
                mapE.prependPath(new JsonMappingException.Reference(bean,
                        i == use.length ? "[anySetter]" : use[i].getName()));
                throw mapE;
            }
        }

        /**
         * 外层包装的属性:值就是被过滤的返回值时压入根掩码
         */
        private static void serializeOutside(BeanPropertyWriter w, Object bean, JsonGenerator gen,
                                             SerializerProvider provider, FilterSession session) throws Exception {
            Object value;
            try {
                value = w.get(bean);
            } catch (Exception ignore) {
                value = null;
            }
            FieldMask next = session.enter(w.getName(), value);
            if (next == null) {
                w.serializeAsField(bean, gen, provider);
                return;
            }
            session.push(next);
            try {
                w.serializeAsField(bean, gen, provider);
            } finally {
                session.pop();
            }
        }
    }

    /**
     * 待过滤输出的值:消息转换器写出它时,改用已安装过滤支持的 ObjectWriter 写入同一个 JsonGenerator
     */
    @JsonSerialize(using = FilteredValueSerializer.class)
    public static final class FilteredValue {
        private final Object value;
        private final FilterSession session;
        private final ObjectWriter writer;

        FilteredValue(Object value, FilterSession session, ObjectWriter writer) {
            this.value = value;
            this.session = session;
            this.writer = writer;
        }

        public Object getValue() {
            return value;
        }
    }

    /**
     * {@link FilteredValue} 的序列化
     */
    public static final class FilteredValueSerializer extends JsonSerializer<FilteredValue> {
        @Override
        public void serialize(FilteredValue filtered, JsonGenerator gen, SerializerProvider serializers)
                throws IOException {
            ObjectWriter writer = filtered.writer;
            // 外层(如 JsonViewResponseBodyAdvice 在本过滤之后包装的 MappingJacksonValue)指定了 view 时沿用
            Class<?> view = serializers.getActiveView();
            if (view != null && writer.getConfig().getActiveView() == null) {
                writer = writer.withView(view);
            }
            FilterSession previous = CURRENT.get();
            CURRENT.set(filtered.session);
            filtered.session.begin(filtered.value);
            try {
                writer.writeValue(gen, filtered.value);
            } finally {
                filtered.session.pop();
                CURRENT.set(previous);
            }
        }
    }
}
