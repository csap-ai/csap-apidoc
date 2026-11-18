package ai.csap.apidoc.runtime.filter;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.MethodParameter;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.MappingJacksonValue;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

/**
 * 第一个执行的 ResponseBodyAdvice:接口有返回规则时,记下控制器方法的原始返回值。
 * 之后其他 advice 可能把它包进统一的外层结构,真正的过滤在最后的 {@link ResponseFilterAdvice} 里做,
 * 只作用于原始返回值这一部分。
 *
 * @author ycf
 * @since 1.2.0
 */
@ControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ResponseFilterCaptureAdvice implements ResponseBodyAdvice<Object>, Ordered {

    private static final Logger log = LoggerFactory.getLogger(ResponseFilterCaptureAdvice.class);

    private final ResponseRuleRegistry registry;

    private final Function<String, FilterMode> modeResolver;

    private final AtomicBoolean notReadyLogged = new AtomicBoolean();

    /**
     * @param registry     规则
     * @param modeResolver 接口(类名.方法名)使用的模式
     */
    public ResponseFilterCaptureAdvice(ResponseRuleRegistry registry, Function<String, FilterMode> modeResolver) {
        this.registry = registry;
        this.modeResolver = modeResolver;
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        return true;
    }

    @Override
    public Object beforeBodyWrite(Object body, MethodParameter returnType, MediaType selectedContentType,
                                  Class<? extends HttpMessageConverter<?>> selectedConverterType,
                                  ServerHttpRequest request, ServerHttpResponse response) {
        if (body == null || returnType.getMethod() == null) {
            return body;
        }
        String key = endpointKey(returnType);
        FilterMode mode = modeResolver.apply(key);
        if (mode == null || mode == FilterMode.OFF) {
            return body;
        }
        if (!registry.isReady()) {
            if (notReadyLogged.compareAndSet(false, true)) {
                log.info("CSAP Apidoc 文档尚未扫描完成,扫描完成前的响应不做返回字段过滤");
            }
            return body;
        }
        CompiledEndpoint endpoint = registry.get(key);
        if (endpoint == null || !endpoint.filters(mode)) {
            return body;
        }
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return body;
        }
        Object original = body instanceof MappingJacksonValue ? ((MappingJacksonValue) body).getValue() : body;
        attributes.setAttribute(PendingFilter.ATTRIBUTE, new PendingFilter(mode, endpoint, original),
                RequestAttributes.SCOPE_REQUEST);
        return body;
    }

    /**
     * 接口标识:类名.方法名(与文档、规则的键一致)
     */
    static String endpointKey(MethodParameter returnType) {
        return returnType.getContainingClass().getName() + '.' + returnType.getMethod().getName();
    }
}
