package ai.csap.apidoc.mcp.protocol;

import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

/**
 * 一次 MCP HTTP 请求的上下文:请求头(忽略大小写)与调用方标识
 *
 * @author ycf
 * @since 1.3.0
 */
public class McpCallContext {

    private final Map<String, String> headers;
    private final String caller;
    private final String remoteAddress;

    public McpCallContext(Map<String, String> headers, String caller, String remoteAddress) {
        TreeMap<String, String> h = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        if (headers != null) {
            h.putAll(headers);
        }
        this.headers = Collections.unmodifiableMap(h);
        this.caller = caller;
        this.remoteAddress = remoteAddress;
    }

    public static McpCallContext of(Map<String, String> headers) {
        return new McpCallContext(headers, "anonymous", null);
    }

    public String header(String name) {
        return headers.get(name);
    }

    public Map<String, String> getHeaders() {
        return headers;
    }

    /**
     * 调用方:令牌名称,透传模式下为来源 IP
     */
    public String getCaller() {
        return caller;
    }

    public String getRemoteAddress() {
        return remoteAddress;
    }
}
