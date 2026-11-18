package ai.csap.apidoc.runtime.filter;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.http.converter.json.MappingJacksonValue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;

/**
 * Jackson 引擎。
 *
 * <p>响应体包装成 {@link JacksonFilterSupport.FilteredValue}:原消息转换器写出它时,
 * 改用该转换器 ObjectMapper 的副本(已安装过滤支持,见 {@link JacksonConverterInstaller})按规则写入同一个输出流。原 ObjectMapper 不做任何修改,没有规则的接口完全不受影响。
 * 响应体是 {@link MappingJacksonValue}(如 {@code @JsonView})时沿用其中的 view 和 filters。
 *
 * @author ycf
 * @since 1.2.0
 */
public class JacksonResponseFilterEngine implements ResponseFilterEngine {

    /**
     * 消息转换器类型 → 已安装过滤支持的 ObjectMapper 副本
     */
    private final Map<Class<?>, ObjectMapper> mappers = new ConcurrentHashMap<>();

    /**
     * 登记某个消息转换器使用的过滤副本(同一类型只登记第一个)
     *
     * @param converterType 消息转换器类型
     * @param filteringCopy 已安装过滤支持的 ObjectMapper 副本
     */
    public void register(Class<?> converterType, ObjectMapper filteringCopy) {
        JacksonFilterSupport.install(filteringCopy);
        mappers.putIfAbsent(converterType, filteringCopy);
    }

    /**
     * 是否已登记
     */
    public boolean isRegistered(Class<?> converterType) {
        return mappers.containsKey(converterType);
    }

    @Override
    public FilterEngine type() {
        return FilterEngine.JACKSON;
    }

    @Override
    public boolean supports(Class<?> converterType) {
        return mappers.containsKey(converterType);
    }

    @Override
    public Object apply(Object body, FilterSession session, Class<?> converterType) {
        ObjectMapper mapper = mappers.get(converterType);
        if (mapper == null) {
            return body;
        }
        MappingJacksonValue container = body instanceof MappingJacksonValue ? (MappingJacksonValue) body : null;
        Object value = container != null ? container.getValue() : body;
        ObjectWriter writer = mapper.writer(JacksonFilterSupport.requestProvider(mapper, session,
                container != null ? container.getFilters() : null));
        if (container != null && container.getSerializationView() != null) {
            writer = writer.withView(container.getSerializationView());
        }
        JacksonFilterSupport.FilteredValue filtered = new JacksonFilterSupport.FilteredValue(value, session, writer);
        if (container != null) {
            container.setValue(filtered);
            container.setFilters(null);
            container.setSerializationView(null);
            return container;
        }
        return filtered;
    }
}
