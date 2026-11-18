package ai.csap.apidoc.mcp.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;

import ai.csap.apidoc.mcp.autoconfigure.McpProperties;

class SettingsStoreTest {

    static McpSettings sample() {
        McpSettings s = McpSettings.empty();
        s.setAuth(new AuthSetting("passthrough", Arrays.asList("Authorization", "X-Tenant")));
        s.getTools().put("com.example.ItemController.get", new ToolSetting(null, "get_item", "按 ID 查询"));
        s.getTools().put("com.example.ItemController.delete", new ToolSetting(true, null, null));
        return s;
    }

    static void assertSample(McpSettings s) {
        assertEquals("passthrough", s.getAuth().getMode());
        assertEquals(Arrays.asList("Authorization", "X-Tenant"), s.getAuth().getForwardHeaders());
        assertEquals("get_item", s.tool("com.example.ItemController.get").getName());
        assertNull(s.tool("com.example.ItemController.get").getEnabled());
        assertTrue(s.tool("com.example.ItemController.delete").getEnabled());
    }

    @Test
    void yamlRoundTrip() throws Exception {
        File f = new File(Files.createTempDirectory("mcp-yaml").toFile(), "rules/mcp.yaml");
        YamlMcpSettingsStore store = new YamlMcpSettingsStore(f, null);
        assertTrue(store.load().getTools().isEmpty());
        store.save(sample());
        String text = new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
        assertTrue(text.contains("com.example.ItemController.get:"), text);
        assertTrue(text.contains("mode: passthrough"), text);
        assertFalse(text.contains("empty"), text);
        assertSample(store.load());
    }

    @Test
    void yamlReadOnlyKeepsChangesInMemory() throws Exception {
        byte[] yaml = "tools:\n  a.B.c:\n    enabled: false\n".getBytes(StandardCharsets.UTF_8);
        YamlMcpSettingsStore store = new YamlMcpSettingsStore(null, new ByteArrayResource(yaml));
        assertFalse(store.isPersistent());
        assertFalse(store.load().tool("a.B.c").getEnabled());
        store.save(sample());
        assertSample(store.load());
    }

    @Test
    void sqliteRoundTripAlongsideExistingTables() throws Exception {
        File db = new File(Files.createTempDirectory("mcp-sqlite").toFile(), "apidoc.db");
        try (java.sql.Connection c = java.sql.DriverManager.getConnection("jdbc:sqlite:" + db.getPath());
             java.sql.Statement st = c.createStatement()) {
            st.execute("CREATE TABLE api_method (id INTEGER PRIMARY KEY, name TEXT)");
            st.execute("INSERT INTO api_method(name) VALUES ('keep')");
        }
        SqliteMcpSettingsStore store = new SqliteMcpSettingsStore(db, true);
        assertTrue(store.load().getTools().isEmpty());
        store.save(sample());
        assertSample(store.load());
        store.save(sample());
        assertEquals(2, store.load().getTools().size());
        try (java.sql.Connection c = java.sql.DriverManager.getConnection("jdbc:sqlite:" + db.getPath());
             java.sql.Statement st = c.createStatement();
             java.sql.ResultSet rs = st.executeQuery("SELECT count(*) FROM api_method")) {
            rs.next();
            assertEquals(1, rs.getInt(1));
        }
    }

    @Test
    void resolverFollowsRuleStrategy() throws Exception {
        McpProperties p = new McpProperties();
        assertEquals("yaml", McpSettingsStores.create(p, "csap-api-doc", false, null).type());
        assertEquals("sqlite", McpSettingsStores.create(p, "csap-api-doc", true, null).type());
        p.setStore(McpProperties.Store.MEMORY);
        assertEquals("memory", McpSettingsStores.create(p, "csap-api-doc", true, null).type());
        p.setStore(McpProperties.Store.AUTO);
        File explicit = new File(Files.createTempDirectory("mcp-explicit").toFile(), "mcp.db");
        p.setConfigFile(explicit.getPath());
        McpSettingsStore s = McpSettingsStores.create(p, "x", false, null);
        assertEquals("sqlite", s.type());
        assertTrue(s.isPersistent());
    }
}
