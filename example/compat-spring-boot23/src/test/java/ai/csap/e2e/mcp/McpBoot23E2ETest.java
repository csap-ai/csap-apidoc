package ai.csap.e2e.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringBootVersion;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.SpringVersion;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Spring Boot 2.3 / Spring 5.2 上的 MCP 端到端:initialize → tools/list → tools/call,返回字段过滤对 MCP 生效。
 * Controller、模型和 {@link McpClient} 复用 example-spring-boot2 的测试源码。
 * Boot 2.3 没有 {@code org.springframework.boot.test.web.server.LocalServerPort},端口用 {@code local.server.port} 取。
 */
@SpringBootTest(classes = McpE2EApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"csap.apidoc.mcp.enabled=true", "csap.apidoc.mcp.store=memory",
                "csap.apidoc.mcp.auth.tokens[0].name=e2e", "csap.apidoc.mcp.auth.tokens[0].value=e2e-test-token",
                "csap.apidoc.devtool.enabled=true", "csap.apidoc.runtime.filter.enabled=true"})
class McpBoot23E2ETest {

    @Value("${local.server.port}")
    int port;

    @Test
    void runsOnSpringBoot23() {
        assertTrue(SpringBootVersion.getVersion().startsWith("2.3."), SpringBootVersion.getVersion());
        assertTrue(SpringVersion.getVersion().startsWith("5.2."), SpringVersion.getVersion());
    }

    @Test
    void initializeListAndCall() throws Exception {
        McpClient client = new McpClient(port);
        assertEquals(200, client.get("/csap/apidoc").statusCode());

        client.token = null;
        assertEquals(401, client.rpcRaw("tools/list", null).statusCode());
        client.token = "e2e-test-token";

        JsonNode init = client.rpc("initialize", "{\"protocolVersion\":\"2025-06-18\",\"capabilities\":{},"
                + "\"clientInfo\":{\"name\":\"boot23\",\"version\":\"1\"}}");
        assertEquals("2025-06-18", init.at("/result/protocolVersion").asText(), init.toString());
        client.protocolVersion = "2025-06-18";
        assertEquals(202, client.post("/csap/mcp", "{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}",
                "Authorization", "Bearer e2e-test-token", "MCP-Protocol-Version", "2025-06-18").statusCode());

        JsonNode list = client.rpc("tools/list", null);
        List<String> names = new ArrayList<>();
        list.at("/result/tools").forEach(t -> names.add(t.get("name").asText()));
        assertEquals(List.of("McpOrder_get", "McpOrder_whoami"), names, list.toString());
        assertEquals("integer", list.at("/result/tools/0/inputSchema/properties/id/type").asText(), list.toString());

        JsonNode res = client.callTool("McpOrder_get", "{\"id\":7,\"X-Tenant\":\"t1\",\"lang\":\"zh\"}");
        assertFalse(res.at("/result/isError").asBoolean(), res.toString());
        JsonNode order = McpClient.JSON.readTree(res.at("/result/content/0/text").asText());
        assertEquals(7, order.get("id").asInt());
        assertEquals("t1", order.get("tenant").asText());
        assertEquals("zh", order.get("lang").asText());
        // 返回字段过滤(规则只放行 id / no / tenant / lang)
        assertFalse(order.has("secret"), order.toString());
        assertFalse(order.has("password"), order.toString());

        // 2026-07-28 无状态请求
        String meta = "\"_meta\":{\"io.modelcontextprotocol/protocolVersion\":\"2026-07-28\"}";
        client.protocolVersion = null;
        String body = client.rpcRaw("tools/call", "{\"name\":\"McpOrder_get\",\"arguments\":{\"id\":3}," + meta + "}",
                "MCP-Protocol-Version", "2026-07-28", "Mcp-Method", "tools/call", "Mcp-Name", "McpOrder_get").body();
        assertTrue(body.contains("NO-3"), body);
    }
}
