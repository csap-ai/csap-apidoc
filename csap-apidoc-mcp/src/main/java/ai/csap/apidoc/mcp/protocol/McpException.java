package ai.csap.apidoc.mcp.protocol;

/**
 * 以 JSON-RPC 错误返回的协议异常
 *
 * @author ycf
 * @since 1.3.0
 */
public class McpException extends RuntimeException {

    private final int code;
    private final int httpStatus;
    private final transient com.fasterxml.jackson.databind.JsonNode data;

    public McpException(int code, String message) {
        this(code, message, 200, null);
    }

    public McpException(int code, String message, int httpStatus, com.fasterxml.jackson.databind.JsonNode data) {
        super(message);
        this.code = code;
        this.httpStatus = httpStatus;
        this.data = data;
    }

    public int getCode() {
        return code;
    }

    public int getHttpStatus() {
        return httpStatus;
    }

    public com.fasterxml.jackson.databind.JsonNode getData() {
        return data;
    }
}
