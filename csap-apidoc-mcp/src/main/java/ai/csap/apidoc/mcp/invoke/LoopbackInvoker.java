package ai.csap.apidoc.mcp.invoke;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.web.util.UriUtils;

import ai.csap.apidoc.mcp.protocol.JsonRpc;
import ai.csap.apidoc.mcp.tool.McpTool;
import ai.csap.apidoc.mcp.tool.ParamBinding;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * 本机回环 HTTP 调用:把工具参数还原成对本服务接口的一次真实 HTTP 请求。
 *
 * <p>选择回环而不是 DispatcherServlet 内部分派:不依赖 javax / jakarta Servlet(Boot 2 / 3 一个 jar),
 * 并且完整经过 Servlet Filter(Spring Security 等),返回字段过滤也自然生效。
 *
 * @author ycf
 * @since 1.3.0
 */
public class LoopbackInvoker {

    /**
     * 回环请求上标记工具名的请求头
     */
    public static final String TOOL_HEADER = "X-Csap-Mcp-Tool";

    /**
     * JDK HttpClient 不允许设置的头,以及逐跳头
     */
    static final Set<String> RESTRICTED = new HashSet<>(Arrays.asList("connection", "content-length", "expect",
            "host", "upgrade", "keep-alive", "proxy-connection", "te", "trailer", "transfer-encoding",
            "http2-settings", "accept-encoding"));

    private static final Pattern VAR = Pattern.compile("\\{\\*?([^}:]+)(?::[^}]*)?}");

    private final HttpClient client;
    private final Supplier<String> baseUrl;
    private final Duration timeout;

