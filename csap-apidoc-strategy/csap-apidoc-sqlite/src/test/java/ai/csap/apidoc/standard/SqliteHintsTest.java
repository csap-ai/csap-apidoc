package ai.csap.apidoc.standard;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.List;
import java.util.Map;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import ai.csap.apidoc.model.CsapDocAuthHint;
import ai.csap.apidoc.model.CsapDocGlobalHeaderHint;
import ai.csap.apidoc.model.CsapDocMethod;
import ai.csap.apidoc.model.CsapDocModelController;

import lombok.SneakyThrows;

/**
 * Verifies M7.1 — the {@code globalHeaderHints} / {@code authHint} fields
 * (introduced for the {@code annotation} and {@code yaml} doc sources in M7)
 * load correctly when {@code docType=sql_lite} stores them in the optional
 * {@code api_method_global_header_hint} and {@code api_method_auth_hint}
 * tables.
 *
 * <p>The loader feature-detects both tables via {@code sqlite_master}, so a
 * legacy database without these tables still loads cleanly — only the hint
 * fields stay {@code null}.
 *
 * <p>Mirrors {@code YamlHintsTest} in {@code csap-apidoc-yaml} so the three
 * doc sources stay at parity for the M7 try-it-out feature surface.
 *
 * @author yangchengfu
 * @since 1.x M7.1
 */
public class SqliteHintsTest {

    private File dbFile;
    private String url;

    @Before
    @SneakyThrows
    public void setUp() {
        dbFile = File.createTempFile("apidoc-hints-test-", ".db");
        dbFile.deleteOnExit();
        url = "jdbc:sqlite:" + dbFile.getAbsolutePath();
        System.setProperty("csap.apidoc.sqlite.url", url);
    }

    @After
    public void tearDown() {
        System.clearProperty("csap.apidoc.sqlite.url");
        if (dbFile != null && dbFile.exists() && !dbFile.delete()) {
            dbFile.deleteOnExit();
        }
    }

    @Test
    @SneakyThrows
    public void hintsLoadFromSqlite() {
        seedSchemaWithHints();

        SqliteApidocStrategy.LoadedData data = new SqliteApidocStrategy().load();

        Map<String, CsapDocModelController> controllers = data.controllerMap;
        assertEquals(1, controllers.size());
        CsapDocModelController ctrl = controllers.get("ai.csap.apidoc.web.SqliteController");
        assertNotNull("controller should be loaded", ctrl);

        Map<String, CsapDocMethod> methods = data.methodMap.get("ai.csap.apidoc.web.SqliteController");
        assertNotNull("method map should be present", methods);

        // GET getSqlite — bearer + 2 global headers (parity with YamlHintsTest)
        CsapDocMethod get = methods.get("getSqlite");
        assertNotNull("getSqlite method should be loaded", get);

        List<CsapDocGlobalHeaderHint> headers = get.getGlobalHeaderHints();
        assertNotNull("globalHeaderHints should be loaded", headers);
        assertEquals(2, headers.size());

        CsapDocGlobalHeaderHint tenant = headers.get(0);
        assertEquals("X-Tenant-Id", tenant.getName());
        assertEquals("tenant-demo", tenant.getExample());
        assertTrue("X-Tenant-Id should be required", tenant.isRequired());

        CsapDocGlobalHeaderHint trace = headers.get(1);
        assertEquals("X-Trace-Id", trace.getName());
        assertFalse("X-Trace-Id should be optional", trace.isRequired());

        CsapDocAuthHint auth = get.getAuthHint();
        assertNotNull("authHint should be loaded", auth);
        assertEquals("bearer", auth.getScheme());
        assertNull("bearer scheme must not carry 'in'", auth.getIn());
        assertNull("bearer scheme must not carry 'name'", auth.getName());

        // PUT putSqlite — apikey scheme with in_location/param_name populated
        CsapDocMethod put = methods.get("putSqlite");
        assertNotNull("putSqlite method should be loaded", put);

        CsapDocAuthHint apikey = put.getAuthHint();
        assertNotNull("apikey authHint should be loaded", apikey);
        assertEquals("apikey", apikey.getScheme());
        assertEquals("query", apikey.getIn());
        assertEquals("api_key", apikey.getName());
        assertNull("putSqlite has no header hints → field should stay null, not empty",
                put.getGlobalHeaderHints());
    }

    @Test
    @SneakyThrows
    public void legacyDbWithoutHintTablesStillLoads() {
        seedSchemaWithoutHints();

        SqliteApidocStrategy.LoadedData data = new SqliteApidocStrategy().load();
        CsapDocMethod get = data.methodMap
                .get("ai.csap.apidoc.web.SqliteController")
                .get("getSqlite");
        assertNotNull("loader must succeed even when hint tables are missing", get);
        assertNull("absent api_method_global_header_hint → field stays null",
                get.getGlobalHeaderHints());
        assertNull("absent api_method_auth_hint → field stays null",
                get.getAuthHint());
    }

