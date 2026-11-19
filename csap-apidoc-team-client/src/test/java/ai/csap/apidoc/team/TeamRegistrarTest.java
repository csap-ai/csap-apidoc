package ai.csap.apidoc.team;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxyUtil;
import ch.qos.logback.core.read.ListAppender;

class TeamRegistrarTest {

    private static final String TOKEN = "tok-secret-9f3a1c";
    /** 测试里 1 "秒" = 20 毫秒,心跳 10 秒 = 200 毫秒 */
    private static final long SCALE = 20L;

    private TeamTestServer server;
    private TeamRegistrar registrar;
    private ListAppender<ILoggingEvent> logs;
    private Logger logger;

    @BeforeEach
    void setUp() throws IOException {
        server = new TeamTestServer();
        logger = (Logger) LoggerFactory.getLogger(TeamRegistrar.class);
        logger.setLevel(Level.DEBUG);
        logs = new ListAppender<>();
        logs.start();
        logger.addAppender(logs);
    }

    @AfterEach
    void tearDown() {
        if (registrar != null) {
            registrar.close();
        }
        server.close();
        logger.detachAppender(logs);
    }

    private TeamRegistrar.Registration registration(String url) {
        return new TeamRegistrar.Registration(url, TOKEN, "order-service", "10.0.0.5:8080",
                "http://10.0.0.5:8080", "/csap/apidoc", "java", "1.5.0");
    }

    private static void await(BooleanSupplier cond, long timeoutMillis) throws InterruptedException {
        long end = System.currentTimeMillis() + timeoutMillis;
        while (!cond.getAsBoolean() && System.currentTimeMillis() < end) {
            Thread.sleep(10);
        }
    }

    private List<String> allLogText() {
        List<String> out = new ArrayList<>();
        for (ILoggingEvent e : new ArrayList<>(logs.list)) {
            String s = e.getFormattedMessage();
            if (e.getThrowableProxy() != null) {
                s += ThrowableProxyUtil.asString(e.getThrowableProxy());
            }
            out.add(s);
        }
        return out;
    }

    private long count(Level level) {
        return new ArrayList<>(logs.list).stream().filter(e -> e.getLevel() == level).count();
    }

    private void assertNoToken() {
        for (String s : allLogText()) {
            assertFalse(s.contains(TOKEN), "日志里出现了令牌: " + s);
        }
    }

    @Test
    void registerRequestMatchesContract() throws Exception {
        registrar = new TeamRegistrar(registration(server.url() + "/"), SCALE);
        registrar.start();
        await(() -> server.count("/api/open/v1/services/register") >= 1, 3000);

        TeamTestServer.Req req = server.requests.get(0);
        assertEquals("POST", req.method);
        assertEquals("/api/open/v1/services/register", req.path);
        assertEquals("Bearer " + TOKEN, req.header("Authorization"));
        assertTrue(req.header("Content-Type").startsWith("application/json"));
        JsonNode body = new ObjectMapper().readTree(req.body);
        assertEquals("order-service", body.get("service").asText());
        assertEquals("10.0.0.5:8080", body.get("instanceId").asText());
        assertEquals("http://10.0.0.5:8080", body.get("baseUrl").asText());
        assertEquals("/csap/apidoc", body.get("docPath").asText());
        assertEquals("java", body.get("language").asText());
        assertEquals("1.5.0", body.get("frameworkVersion").asText());
        assertEquals(6, body.size());
        assertNoToken();
    }

    @Test
    void heartbeatRepeatsSameRequestAtServerInterval() throws Exception {
        server.respond(r -> new TeamTestServer.Resp(200, "{\"heartbeatSeconds\":10}"));
        registrar = new TeamRegistrar(registration(server.url()), SCALE);
        registrar.start();
        await(() -> server.count("/api/open/v1/services/register") >= 4, 5000);

        List<TeamTestServer.Req> regs = server.requests.stream()
                .filter(r -> r.path.endsWith("/register")).collect(Collectors.toList());
        assertTrue(regs.size() >= 4);
        for (TeamTestServer.Req r : regs) {
            assertEquals(regs.get(0).body, r.body, "心跳必须重复发送同一个请求");
        }
        // 间隔约 10 "秒" = 200ms,允许调度抖动
        for (int i = 1; i < 4; i++) {
            long gapMs = (regs.get(i).atNanos - regs.get(i - 1).atNanos) / 1_000_000L;
            assertTrue(gapMs >= 150 && gapMs < 1000, "心跳间隔应约 200ms,实际 " + gapMs);
        }
    }

