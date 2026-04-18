package ai.csap.apidoc.autoconfigure;

import java.util.concurrent.CompletableFuture;

import org.springframework.boot.context.event.ApplicationStartedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.env.Environment;

import ai.csap.apidoc.service.ApidocContext;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 运行初始化文档信息
 *
 * @author yangchengfu
 * @date 2021/1/27 10:18 上午
 **/
@AllArgsConstructor
@Slf4j
public class RunApplicationListener implements ApplicationListener<ApplicationStartedEvent> {

    @Override
    public void onApplicationEvent(ApplicationStartedEvent event) {
        log.info("CSAP Apidoc: Initializing API documentation...");

        Environment env = event.getApplicationContext().getEnvironment();
        String port = env.getProperty("server.port", "8080");
        String contextPath = env.getProperty("server.servlet.context-path", "");

        CompletableFuture.runAsync(() -> {
            try {
                ApidocContext.cmd(true, false);

                // 构建访问地址
                String baseUrl = "http://localhost:" + port + contextPath;
                String apiDocUrl = baseUrl + "/csap-api.html";

                // 打印美化的横幅
                printApidocBanner(apiDocUrl);

            } catch (Exception e) {
                log.error("Failed to initialize CSAP Apidoc", e);
            }
        });
    }

    /**
     * 打印 API 文档横幅
     */
    private void printApidocBanner(String apiDocUrl) {
        String banner = "\n" +
                "╔══════════════════════════════════════════════════════════════════════╗\n" +
                "║                                                                      ║\n" +
                "║   📚  CSAP API Documentation Generated Successfully!                ║\n" +
                "║                                                                      ║\n" +
                "║   📄  API 文档地址：                                                  ║\n" +
                "║      " + String.format("%-64s", apiDocUrl) + "  ║\n" +
                "║                                                                      ║\n" +
                "║   💡  支持的格式：                                                    ║\n" +
                "║      • HTML - 静态 HTML 文档                                          ║\n" +
                "║      • JSON - OpenAPI/Swagger JSON                                  ║\n" +
                "║      • YAML - OpenAPI/Swagger YAML                                  ║\n" +
                "║                                                                      ║\n" +
                "╚══════════════════════════════════════════════════════════════════════╝\n";
        // 同时使用 System.out 确保在控制台可见
        System.out.println(banner);
    }
}
