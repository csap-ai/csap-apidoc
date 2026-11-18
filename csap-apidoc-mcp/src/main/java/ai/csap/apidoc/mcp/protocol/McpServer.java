package ai.csap.apidoc.mcp.protocol;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.function.Supplier;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * MCP 服务端协议层(Streamable HTTP,只返回 application/json,无状态)。
 *
 * <p>dual-era:请求 {@code params._meta} 里带 {@code io.modelcontextprotocol/protocolVersion}
 * (或 {@code MCP-Protocol-Version} 头是 modern 版本)按 2026-07-28 无状态处理,校验请求头与请求体一致;
 * 否则按 initialize 握手式版本(2025-11-25 / 2025-06-18 / 2025-03-26)处理。
 *
 * <p>不依赖 Spring,可直接单测。
 *
 * @author ycf
 * @since 1.3.0
 */
public class McpServer {

    public static final String META_PROTOCOL_VERSION = "io.modelcontextprotocol/protocolVersion";
    public static final String META_SERVER_INFO = "io.modelcontextprotocol/serverInfo";
    public static final String HEADER_PROTOCOL_VERSION = "MCP-Protocol-Version";
    public static final String HEADER_METHOD = "Mcp-Method";
    public static final String HEADER_NAME = "Mcp-Name";

    /**
     * tools/list 等结果的缓存提示(毫秒)
     */
    static final long LIST_TTL_MS = 60_000L;

    private final McpToolHandler tools;
    private final String serverName;
    private final String serverVersion;
    private final Supplier<String> serverTitle;
    private final Supplier<String> instructions;

    public McpServer(McpToolHandler tools, String serverName, String serverVersion,
                     Supplier<String> serverTitle, Supplier<String> instructions) {
        this.tools = tools;
        this.serverName = serverName;
        this.serverVersion = serverVersion;
        this.serverTitle = serverTitle;
        this.instructions = instructions;
    }

    /**
     * 处理一次 POST
     *
     * @param body    请求体
     * @param context 上下文(请求头等)
     * @return HTTP 响应
     */
    public McpHttpResponse handle(String body, McpCallContext context) {
        JsonNode message;
        try {
            message = body == null || body.trim().isEmpty() ? null : JsonRpc.MAPPER.readTree(body);
        } catch (Exception e) {
            message = null;
        }
        if (message == null || !(message.isObject() || message.isArray())) {
            return new McpHttpResponse(400, JsonRpc.write(JsonRpc.error(null, JsonRpc.PARSE_ERROR, "Parse error")));
        }
        if (message.isArray()) {
            return handleBatch((ArrayNode) message, context);
        }
        Outcome outcome = handleOne((ObjectNode) message, context, false);
        return outcome.response == null ? new McpHttpResponse(outcome.status, null)
                : new McpHttpResponse(outcome.status, JsonRpc.write(outcome.response));
    }

    /**
     * 2025-03-26 允许批量;之后的版本取消了批量,这里仍按 legacy 语义处理
     */
    private McpHttpResponse handleBatch(ArrayNode batch, McpCallContext context) {
        if (batch.size() == 0) {
            return new McpHttpResponse(400, JsonRpc.write(JsonRpc.error(null, JsonRpc.INVALID_REQUEST, "Empty batch")));
        }
        ArrayNode out = JsonRpc.NODES.arrayNode();
        for (JsonNode item : batch) {
            if (!item.isObject()) {
                out.add(JsonRpc.error(null, JsonRpc.INVALID_REQUEST, "Invalid Request"));
                continue;
            }
            Outcome o = handleOne((ObjectNode) item, context, true);
            if (o.response != null) {
                out.add(o.response);
            }
        }
        return out.size() == 0 ? new McpHttpResponse(202, null) : new McpHttpResponse(200, JsonRpc.write(out));
    }