    @Test
    void heartbeatIsClampedOrFallsBackTo30() {
        assertEquals(30, TeamRegistrar.parseHeartbeat(null));
        assertEquals(30, TeamRegistrar.parseHeartbeat(""));
        assertEquals(30, TeamRegistrar.parseHeartbeat("not json"));
        assertEquals(30, TeamRegistrar.parseHeartbeat("{}"));
        assertEquals(30, TeamRegistrar.parseHeartbeat("{\"heartbeatSeconds\":\"x\"}"));
        assertEquals(10, TeamRegistrar.parseHeartbeat("{\"heartbeatSeconds\":5}"));
        assertEquals(300, TeamRegistrar.parseHeartbeat("{\"heartbeatSeconds\":900}"));
        assertEquals(30, TeamRegistrar.parseHeartbeat("{\"heartbeatSeconds\":0}"));
        assertEquals(30, TeamRegistrar.parseHeartbeat("{\"heartbeatSeconds\":-1}"));
        assertEquals(30, TeamRegistrar.parseHeartbeat("{\"heartbeatSeconds\":30.5}"));
        assertEquals(30, TeamRegistrar.parseHeartbeat("{\"heartbeatSeconds\":\"abc\"}"));
        assertEquals(10, TeamRegistrar.parseHeartbeat("{\"heartbeatSeconds\":10}"));
        assertEquals(300, TeamRegistrar.parseHeartbeat("{\"heartbeatSeconds\":300}"));
        assertEquals(60, TeamRegistrar.parseHeartbeat("{\"heartbeatSeconds\":60}"));
    }

