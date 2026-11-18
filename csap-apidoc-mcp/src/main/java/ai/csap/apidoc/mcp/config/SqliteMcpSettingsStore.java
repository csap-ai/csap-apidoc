package ai.csap.apidoc.mcp.config;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * SQLite 规则存储:与字段规则共用同一个库,按需建表 mcp_setting / mcp_tool,不影响已有表。
 *
 * @author ycf
 * @since 1.3.0
 */
public class SqliteMcpSettingsStore implements McpSettingsStore {

    static final String DDL_SETTING = "CREATE TABLE IF NOT EXISTS mcp_setting (name TEXT PRIMARY KEY, value TEXT)";
    static final String DDL_TOOL = "CREATE TABLE IF NOT EXISTS mcp_tool (method_key TEXT PRIMARY KEY, enabled INTEGER,"
            + " name TEXT, description TEXT, updated_at INTEGER)";

    private final File file;
    private final boolean persistent;
    private McpSettings override;

    /**
     * @param file       数据库文件
     * @param persistent false 表示文件是从只读 classpath 复制出来的临时副本,修改只保存在内存
     */
    public SqliteMcpSettingsStore(File file, boolean persistent) {
        this.file = file;
        this.persistent = persistent;
    }

    private Connection connect() throws SQLException {
        return DriverManager.getConnection("jdbc:sqlite:" + file.getPath());
    }

    private static void ensureSchema(Connection c) throws SQLException {
        try (Statement st = c.createStatement()) {
            st.execute(DDL_SETTING);
            st.execute(DDL_TOOL);
        }
    }

    private static boolean tableExists(Connection c, String table) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT 1 FROM sqlite_master WHERE type='table' AND name=?")) {
            ps.setString(1, table);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    @Override
    public synchronized McpSettings load() {
        if (override != null) {
            return override.copy();
        }
        McpSettings s = McpSettings.empty();
        if (!file.isFile()) {
            return s;
        }
        try (Connection c = connect()) {
            if (tableExists(c, "mcp_setting")) {
                String mode = null;
                List<String> headers = null;
                try (Statement st = c.createStatement();
                     ResultSet rs = st.executeQuery("SELECT name, value FROM mcp_setting")) {
                    while (rs.next()) {
                        String name = rs.getString(1);
                        String value = rs.getString(2);
                        if ("auth.mode".equals(name)) {
                            mode = value;
                        } else if ("auth.forwardHeaders".equals(name) && value != null) {
                            headers = new ArrayList<>();
                            for (String h : value.split(",")) {
                                if (!h.trim().isEmpty()) {
                                    headers.add(h.trim());
                                }
                            }
                        }
                    }
                }
                if (mode != null || headers != null) {
                    s.setAuth(new AuthSetting(mode, headers));
                }
            }
            if (tableExists(c, "mcp_tool")) {
                try (Statement st = c.createStatement();
                     ResultSet rs = st.executeQuery("SELECT method_key, enabled, name, description FROM mcp_tool")) {
                    while (rs.next()) {
                        Object en = rs.getObject(2);
                        Boolean enabled = en == null ? null : ((Number) en).intValue() != 0;
                        s.getTools().put(rs.getString(1), new ToolSetting(enabled, rs.getString(3), rs.getString(4)));
                    }
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("读取 MCP 设置失败: " + file + ": " + e.getMessage(), e);
        }
        return s;
    }

    @Override
    public synchronized void save(McpSettings settings) throws SQLException {
        if (!persistent) {
            override = settings.copy();
            return;
        }
        File dir = file.getAbsoluteFile().getParentFile();
        if (dir != null && !dir.isDirectory()) {
            dir.mkdirs();
        }
        try (Connection c = connect()) {
            c.setAutoCommit(false);
            ensureSchema(c);
            try (Statement st = c.createStatement()) {
                st.execute("DELETE FROM mcp_setting");
                st.execute("DELETE FROM mcp_tool");
            }
            AuthSetting auth = settings.getAuth();
            if (auth != null) {
                try (PreparedStatement ps = c.prepareStatement("INSERT INTO mcp_setting(name, value) VALUES (?, ?)")) {
                    if (auth.getMode() != null) {
                        ps.setString(1, "auth.mode");
                        ps.setString(2, auth.getMode());
                        ps.addBatch();
                    }
                    if (auth.getForwardHeaders() != null) {
                        ps.setString(1, "auth.forwardHeaders");
                        ps.setString(2, String.join(",", auth.getForwardHeaders()));
                        ps.addBatch();
                    }
                    ps.executeBatch();
                }
            }
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO mcp_tool(method_key, enabled, name, description, updated_at) VALUES (?, ?, ?, ?, ?)")) {
                long now = System.currentTimeMillis();
                for (Map.Entry<String, ToolSetting> e : settings.getTools().entrySet()) {
                    ToolSetting t = e.getValue();
                    ps.setString(1, e.getKey());
                    if (t.getEnabled() == null) {
                        ps.setNull(2, java.sql.Types.INTEGER);
                    } else {
                        ps.setInt(2, t.getEnabled() ? 1 : 0);
                    }
                    ps.setString(3, t.getName());
                    ps.setString(4, t.getDescription());
                    ps.setLong(5, now);
                    ps.addBatch();
                }
                ps.executeBatch();
            }
            c.commit();
        }
    }

    @Override
    public boolean isPersistent() {
        return persistent;
    }

    @Override
    public String type() {
        return "sqlite";
    }

    @Override
    public String location() {
        return file.getPath() + (persistent ? "" : " (只读副本)");
    }
}
