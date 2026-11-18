package ai.csap.apidoc.mcp.protocol;

/**
 * 协议层给出的 HTTP 响应:状态码 + JSON 体(202 时为 null)
 *
 * @author ycf
 * @since 1.3.0
 */
public class McpHttpResponse {

    private final int status;
    private final String body;

    public McpHttpResponse(int status, String body) {
        this.status = status;
        this.body = body;
    }

    public int getStatus() {
        return status;
    }

    public String getBody() {
        return body;
    }
}