    /** 首次注册请求已到达团队版但还没收到响应时关闭:也要发注销,否则团队版留下一条过期实例。 */
    @Test
    void closeWhileFirstRegisterInFlightStillDeregisters() throws Exception {
        server.respond(r -> {
            if (r.path.endsWith("/register")) {
                try {
                    Thread.sleep(800);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            return new TeamTestServer.Resp(200, "{\"heartbeatSeconds\":30}");
        });
        registrar = new TeamRegistrar(registration(server.url()), SCALE);
        registrar.start();
        await(() -> server.count("/api/open/v1/services/register") >= 1, 3000);
        registrar.close();
        await(() -> server.count("/api/open/v1/services/deregister") >= 1, 3000);
        assertEquals(1, server.count("/api/open/v1/services/deregister"));
    }

    @Test
    void deregisterOnClose() throws Exception {
        registrar = new TeamRegistrar(registration(server.url()), SCALE);
        registrar.start();
        await(() -> server.count("/api/open/v1/services/register") >= 1, 3000);
        registrar.close();

        assertEquals(1, server.count("/api/open/v1/services/deregister"));
        TeamTestServer.Req dereg = server.requests.stream()
                .filter(r -> r.path.endsWith("/deregister")).findFirst().get();
        assertEquals("POST", dereg.method);
        assertEquals("Bearer " + TOKEN, dereg.header("Authorization"));
        JsonNode body = new ObjectMapper().readTree(dereg.body);
        assertEquals("order-service", body.get("service").asText());
        assertEquals("10.0.0.5:8080", body.get("instanceId").asText());
        assertEquals(2, body.size());

        // 关闭后不再发心跳
        int n = server.requests.size();
        Thread.sleep(500);
        assertEquals(n, server.requests.size());
        // 重复关闭不会再注销
        registrar.close();
        assertEquals(1, server.count("/api/open/v1/services/deregister"));
    }

    @Test
    void deregisterFailureIsIgnored() throws Exception {
        registrar = new TeamRegistrar(registration(server.url()), SCALE);
        registrar.start();
        await(() -> server.count("/api/open/v1/services/register") >= 1, 3000);
        server.respond(r -> new TeamTestServer.Resp(500, "boom"));
        registrar.close();
        assertEquals(1, server.count("/api/open/v1/services/deregister"));
        assertNoToken();
    }

    @Test
    void unauthorizedWarnsOnceThenRetriesEvery300Seconds() throws Exception {
        server.respond(r -> new TeamTestServer.Resp(401, "{\"error\":\"unauthorized\"}"));
        registrar = new TeamRegistrar(registration(server.url()), SCALE);
        registrar.start();
        await(() -> server.count("/api/open/v1/services/register") >= 1, 3000);
        // 300 "秒" = 6 秒,期间只有这一次请求(正常心跳 30 "秒" = 600ms 内会有多次)
        Thread.sleep(1500);
        assertEquals(1, server.count("/api/open/v1/services/register"));
        long warns = new ArrayList<>(logs.list).stream()
                .filter(e -> e.getLevel() == Level.WARN && e.getFormattedMessage().contains("令牌无效或权限不足")).count();
        assertEquals(1, warns);
        assertNoToken();
    }

    @Test
    void forbiddenAlsoSlowsDown() throws Exception {
        server.respond(r -> new TeamTestServer.Resp(403, ""));
        registrar = new TeamRegistrar(registration(server.url()), SCALE);
        registrar.start();
        await(() -> server.count("/api/open/v1/services/register") >= 1, 3000);
        Thread.sleep(1200);
        assertEquals(1, server.count("/api/open/v1/services/register"));
        assertEquals(1, count(Level.WARN));
    }

    @Test
    void failuresAreThrottledAndRecoveryIsLoggedOnce() throws Exception {
        // 前 25 次返回 500,之后恢复;scale=1ms,默认心跳 30 "秒" = 30ms
        server.respond(r -> server.requests.size() <= 25
                ? new TeamTestServer.Resp(500, "oops")
                : new TeamTestServer.Resp(200, "{\"heartbeatSeconds\":10}"));
        registrar = new TeamRegistrar(registration(server.url()), 1L);
        registrar.start();
        await(() -> logs.list.stream().anyMatch(e -> e.getLevel() == Level.INFO
                && e.getFormattedMessage().contains("已恢复")), 10000);
        long warns = count(Level.WARN);
        // 25 次连续失败:第 1、10、20 次 warn
        assertEquals(3, warns, allLogText().toString());
        long recovered = new ArrayList<>(logs.list).stream()
                .filter(e -> e.getLevel() == Level.INFO && e.getFormattedMessage().contains("已恢复")).count();
        assertEquals(1, recovered);
        assertNoToken();
    }

    @Test
    void unreachableServerDoesNotBlockOrThrow() throws Exception {
        int freePort;
        try (ServerSocket s = new ServerSocket(0)) {
            freePort = s.getLocalPort();
        }
        registrar = new TeamRegistrar(registration("http://127.0.0.1:" + freePort), SCALE);
        long t0 = System.nanoTime();
        registrar.start();
        long startMs = (System.nanoTime() - t0) / 1_000_000L;
        assertTrue(startMs < 500, "start() 必须立即返回,实际 " + startMs + "ms");
        await(() -> count(Level.WARN) >= 1, 5000);
        assertEquals(1, count(Level.WARN));
        assertNoToken();
        long t1 = System.nanoTime();
        registrar.close();
        assertTrue((System.nanoTime() - t1) / 1_000_000L < 3000);
    }

    @Test
    void invalidUrlDoesNotThrowAndHidesToken() throws Exception {
        registrar = new TeamRegistrar(registration("http://bad host:99999"), SCALE);
        registrar.start();
        await(() -> count(Level.WARN) >= 1, 3000);
        assertTrue(count(Level.WARN) >= 1);
        assertNoToken();
    }

    @Test
    void tokenInExceptionMessageIsMasked() throws Exception {
        // 令牌被拼进了 url 的极端情况:异常信息里的令牌也要被替换
        registrar = new TeamRegistrar(registration("http://" + TOKEN + ".invalid.example:1"), SCALE);
        registrar.start();
        await(() -> count(Level.WARN) >= 1, 8000);
        assertNoToken();
    }

    @Test
    void noThreadBeforeStart() {
        registrar = new TeamRegistrar(registration(server.url()), SCALE);
        assertNull(findThread());
        assertEquals(0, server.requests.size());
        registrar.close();
        assertNull(findThread());
    }

    @Test
    void startCreatesSingleDaemonThread() throws Exception {
        registrar = new TeamRegistrar(registration(server.url()), SCALE);
        registrar.start();
        registrar.start();
        await(() -> findThread() != null, 2000);
        Thread t = findThread();
        assertNotNull(t);
        assertTrue(t.isDaemon());
        long n = Thread.getAllStackTraces().keySet().stream()
                .filter(x -> "csap-apidoc-team-register".equals(x.getName())).count();
        assertEquals(1, n);
    }

    static Thread findThread() {
        return Thread.getAllStackTraces().keySet().stream()
                .filter(x -> "csap-apidoc-team-register".equals(x.getName()) && x.isAlive()).findFirst().orElse(null);
    }
}
