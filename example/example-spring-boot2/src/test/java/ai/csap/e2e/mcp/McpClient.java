package ai.csap.e2e.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 测试用的最小 MCP / HTTP 客户端
 */
class McpClient {

    static final ObjectMapper JSON = new ObjectMapper();
    final HttpClient http = HttpClient.newHttpClient();
    final String base;
    String token;
    String protocolVersion;
    int id;

    McpClient(int port) {
        this.base = "http://127.0.0.1:" + port;
    }

    HttpResponse<String> post(String path, String body, String... headers) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(base + path))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json, text/event-stream")
                .POST(HttpRequest.BodyPublishers.ofString(body));
        for (int i = 0; i < headers.length; i += 2) {
            b.header(headers[i], headers[i + 1]);
        }
        return http.send(b.build(), HttpResponse.BodyHandlers.ofString());
    }

    HttpResponse<String> get(String path) throws Exception {
        return http.send(HttpRequest.newBuilder(URI.create(base + path)).build(), HttpResponse.BodyHandlers.ofString());
    }

    /**
     * 发一个 JSON-RPC 请求到 /csap/mcp(带令牌和协商的版本头)
     */
    HttpResponse<String> rpcRaw(String method, String params, String... extraHeaders) throws Exception {
        String body = "{\"jsonrpc\":\"2.0\",\"id\":" + (++id) + ",\"method\":\"" + method + "\""
                + (params == null ? "" : ",\"params\":" + params) + "}";
        java.util.List<String> h = new java.util.ArrayList<>();
        if (token != null) {
            h.add("Authorization");
            h.add("Bearer " + token);
        }
        if (protocolVersion != null) {
            h.add("MCP-Protocol-Version");
            h.add(protocolVersion);
        }
        h.addAll(java.util.Arrays.asList(extraHeaders));
        return post("/csap/mcp", body, h.toArray(new String[0]));
    }

    JsonNode rpc(String method, String params) throws Exception {
        HttpResponse<String> r = rpcRaw(method, params);
        assertEquals(200, r.statusCode(), r.body());
        return JSON.readTree(r.body());
    }

    JsonNode callTool(String name, String arguments) throws Exception {
        return rpc("tools/call", "{\"name\":\"" + name + "\",\"arguments\":" + arguments + "}");
    }
}
