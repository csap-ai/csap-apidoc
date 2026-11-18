package ai.csap.e2e.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * MCP 端到端:initialize → tools/list → tools/call;鉴权失败 401;写操作默认不暴露;返回字段过滤生效;审计脱敏;
 * 透传模式;2026-07-28 无状态请求。
 */
@SpringBootTest(classes = McpE2EApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"csap.apidoc.mcp.enabled=true", "csap.apidoc.mcp.store=memory",
                "csap.apidoc.mcp.auth.tokens[0].name=e2e", "csap.apidoc.mcp.auth.tokens[0].value=e2e-test-token",
                "csap.apidoc.devtool.enabled=true", "csap.apidoc.runtime.filter.enabled=true"})
class McpE2ETest {

    static final String KEY = McpOrderController.class.getName() + ".";

    @LocalServerPort
    int port;

    McpClient client;

    @BeforeEach
    void setUp() throws Exception {
        client = new McpClient(port);
        // 等启动扫描完成
        assertEquals(200, client.get("/csap/apidoc").statusCode());
        // 恢复默认设置(上下文在测试之间共享)
        client.post("/devtools/mcp/auth", "{}");
        client.post("/devtools/mcp/tools", "[{\"methodKey\":\"" + KEY + "create\"},{\"methodKey\":\"" + KEY + "get\"}]");
        client.token = "e2e-test-token";
    }

    List<String> toolNames(JsonNode list) {
        List<String> names = new ArrayList<>();
        list.at("/result/tools").forEach(t -> names.add(t.get("name").asText()));
        return names;
    }

    @Test
    void rejectsWithoutValidToken() throws Exception {
        client.token = null;
        HttpResponse<String> r = client.rpcRaw("tools/list", null);
        assertEquals(401, r.statusCode(), r.body());
        assertTrue(r.headers().firstValue("WWW-Authenticate").orElse("").startsWith("Bearer"));
        client.token = "wrong";
        assertEquals(401, client.rpcRaw("tools/list", null).statusCode());
        // GET 不提供 SSE 流
        assertEquals(405, client.get("/csap/mcp").statusCode());
    }

    @Test
    void handshakeListAndCallWithFiltering() throws Exception {
        JsonNode init = client.rpc("initialize", "{\"protocolVersion\":\"2025-06-18\",\"capabilities\":{},"
                + "\"clientInfo\":{\"name\":\"e2e\",\"version\":\"1\"}}");
        assertEquals("2025-06-18", init.at("/result/protocolVersion").asText());
        assertEquals("csap-apidoc", init.at("/result/serverInfo/name").asText());
        client.protocolVersion = "2025-06-18";
        assertEquals(202, client.post("/csap/mcp", "{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}",
                "Authorization", "Bearer e2e-test-token", "MCP-Protocol-Version", "2025-06-18").statusCode());

        JsonNode list = client.rpc("tools/list", null);
        // 默认只暴露 GET
        assertEquals(List.of("McpOrder_get", "McpOrder_whoami"), toolNames(list));
        JsonNode get = list.at("/result/tools/0");
        assertTrue(get.get("description").asText().contains("HTTP GET /e2e/mcp/orders/{id}"), get.toString());
        assertEquals("integer", get.at("/inputSchema/properties/id/type").asText(), get.toString());
        assertTrue(get.at("/inputSchema/properties/X-Tenant").isObject(), get.toString());
        assertTrue(get.at("/inputSchema/properties/lang").isObject(), get.toString());
        assertEquals("id", get.at("/inputSchema/required/0").asText());
        assertTrue(get.at("/annotations/readOnlyHint").asBoolean());

        JsonNode res = client.callTool("McpOrder_get", "{\"id\":7,\"X-Tenant\":\"t1\",\"lang\":\"zh\"}");
        assertFalse(res.at("/result/isError").asBoolean(), res.toString());
        JsonNode order = McpClient.JSON.readTree(res.at("/result/content/0/text").asText());
        assertEquals(7, order.get("id").asInt());
        assertEquals("NO-7", order.get("no").asText());
        assertEquals("t1", order.get("tenant").asText());
        assertEquals("zh", order.get("lang").asText());
        // 返回字段过滤(1.2.0 规则)对 MCP 调用同样生效
        assertFalse(order.has("secret"), order.toString());
        assertFalse(order.has("password"), order.toString());

        // 缺必填参数:工具错误,模型可以自行纠正
        JsonNode missing = client.callTool("McpOrder_get", "{}");
        assertTrue(missing.at("/result/isError").asBoolean(), missing.toString());
    }

