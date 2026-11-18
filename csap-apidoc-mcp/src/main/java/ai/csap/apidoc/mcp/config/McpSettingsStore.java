package ai.csap.apidoc.mcp.config;

/**
 * MCP 规则文件存储
 *
 * @author ycf
 * @since 1.3.0
 */
public interface McpSettingsStore {

    /**
     * 读取;文件不存在时返回空设置
     *
     * @return 设置
     */
    McpSettings load();

    /**
     * 保存(只读存储不做任何事)
     *
     * @param settings 设置
     * @throws Exception 写入失败
     */
    void save(McpSettings settings) throws Exception;

    /**
     * 修改能否持久化(打成 jar 后 classpath 中的规则文件只读)
     *
     * @return 是否可持久化
     */
    boolean isPersistent();

    /**
     * 存储类型:yaml / sqlite / memory
     *
     * @return 类型
     */
    String type();

    /**
     * 存储位置描述(用于 DevTools 显示与日志)
     *
     * @return 位置
     */
    String location();
}