    private Outcome handleOne(ObjectNode msg, McpCallContext ctx, boolean inBatch) {
        JsonNode id = msg.get("id");
        JsonNode methodNode = msg.get("method");
        if (methodNode == null) {
            if (msg.has("result") || msg.has("error")) {
                // 客户端发来的响应(legacy 允许),无需处理
                return new Outcome(202, null);
            }
            return new Outcome(400, JsonRpc.error(id, JsonRpc.INVALID_REQUEST, "Invalid Request"));
        }
        if (!"2.0".equals(text(msg.get("jsonrpc"))) || !methodNode.isTextual()
                || (id != null && !(id.isTextual() || id.isIntegralNumber()))) {
            return new Outcome(400, JsonRpc.error(id, JsonRpc.INVALID_REQUEST, "Invalid Request"));
        }
        String method = methodNode.asText();
        boolean notification = !msg.has("id");
        JsonNode params = msg.get("params");
        if (params != null && !params.isObject()) {
            return new Outcome(400, JsonRpc.error(id, JsonRpc.INVALID_PARAMS, "params must be an object"));
        }
        String metaVersion = params == null ? null : text(params.path("_meta").get(META_PROTOCOL_VERSION));
        String headerVersion = trimToNull(ctx.header(HEADER_PROTOCOL_VERSION));
        boolean modern = metaVersion != null || (headerVersion != null && McpVersions.MODERN.contains(headerVersion));
        try {
            if (modern) {
                if (inBatch) {
                    throw new McpException(JsonRpc.INVALID_REQUEST, "Batch requests are not supported in "
                            + McpVersions.V2026_07_28, 400, null);
                }
                return modern(id, method, params, metaVersion, headerVersion, notification, ctx);
            }
            return legacy(id, method, params, headerVersion, notification, ctx);
        } catch (McpException e) {
            if (notification) {
                return new Outcome(e.getHttpStatus() == 200 ? 202 : e.getHttpStatus(), null);
            }
            return new Outcome(e.getHttpStatus(), JsonRpc.error(id, e.getCode(), e.getMessage(), e.getData()));
        } catch (RuntimeException e) {
            return new Outcome(notification ? 202 : 200, notification ? null
                    : JsonRpc.error(id, JsonRpc.INTERNAL_ERROR, "Internal error: " + e.getClass().getSimpleName()));
        }
    }

    // ------------------------------------------------------------------ modern

    private Outcome modern(JsonNode id, String method, JsonNode params, String metaVersion, String headerVersion,
                           boolean notification, McpCallContext ctx) {
        if (metaVersion == null) {
            throw new McpException(JsonRpc.HEADER_MISMATCH, "Header mismatch: MCP-Protocol-Version header is "
                    + headerVersion + " but params._meta[\"" + META_PROTOCOL_VERSION + "\"] is missing", 400, null);
        }
        if (!McpVersions.MODERN.contains(metaVersion)) {
            throw unsupported(metaVersion);
        }
        if (headerVersion == null || !headerVersion.equals(metaVersion)) {
            throw new McpException(JsonRpc.HEADER_MISMATCH, "Header mismatch: MCP-Protocol-Version header value '"
                    + headerVersion + "' does not match body value '" + metaVersion + "'", 400, null);
        }
        if (notification) {
            return new Outcome(202, null);
        }
        String headerMethod = trimToNull(ctx.header(HEADER_METHOD));
        if (!method.equals(headerMethod)) {
            throw new McpException(JsonRpc.HEADER_MISMATCH, "Header mismatch: Mcp-Method header value '"
                    + headerMethod + "' does not match body value '" + method + "'", 400, null);
        }
        ObjectNode result;
        switch (method) {
            case "server/discover":
                result = discover();
                break;
            case "tools/list":
                result = toolsList();
                result.put("ttlMs", LIST_TTL_MS);
                result.put("cacheScope", "private");
                break;
            case "tools/call":
                String name = text(params.get("name"));
                String headerName = decodeHeaderValue(ctx.header(HEADER_NAME));
                if (name == null || !name.equals(headerName)) {
                    throw new McpException(JsonRpc.HEADER_MISMATCH, "Header mismatch: Mcp-Name header value '"
                            + headerName + "' does not match body value '" + name + "'", 400, null);
                }
                result = toolsCall(params, ctx);
                break;
            default:
                throw new McpException(JsonRpc.METHOD_NOT_FOUND, "Method not found: " + method, 404, null);
        }
        result.put("resultType", "complete");
        result.putObject("_meta").set(META_SERVER_INFO, serverInfo());
        return new Outcome(200, JsonRpc.result(id, result));
    }

    private ObjectNode discover() {
        ObjectNode r = JsonRpc.NODES.objectNode();
        ArrayNode versions = r.putArray("supportedVersions");
        McpVersions.ALL.forEach(versions::add);
        r.set("capabilities", capabilities());
        String ins = instructions == null ? null : instructions.get();
        if (ins != null && !ins.isEmpty()) {
            r.put("instructions", ins);
        }
        r.put("ttlMs", LIST_TTL_MS);
        r.put("cacheScope", "private");
        return r;
    }

