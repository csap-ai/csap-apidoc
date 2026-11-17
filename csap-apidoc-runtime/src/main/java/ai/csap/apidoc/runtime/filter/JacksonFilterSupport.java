package ai.csap.apidoc.runtime.filter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.AnnotationIntrospector;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
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
 *   <li>{@link Provider}:默认对 {@link #FILTER_ID} 返回"全部输出",有规则的请求换成本次请求的过滤器,
 *       其他过滤器 id 交给原有的 FilterProvider;</li>
 *   <li>{@link SessionFilter}:每个请求新建,按 {@link FilterSession} 决定字段是否输出。</li>
 * </ul>
 * 共享的 ObjectMapper 只在启动时配置一次,之后不再修改,线程安全。
 *
 * @author ycf
 * @since 1.2.0
 */
public final class JacksonFilterSupport {

    /**
     * 过滤器 id
     */
    public static final String FILTER_ID = "csapApidocResponseFilter";

    private static final Logger log = LoggerFactory.getLogger(JacksonFilterSupport.class);

    private static final PropertyFilter SERIALIZE_ALL = SimpleBeanPropertyFilter.serializeAll();

    /**
     * 安装时 ObjectMapper 上原有的 FilterProvider(处理用户自己的 @JsonFilter)
     */
    private static final List<FilterProvider> ORIGINAL_PROVIDERS = new CopyOnWriteArrayList<>();

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
        if (original != null && !(original instanceof Provider)) {
            ORIGINAL_PROVIDERS.add(original);
        }
        mapper.setFilterProvider(new Provider(null, original == null ? Collections.emptyList()
                : Collections.singletonList(original)));
        if (mapper.getSerializerProvider() instanceof DefaultSerializerProvider) {
            ((DefaultSerializerProvider) mapper.getSerializerProvider()).flushCachedSerializers();
        }
    }

    /**
     * 给某个 ObjectMapper 安装过滤支持,优先在副本上安装,不影响应用里其他地方使用的同一个 ObjectMapper。
     *
     * @param mapper 原 ObjectMapper
     * @return 已安装过滤支持的 ObjectMapper(副本;无法复制时为原对象)
     */
    public static ObjectMapper installOnCopy(ObjectMapper mapper) {
        if (isInstalled(mapper)) {
            return mapper;
        }
        ObjectMapper target;
        try {
            target = mapper.copy();
        } catch (RuntimeException e) {
            log.info("ObjectMapper {} 不支持 copy(),在原对象上安装返回字段过滤", mapper.getClass().getName());
            target = mapper;
        }
        install(target);
        return target;
    }

    /**
     * 本次请求使用的 FilterProvider
     *
     * @param session  过滤状态
     * @param existing 响应体上已有的 FilterProvider(如用户返回的 MappingJacksonValue),可为 null
     */
    public static FilterProvider requestProvider(FilterSession session, FilterProvider existing) {
        List<FilterProvider> delegates = new ArrayList<>(ORIGINAL_PROVIDERS.size() + 1);
        if (existing != null && !(existing instanceof Provider)) {
            delegates.add(existing);
        }
        delegates.addAll(ORIGINAL_PROVIDERS);
        return new Provider(new SessionFilter(session), delegates);
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
     * {@link #FILTER_ID} 返回本次请求的过滤器(没有时全部输出),其他 id 交给原有的 FilterProvider
     */
    public static final class Provider extends FilterProvider {
        private final PropertyFilter filter;
        private final List<FilterProvider> delegates;

        Provider(PropertyFilter filter, List<FilterProvider> delegates) {
            this.filter = filter;
            this.delegates = delegates;
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

        SessionFilter(FilterSession session) {
            this.session = session;
        }

        @Override
        public void serializeAsField(Object pojo, JsonGenerator gen, SerializerProvider provider,
                                     PropertyWriter writer) throws Exception {
            String name = writer.getName();
            if (!session.isPathMode()) {
                if (session.typeAllows(pojo, name)) {
                    writer.serializeAsField(pojo, gen, provider);
                } else if (!gen.canOmitFields()) {
                    writer.serializeAsOmittedField(pojo, gen, provider);
                }
                return;
            }
            FieldMask current = session.current();
            FieldMask next;
            if (current == FieldMask.OUTSIDE) {
                next = session.enter(name, valueOf(pojo, writer));
            } else {
                next = session.enter(name, null);
                if (next == FieldMask.DROP) {
                    if (!gen.canOmitFields()) {
                        writer.serializeAsOmittedField(pojo, gen, provider);
                    }
                    return;
                }
            }
            if (next == null) {
                writer.serializeAsField(pojo, gen, provider);
                return;
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
}
