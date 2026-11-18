package ai.csap.apidoc.mcp.protocol;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * JSON-RPC 2.0 消息构造与错误码
 *
 * @author ycf
 * @since 1.3.0
 */
public final class JsonRpc {

    /**
     * 协议层使用独立的 ObjectMapper,不受应用 Jackson 定制(如 snake_case)影响
     */
    public static final ObjectMapper MAPPER = new ObjectMapper();
    public static final JsonNodeFactory NODES = JsonNodeFactory.instance;

    public static final int PARSE_ERROR = -32700;
    public static final int INVALID_REQUEST = -32600;
    public static final int METHOD_NOT_FOUND = -32601;
    public static final int INVALID_PARAMS = -32602;
    public static final int INTERNAL_ERROR = -32603;
    /**
     * 实现自定义:未授权(-32000 ~ -32019 为实现自定义段)
     */
    public static final int UNAUTHORIZED = -32001;
    /**
     * 2026-07-28:HTTP 头与请求体不一致或缺少必需头
     */
    public static final int HEADER_MISMATCH = -32020;
    /**
     * 2026-07-28:不支持的协议版本
     */
    public static final int UNSUPPORTED_PROTOCOL_VERSION = -32022;

    private JsonRpc() {
    }

    public static ObjectNode result(JsonNode id, JsonNode result) {
        ObjectNode n = NODES.objectNode();
        n.put("jsonrpc", "2.0");
        n.set("id", id == null ? NODES.nullNode() : id);
        n.set("result", result);
        return n;
    }

    public static ObjectNode error(JsonNode id, int code, String message) {
        return error(id, code, message, null);
    }

    public static ObjectNode error(JsonNode id, int code, String message, JsonNode data) {
        ObjectNode n = NODES.objectNode();
        n.put("jsonrpc", "2.0");
        n.set("id", id == null ? NODES.nullNode() : id);
        ObjectNode e = n.putObject("error");
        e.put("code", code);
        e.put("message", message);
        if (data != null) {
            e.set("data", data);
        }
        return n;
    }

    public static String write(JsonNode node) {
        try {
            return MAPPER.writeValueAsString(node);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
