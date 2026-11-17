package ai.csap.apidoc.runtime.filter;

import java.util.IdentityHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.context.ApplicationContext;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.AbstractJackson2HttpMessageConverter;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 启动完成后,为 Spring MVC 使用的每个 JSON 格式的 Jackson 消息转换器准备一个过滤用的 ObjectMapper 副本。
 *
 * <p>只复制、不修改原 ObjectMapper。无法复制的(自定义 ObjectMapper 子类没有实现 copy())和非 JSON 格式
 * (XML、CBOR 等)的转换器不支持过滤,其响应原样输出并记录日志。
 *
 * @author ycf
 * @since 1.2.0
 */
public class JacksonConverterInstaller implements SmartInitializingSingleton {

    private static final Logger log = LoggerFactory.getLogger(JacksonConverterInstaller.class);

    private final ApplicationContext applicationContext;

    private final JacksonResponseFilterEngine engine;

    public JacksonConverterInstaller(ApplicationContext applicationContext, JacksonResponseFilterEngine engine) {
        this.applicationContext = applicationContext;
        this.engine = engine;
    }

    @Override
    public void afterSingletonsInstantiated() {
        Map<ObjectMapper, ObjectMapper> copies = new IdentityHashMap<>();
        for (RequestMappingHandlerAdapter adapter
                : applicationContext.getBeansOfType(RequestMappingHandlerAdapter.class, false, false).values()) {
            for (HttpMessageConverter<?> converter : adapter.getMessageConverters()) {
                if (!(converter instanceof AbstractJackson2HttpMessageConverter)
                        || engine.isRegistered(converter.getClass())) {
                    continue;
                }
                ObjectMapper mapper = ((AbstractJackson2HttpMessageConverter) converter).getObjectMapper();
                if (!"JSON".equals(mapper.getFactory().getFormatName())) {
                    continue;
                }
                ObjectMapper copy = copies.computeIfAbsent(mapper, JacksonConverterInstaller::copyOf);
                if (copy != null) {
                    engine.register(converter.getClass(), copy);
                }
            }
        }
    }

    private static ObjectMapper copyOf(ObjectMapper mapper) {
        try {
            return mapper.copy();
        } catch (RuntimeException e) {
            log.warn("ObjectMapper {} 不支持 copy(),使用它输出的响应不做返回字段过滤", mapper.getClass().getName());
            return null;
        }
    }
}
