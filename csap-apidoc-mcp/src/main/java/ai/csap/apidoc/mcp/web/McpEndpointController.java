package ai.csap.apidoc.mcp.web;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import ai.csap.apidoc.mcp.McpService;
import ai.csap.apidoc.mcp.protocol.JsonRpc;
import ai.csap.apidoc.mcp.protocol.McpCallContext;
import ai.csap.apidoc.mcp.protocol.McpHttpResponse;
import ai.csap.apidoc.mcp.protocol.McpServer;
import ai.csap.apidoc.mcp.security.McpAuthenticator;

/**
 * MCP Streamable HTTP 端点(默认 /csap/mcp)。只在 csap.apidoc.mcp.enabled=true 时注册。
 *
 * <p>由自动配置以 Bean 注册;类上的条件保证即使被应用的组件扫描到,未开启时也不会加载。
 *
 * @author ycf
 * @since 1.3.0
 */
@ConditionalOnProperty(prefix = "csap.apidoc.mcp", name = "enabled", havingValue = "true")
@RestController
@RequestMapping("${csap.apidoc.mcp.path:/csap/mcp}")
public class McpEndpointController {

    private final McpServer server;
    private final McpService service;
    private final List<String> allowedOrigins;

    public McpEndpointController(McpServer server, McpService service, List<String> allowedOrigins) {
        this.server = server;
        this.service = service;
        this.allowedOrigins = allowedOrigins;
    }

    @RequestMapping(method = RequestMethod.POST)
    public ResponseEntity<byte[]> post(@RequestBody(required = false) byte[] body,
                                       @RequestHeader HttpHeaders headers) {
        String ip = RemoteAddress.current();
        String origin = headers.getFirst(HttpHeaders.ORIGIN);
        if (origin != null && !originAllowed(origin)) {
            service.recordRejected(ip == null ? "unknown" : ip, ip, 403, "Origin 不在 allowed-origins 中: " + origin);
            return json(403, JsonRpc.write(JsonRpc.error(null, JsonRpc.INVALID_REQUEST,
                    "Forbidden origin")), null);
        }
        McpAuthenticator.Result auth = service.authenticate(headers.getFirst(HttpHeaders.AUTHORIZATION), ip);
        if (!auth.isOk()) {
            service.recordRejected(auth.getCaller(), ip, 401, auth.getReason());
            HttpHeaders h = new HttpHeaders();
            h.set(HttpHeaders.WWW_AUTHENTICATE, "Bearer realm=\"csap-mcp\"");
            return json(401, JsonRpc.write(JsonRpc.error(null, JsonRpc.UNAUTHORIZED,
                    "Unauthorized: " + auth.getReason())), h);
        }
        Map<String, String> flat = new LinkedHashMap<>();
        headers.forEach((k, v) -> {
            if (v != null && !v.isEmpty()) {
                flat.put(k, String.join(", ", v));
            }
        });
        McpCallContext ctx = new McpCallContext(flat, auth.getCaller(), ip);
        McpHttpResponse resp = server.handle(body == null ? null : new String(body, StandardCharsets.UTF_8), ctx);
        if (resp.getBody() == null) {
            return ResponseEntity.status(resp.getStatus()).build();
        }
        return json(resp.getStatus(), resp.getBody(), null);
    }

    /**
     * 不提供 SSE 流,也没有会话可删除
     */
    @RequestMapping(method = {RequestMethod.GET, RequestMethod.DELETE})
    public ResponseEntity<byte[]> notAllowed() {
        HttpHeaders h = new HttpHeaders();
        h.set(HttpHeaders.ALLOW, "POST");
        return ResponseEntity.status(405).headers(h).build();
    }

    private boolean originAllowed(String origin) {
        if (allowedOrigins == null) {
            return false;
        }
        for (String o : allowedOrigins) {
            if ("*".equals(o) || origin.equalsIgnoreCase(o)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 状态码一律用 int:ResponseEntity.status(HttpStatus) 在 Spring 6 改成了 HttpStatusCode,二进制不兼容。
     * 直接写字节,不经过应用的 JSON 消息转换器(避免被 fastjson / 全局包装等改写)
     */
    private static ResponseEntity<byte[]> json(int status, String body, HttpHeaders extra) {
        HttpHeaders h = new HttpHeaders();
        if (extra != null) {
            h.putAll(extra);
        }
        h.setContentType(new MediaType("application", "json", StandardCharsets.UTF_8));
        return ResponseEntity.status(status).headers(h).body(body.getBytes(StandardCharsets.UTF_8));
    }
}
