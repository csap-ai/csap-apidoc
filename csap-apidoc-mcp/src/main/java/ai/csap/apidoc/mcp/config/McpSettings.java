package ai.csap.apidoc.mcp.config;

import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * MCP 规则文件内容:鉴权设置 + 每个接口(文档方法 key)的工具设置。
 *
 * <pre>
 * auth:
 *   mode: token
 *   forwardHeaders: [Authorization, Cookie]
 * tools:
 *   com.example.UserController.get:
 *     enabled: true
 *     name: get_user
 * </pre>
 *
 * @author ycf
 * @since 1.3.0
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class McpSettings {

    private AuthSetting auth;

    private Map<String, ToolSetting> tools = new TreeMap<>();

    public static McpSettings empty() {
        return new McpSettings();
    }

    /**
     * 深拷贝,修改时在副本上操作再整体替换
     *
     * @return 副本
     */
    public McpSettings copy() {
        McpSettings c = new McpSettings();
        if (auth != null) {
            c.auth = new AuthSetting(auth.getMode(), auth.getForwardHeaders() == null ? null
                    : new java.util.ArrayList<>(auth.getForwardHeaders()));
        }
        tools.forEach((k, v) -> c.tools.put(k, new ToolSetting(v.getEnabled(), v.getName(), v.getDescription())));
        return c;
    }

    public ToolSetting tool(String methodKey) {
        return tools.get(methodKey);
    }

    public AuthSetting getAuth() {
        return auth;
    }

    public void setAuth(AuthSetting auth) {
        this.auth = auth;
    }

    public Map<String, ToolSetting> getTools() {
        return tools == null ? Collections.emptyMap() : tools;
    }

    public void setTools(Map<String, ToolSetting> tools) {
        this.tools = tools == null ? new TreeMap<>() : new TreeMap<>(tools);
    }
}
