package ai.csap.validation.boot.autoconfigure;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter;

import ai.csap.validation.runtime.ValidatingArgumentResolver;
import ai.csap.validation.runtime.ValidationExecutor;

import lombok.extern.slf4j.Slf4j;

/**
 * 把 {@link RequestMappingHandlerAdapter} 的每个参数解析器包装成 {@link ValidatingArgumentResolver}.
 *
 * <p>保持原解析器的顺序,已经包装过的不再包装;在 adapter 初始化(afterPropertiesSet 组装默认解析器)之后执行。
 *
 * @author ycf
 * @since 1.2.0
 */
@Slf4j
public class ValidatingHandlerAdapterPostProcessor implements BeanPostProcessor {
    private final ObjectProvider<ValidationExecutor> executor;

    public ValidatingHandlerAdapterPostProcessor(ObjectProvider<ValidationExecutor> executor) {
        this.executor = executor;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) {
        if (bean instanceof RequestMappingHandlerAdapter) {
            wrap((RequestMappingHandlerAdapter) bean, beanName);
        }
        return bean;
    }

    private void wrap(RequestMappingHandlerAdapter adapter, String beanName) {
        List<HandlerMethodArgumentResolver> resolvers = adapter.getArgumentResolvers();
        ValidationExecutor validationExecutor = executor.getIfAvailable();
        if (resolvers == null || resolvers.isEmpty() || validationExecutor == null) {
            return;
        }
        List<HandlerMethodArgumentResolver> wrapped = new ArrayList<>(resolvers.size());
        int count = 0;
        for (HandlerMethodArgumentResolver resolver : resolvers) {
            if (resolver instanceof ValidatingArgumentResolver) {
                wrapped.add(resolver);
            } else {
                wrapped.add(new ValidatingArgumentResolver(resolver, validationExecutor));
                count++;
            }
        }
        if (count > 0) {
            adapter.setArgumentResolvers(wrapped);
            log.info("CSAP validation enabled on {}: wrapped {} argument resolvers", beanName, count);
        }
    }
}
