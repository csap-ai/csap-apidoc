package ai.csap.apidoc.mcp.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

class McpServerTest {

    final List<String> calls = new ArrayList<>();

    final McpToolHandler handler = new McpToolHandler() {
        @Override
        public List<ObjectNode> listTools() {
            ObjectNode t = JsonRpc.NODES.objectNode();
            t.put("name", "Order_get");
            t.put("description", "查询订单");
            t.putObject("inputSchema").put("type", "object");
            return Collections.singletonList(t);
        }

        @Override
        public ToolResult callTool(String name, JsonNode arguments, McpCallContext context) {
            if (!"Order_get".equals(name) && !"订单".equals(name)) {
                throw new McpException(JsonRpc.INVALID_PARAMS, "Unknown tool: " + name);
            }
            calls.add(name + arguments);
            return new ToolResult("{\"id\":1}", false);
        }
    };

    final McpServer server = new McpServer(handler, "csap-apidoc", "test", () -> "演示服务", () -> "说明");

    JsonNode call(String body, Map<String, String> headers, int expectStatus) throws Exception {
        McpHttpResponse r = server.handle(body, McpCallContext.of(headers));
        assertEquals(expectStatus, r.getStatus(), r.getBody());
        return r.getBody() == null ? null : JsonRpc.MAPPER.readTree(r.getBody());
    }

