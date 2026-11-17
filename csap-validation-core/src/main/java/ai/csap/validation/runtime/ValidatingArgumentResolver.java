package ai.csap.validation.runtime;

import org.springframework.core.MethodParameter;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * 包装一个参数解析器:先交给原解析器解析,再按模式执行 CSAP 规则校验.
 *
 * <p>只依赖 spring-web 的抽象,不引用 javax.servlet / jakarta.servlet,Spring Boot 2 / 3 通用。
 *
 * @author ycf
 * @since 1.2.0
 */
public final class ValidatingArgumentResolver implements HandlerMethodArgumentResolver {
    private final HandlerMethodArgumentResolver delegate;
    private final ValidationExecutor executor;

    public ValidatingArgumentResolver(HandlerMethodArgumentResolver delegate, ValidationExecutor executor) {
        this.delegate = delegate;
        this.executor = executor;
    }

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return delegate.supportsParameter(parameter);
    }

    @Override
    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                  NativeWebRequest webRequest, WebDataBinderFactory binderFactory) throws Exception {
        Object value = delegate.resolveArgument(parameter, mavContainer, webRequest, binderFactory);
        executor.validate(parameter, value);
        return value;
    }

    /**
     * @return 被包装的原解析器
     */
    public HandlerMethodArgumentResolver getDelegate() {
        return delegate;
    }

    @Override
    public String toString() {
        return "ValidatingArgumentResolver{" + delegate + '}';
    }
}
