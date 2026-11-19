package ai.csap.apidoc.team.support;

import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/** 测试专用 HTTP 客户端:带 Cookie,非 GET 自动带 X-CSAP-Team。 */
public class Api {

    public static final ObjectMapper MAPPER = new ObjectMapper();

    private final String base;
    private final HttpClient client;
    private final String bearer;

    public Api(int port) {
        this(port, null);
    }

    private Api(int port, String bearer) {
        this.base = "http://127.0.0.1:" + port;
        this.bearer = bearer;
        this.client = HttpClient.newBuilder().cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL))
                .connectTimeout(Duration.ofSeconds(10)).build();
    }

    public static Api bearer(int port, String token) {
        return new Api(port, token);
    }

    public record Resp(int status, String text, java.net.http.HttpHeaders headers) {
        public JsonNode json() {
            try {
                return text == null || text.isEmpty() ? null : MAPPER.readTree(text);
            } catch (Exception e) {
                throw new IllegalStateException("响应不是 JSON: " + text, e);
            }
        }

        public String code() {
            return json().path("code").asText();
        }

        public String header(String name) {
            return headers.firstValue(name).orElse(null);
        }
    }

    public Resp get(String path) {
        return send("GET", path, null, true);
    }

    public Resp post(String path, Object body) {
        return send("POST", path, body, true);
    }

    public Resp put(String path, Object body) {
        return send("PUT", path, body, true);
    }

    public Resp patch(String path, Object body) {
        return send("PATCH", path, body, true);
    }

    public Resp delete(String path) {
        return send("DELETE", path, null, true);
    }

    public Resp sendNoCsrf(String method, String path, Object body) {
        return send(method, path, body, false);
    }

    public Resp send(String method, String path, Object body, boolean csrf) {
        try {
            HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(base + path)).timeout(Duration.ofSeconds(60));
            if (csrf && !"GET".equals(method)) {
                b.header("X-CSAP-Team", "1");
            }
            if (bearer != null) {
                b.header("Authorization", "Bearer " + bearer);
            }
            b.header("Content-Type", "application/json");
            HttpRequest.BodyPublisher pub = body == null ? HttpRequest.BodyPublishers.noBody()
                    : HttpRequest.BodyPublishers.ofString(body instanceof String s ? s : MAPPER.writeValueAsString(body),
                            StandardCharsets.UTF_8);
            HttpResponse<String> res = client.send(b.method(method, pub).build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            return new Resp(res.statusCode(), res.body(), res.headers());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