    public LoopbackInvoker(Supplier<String> baseUrl, Duration timeout) {
        this.baseUrl = baseUrl;
        this.timeout = timeout == null ? Duration.ofSeconds(30) : timeout;
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NEVER)
                .version(HttpClient.Version.HTTP_1_1)
                .build();
    }

    /**
     * 调用结果
     */
    public static class Result {
        private final int status;
        private final String body;
        private final String url;

        public Result(int status, String body, String url) {
            this.status = status;
            this.body = body;
            this.url = url;
        }

        public int getStatus() {
            return status;
        }

        public String getBody() {
            return body;
        }

        public String getUrl() {
            return url;
        }
    }

    /**
     * 参数不合法(缺必填等),作为工具错误返回给模型
     */
    public static class InvalidArgumentsException extends RuntimeException {
        public InvalidArgumentsException(String message) {
            super(message);
        }
    }

    /**
     * 拼出请求(路径、查询串)但不发送,用于审计和测试
     *
     * @param tool 工具
     * @param args 参数
     * @return 相对路径(含查询串)
     */
    public String buildPathAndQuery(McpTool tool, JsonNode args) {
        String path = tool.getPathPattern();
        Map<String, String> pathValues = new LinkedHashMap<>();
        StringBuilder query = new StringBuilder();
        for (ParamBinding b : tool.getBindings()) {
            JsonNode v = args.get(b.getProperty());
            boolean missing = v == null || v.isNull() || (v.isTextual() && v.asText().isEmpty()
                    && b.getLocation() == ParamBinding.Location.PATH);
            if (missing) {
                if (b.isRequired()) {
                    throw new InvalidArgumentsException("缺少必填参数: " + b.getProperty());
                }
                continue;
            }
            if (b.getLocation() == ParamBinding.Location.PATH) {
                pathValues.put(b.getHttpName(), scalar(v));
            } else if (b.getLocation() == ParamBinding.Location.QUERY) {
                if (v.isArray()) {
                    for (JsonNode item : v) {
                        appendQuery(query, b.getHttpName(), scalar(item));
                    }
                } else {
                    appendQuery(query, b.getHttpName(), scalar(v));
                }
            }
        }
        Matcher m = VAR.matcher(path);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String name = m.group(1);
            String value = pathValues.get(name);
            if (value == null) {
                throw new InvalidArgumentsException("缺少路径参数: " + name);
            }
            // 编码 "/"、".." 等,参数值不能改变路径层级
            m.appendReplacement(sb, Matcher.quoteReplacement(encodeSegment(value)));
        }
        m.appendTail(sb);
        String p = sb.toString();
        if (!p.startsWith("/")) {
            p = "/" + p;
        }
        return query.length() == 0 ? p : p + "?" + query;
    }

    /**
     * 发送请求
     *
     * @param tool         工具
     * @param args         参数
     * @param extraHeaders 转发头与附加头
     * @return 结果
     * @throws Exception 网络错误、超时
     */
    public Result invoke(McpTool tool, JsonNode args, Map<String, String> extraHeaders) throws Exception {
        String pathAndQuery = buildPathAndQuery(tool, args);
        String base = baseUrl.get();
        if (base == null) {
            throw new IllegalStateException("本服务端口未知(服务尚未启动完成),请配置 csap.apidoc.mcp.base-url");
        }
        String url = stripTrailingSlash(base) + pathAndQuery;
        HttpRequest.Builder rb = HttpRequest.newBuilder(URI.create(url)).timeout(timeout);
        Map<String, String> headers = new LinkedHashMap<>();
        if (extraHeaders != null) {
            headers.putAll(extraHeaders);
        }
        String body = null;
        for (ParamBinding b : tool.getBindings()) {
            JsonNode v = args.get(b.getProperty());
            if (v == null || v.isNull()) {
                continue;
            }
            if (b.getLocation() == ParamBinding.Location.HEADER) {
                headers.put(b.getHttpName(), scalar(v));
            } else if (b.getLocation() == ParamBinding.Location.BODY) {
                body = JsonRpc.MAPPER.writeValueAsString(v);
            }
        }
        headers.put(TOOL_HEADER, tool.getName());
        headers.putIfAbsent("Accept", "application/json, */*");
        for (Map.Entry<String, String> h : headers.entrySet()) {
            String name = h.getKey();
            if (name == null || h.getValue() == null || RESTRICTED.contains(name.toLowerCase(Locale.ROOT))
                    || "content-type".equalsIgnoreCase(name)) {
                continue;
            }
            if (h.getValue().indexOf('\r') >= 0 || h.getValue().indexOf('\n') >= 0) {
                continue;
            }
            rb.header(name, h.getValue());
        }
        if (body != null) {
            rb.header("Content-Type", "application/json");
            rb.method(tool.getHttpMethod(), HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
        } else {
            rb.method(tool.getHttpMethod(), HttpRequest.BodyPublishers.noBody());
        }
        HttpResponse<byte[]> resp = client.send(rb.build(), HttpResponse.BodyHandlers.ofByteArray());
        Charset cs = charset(resp.headers().firstValue("Content-Type").orElse(null));
        return new Result(resp.statusCode(), new String(resp.body(), cs), url);
    }

    /**
     * 路径段编码;"." 与 ".." 也编码,避免被规范化成上级路径
     */
    static String encodeSegment(String value) {
        String e = UriUtils.encodePathSegment(value, StandardCharsets.UTF_8);
        return ".".equals(value) || "..".equals(value) ? e.replace(".", "%2E") : e;
    }

    static Charset charset(String contentType) {
        if (contentType != null) {
            for (String part : contentType.split(";")) {
                String p = part.trim();
                if (p.toLowerCase(Locale.ROOT).startsWith("charset=")) {
                    try {
                        return Charset.forName(p.substring(8).replace("\"", "").trim());
                    } catch (Exception ignore) {
                        return StandardCharsets.UTF_8;
                    }
                }
            }
        }
        return StandardCharsets.UTF_8;
    }

    private static void appendQuery(StringBuilder q, String name, String value) {
        if (q.length() > 0) {
            q.append('&');
        }
        q.append(URLEncoder.encode(name, StandardCharsets.UTF_8).replace("+", "%20"))
                .append('=')
                .append(URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20"));
    }

    private static String scalar(JsonNode v) {
        if (v.isValueNode()) {
            return v.asText();
        }
        return JsonRpc.write(v);
    }

    private static String stripTrailingSlash(String s) {
        return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
    }
}
