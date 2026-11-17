package ai.csap.apidoc.runtime.filter;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.MethodParameter;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

/**
 * 最后执行的 ResponseBodyAdvice:按接口实际使用的消息转换器选择引擎,包装响应体,序列化时过滤字段。
 *
 * @author ycf
 * @since 1.2.0
 */
@ControllerAdvice
@Order(Ordered.LOWEST_PRECEDENCE)
public class ResponseFilterAdvice implements ResponseBodyAdvice<Object>, Ordered {

    private static final Logger log = LoggerFactory.getLogger(ResponseFilterAdvice.class);

    private static final ResponseFilterEngine NONE = new ResponseFilterEngine() {
        @Override
        public FilterEngine type() {
            return FilterEngine.AUTO;
        }

        @Override
        public boolean supports(Class<?> converterType) {
            return false;
        }

        @Override
        public Object apply(Object body, FilterSession session) {
            return body;
        }
    };

    private final List<ResponseFilterEngine> engines;

    private final FilterEngine preferred;

    private final Map<Class<?>, ResponseFilterEngine> selected = new ConcurrentHashMap<>();

    private final Set<Class<?>> warned = ConcurrentHashMap.newKeySet();

    /**
     * @param engines   可用的引擎
     * @param preferred 指定的引擎;AUTO 表示按转换器自动选择
     */
    public ResponseFilterAdvice(List<ResponseFilterEngine> engines, FilterEngine preferred) {
        this.engines = engines;
        this.preferred = preferred == null ? FilterEngine.AUTO : preferred;
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }

    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        return true;
    }

    @Override
    public Object beforeBodyWrite(Object body, MethodParameter returnType, MediaType selectedContentType,
                                  Class<? extends HttpMessageConverter<?>> selectedConverterType,
                                  ServerHttpRequest request, ServerHttpResponse response) {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return body;
        }
        Object pending = attributes.getAttribute(PendingFilter.ATTRIBUTE, RequestAttributes.SCOPE_REQUEST);
        if (!(pending instanceof PendingFilter)) {
            return body;
        }
        attributes.removeAttribute(PendingFilter.ATTRIBUTE, RequestAttributes.SCOPE_REQUEST);
        if (body == null) {
            return null;
        }
        ResponseFilterEngine engine = selected.computeIfAbsent(selectedConverterType, this::select);
        if (engine == NONE) {
            if (warned.add(selectedConverterType)) {
                log.warn("CSAP Apidoc 返回字段过滤不支持消息转换器 {}(engine={}),{} 的响应不过滤",
                        selectedConverterType.getName(), preferred.name().toLowerCase(),
                        ResponseFilterCaptureAdvice.endpointKey(returnType));
            }
            return body;
        }
        return engine.apply(body, ((PendingFilter) pending).newSession());
    }

    private ResponseFilterEngine select(Class<?> converterType) {
        for (ResponseFilterEngine engine : engines) {
            if ((preferred == FilterEngine.AUTO || preferred == engine.type()) && engine.supports(converterType)) {
                return engine;
            }
        }
        return NONE;
    }
}