    @Test
    void writeToolsNeedToBeEnabledOneByOne() throws Exception {
        JsonNode denied = client.callTool("McpOrder_create", "{\"body\":{\"no\":\"N1\"}}");
        assertEquals(-32602, denied.at("/error/code").asInt(), denied.toString());
        assertEquals(-32602, client.callTool("McpOrder_delete", "{\"id\":1}").at("/error/code").asInt());

        HttpResponse<String> save = client.post("/devtools/mcp/tools",
                "[{\"methodKey\":\"" + KEY + "create\",\"enabled\":true,\"name\":\"create_order\",\"description\":\"新建订单\"}]");
        assertTrue(save.body().contains("\"success\":true"), save.body());
        JsonNode list = client.rpc("tools/list", null);
        assertEquals(List.of("McpOrder_get", "McpOrder_whoami", "create_order"), toolNames(list));
        assertFalse(list.at("/result/tools/2/annotations/readOnlyHint").asBoolean(true));

        JsonNode created = client.callTool("create_order", "{\"body\":{\"no\":\"N1\",\"password\":\"p@ss-123\"}}");
        assertFalse(created.at("/result/isError").asBoolean(), created.toString());
        assertTrue(created.at("/result/content/0/text").asText().contains("\"no\":\"N1\""), created.toString());

        // 审计:调用方是令牌名,参数里的密码打码
        JsonNode audit = McpClient.JSON.readTree(client.get("/devtools/mcp/audit?tool=create_order").body());
        JsonNode entry = audit.at("/data/0");
        assertEquals("e2e", entry.get("caller").asText(), audit.toString());
        assertEquals(200, entry.get("status").asInt());
        assertTrue(entry.get("target").asText().startsWith("POST /e2e/mcp/orders"), entry.toString());
        assertTrue(entry.get("arguments").asText().contains("\"password\":\"***\""), entry.toString());
        assertFalse(audit.toString().contains("p@ss-123"), audit.toString());

        // 非法工具名不保存
        HttpResponse<String> bad = client.post("/devtools/mcp/tools",
                "[{\"methodKey\":\"" + KEY + "create\",\"name\":\"bad name\"}]");
        assertTrue(bad.body().contains("\"success\":false"), bad.body());
    }

    @Test
    void passthroughForwardsCallerHeaders() throws Exception {
        // token 模式:MCP 的 Authorization 是 MCP 令牌,不转发
        JsonNode r = client.callTool("McpOrder_whoami", "{}");
        assertEquals("{\"authorization\":\"none\"}", r.at("/result/content/0/text").asText());

        assertTrue(client.post("/devtools/mcp/auth", "{\"mode\":\"passthrough\"}").body().contains("\"success\":true"));
        client.token = "user-token";
        r = client.callTool("McpOrder_whoami", "{}");
        assertEquals("{\"authorization\":\"Bearer user-token\"}", r.at("/result/content/0/text").asText(), r.toString());
        JsonNode audit = McpClient.JSON.readTree(client.get("/devtools/mcp/audit?tool=McpOrder_whoami&limit=1").body());
        assertEquals("127.0.0.1", audit.at("/data/0/caller").asText(), audit.toString());
        JsonNode status = McpClient.JSON.readTree(client.get("/devtools/mcp/status").body());
        assertEquals("passthrough", status.at("/data/authMode").asText());
        assertEquals("e2e", status.at("/data/tokenNames/0").asText());
        assertFalse(status.toString().contains("e2e-test-token"), status.toString());
    }

    @Test
    void modernStatelessRequests() throws Exception {
        String meta = "\"_meta\":{\"io.modelcontextprotocol/protocolVersion\":\"2026-07-28\"}";
        HttpResponse<String> r = client.rpcRaw("tools/list", "{" + meta + "}",
                "MCP-Protocol-Version", "2026-07-28", "Mcp-Method", "tools/list");
        assertEquals(200, r.statusCode(), r.body());
        JsonNode list = McpClient.JSON.readTree(r.body());
        assertEquals("complete", list.at("/result/resultType").asText());
        assertEquals(List.of("McpOrder_get", "McpOrder_whoami"), toolNames(list));
        r = client.rpcRaw("tools/call", "{\"name\":\"McpOrder_get\",\"arguments\":{\"id\":3}," + meta + "}",
                "MCP-Protocol-Version", "2026-07-28", "Mcp-Method", "tools/call", "Mcp-Name", "McpOrder_get");
        assertEquals(200, r.statusCode(), r.body());
        assertTrue(r.body().contains("NO-3"), r.body());
        // 头与请求体不一致
        r = client.rpcRaw("tools/call", "{\"name\":\"McpOrder_get\",\"arguments\":{\"id\":3}," + meta + "}",
                "MCP-Protocol-Version", "2026-07-28", "Mcp-Method", "tools/call", "Mcp-Name", "other");
        assertEquals(400, r.statusCode());
        assertNotNull(McpClient.JSON.readTree(r.body()).get("error"));
    }
}