    static Map<String, String> h(String... kv) {
        Map<String, String> m = new HashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put(kv[i], kv[i + 1]);
        }
        return m;
    }

    @Test
    void legacyInitializeEchoesSupportedVersion() throws Exception {
        for (String v : new String[]{"2025-03-26", "2025-06-18", "2025-11-25"}) {
            JsonNode r = call("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{\"protocolVersion\":\""
                    + v + "\",\"capabilities\":{},\"clientInfo\":{\"name\":\"t\",\"version\":\"1\"}}}", h(), 200);
            assertEquals(v, r.at("/result/protocolVersion").asText());
            assertEquals("csap-apidoc", r.at("/result/serverInfo/name").asText());
            assertEquals("演示服务", r.at("/result/serverInfo/title").asText());
            assertFalse(r.at("/result/capabilities/tools/listChanged").asBoolean(true));
            assertEquals("说明", r.at("/result/instructions").asText());
        }
    }

    @Test
    void legacyInitializeFallsBackToLatestLegacy() throws Exception {
        JsonNode r = call("{\"jsonrpc\":\"2.0\",\"id\":\"a\",\"method\":\"initialize\",\"params\":{\"protocolVersion\":\"2024-11-05\"}}",
                h(), 200);
        assertEquals("2025-11-25", r.at("/result/protocolVersion").asText());
        assertEquals("a", r.get("id").asText());
    }

    @Test
    void legacyFlow() throws Exception {
        assertNull(call("{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}", h(), 202));
        JsonNode ping = call("{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"ping\"}", h("MCP-Protocol-Version", "2025-06-18"), 200);
        assertTrue(ping.get("result").isObject());
        JsonNode list = call("{\"jsonrpc\":\"2.0\",\"id\":3,\"method\":\"tools/list\"}", h("MCP-Protocol-Version", "2025-06-18"), 200);
        assertEquals("Order_get", list.at("/result/tools/0/name").asText());
        JsonNode res = call("{\"jsonrpc\":\"2.0\",\"id\":4,\"method\":\"tools/call\",\"params\":{\"name\":\"Order_get\",\"arguments\":{\"id\":1}}}",
                h(), 200);
        assertEquals("text", res.at("/result/content/0/type").asText());
        assertEquals("{\"id\":1}", res.at("/result/content/0/text").asText());
        assertFalse(res.at("/result/isError").asBoolean(true));
        assertEquals(Collections.singletonList("Order_get{\"id\":1}"), calls);
    }

    @Test
    void errors() throws Exception {
        assertEquals(JsonRpc.PARSE_ERROR, call("{oops", h(), 400).at("/error/code").asInt());
        assertEquals(JsonRpc.INVALID_REQUEST, call("{\"jsonrpc\":\"1.0\",\"id\":1,\"method\":\"ping\"}", h(), 400).at("/error/code").asInt());
        assertEquals(JsonRpc.METHOD_NOT_FOUND, call("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"nope\"}", h(), 200).at("/error/code").asInt());
        assertEquals(JsonRpc.INVALID_PARAMS, call("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/call\",\"params\":{\"name\":\"x\"}}", h(), 200)
                .at("/error/code").asInt());
        assertEquals(JsonRpc.INVALID_PARAMS, call("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/call\",\"params\":{\"name\":\"Order_get\",\"arguments\":[1]}}", h(), 200)
                .at("/error/code").asInt());
        JsonNode bad = call("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/list\"}", h("MCP-Protocol-Version", "1999-01-01"), 400);
        assertEquals(JsonRpc.UNSUPPORTED_PROTOCOL_VERSION, bad.at("/error/code").asInt());
        assertEquals("2026-07-28", bad.at("/error/data/supported/0").asText());
        assertTrue(calls.isEmpty());
    }

    @Test
    void batchForOldClients() throws Exception {
        JsonNode r = call("[{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"ping\"},{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"},"
                + "{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/list\"}]", h(), 200);
        assertEquals(2, r.size());
        assertNull(call("[{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}]", h(), 202));
    }

    static final String META = "\"_meta\":{\"io.modelcontextprotocol/protocolVersion\":\"2026-07-28\","
            + "\"io.modelcontextprotocol/clientInfo\":{\"name\":\"t\",\"version\":\"1\"},\"io.modelcontextprotocol/clientCapabilities\":{}}";

    @Test
    void modernStateless() throws Exception {
        JsonNode d = call("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"server/discover\",\"params\":{" + META + "}}",
                h("MCP-Protocol-Version", "2026-07-28", "Mcp-Method", "server/discover"), 200);
        assertEquals("complete", d.at("/result/resultType").asText());
        assertEquals("2026-07-28", d.at("/result/supportedVersions/0").asText());
        assertEquals("csap-apidoc", d.at("/result/_meta/io.modelcontextprotocol~1serverInfo/name").asText());
        JsonNode l = call("{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/list\",\"params\":{" + META + "}}",
                h("MCP-Protocol-Version", "2026-07-28", "Mcp-Method", "tools/list"), 200);
        assertEquals("Order_get", l.at("/result/tools/0/name").asText());
        assertEquals("private", l.at("/result/cacheScope").asText());
        assertTrue(l.at("/result/ttlMs").isNumber());
        JsonNode c = call("{\"jsonrpc\":\"2.0\",\"id\":3,\"method\":\"tools/call\",\"params\":{\"name\":\"Order_get\",\"arguments\":{}," + META + "}}",
                h("MCP-Protocol-Version", "2026-07-28", "Mcp-Method", "tools/call", "Mcp-Name", "Order_get"), 200);
        assertEquals("complete", c.at("/result/resultType").asText());
        assertEquals("{\"id\":1}", c.at("/result/content/0/text").asText());
        // base64 编码的 Mcp-Name
        String encoded = "=?base64?" + Base64.getEncoder().encodeToString("订单".getBytes(StandardCharsets.UTF_8)) + "?=";
        call("{\"jsonrpc\":\"2.0\",\"id\":4,\"method\":\"tools/call\",\"params\":{\"name\":\"订单\"," + META + "}}",
                h("MCP-Protocol-Version", "2026-07-28", "Mcp-Method", "tools/call", "Mcp-Name", encoded), 200);
    }

    @Test
    void modernValidation() throws Exception {
        String list = "{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/list\",\"params\":{" + META + "}}";
        // 缺 Mcp-Method
        assertEquals(JsonRpc.HEADER_MISMATCH, call(list, h("MCP-Protocol-Version", "2026-07-28"), 400).at("/error/code").asInt());
        // 版本头缺失
        assertEquals(JsonRpc.HEADER_MISMATCH, call(list, h("Mcp-Method", "tools/list"), 400).at("/error/code").asInt());
        // Mcp-Name 不一致
        String callBody = "{\"jsonrpc\":\"2.0\",\"id\":3,\"method\":\"tools/call\",\"params\":{\"name\":\"Order_get\"," + META + "}}";
        assertEquals(JsonRpc.HEADER_MISMATCH, call(callBody, h("MCP-Protocol-Version", "2026-07-28", "Mcp-Method", "tools/call",
                "Mcp-Name", "other"), 400).at("/error/code").asInt());
        // 未知方法 404;modern 没有 initialize / ping
        String ping = "{\"jsonrpc\":\"2.0\",\"id\":5,\"method\":\"ping\",\"params\":{" + META + "}}";
        assertEquals(JsonRpc.METHOD_NOT_FOUND, call(ping, h("MCP-Protocol-Version", "2026-07-28", "Mcp-Method", "ping"), 404)
                .at("/error/code").asInt());
        // 不支持的 modern 版本
        String future = "{\"jsonrpc\":\"2.0\",\"id\":6,\"method\":\"tools/list\",\"params\":{\"_meta\":{\"io.modelcontextprotocol/protocolVersion\":\"2099-01-01\"}}}";
        JsonNode r = call(future, h("MCP-Protocol-Version", "2099-01-01", "Mcp-Method", "tools/list"), 400);
        assertEquals(JsonRpc.UNSUPPORTED_PROTOCOL_VERSION, r.at("/error/code").asInt());
        assertEquals("2099-01-01", r.at("/error/data/requested").asText());
        assertTrue(calls.isEmpty());
    }
}
