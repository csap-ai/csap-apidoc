package ai.csap.apidoc.mcp.audit;

/**
 * 一条 MCP 调用审计记录
 *
 * @author ycf
 * @since 1.3.0
 */
public class AuditEntry {
    private long id;
    /**
     * 时间(毫秒)
     */
    private long time;
    /**
     * 调用方:令牌名称,透传模式为来源 IP
     */
    private String caller;
    /**
     * 来源 IP
     */
    private String remoteAddress;
    /**
     * 工具名;鉴权失败等非工具调用为 "-"
     */
    private String tool;
    /**
     * HTTP 方法 + 路径(含查询串)
     */
    private String target;
    /**
     * 参数摘要(已脱敏、截断)
     */
    private String arguments;
    /**
     * HTTP 状态码;未发出请求时为 0
     */
    private int status;
    /**
     * 是否成功
     */
    private boolean success;
    /**
     * 结果摘要或错误信息(已脱敏、截断)
     */
    private String result;
    /**
     * 耗时(毫秒)
     */
    private long durationMs;

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public long getTime() {
        return time;
    }

    public void setTime(long time) {
        this.time = time;
    }

    public String getCaller() {
        return caller;
    }

    public void setCaller(String caller) {
        this.caller = caller;
    }

    public String getRemoteAddress() {
        return remoteAddress;
    }

    public void setRemoteAddress(String remoteAddress) {
        this.remoteAddress = remoteAddress;
    }

    public String getTool() {
        return tool;
    }

    public void setTool(String tool) {
        this.tool = tool;
    }

    public String getTarget() {
        return target;
    }

    public void setTarget(String target) {
        this.target = target;
    }

    public String getArguments() {
        return arguments;
    }

    public void setArguments(String arguments) {
        this.arguments = arguments;
    }

    public int getStatus() {
        return status;
    }

    public void setStatus(int status) {
        this.status = status;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getResult() {
        return result;
    }

    public void setResult(String result) {
        this.result = result;
    }

    public long getDurationMs() {
        return durationMs;
    }

    public void setDurationMs(long durationMs) {
        this.durationMs = durationMs;
    }
}
