package ai.csap.apidoc.boot.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.web.servlet.ServletWebServerFactoryAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.server.WebServer;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;

import ai.csap.apidoc.team.autoconfigure.TeamAutoConfiguration;

/**
 * 团队版服务注册的端到端:真实的内嵌 Web 服务器 + 本地 HTTP 服务器扮演团队版。
 */
class TeamRegistrationE2eTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Configuration
    @Import({ServletWebServerFactoryAutoConfiguration.class, TeamAutoConfiguration.class})
    static class App {
    }

    private static final class Recorded {
        final String path;
        final String auth;
        final String body;

        Recorded(String path, String auth, String body) {
            this.path = path;
            this.auth = auth;
            this.body = body;
        }
    }

    @Test
    void registersAfterWebServerStartsAndDeregistersOnClose() throws Exception {
        List<Recorded> got = new CopyOnWriteArrayList<>();
        HttpServer team = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        team.createContext("/", ex -> {
            got.add(new Recorded(ex.getRequestURI().getPath(), ex.getRequestHeaders().getFirst("Authorization"),
                    new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)));
            byte[] out = "{\"heartbeatSeconds\":30}".getBytes(StandardCharsets.UTF_8);
            ex.sendResponseHeaders(200, out.length);
            try (OutputStream os = ex.getResponseBody()) {
                os.write(out);
            }
        });
        team.start();
        ConfigurableApplicationContext ctx = null;
        try {
            ctx = new SpringApplicationBuilder(App.class)
                    .web(WebApplicationType.SERVLET)
                    .properties("server.port=0", "server.servlet.context-path=/orders",
                            "spring.application.name=order-service",
                            "csap.apidoc.team.enabled=true",
                            "csap.apidoc.team.url=http://127.0.0.1:" + team.getAddress().getPort(),
                            "csap.apidoc.team.token=e2e-token")
                    .run();
            WebServer web = ((ServletWebServerApplicationContext) ctx).getWebServer();
            long end = System.currentTimeMillis() + 5000;
            while (got.isEmpty() && System.currentTimeMillis() < end) {
                Thread.sleep(20);
            }
            assertThat(got).isNotEmpty();
            Recorded r = got.get(0);
            assertThat(r.path).isEqualTo("/api/open/v1/services/register");
            assertThat(r.auth).isEqualTo("Bearer e2e-token");
            JsonNode b = JSON.readTree(r.body);
            assertThat(b.get("service").asText()).isEqualTo("order-service");
            assertThat(b.get("language").asText()).isEqualTo("java");
            assertThat(b.get("docPath").asText()).isEqualTo("/csap/apidoc");
            // baseUrl:http://{本机非回环 IPv4}:{实际端口}/orders;没有网卡时不会注册(此时上面的断言已失败)
            assertThat(b.get("baseUrl").asText()).matches("http://\\d+\\.\\d+\\.\\d+\\.\\d+:" + web.getPort() + "/orders");
            assertThat(b.get("instanceId").asText()).matches("\\d+\\.\\d+\\.\\d+\\.\\d+:" + web.getPort());
            assertThat(b.get("frameworkVersion").asText()).matches("\\d+\\.\\d+\\.\\d+.*").doesNotContain("$");

            ctx.close();
            ctx = null;
            assertThat(got.stream().filter(x -> x.path.endsWith("/deregister")).count()).isEqualTo(1);
        } finally {
            if (ctx != null) {
                ctx.close();
            }
            team.stop(0);
        }
    }

    @Test
    void disabledByDefaultSendsNothingAndCreatesNoThread() {
        try (ConfigurableApplicationContext ctx = new SpringApplicationBuilder(App.class)
                .web(WebApplicationType.SERVLET).properties("server.port=0").run()) {
            assertThat(Thread.getAllStackTraces().keySet())
                    .noneMatch(t -> "csap-apidoc-team-register".equals(t.getName()));
        }
    }
}
