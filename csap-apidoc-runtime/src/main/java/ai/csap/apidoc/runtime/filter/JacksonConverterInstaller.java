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
 * 启动完成后,为 Spring MVC 使用的 Jackson 消息转换器安装过滤支持。
 *
 * <p>在 ObjectMapper 的副本上安装后替换到转换器里,应用里其他地方使用的同一个 ObjectMapper 不受影响;
 * 多个转换器共用一个 ObjectMapper 时也共用同一个副本。
 *
 * @author ycf
 * @since 1.2.0
 */
public class JacksonConverterInstaller implements SmartInitializingSingleton {

    private static final Logger log = LoggerFactory.getLogger(JacksonConverterInstaller.class);

    private final ApplicationContext applicationContext;

    public JacksonConverterInstaller(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    @Override
    public void afterSingletonsInstantiated() {
        Map<ObjectMapper, ObjectMapper> installed = new IdentityHashMap<>();
        int count = 0;
        for (RequestMappingHandlerAdapter adapter
                : applicationContext.getBeansOfType(RequestMappingHandlerAdapter.class, false, false).values()) {
            for (HttpMessageConverter<?> converter : adapter.getMessageConverters()) {
                if (converter instanceof AbstractJackson2HttpMessageConverter) {
                    AbstractJackson2HttpMessageConverter jackson = (AbstractJackson2HttpMessageConverter) converter;
                    ObjectMapper mapper = jackson.getObjectMapper();
                    ObjectMapper target = installed.computeIfAbsent(mapper, JacksonFilterSupport::installOnCopy);
                    if (target != mapper) {
                        jackson.setObjectMapper(target);
                    }
                    count++;
                }
            }
        }
        log.debug("CSAP Apidoc 返回字段过滤:已为 {} 个 Jackson 消息转换器安装过滤支持", count);
    }
}
