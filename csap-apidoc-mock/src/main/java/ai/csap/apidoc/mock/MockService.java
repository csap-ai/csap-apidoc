package ai.csap.apidoc.mock;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

import ai.csap.apidoc.model.CsapDocMethod;
import ai.csap.apidoc.model.CsapDocResponse;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Mock 请求处理:匹配接口 → 生成数据 → 组装响应。不依赖 Servlet。
 *
 * @author ycf
 * @since 1.4.0
 */
public class MockService {

    public static final String HEADER_MOCK = "X-CSAP-Mock";
    public static final String HEADER_OPERATION = "X-CSAP-Mock-Operation";
    public static final String HEADER_SEED = "X-CSAP-Mock-Seed";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final MockProperties properties;
    private final Supplier<CsapDocResponse> docs;
    private volatile MockMatcher matcher;

    public MockService(MockProperties properties, Supplier<CsapDocResponse> docs) {
        this.properties = properties;
        this.docs = docs;
    }

    /** 文档刷新后丢弃匹配索引 */
    public void onDocumentRefreshed() {
        matcher = null;
    }

    /** 响应 */
    public static final class Reply {
        private final int status;
        private final Map<String, String> headers = new LinkedHashMap<>();
        private final String body;

        Reply(int status, String body) {
            this.status = status;
            this.body = body;
        }

        public int getStatus() {
            return status;
        }

        public Map<String, String> getHeaders() {
            return headers;
        }

        /** 响应体;null 表示空 body */
        public String getBody() {
            return body;
        }
    }

    /**
     * 处理一次 Mock 请求
     *
     * @param verb         HTTP 方法
     * @param path         去掉 /csap/mock 前缀后的路径
     * @param seedOverride 本次请求指定的种子,可为 null
     * @return 响应
     */
    public Reply handle(String verb, String path, String seedOverride) {
        MockMatcher m = matcher;
        if (m == null) {
            m = new MockMatcher(docs.get());
            matcher = m;
        }
        String v = verb == null ? "GET" : verb.toUpperCase(java.util.Locale.ROOT);
        MockMatcher.Match match = m.match(v, path);
        if (match.getMethod() == null) {
            if (match.isPathFound()) {
                Reply r = error(405, "MOCK_METHOD_NOT_ALLOWED", "method " + v + " not allowed for " + path);
                r.getHeaders().put("Allow", String.join(", ", match.getAllowedMethods()));
                return r;
            }
            return error(404, "MOCK_NOT_FOUND", "no documented endpoint for " + v + " " + path);
        }
        sleep(properties.getDelayMs());
        String seed = seed(seedOverride);
        CsapDocMethod method = match.getMethod();
        JsonNode body = new MockGenerator(seed, properties.getArraySize(), properties.getMaxDepth(),
                properties.isHeuristics()).generate(method);
        Reply r = new Reply(200, body == null ? null : write(body));
        r.getHeaders().put(HEADER_MOCK, "1");
        r.getHeaders().put(HEADER_OPERATION, method.getKey());
        r.getHeaders().put(HEADER_SEED, seed);
        return r;
    }

    private String seed(String override) {
        String s = override != null && !override.isEmpty() ? override : properties.getSeed();
        if (s == null || s.isEmpty()) {
            return "csap";
        }
        return MockProperties.RANDOM.equals(s) ? UUID.randomUUID().toString() : s;
    }

    private static Reply error(int status, String code, String message) {
        Map<String, String> body = new LinkedHashMap<>();
        body.put("code", code);
        body.put("message", message);
        Reply r = new Reply(status, write(body));
        r.getHeaders().put(HEADER_MOCK, "1");
        return r;
    }

    private static void sleep(long ms) {
        if (ms > 0) {
            try {
                Thread.sleep(ms);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private static String write(Object o) {
        try {
            return new String(MAPPER.writeValueAsBytes(o), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
