package ai.csap.apidoc.devtools;

import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.web.context.WebServerApplicationContext;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationListener;
import org.springframework.core.env.Environment;

/**
 * @author yangchengfu
 * @description Devtools 启动横幅 - 在应用启动成功后打印访问路径
 * @date 2025-10-20
 */
public class DevtoolsStartupBanner implements ApplicationListener<ApplicationReadyEvent> {

    private static final Logger LOG = LoggerFactory.getLogger(DevtoolsStartupBanner.class);

    // 使用 AtomicBoolean 确保只打印一次
    private final AtomicBoolean printed = new AtomicBoolean(false);

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        ApplicationContext context = event.getApplicationContext();

        // 防止重复打印：
        // 1. 只在主 Web 容器中打印（避免父子容器重复）
        // 2. 使用原子变量确保只打印一次
        if (!(context instanceof WebServerApplicationContext)) {
            return;
        }

        if (!printed.compareAndSet(false, true)) {
            LOG.debug("Devtools banner already printed, skipping duplicate event");
            return;
        }

        Environment env = context.getEnvironment();

        // 获取服务器端口
        String port = env.getProperty("server.port", "8080");
        String contextPath = env.getProperty("server.servlet.context-path", "");

        // 构建访问地址
        String baseUrl = "http://localhost:" + port + contextPath;
        String devtoolsUrl = baseUrl + "/devtools-ui";

        // 打印漂亮的启动横幅
        printBanner(devtoolsUrl);
    }

    private void printBanner(String devtoolsUrl) {
        String banner = "\n" +
                "╔══════════════════════════════════════════════════════════════════════╗\n" +
                "║                                                                      ║\n" +
                "║   🎉  CSAP API Devtools Started Successfully!                       ║\n" +
                "║                                                                      ║\n" +
                "║   📱  访问地址：                                                      ║\n" +
                "║      " + String.format("%-64s", devtoolsUrl) + "  ║\n" +
                "║                                                                      ║\n" +
                "║   💡  功能说明：                                                      ║\n" +
                "║      • API 接口管理与文档生成                                          ║\n" +
                "║      • 接口参数配置与验证                                              ║\n" +
                "║      • YAML/JSON 文档导出                                             ║\n" +
                "║                                                                      ║\n" +
                "║   🔌  API 接口：                                                      ║\n" +
                "║      • /devtools/*  - Devtools API                                  ║\n" +
                "║      • /csap/yaml/* - YAML API                                      ║\n" +
                "║                                                                      ║\n" +
                "╚══════════════════════════════════════════════════════════════════════╝\n";

        // 同时使用 System.out 确保在控制台可见
        System.out.println(banner);
    }
}
