package ai.csap.apidoc.runtime.filter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.AnnotationIntrospector;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.introspect.Annotated;
import com.fasterxml.jackson.databind.introspect.AnnotatedClass;
import com.fasterxml.jackson.databind.introspect.AnnotationIntrospectorPair;
import com.fasterxml.jackson.databind.introspect.NopAnnotationIntrospector;
import com.fasterxml.jackson.databind.ser.BeanPropertyFilter;
import com.fasterxml.jackson.databind.ser.BeanPropertyWriter;
import com.fasterxml.jackson.databind.ser.DefaultSerializerProvider;
import com.fasterxml.jackson.databind.ser.FilterProvider;
import com.fasterxml.jackson.databind.ser.PropertyFilter;
import com.fasterxml.jackson.databind.ser.PropertyWriter;
import com.fasterxml.jackson.databind.ser.impl.SimpleBeanPropertyFilter;
import com.fasterxml.jackson.databind.ser.std.MapProperty;

/**
 * Jackson 过滤的基础设施。
 *
 * <ul>
 *   <li>{@link Introspector}:没有自定义 {@code @JsonFilter} 的类(含 Map)统一使用过滤器 {@link #FILTER_ID};</li>
 *   <li>{@link Provider}:{@link #FILTER_ID} 返回本次请求的过滤器,其他过滤器 id 交给原有的 FilterProvider;</li>
 *   <li>{@link SessionFilter}:每个请求新建,按 {@link FilterSession} 决定字段是否输出;</li>
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
        if (mapper.getSerializerProvider() instanceof DefaultSerializerProvider) {
            ((DefaultSerializerProvider) mapper.getSerializerProvider()).flushCachedSerializers();
        }
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
     * 没有自定义过滤器 id 的类统一使用 {@link #FILTER_ID}
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
            if (id == null && a instanceof AnnotatedClass) {
                return FILTER_ID;
            }
            return id;
        }
    }

    /**
     * {@link #FILTER_ID} 返回本次请求的过滤器(没有时全部输出),其他 id 依次交给响应体上的、ObjectMapper 原有的 FilterProvider
     */
    public static final class Provider extends FilterProvider {
        private final PropertyFilter filter;
        private final FilterProvider original;
        private final List<FilterProvider> delegates = new ArrayList<>(2);

        Provider(PropertyFilter filter, FilterProvider first, FilterProvider second) {
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
                return filter != null ? filter : SERIALIZE_ALL;
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
            filtered.session.begin(filtered.value);
            try {
                filtered.writer.writeValue(gen, filtered.value);
            } finally {
                filtered.session.pop();
            }
        }
    }
}