    @SneakyThrows
    private void seedSchemaWithHints() {
        try (Connection conn = DriverManager.getConnection(url);
                Statement st = conn.createStatement()) {
            seedBaseSchema(st);
            // M7.1 hint tables
            st.execute("CREATE TABLE api_method_global_header_hint (" +
                    "method_id INTEGER NOT NULL, name TEXT NOT NULL, description TEXT, " +
                    "example TEXT, required INTEGER DEFAULT 0, position INTEGER DEFAULT 0)");
            st.execute("CREATE TABLE api_method_auth_hint (" +
                    "method_id INTEGER NOT NULL PRIMARY KEY, scheme TEXT NOT NULL, " +
                    "description TEXT, in_location TEXT, param_name TEXT)");

            // hints attached to method_id=1 (getSqlite)
            st.execute("INSERT INTO api_method_global_header_hint " +
                    "(method_id,name,description,example,required,position) VALUES " +
                    "(1,'X-Tenant-Id','多租户隔离标识，由网关注入；调试时手动填','tenant-demo',1,0)");
            st.execute("INSERT INTO api_method_global_header_hint " +
                    "(method_id,name,description,example,required,position) VALUES " +
                    "(1,'X-Trace-Id','链路追踪 ID，可选；空时由 SkyWalking agent 自动生成','0000-0000-0000-0001',0,1)");
            st.execute("INSERT INTO api_method_auth_hint " +
                    "(method_id,scheme,description,in_location,param_name) VALUES " +
                    "(1,'bearer','复用业务 JWT；可在 ui Auth Drawer 切到 OAuth2-CC 自动换 token',NULL,NULL)");
            // apikey example on method_id=2 (putSqlite)
            st.execute("INSERT INTO api_method_auth_hint " +
                    "(method_id,scheme,description,in_location,param_name) VALUES " +
                    "(2,'apikey','后端用 API Key 鉴权，从 query 取','query','api_key')");
        }
    }

    @SneakyThrows
    private void seedSchemaWithoutHints() {
        try (Connection conn = DriverManager.getConnection(url);
                Statement st = conn.createStatement()) {
            seedBaseSchema(st);
        }
    }

    private void seedBaseSchema(Statement st) throws java.sql.SQLException {
        st.execute("CREATE TABLE api_info (" +
                "title TEXT, description TEXT, version TEXT, license TEXT, " +
                "license_url TEXT, service_url TEXT, authorization_type TEXT, " +
                "contact_name TEXT, contact_email TEXT, contact_url TEXT)");
        st.execute("INSERT INTO api_info " +
                "(title,description,version,service_url) VALUES " +
                "('csap-apidoc sqlite hints test','M7.1 fixture','1.x','http://localhost')");

        st.execute("CREATE TABLE controller (id INTEGER PRIMARY KEY, name TEXT, " +
                "simple_name TEXT, title TEXT, description TEXT, position INTEGER, " +
                "hidden INTEGER, status TEXT, protocols TEXT)");
        st.execute("INSERT INTO controller " +
                "(id,name,simple_name,title,description,position,hidden) VALUES " +
                "(10,'ai.csap.apidoc.web.SqliteController','SqliteController','SQLite hints fixture',NULL,1,0)");

        st.execute("CREATE TABLE controller_path (controller_id INTEGER, path TEXT)");
        st.execute("CREATE TABLE controller_group (controller_id INTEGER, group_id INTEGER)");
        st.execute("CREATE TABLE doc_group (id INTEGER, name TEXT)");
        st.execute("CREATE TABLE controller_version (controller_id INTEGER, version_id INTEGER)");
        st.execute("CREATE TABLE doc_version (id INTEGER, name TEXT)");
        st.execute("CREATE TABLE controller_tag (controller_id INTEGER, tag TEXT)");

        st.execute("CREATE TABLE api_method (id INTEGER PRIMARY KEY, controller_id INTEGER, " +
                "method_key TEXT, name TEXT, title TEXT, description TEXT, " +
                "status TEXT, hidden INTEGER, param_type TEXT)");
        st.execute("INSERT INTO api_method " +
                "(id,controller_id,method_key,name,title,description,hidden) VALUES " +
                "(1,10,'getSqlite','getSqlite','获取信息（SQLite hints）','GET demo',0)");
        st.execute("INSERT INTO api_method " +
                "(id,controller_id,method_key,name,title,description,hidden) VALUES " +
                "(2,10,'putSqlite','putSqlite','修改信息（apikey 示例）','PUT demo',0)");

        st.execute("CREATE TABLE api_method_http (method_id INTEGER, http_method TEXT)");
        st.execute("INSERT INTO api_method_http (method_id,http_method) VALUES (1,'GET'),(2,'PUT')");
        st.execute("CREATE TABLE api_method_api_path (method_id INTEGER, api_path TEXT)");
        st.execute("CREATE TABLE api_method_path (method_id INTEGER, path TEXT)");
        st.execute("INSERT INTO api_method_path (method_id,path) VALUES (1,'/getSqlite'),(2,'/putSqlite')");
        st.execute("CREATE TABLE api_method_header (method_id INTEGER, header_key TEXT, " +
                "header_value TEXT, description TEXT, required INTEGER, example TEXT, " +
                "position INTEGER, hidden INTEGER)");

        // model + parameter tables are optional for these assertions but the
        // loader queries them, so create empty stubs to avoid SQLException.
        st.execute("CREATE TABLE api_model (id INTEGER PRIMARY KEY, method_id INTEGER, " +
                "name TEXT, title TEXT, description TEXT, model_type TEXT, " +
                "FORCE INTEGER, GLOBAL INTEGER, method_param_name TEXT, io_type TEXT)");
        st.execute("CREATE TABLE api_param (id INTEGER PRIMARY KEY, model_id INTEGER, " +
                "parent_id INTEGER, name TEXT, key TEXT, key_name TEXT, value TEXT, " +
                "description TEXT, example TEXT, data_type TEXT, long_data_type TEXT, " +
                "model_type TEXT, param_type TEXT, required INTEGER, hidden INTEGER, " +
                "force INTEGER, default_value TEXT, position INTEGER, decimals INTEGER, " +
                "length INTEGER)");
    }

}
