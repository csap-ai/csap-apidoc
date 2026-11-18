package ai.csap.apidoc.mcp.config;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 规则文件里的鉴权设置。字段为 null 表示使用配置文件(csap.apidoc.mcp.auth.*)的值。
 * 令牌值不在这里保存。
 *
 * @author ycf
 * @since 1.3.0
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AuthSetting {
    /**
     * token 或 passthrough
     */
    private String mode;
    /**
     * 转发给目标接口的请求头
     */
    private List<String> forwardHeaders;

    public AuthSetting() {
    }

    public AuthSetting(String mode, List<String> forwardHeaders) {
        this.mode = mode;
        this.forwardHeaders = forwardHeaders;
    }

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    public List<String> getForwardHeaders() {
        return forwardHeaders;
    }

    public void setForwardHeaders(List<String> forwardHeaders) {
        this.forwardHeaders = forwardHeaders;
    }
}
