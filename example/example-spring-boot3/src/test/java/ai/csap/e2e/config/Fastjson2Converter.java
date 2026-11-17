package ai.csap.e2e.config;

import java.util.List;

import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.alibaba.fastjson2.support.spring6.http.converter.FastJsonHttpMessageConverter;

/**
 * 用 fastjson2 输出 JSON(放在第一个)。不在组件扫描范围内,由测试显式引入。
 */
public class Fastjson2Converter implements WebMvcConfigurer {

    @Override
    public void extendMessageConverters(List<HttpMessageConverter<?>> converters) {
        FastJsonHttpMessageConverter converter = new FastJsonHttpMessageConverter();
        converter.setSupportedMediaTypes(List.of(MediaType.APPLICATION_JSON));
        converters.add(0, converter);
    }
}
