package ai.csap.apidoc.runtime.filter;

import org.springframework.http.converter.json.AbstractJackson2HttpMessageConverter;
import org.springframework.http.converter.json.MappingJacksonValue;

/**
 * Jackson 引擎:把响应体包装成 {@link MappingJacksonValue},并设置本次请求的过滤器。
 * 已经是 MappingJacksonValue 的(如 {@code @JsonView})保留原有设置。
 *
 * @author ycf
 * @since 1.2.0
 */
public class JacksonResponseFilterEngine implements ResponseFilterEngine {

    @Override
    public FilterEngine type() {
        return FilterEngine.JACKSON;
    }

    @Override
    public boolean supports(Class<?> converterType) {
        return AbstractJackson2HttpMessageConverter.class.isAssignableFrom(converterType);
    }

    @Override
    public Object apply(Object body, FilterSession session) {
        MappingJacksonValue container = body instanceof MappingJacksonValue
                ? (MappingJacksonValue) body : new MappingJacksonValue(body);
        session.begin(container.getValue());
        container.setFilters(JacksonFilterSupport.requestProvider(session, container.getFilters()));
        return container;
    }
}
