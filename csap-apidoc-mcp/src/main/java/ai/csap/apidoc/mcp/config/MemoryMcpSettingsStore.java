package ai.csap.apidoc.mcp.config;

/**
 * 内存存储:不持久化,重启后恢复为配置文件默认值
 *
 * @author ycf
 * @since 1.3.0
 */
public class MemoryMcpSettingsStore implements McpSettingsStore {

    private volatile McpSettings settings;
    private final String reason;

    public MemoryMcpSettingsStore() {
        this(McpSettings.empty(), "memory");
    }

    public MemoryMcpSettingsStore(McpSettings initial, String reason) {
        this.settings = initial == null ? McpSettings.empty() : initial;
        this.reason = reason;
    }

    @Override
    public McpSettings load() {
        return settings.copy();
    }

    @Override
    public void save(McpSettings settings) {
        this.settings = settings.copy();
    }

    @Override
    public boolean isPersistent() {
        return false;
    }

    @Override
    public String type() {
        return "memory";
    }

    @Override
    public String location() {
        return reason;
    }
}
