package ai.csap.apidoc.runtime.autoconfigure;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import ai.csap.apidoc.runtime.filter.Fastjson2ResponseFilterEngine;
import ai.csap.apidoc.runtime.filter.FastjsonResponseFilterEngine;
import ai.csap.apidoc.runtime.filter.JacksonConverterInstaller;
import ai.csap.apidoc.runtime.filter.JacksonResponseFilterEngine;
import ai.csap.apidoc.runtime.filter.ResponseFilterAdvice;
import ai.csap.apidoc.runtime.filter.ResponseFilterCaptureAdvice;
import ai.csap.apidoc.runtime.filter.ResponseFilterEngine;
import ai.csap.apidoc.runtime.filter.ResponseRuleRegistry;

/**
 * 返回字段过滤的自动配置。默认关闭,{@code csap.apidoc.runtime.filter.enabled=true} 时生效。
 *
 * <p>只依赖 spring-web / spring-webmvc 的抽象,同一个 jar 可用于 Spring Boot 2 和 3。
 *
 * @author ycf
 * @since 1.2.0
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass(name = "org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice")
@ConditionalOnProperty(prefix = RuntimeFilterProperties.PREFIX, name = "enabled", havingValue = "true")
@EnableConfigurationProperties(RuntimeFilterProperties.class)
public class RuntimeFilterAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public ResponseRuleRegistry csapResponseRuleRegistry() {
        return new ResponseRuleRegistry();
    }

    @Bean
    public ResponseFilterCaptureAdvice csapResponseFilterCaptureAdvice(ResponseRuleRegistry registry,
                                                                       RuntimeFilterProperties properties) {
        return new ResponseFilterCaptureAdvice(registry, properties::modeFor);
    }

    @Bean
    public ResponseFilterAdvice csapResponseFilterAdvice(ObjectProvider<ResponseFilterEngine> engines,
                                                         RuntimeFilterProperties properties) {
        List<ResponseFilterEngine> list = engines.orderedStream().collect(Collectors.toList());
        return new ResponseFilterAdvice(list, properties.getEngine());
    }

    /**
     * Jackson
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = {"com.fasterxml.jackson.databind.ObjectMapper",
            "org.springframework.http.converter.json.AbstractJackson2HttpMessageConverter"})
    static class JacksonEngineConfiguration {

        @Bean
        public ResponseFilterEngine csapJacksonResponseFilterEngine() {
            return new JacksonResponseFilterEngine();
        }

        @Bean
        public static JacksonConverterInstaller csapJacksonConverterInstaller(ApplicationContext applicationContext) {
            return new JacksonConverterInstaller(applicationContext);
        }
    }

    /**
     * fastjson 1.x
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = {"com.alibaba.fastjson.serializer.JSONSerializable",
            "com.alibaba.fastjson.support.spring.FastJsonContainer"})
    static class FastjsonEngineConfiguration {

        @Bean
        public ResponseFilterEngine csapFastjsonResponseFilterEngine() {
            return new FastjsonResponseFilterEngine();
        }
    }

    /**
     * fastjson2
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "com.alibaba.fastjson2.writer.ObjectWriter")
    static class Fastjson2EngineConfiguration {

        @Bean
        public ResponseFilterEngine csapFastjson2ResponseFilterEngine() {
            return new Fastjson2ResponseFilterEngine();
        }
    }
}
