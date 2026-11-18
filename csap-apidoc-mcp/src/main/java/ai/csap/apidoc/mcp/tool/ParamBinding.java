package ai.csap.apidoc.mcp.tool;

/**
 * 工具参数(inputSchema 顶层属性)与 HTTP 请求位置的对应关系
 *
 * @author ycf
 * @since 1.3.0
 */
public class ParamBinding {

    public enum Location {
        PATH, QUERY, HEADER, BODY
    }

    private final String property;
    private final Location location;
    private final String httpName;
    private final boolean required;

    public ParamBinding(String property, Location location, String httpName, boolean required) {
        this.property = property;
        this.location = location;
        this.httpName = httpName;
        this.required = required;
    }

    /**
     * inputSchema 中的属性名
     */
    public String getProperty() {
        return property;
    }

    public Location getLocation() {
        return location;
    }

    /**
     * HTTP 中的名称(路径变量名、查询参数名、请求头名)
     */
    public String getHttpName() {
        return httpName;
    }

    public boolean isRequired() {
        return required;
    }
}
