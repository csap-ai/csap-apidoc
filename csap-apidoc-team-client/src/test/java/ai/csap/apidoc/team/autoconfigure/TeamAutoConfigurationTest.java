package ai.csap.apidoc.team.autoconfigure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.mock.env.MockEnvironment;

import ai.csap.apidoc.team.TeamRegistrar;
import ai.csap.apidoc.team.autoconfigure.TeamAutoConfiguration.TeamRegistrationStarter;

class TeamAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(TeamAutoConfiguration.class));

    private static boolean registerThreadAlive() {
        return Thread.getAllStackTraces().keySet().stream()
                .anyMatch(t -> "csap-apidoc-team-register".equals(t.getName()) && t.isAlive());
    }

    private static TeamRegistrationStarter starter(TeamProperties p, MockEnvironment env, String ip) {
        return new TeamRegistrationStarter(p, env, () -> ip);
    }

    private static TeamProperties props(String url) {
        TeamProperties p = new TeamProperties();
        p.setEnabled(true);
        p.setUrl(url);
        p.setToken("t");
        return p;
    }

    @Test
    void disabledByDefaultCreatesNothing() {
        runner.run(ctx -> {
            assertTrue(ctx.getBeansOfType(TeamRegistrationStarter.class).isEmpty());
            assertFalse(registerThreadAlive());
        });
        runner.withPropertyValues("csap.apidoc.team.enabled=false", "csap.apidoc.team.url=http://x:1")
              .run(ctx -> {
                  assertTrue(ctx.getBeansOfType(TeamRegistrationStarter.class).isEmpty());
                  assertFalse(registerThreadAlive());
              });
    }

    @Test
    void enabledBindsPropertiesAndStillStartsNoThreadWithoutWebServer() {
        runner.withPropertyValues("csap.apidoc.team.enabled=true", "csap.apidoc.team.url=http://x:1",
                      "csap.apidoc.team.token=abc", "csap.apidoc.team.doc-path=/docs")
              .run(ctx -> {
                  TeamProperties p = ctx.getBean(TeamProperties.class);
                  assertTrue(p.isEnabled());
                  assertEquals("http://x:1", p.getUrl());
                  assertEquals("/docs", p.getDocPath());
                  assertFalse(p.toString().contains("abc"));
                  assertNotNull(ctx.getBean(TeamRegistrationStarter.class));
                  // 没有 Web 服务器事件,不会注册
                  assertFalse(registerThreadAlive());
              });
    }

    @Test
    void emptyUrlMeansNoRegistration() {
        assertNull(starter(props(""), new MockEnvironment(), "10.0.0.5").create(8080));
        assertNull(starter(props("  "), new MockEnvironment(), "10.0.0.5").create(8080));
        assertNull(starter(props("not a url"), new MockEnvironment(), "10.0.0.5").create(8080));
        assertFalse(registerThreadAlive());
    }

    @Test
    void serviceDefaultsFollowContractOrder() {
        MockEnvironment env = new MockEnvironment();
        TeamProperties p = props("http://team:8090");
        assertEquals("default", starter(p, env, "1.1.1.1").resolveService());
        env.setProperty("spring.application.name", "app-name");
        assertEquals("app-name", starter(p, env, "1.1.1.1").resolveService());
        env.setProperty("csap.apidoc.database.service", "db-service");
        assertEquals("db-service", starter(p, env, "1.1.1.1").resolveService());
        p.setService("explicit");
        assertEquals("explicit", starter(p, env, "1.1.1.1").resolveService());
    }

    @Test
    void baseUrlInferredFromLocalAddressPortAndContextPath() {
        MockEnvironment env = new MockEnvironment();
        TeamProperties p = props("http://team:8090");
        assertEquals("http://10.0.0.5:8080", starter(p, env, "10.0.0.5").resolveBaseUrl(8080));
        env.setProperty("server.servlet.context-path", "/api/");
        assertEquals("http://10.0.0.5:8080/api", starter(p, env, "10.0.0.5").resolveBaseUrl(8080));
        env.setProperty("server.servlet.context-path", "/");
        assertEquals("http://10.0.0.5:8080", starter(p, env, "10.0.0.5").resolveBaseUrl(8080));
        env.setProperty("server.ssl.enabled", "true");
        assertEquals("https://10.0.0.5:8443", starter(p, env, "10.0.0.5").resolveBaseUrl(8443));
        // 推断不出本机地址时不注册
        assertNull(starter(p, new MockEnvironment(), null).resolveBaseUrl(8080));
        assertNull(starter(p, new MockEnvironment(), null).create(8080));
        // 显式配置优先,去掉末尾 /
        p.setBaseUrl("http://gateway.example.com/order/");
        assertEquals("http://gateway.example.com/order", starter(p, env, null).resolveBaseUrl(8080));
    }

    @Test
    void instanceIdDefaultsToHostAndPortOfBaseUrl() {
        TeamRegistrationStarter s = starter(props("http://team:8090"), new MockEnvironment(), "10.0.0.5");
        assertEquals("10.0.0.5:8080", s.resolveInstanceId("http://10.0.0.5:8080/ctx"));
        assertEquals("gateway.example.com", s.resolveInstanceId("http://gateway.example.com/order"));
        TeamProperties p = props("http://team:8090");
        p.setInstanceId("pod-1");
        assertEquals("pod-1", starter(p, new MockEnvironment(), "10.0.0.5").resolveInstanceId("http://a:1"));
    }

    @Test
    void createBuildsRegistrarWithoutStartingIt() {
        TeamRegistrar r = starter(props("http://team:8090"), new MockEnvironment(), "10.0.0.5").create(8080);
        assertNotNull(r);
        assertFalse(registerThreadAlive());
    }

    @Test
    void frameworkVersionComesFromBuild() {
        String v = TeamAutoConfiguration.frameworkVersion();
        assertFalse(v.contains("${"), v);
        assertTrue(v.matches("\\d+\\.\\d+\\.\\d+.*"), v);
    }

    @Test
    void localAddressIsNeverLoopback() {
        String ip = TeamAutoConfiguration.firstNonLoopbackIpv4();
        if (ip != null) {
            assertFalse(ip.startsWith("127."));
        }
    }
}
