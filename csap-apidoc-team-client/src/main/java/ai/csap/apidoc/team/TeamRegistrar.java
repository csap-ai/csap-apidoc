package ai.csap.apidoc.team;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 向团队版注册服务并保持心跳(协议见 docs/design/team-integration.md 第 1 节)。
 *
 * <p>全部工作在一个后台守护线程里完成,注册失败、团队版不可达都不会抛到调用方,也不会阻止应用启动。
 * 日志和异常信息里不会出现令牌。{@link #start()} 之前不创建线程,也不发请求。
 *
 * @author ycf
 * @since 1.5.0
 */
public class TeamRegistrar implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(TeamRegistrar.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    static final String REGISTER_PATH = "/api/open/v1/services/register";
    static final String DEREGISTER_PATH = "/api/open/v1/services/deregister";
    static final int DEFAULT_HEARTBEAT_SECONDS = 30;
    static final int MIN_HEARTBEAT_SECONDS = 10;
    static final int MAX_HEARTBEAT_SECONDS = 300;
    static final int AUTH_RETRY_SECONDS = 300;
    static final long REQUEST_TIMEOUT_MILLIS = 3000L;
    static final long DEREGISTER_TIMEOUT_MILLIS = 2000L;
    /**
     * 连续失败时,第一次之后每隔多少次再 warn 一次
     */
    static final int WARN_EVERY = 10;

    /**
     * 注册请求的参数,字段含义见协议 1.1。
     */
    public static final class Registration {
        private final String url;
        private final String token;
        private final String service;
        private final String instanceId;
        private final String baseUrl;
        private final String docPath;
        private final String language;
        private final String frameworkVersion;

        public Registration(String url, String token, String service, String instanceId, String baseUrl,
                            String docPath, String language, String frameworkVersion) {
            this.url = stripTrailingSlash(url);
            this.token = token;
            this.service = service;
            this.instanceId = instanceId;
            this.baseUrl = baseUrl;
            this.docPath = docPath;
            this.language = language;
            this.frameworkVersion = frameworkVersion;
        }

        public String getService() {
            return service;
        }

        public String getInstanceId() {
            return instanceId;
        }

        public String getBaseUrl() {
            return baseUrl;
        }

        public String getDocPath() {
            return docPath;
        }
    }

    /**
     * 时间刻度:一个"秒"对应多少毫秒。生产为 1000,测试里缩小以便快速验证心跳间隔。
     */
    private final long secondMillis;
    private final Registration reg;
    private final String registerBody;
    private final String deregisterBody;

    private volatile ScheduledExecutorService scheduler;
    private volatile HttpClient client;
    private volatile boolean closed;
    private volatile int heartbeatSeconds = DEFAULT_HEARTBEAT_SECONDS;
    private int failures;
    private boolean authWarned;
    private volatile boolean registered;
    /**
     * 首次注册请求已发出、还没结束。此时关闭也发注销(请求可能已到达团队版,否则团队版会留下一条过期实例);
     * 之后的心跳以 {@link #registered} 为准。
     */
    private volatile boolean firstInFlight;
    /** 已有注册请求结束过(不论结果)。 */
    private volatile boolean attempted;

    public TeamRegistrar(Registration registration) {
        this(registration, 1000L);
    }

    TeamRegistrar(Registration registration, long secondMillis) {
        this.reg = registration;
        this.secondMillis = secondMillis;
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("service", reg.service);
        body.put("instanceId", reg.instanceId);
        body.put("baseUrl", reg.baseUrl);
        body.put("docPath", reg.docPath);
        body.put("language", reg.language);
        body.put("frameworkVersion", reg.frameworkVersion);
        this.registerBody = toJson(body);
        Map<String, Object> dereg = new LinkedHashMap<>();
        dereg.put("service", reg.service);
        dereg.put("instanceId", reg.instanceId);
        this.deregisterBody = toJson(dereg);
    }

    /**
     * 启动后台线程:立即注册一次,之后按心跳周期重复发送。重复调用无效。
     */
    public synchronized void start() {
        if (scheduler != null || closed) {
            return;
        }
        client = HttpClient.newBuilder().connectTimeout(Duration.ofMillis(REQUEST_TIMEOUT_MILLIS)).build();
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "csap-apidoc-team-register");
            t.setDaemon(true);
            return t;
        });
        scheduler.execute(this::beat);
    }

    private void beat() {
        if (closed) {
            return;
        }
        long delaySeconds;
        try {
            delaySeconds = sendRegister();
        } catch (RuntimeException e) {
            // 任何意外都不能让心跳线程退出
            delaySeconds = heartbeatSeconds;
            LOG.warn("csap-apidoc 团队版注册出现异常: {}", describe(e));
        }
        ScheduledExecutorService s = scheduler;
        if (!closed && s != null) {
            try {
                s.schedule(this::beat, delaySeconds * secondMillis, TimeUnit.MILLISECONDS);
            } catch (RuntimeException ignore) {
                // 已关闭
            }
        }
    }

    /**
     * 注册请求正常结束(不论结果)。已在关闭时保留 firstInFlight,由 close() 决定是否注销。
     */
    private void requestFinished() {
        if (!closed) {
            firstInFlight = false;
            attempted = true;
        }
    }

    /**
     * 发送一次注册 / 心跳请求。
     *
     * @return 下次发送前等待的秒数
     */
    private long sendRegister() {
        int status;
        String responseBody = null;
        firstInFlight = !attempted;
        try {
            HttpResponse<String> resp = post(REGISTER_PATH, registerBody, REQUEST_TIMEOUT_MILLIS);
            status = resp.statusCode();
            responseBody = resp.body();
        } catch (InterruptedException e) {
            // 被 close() 打断:保留 firstInFlight,由 close() 决定是否注销
            Thread.currentThread().interrupt();
            return heartbeatSeconds;
        } catch (IOException | RuntimeException e) {
            requestFinished();
            return onFailure("连接团队版失败: " + describe(e));
        }
        requestFinished();
        if (status >= 200 && status < 300) {
            heartbeatSeconds = parseHeartbeat(responseBody);
            if (failures > 0 || authWarned) {
                LOG.info("csap-apidoc 团队版注册已恢复(service={}, instanceId={})", reg.service, reg.instanceId);
            } else if (!registered) {
                LOG.info("csap-apidoc 已向团队版注册(service={}, instanceId={}, baseUrl={}, 心跳 {} 秒)",
                        reg.service, reg.instanceId, reg.baseUrl, heartbeatSeconds);
            }
            failures = 0;
            authWarned = false;
            registered = true;
            return heartbeatSeconds;
        }
        if (status == 401 || status == 403) {
            if (!authWarned) {
                authWarned = true;
                LOG.warn("csap-apidoc 团队版注册被拒绝(HTTP {}):令牌无效或权限不足,之后每 {} 秒重试一次",
                        status, AUTH_RETRY_SECONDS);
            }
            failures = Math.max(failures, 1);
            return AUTH_RETRY_SECONDS;
        }
        return onFailure("团队版返回 HTTP " + status);
    }

    private long onFailure(String message) {
        failures++;
        if (failures == 1 || failures % WARN_EVERY == 0) {
            LOG.warn("csap-apidoc 团队版注册失败(已连续 {} 次): {};按 {} 秒周期重试,不影响应用运行",
                    failures, message, heartbeatSeconds);
        }
        return heartbeatSeconds;
    }

    private HttpResponse<String> post(String path, String body, long timeoutMillis)
            throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(reg.url + path))
                .timeout(Duration.ofMillis(timeoutMillis))
                .header("Authorization", "Bearer " + reg.token)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    /**
     * 解析响应里的 heartbeatSeconds,正整数时限制在 10–300(超出取边界),缺失 / 非整数 / 小于等于 0 时用 30。
     */
    static int parseHeartbeat(String body) {
        if (body == null || body.trim().isEmpty()) {
            return DEFAULT_HEARTBEAT_SECONDS;
        }
        try {
            JsonNode n = MAPPER.readTree(body).get("heartbeatSeconds");
            if (n == null || !n.isIntegralNumber()) {
                return DEFAULT_HEARTBEAT_SECONDS;
            }
            long v = n.asLong();
            if (v <= 0) {
                return DEFAULT_HEARTBEAT_SECONDS;
            }
            return (int) Math.max(MIN_HEARTBEAT_SECONDS, Math.min(MAX_HEARTBEAT_SECONDS, v));
        } catch (IOException | RuntimeException e) {
            return DEFAULT_HEARTBEAT_SECONDS;
        }
    }

    /**
     * 停止心跳,并发送一次注销(超时 2 秒,失败忽略)。
     */
    @Override
    public void close() {
        ScheduledExecutorService s;
        synchronized (this) {
            if (closed) {
                return;
            }
            closed = true;
            s = scheduler;
        }
        if (s == null) {
            return;
        }
        s.shutdownNow();
        try {
            // 在途请求会被中断,很快结束;等它把 registered 写好再判断
            s.awaitTermination(1, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        if (!registered && !firstInFlight) {
            return;
        }
        try {
            post(DEREGISTER_PATH, deregisterBody, DEREGISTER_TIMEOUT_MILLIS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (IOException | RuntimeException e) {
            LOG.debug("csap-apidoc 团队版注销失败(已忽略): {}", describe(e));
        }
    }

    /**
     * 异常的简短描述;兜底把令牌替换掉,保证不会出现在日志里。
     */
    private String describe(Throwable e) {
        String m = e.getMessage();
        String text = e.getClass().getSimpleName() + (m == null ? "" : ": " + m);
        if (reg.token != null && !reg.token.isEmpty()) {
            text = text.replace(reg.token, "***");
        }
        return text;
    }

    private static String toJson(Map<String, Object> body) {
        try {
            return MAPPER.writeValueAsString(body);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    public static String stripTrailingSlash(String s) {
        if (s == null) {
            return "";
        }
        String r = s.trim();
        while (r.endsWith("/")) {
            r = r.substring(0, r.length() - 1);
        }
        return r;
    }
}
