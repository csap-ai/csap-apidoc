package ai.csap.apidoc.team.config;

import java.io.IOException;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

/** 前端静态资源(打进 jar 的 static/),非文件路径回退到 index.html(前端路由);/api 下不回退。 */
@Configuration
@EnableConfigurationProperties(TeamProperties.class)
@EnableScheduling
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/**").addResourceLocations("classpath:/static/").resourceChain(true)
                .addResolver(new PathResourceResolver() {
                    @Override
                    protected Resource getResource(String resourcePath, Resource location) throws IOException {
                        Resource r = location.createRelative(resourcePath);
                        if (r.exists() && r.isReadable()) {
                            return r;
                        }
                        if (resourcePath.startsWith("api/") || resourcePath.contains(".")) {
                            return null;
                        }
                        Resource index = new ClassPathResource("static/index.html");
                        return index.exists() ? index : null;
                    }
                });
    }
}
