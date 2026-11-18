package ai.csap.apidoc.mcp.config;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 单个接口的 MCP 工具设置(规则文件中的一项)。字段为 null 表示使用默认值。
 *
 * @author ycf
 * @since 1.3.0
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ToolSetting {
    /**
     * 是否暴露;null 时 GET 按 expose-get-by-default,其余不暴露
     */
    private Boolean enabled;
    /**
     * 改写的工具名
     */
    private String name;
    /**
     * 改写的说明
     */
    private String description;

    public ToolSetting() {
    }

    public ToolSetting(Boolean enabled, String name, String description) {
        this.enabled = enabled;
        this.name = name;
        this.description = description;
    }

    @JsonIgnore
    public boolean isEmpty() {
        return enabled == null && isBlank(name) && isBlank(description);
    }

    static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}