    private static McpException unsupported(String requested) {
        ObjectNode data = JsonRpc.NODES.objectNode();
        ArrayNode s = data.putArray("supported");
        McpVersions.ALL.forEach(s::add);
        data.put("requested", requested);
        return new McpException(JsonRpc.UNSUPPORTED_PROTOCOL_VERSION, "Unsupported protocol version", 400, data);
    }

    // ------------------------------------------------------------------ legacy

    private Outcome legacy(JsonNode id, String method, JsonNode params, String headerVersion,
                           boolean notification, McpCallContext ctx) {
        if (!"initialize".equals(method) && headerVersion != null && !McpVersions.ALL.contains(headerVersion)) {
            throw unsupported(headerVersion);
        }
        if (notification) {
            // notifications/initialized、notifications/cancelled 等:无状态,直接接受
            return new Outcome(202, null);
        }
        ObjectNode result;
        switch (method) {
            case "initialize":
                result = initialize(params);
                break;
            case "ping":
                result = JsonRpc.NODES.objectNode();
                break;
            case "server/discover":
                result = discover();
                result.remove("ttlMs");
                result.remove("cacheScope");
                break;
            case "tools/list":
                result = toolsList();
                break;
            case "tools/call":
                result = toolsCall(params, ctx);
                break;
            default:
                throw new McpException(JsonRpc.METHOD_NOT_FOUND, "Method not found: " + method);
        }
        return new Outcome(200, JsonRpc.result(id, result));
    }

    private ObjectNode initialize(JsonNode params) {
        String requested = params == null ? null : text(params.get("protocolVersion"));
        ObjectNode r = JsonRpc.NODES.objectNode();
        r.put("protocolVersion", McpVersions.negotiateLegacy(requested));
        r.set("capabilities", capabilities());
        r.set("serverInfo", serverInfo());
        String ins = instructions == null ? null : instructions.get();
        if (ins != null && !ins.isEmpty()) {
            r.put("instructions", ins);
        }
        return r;
    }

    // ------------------------------------------------------------------ common

    private ObjectNode capabilities() {
        ObjectNode c = JsonRpc.NODES.objectNode();
        c.putObject("tools").put("listChanged", false);
        return c;
    }

    private ObjectNode serverInfo() {
        ObjectNode info = JsonRpc.NODES.objectNode();
        info.put("name", serverName);
        info.put("version", serverVersion);
        String title = serverTitle == null ? null : serverTitle.get();
        if (title != null && !title.isEmpty()) {
            info.put("title", title);
        }
        return info;
    }

    private ObjectNode toolsList() {
        ObjectNode r = JsonRpc.NODES.objectNode();
        ArrayNode arr = r.putArray("tools");
        List<ObjectNode> list = tools.listTools();
        list.forEach(arr::add);
        return r;
    }

    private ObjectNode toolsCall(JsonNode params, McpCallContext ctx) {
        String name = params == null ? null : text(params.get("name"));
        if (name == null || name.isEmpty()) {
            throw new McpException(JsonRpc.INVALID_PARAMS, "Missing tool name");
        }
        JsonNode args = params.get("arguments");
        if (args == null || args.isNull()) {
            args = JsonRpc.NODES.objectNode();
        } else if (!args.isObject()) {
            throw new McpException(JsonRpc.INVALID_PARAMS, "arguments must be an object");
        }
        McpToolHandler.ToolResult tr = tools.callTool(name, args, ctx);
        ObjectNode r = JsonRpc.NODES.objectNode();
        ObjectNode content = r.putArray("content").addObject();
        content.put("type", "text");
        content.put("text", tr.getText() == null ? "" : tr.getText());
        r.put("isError", tr.isError());
        return r;
    }

    /**
     * 解码 {@code =?base64?...?=} 形式的头值
     *
     * @param value 原值
     * @return 解码后的值
     */
    static String decodeHeaderValue(String value) {
        if (value == null) {
            return null;
        }
        if (value.startsWith("=?base64?") && value.endsWith("?=") && value.length() >= 11) {
            try {
                return new String(Base64.getDecoder().decode(value.substring(9, value.length() - 2)),
                        StandardCharsets.UTF_8);
            } catch (IllegalArgumentException e) {
                return null;
            }
        }
        return value.trim();
    }

    private static String text(JsonNode n) {
        return n == null || !n.isTextual() ? null : n.asText();
    }

    private static String trimToNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private static final class Outcome {
        final int status;
        final JsonNode response;

        Outcome(int status, JsonNode response) {
            this.status = status;
            this.response = response;
        }
    }
}
