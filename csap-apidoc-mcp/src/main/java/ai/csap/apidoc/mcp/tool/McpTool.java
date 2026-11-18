package ai.csap.apidoc.mcp.tool;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * 一个文档接口对应的 MCP 工具(含调用所需的绑定信息)
 *
 * @author ycf
 * @since 1.3.0
 */
public class McpTool {

    private String methodKey;
    private String className;
    private String methodName;
    private String httpMethod;
    private String pathPattern;
    private String title;
    private String defaultName;
    private String name;
    private String defaultDescription;
    private String description;
    private ObjectNode inputSchema;
    private List<ParamBinding> bindings = new ArrayList<>();
    private boolean exposed;
    private boolean exposedByDefault;
    private Boolean enabledSetting;
    private boolean hidden;
    /**
     * 接口状态:DEFAULT(开发中)/ FINISH(完成)/ DEPRECATED(过时)
     */
    private String status = "DEFAULT";
    private String statusNote;
    private String unsupportedReason;

    public boolean isReadOnly() {
        return "GET".equals(httpMethod) || "HEAD".equals(httpMethod);
    }

    public String getMethodKey() {
        return methodKey;
    }

    public void setMethodKey(String methodKey) {
        this.methodKey = methodKey;
    }

    public String getClassName() {
        return className;
    }

    public void setClassName(String className) {
        this.className = className;
    }

    public String getMethodName() {
        return methodName;
    }

    public void setMethodName(String methodName) {
        this.methodName = methodName;
    }

    public String getHttpMethod() {
        return httpMethod;
    }

    public void setHttpMethod(String httpMethod) {
        this.httpMethod = httpMethod;
    }

    public String getPathPattern() {
        return pathPattern;
    }

    public void setPathPattern(String pathPattern) {
        this.pathPattern = pathPattern;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDefaultName() {
        return defaultName;
    }

    public void setDefaultName(String defaultName) {
        this.defaultName = defaultName;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDefaultDescription() {
        return defaultDescription;
    }

    public void setDefaultDescription(String defaultDescription) {
        this.defaultDescription = defaultDescription;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public ObjectNode getInputSchema() {
        return inputSchema;
    }

    public void setInputSchema(ObjectNode inputSchema) {
        this.inputSchema = inputSchema;
    }

    public List<ParamBinding> getBindings() {
        return bindings;
    }

    public void setBindings(List<ParamBinding> bindings) {
        this.bindings = bindings;
    }

    public boolean isExposed() {
        return exposed;
    }

    public void setExposed(boolean exposed) {
        this.exposed = exposed;
    }

    public boolean isExposedByDefault() {
        return exposedByDefault;
    }

    public void setExposedByDefault(boolean exposedByDefault) {
        this.exposedByDefault = exposedByDefault;
    }

    public Boolean getEnabledSetting() {
        return enabledSetting;
    }

    public void setEnabledSetting(Boolean enabledSetting) {
        this.enabledSetting = enabledSetting;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getStatusNote() {
        return statusNote;
    }

    public void setStatusNote(String statusNote) {
        this.statusNote = statusNote;
    }

    public boolean isDeprecated() {
        return "DEPRECATED".equals(status);
    }

    public boolean isHidden() {
        return hidden;
    }

    public void setHidden(boolean hidden) {
        this.hidden = hidden;
    }

    public String getUnsupportedReason() {
        return unsupportedReason;
    }

    public void setUnsupportedReason(String unsupportedReason) {
        this.unsupportedReason = unsupportedReason;
    }
}
