package ai.csap.apidoc.team.unit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import ai.csap.apidoc.team.audit.AuditController;
import ai.csap.apidoc.team.auth.ClientIp;
import ai.csap.apidoc.team.auth.LoginGuard;
import ai.csap.apidoc.team.auth.PasswordService;
import ai.csap.apidoc.team.common.Json;
import ai.csap.apidoc.team.common.UnifiedDiff;
import ai.csap.apidoc.team.config.TeamProperties;
import ai.csap.apidoc.team.db.Db;
import ai.csap.apidoc.team.docs.DocsService;
import ai.csap.apidoc.team.token.TokenService;
import ai.csap.apidoc.team.webhook.WebhookEvent;
import ai.csap.apidoc.team.webhook.WebhookService;

class UnitTests {

    // ---- 统一差异 ----

    @Test
    void unifiedDiffOfRules() {
        assertEquals("", UnifiedDiff.ofRules("{\"a\":1,\"b\":2}", "{ \"b\": 2, \"a\": 1 }", "x", "y"), "键顺序和空白不同但内容相同");
        String d = UnifiedDiff.ofRules("{\"a\":1}", "{\"a\":2}", "before", "after");
        assertTrue(d.startsWith("--- before\n+++ after\n@@"));
        assertTrue(d.contains("-  \"a\" : 1"));
        assertTrue(d.contains("+  \"a\" : 2"));
        // 新建与删除
        String created = UnifiedDiff.ofRules(null, "{\"a\":1}", "before", "after");
        String createdBody = created.substring(created.indexOf("@@"));
        assertTrue(createdBody.contains("+{") && !createdBody.contains("\n-"));
        String deleted = UnifiedDiff.ofRules("{\"a\":1}", null, "before", "after");
        String deletedBody = deleted.substring(deleted.indexOf("@@"));
        assertTrue(deletedBody.contains("-{") && !deletedBody.contains("\n+"));
        assertEquals("", UnifiedDiff.ofRules(null, null, "x", "y"));
    }

    @Test
    void unifiedDiffHunksKeepContextAndSplitDistantChanges() {
        StringBuilder a = new StringBuilder();
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < 40; i++) {
            a.append("line").append(i).append('\n');
            b.append(i == 3 ? "CHANGED3" : i == 36 ? "CHANGED36" : "line" + i).append('\n');
        }
        String d = UnifiedDiff.diff(List.of(a.toString().split("\n")), List.of(b.toString().split("\n")), "a", "b");
        assertEquals(2, d.split("@@ -").length - 1, "两处相距很远的修改应拆成两个 hunk: " + d);
        assertTrue(d.contains("-line3\n+CHANGED3"));
        assertTrue(d.contains(" line0\n"), "保留上下文");
        assertFalse(d.contains("line20"), "中间无关行不输出");
    }

    @Test
    void jsonPrettySortsKeys() {
        assertEquals(Json.pretty("{\"b\":1,\"a\":{\"d\":1,\"c\":2}}"), Json.pretty("{\"a\":{\"c\":2,\"d\":1},\"b\":1}"));
        assertEquals("not json", Json.pretty("not json"));
        assertNull(Json.pretty(null));
        assertTrue(Json.isValid("{}") && !Json.isValid("{"));
    }

    // ---- 安全相关 ----

    @Test
    void tokenFormatAndHash() {
        String t = TokenService.generate();
        assertTrue(t.matches("csapt_[0-9A-Za-z]{40}"), t);
        assertEquals(64, TokenService.sha256(t).length());
        assertEquals(TokenService.sha256(t), TokenService.sha256(t));
        assertFalse(TokenService.generate().equals(t));
    }

    @Test
    void passwordRulesAndBcrypt() {
        PasswordService p = new PasswordService();
        String h = p.hash("Passw0rd!!");
        assertTrue(h.startsWith("$2"));
        assertTrue(p.matches("Passw0rd!!", h));
        assertFalse(p.matches("other", h));
        assertFalse(p.matches("x", "not-a-hash"));
        assertThrows(RuntimeException.class, () -> p.validate("short"));
        assertThrows(RuntimeException.class, () -> p.validate("x".repeat(73)));
        p.validate("12345678");
    }

    @Test
    void loginGuardLocksPerAccountAndIpWithGlobalAccountCap() {
        LoginGuard g = new LoginGuard();
        long t0 = 1_000_000L;
        for (int i = 0; i < 4; i++) {
            g.fail("Alice", "1.1.1.1", t0);
            assertFalse(g.isLocked("alice", "1.1.1.1", t0));
        }
        g.fail("alice", "1.1.1.1", t0);
        assertTrue(g.isLocked("ALICE", "1.1.1.1", t0 + 1000), "账号不区分大小写");
        assertFalse(g.isLocked("alice", "2.2.2.2", t0 + 1000), "别的来源 IP 不受影响,攻击者锁不住真用户");
        assertTrue(g.isLocked("alice", "1.1.1.1", t0 + 14 * 60_000));
        assertFalse(g.isLocked("alice", "1.1.1.1", t0 + 15 * 60_000 + 1));
        g.fail("bob", "1.1.1.1", t0);
        g.success("bob", "1.1.1.1");
        for (int i = 0; i < 4; i++) {
            g.fail("bob", "1.1.1.1", t0);
        }
        assertFalse(g.isLocked("bob", "1.1.1.1", t0), "成功登录后该来源的计数清零");
        // 同一账号 15 分钟内多个 IP 合计 50 次 -> 对所有 IP 锁定
        LoginGuard h = new LoginGuard();
        for (int i = 0; i < 49; i++) {
            h.fail("carol", "10.0.0." + (i % 200), t0 + i);
        }
        assertFalse(h.isLocked("carol", "9.9.9.9", t0 + 100));
        h.fail("carol", "10.1.1.1", t0 + 60);
        assertTrue(h.isLocked("carol", "9.9.9.9", t0 + 100));
        assertFalse(h.isLocked("carol", "9.9.9.9", t0 + 15 * 60_000 + 100));
    }

    @Test
    void webhookUrlIsMasked() {
        assertEquals("https://qyapi.weixin.qq.com/***", WebhookService.maskUrl("https://qyapi.weixin.qq.com/cgi-bin/webhook/send?key=abc"));
        assertEquals("http://h:8080/***", WebhookService.maskUrl("http://h:8080/x"));
    }

    @Test
    void clientIpTrustsForwardedHeaderOnlyFromTrustedProxies() {
        TeamProperties props = new TeamProperties();
        ClientIp none = new ClientIp(props);
        assertEquals("9.9.9.9", none.resolve(request("9.9.9.9", "1.2.3.4")), "默认不信任 X-Forwarded-For");
        props.setTrustedProxies(List.of("10.0.0.0/8", "192.168.1.1"));
        ClientIp ip = new ClientIp(props);
        assertEquals("1.2.3.4", ip.resolve(request("10.1.2.3", "1.2.3.4")));
        assertEquals("1.2.3.4", ip.resolve(request("10.1.2.3", "6.6.6.6, 1.2.3.4, 10.9.9.9")) .equals("1.2.3.4") ? "1.2.3.4" : "x");
        assertEquals("9.9.9.9", ip.resolve(request("9.9.9.9", "1.2.3.4")), "来自不可信来源的头被忽略");
        assertEquals("10.1.2.3", ip.resolve(request("10.1.2.3", null)));
        assertThrows(IllegalArgumentException.class, () -> {
            TeamProperties bad = new TeamProperties();
            bad.setTrustedProxies(List.of("not-an-ip"));
            new ClientIp(bad);
        });
    }

    private static jakarta.servlet.http.HttpServletRequest request(String remote, String xff) {
        org.springframework.mock.web.MockHttpServletRequest r = new org.springframework.mock.web.MockHttpServletRequest();
        r.setRemoteAddr(remote);
        if (xff != null) {
            r.addHeader("X-Forwarded-For", xff);
        }
        return r;
    }

    @Test
    void tablePrefixOnlyAllowsSafeCharacters() {
        DriverManagerDataSource ds = new DriverManagerDataSource("jdbc:h2:mem:prefixtest");
        TeamProperties ok = new TeamProperties();
        ok.setRuleTablePrefix("app_1_");
        assertEquals("app_1_rule", new Db(ds, new DataSourceTransactionManager(ds), ok).ruleTable());
        for (String bad : new String[] {"a-b", "x; DROP TABLE y", "a b", "表", "a.b", "a`b"}) {
            TeamProperties p = new TeamProperties();
            p.setRuleTablePrefix(bad);
            assertThrows(IllegalArgumentException.class, () -> new Db(ds, new DataSourceTransactionManager(ds), p), bad);
        }
    }

    // ---- CSV ----

    @Test
    void csvCellsAreEscapedAndFormulaProtected() {
        assertEquals("plain", AuditController.cell("action", Map.of("action", "plain")));
        assertEquals("\"a,b\"", AuditController.cell("action", Map.of("action", "a,b")));
        assertEquals("\"say \"\"hi\"\"\"", AuditController.cell("action", Map.of("action", "say \"hi\"")));
        assertEquals("'=SUM(A1)", AuditController.cell("reason", Map.of("reason", "=SUM(A1)")));
        assertEquals("'--- before\n".replace("\n", "\n"), "'--- before\n");
        assertTrue(AuditController.cell("diff", Map.of("diff", "--- before\n+++ after")).startsWith("\"'---"));
        assertEquals("", AuditController.cell("service", new java.util.HashMap<>()));
        assertEquals("42", AuditController.cell("id", Map.of("id", 42L)));
        assertTrue(AuditController.cell("time", Map.of("at", 0L)).startsWith("19"));
    }

    // ---- Webhook 请求组装 ----

    @Test
    void webhookRequestsFollowEachPlatformFormat() throws Exception {
        TeamProperties props = new TeamProperties();
        props.setPublicUrl("https://t.example.com");
        WebhookService svc = new WebhookService(null, props);
        WebhookEvent ev = new WebhookEvent("change.published", 1763452800000L, 1L, "订单", 12L, "标题", "张三",
                List.of(WebhookEvent.item("order-service", "K")), "[订单] 张三 的修改单「标题」已发布(1 条规则)");
        var generic = svc.buildRequest(new WebhookService.Hook(1, 1L, "n", "GENERIC", "http://h/x", "sec", "*", true, 0, 0), ev);
        assertEquals("change.published", generic.headers().firstValue("X-CSAP-Team-Event").orElseThrow());
        assertTrue(generic.headers().firstValue("X-CSAP-Team-Signature").orElseThrow().startsWith("sha256="));
        var noSecret = svc.buildRequest(new WebhookService.Hook(1, 1L, "n", "GENERIC", "http://h/x", null, "*", true, 0, 0), ev);
        assertTrue(noSecret.headers().firstValue("X-CSAP-Team-Signature").isEmpty());
        var ding = svc.buildRequest(new WebhookService.Hook(1, 1L, "n", "DINGTALK", "http://h/x", "s", "*", true, 0, 0), ev);
        assertTrue(ding.uri().getQuery().contains("timestamp=") && ding.uri().getQuery().contains("sign="));
        var dingNoSecret = svc.buildRequest(new WebhookService.Hook(1, 1L, "n", "DINGTALK", "http://h/x", null, "*", true, 0, 0), ev);
        assertNull(dingNoSecret.uri().getQuery());
        assertEquals("https://t.example.com/changes/12", svc.link(ev));
        props.setPublicUrl("");
        assertNull(svc.link(ev), "未配置 public-url 不带链接");
    }

    // ---- 文档索引 ----

    @Test
    void docsSearchMatchesChineseSubstrings() throws Exception {
        String doc = "{\"data\":{\"apiList\":[{\"methodList\":[{\"name\":\"创建订单\",\"description\":\"下单并扣减库存\",\"paths\":[\"/orders\"],"
                + "\"methods\":[\"POST\"],\"request\":[{\"parameters\":[{\"name\":\"buyer\",\"description\":\"买家姓名\"}]}],\"response\":[]}]}]}}";
        var index = DocsService.class.getDeclaredMethod("buildIndex", String.class, com.fasterxml.jackson.databind.JsonNode.class);
        index.setAccessible(true);
        @SuppressWarnings("unchecked")
        List<DocsService.Entry> entries = (List<DocsService.Entry>) index.invoke(null, "svc", Json.read(doc));
        assertEquals(1, entries.size());
        assertEquals("POST", entries.get(0).method());
        assertEquals("/orders", entries.get(0).path());
        assertEquals(1, entries.get(0).fields().size());
        // 根节点没有 data 包装时也能解析(Go / Java 实现都可能直接返回)
        @SuppressWarnings("unchecked")
        List<DocsService.Entry> bare = (List<DocsService.Entry>) index.invoke(null, "svc",
                Json.read("{\"apiList\":[{\"methodList\":[{\"name\":\"x\",\"paths\":[\"/a\",\"/b\"],\"methods\":[\"GET\",\"POST\"]}]}]}"));
        assertEquals(4, bare.size(), "每个路径 × 方法一条");
        // 完整路径 = 控制器 path[i] + 方法 paths[j];标题优先取方法 value
        @SuppressWarnings("unchecked")
        List<DocsService.Entry> full = (List<DocsService.Entry>) index.invoke(null, "svc", Json.read("{\"data\":{\"apiList\":["
                + "{\"path\":[\"/api/v1/users\",\"/api/v2/users/\"],\"methodList\":["
                + "{\"name\":\"get\",\"value\":\"查询用户\",\"paths\":[\"/{id}\",\"/by-name\"],\"methods\":[\"GET\",\"HEAD\"]},"
                + "{\"name\":\"list\",\"value\":\"\",\"paths\":[],\"methods\":[\"GET\"]}]}]}}"));
        assertEquals(8 + 2, full.size());
        assertTrue(full.stream().anyMatch(e -> e.path().equals("/api/v1/users/{id}") && e.method().equals("GET") && e.title().equals("查询用户")));
        assertTrue(full.stream().anyMatch(e -> e.path().equals("/api/v2/users/by-name") && e.method().equals("HEAD")));
        assertTrue(full.stream().anyMatch(e -> e.path().equals("/api/v1/users") && e.title().equals("list")), "方法 paths 为空 = 控制器 path,value 为空取 name");
        assertTrue(full.stream().anyMatch(e -> e.path().equals("/api/v2/users/")));
    }

    // ---- 建表脚本 ----

    private static final Pattern TABLE = Pattern.compile("CREATE TABLE (csap_team_\\w+)");

    @Test
    void schemaScriptsExistForEveryDialectAndMatchTheTemplate() throws Exception {
        List<String> expected = List.of("csap_team_setting", "csap_team_user", "csap_team_project", "csap_team_project_service",
                "csap_team_service_instance", "csap_team_member", "csap_team_token", "csap_team_change", "csap_team_change_item",
                "csap_team_audit", "csap_team_webhook", "csap_team_webhook_delivery", "csap_team_cursor", "csap_team_service_env",
                "csap_team_env_instance", "csap_team_comment");
        String template = Files.readString(Paths.get("schema-template.sql"), StandardCharsets.UTF_8);
        Map<String, String[]> dialects = Map.of(
                "mysql", new String[] {"LONGTEXT", "id BIGINT AUTO_INCREMENT PRIMARY KEY", " ENGINE=InnoDB DEFAULT CHARSET=utf8mb4"},
                "postgresql", new String[] {"TEXT", "id BIGSERIAL PRIMARY KEY", ""},
                "dm", new String[] {"CLOB", "id BIGINT IDENTITY(1, 1) PRIMARY KEY", ""},
                "kingbase", new String[] {"TEXT", "id BIGSERIAL PRIMARY KEY", ""},
                "h2", new String[] {"CLOB", "id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY", ""});
        for (Map.Entry<String, String[]> e : dialects.entrySet()) {
            Path p = Paths.get("src/main/resources/META-INF/csap-team/schema/" + e.getKey() + ".sql");
            String sql = Files.readString(p, StandardCharsets.UTF_8);
            String regenerated = template.replace("{{DIALECT}}", e.getKey()).replace("{{TEXT}}", e.getValue()[0])
                    .replace("{{ID}}", e.getValue()[1]).replace("{{OPTS}}", e.getValue()[2]);
            assertEquals(regenerated, sql, e.getKey() + " 与模板不一致,请运行 server/generate-schema.sh");
            List<String> tables = TABLE.matcher(sql).results().map(m -> m.group(1)).toList();
            assertEquals(expected, tables, e.getKey());
            assertFalse(sql.contains("DEFAULT ''"), "不写 DEFAULT ''");
            assertFalse(sql.contains("{{"));
        }
    }

    // ---- 环境配置(契约 1.1)----

    private static ai.csap.apidoc.team.env.Environments envs(TeamProperties props) {
        DriverManagerDataSource ds = new DriverManagerDataSource("jdbc:h2:mem:envcfg");
        Db db = new Db(ds, new DataSourceTransactionManager(ds), props);
        return new ai.csap.apidoc.team.env.Environments(props, db, new org.springframework.mock.env.MockEnvironment()
                .withProperty("spring.datasource.url", "jdbc:h2:mem:envcfg"));
    }

    private static TeamProperties.Environment cfg(String key, String name, String prefix, String url) {
        TeamProperties.Environment e = new TeamProperties.Environment();
        e.setKey(key);
        e.setName(name);
        e.setRuleTablePrefix(prefix);
        if (url != null) {
            TeamProperties.EnvDatasource d = new TeamProperties.EnvDatasource();
            d.setUrl(url);
            d.setUsername("sa");
            e.setDatasource(d);
        }
        return e;
    }

    private static String envError(TeamProperties.Environment... list) {
        TeamProperties p = new TeamProperties();
        p.setEnvironments(List.of(list));
        return assertThrows(IllegalStateException.class, () -> envs(p)).getMessage();
    }

    @Test
    void noEnvironmentsConfiguredMeansOneDefaultEnvironment() {
        var e = envs(new TeamProperties());
        assertEquals(1, e.all().size());
        assertEquals("default", e.defaultEnv().key());
        assertEquals("默认", e.defaultEnv().name());
        assertFalse(e.defaultEnv().isProtected());
        assertFalse(e.defaultEnv().readOnly());
        assertEquals("csap_apidoc_rule", e.defaultEnv().ruleTable());
        assertSame(e.defaultEnv(), e.require(null));
        assertThrows(ai.csap.apidoc.team.common.ApiException.class, () -> e.require("nope"));
    }

    @Test
    void environmentConfigurationIsValidatedWithChineseMessages() {
        assertTrue(envError(cfg("Dev", "开发", null, null)).contains("key"));
        assertTrue(envError(cfg("a_b", "x", null, null)).contains("key"));
        assertTrue(envError(cfg("x".repeat(33), "x", null, null)).contains("key"));
        assertTrue(envError(cfg("dev", "开发", null, null), cfg("dev", "开发 2", "b_", null)).contains("重复"));
        assertTrue(envError(cfg("dev", " ", null, null)).contains("name"));
        assertTrue(envError(cfg("dev", "开发", "a-b", null)).contains("rule-table-prefix"));
        // 同一数据源 + 同一前缀被两个环境共用
        assertTrue(envError(cfg("dev", "开发", null, null), cfg("test", "测试", null, null)).contains("同一个数据源"));
        assertTrue(envError(cfg("a", "A", "p_", "jdbc:h2:mem:x1"), cfg("b", "B", "p_", "jdbc:h2:mem:x1")).contains("同一个数据源"));
        // 数据源不同,或前缀不同都可以
        var ok = new TeamProperties();
        ok.setEnvironments(List.of(cfg("a", "A", null, null), cfg("b", "B", "b_", null), cfg("c", "C", null, "jdbc:h2:mem:envcfg_c")));
        var e = envs(ok);
        assertEquals(List.of("a", "b", "c"), e.all().stream().map(ai.csap.apidoc.team.env.Env::key).toList());
        assertEquals("a", e.defaultEnv().key());
        assertEquals("b_rule", e.find("b").ruleTable());
        assertTrue(e.find("c").ownDataSource());
        assertFalse(e.find("a").ownDataSource());
        e.all().forEach(x -> assertFalse(x.isUp(), "init 之前尚未探测"));
    }

    // ---- 审计哈希链(契约 6.1)----

    @Test
    void auditChainCanonicalFormAndHashMatchTheContractAlgorithm() {
        // 契约示例;期望值由独立的 Python(json.dumps(ensure_ascii=False, separators=(",", ":")) + hashlib.sha256)算出
        Object[] v = ai.csap.apidoc.team.audit.AuditChain.values(12L, 1763452800000L, "USER", 1L, "张三", "CONSOLE", "10.0.0.1",
                "change.publish", 1L, "prod", "order-service", "k", 12L, null, "{}", null, null, 345L,
                ai.csap.apidoc.team.audit.AuditChain.ZERO);
        assertEquals("[12,1763452800000,\"USER\",1,\"张三\",\"CONSOLE\",\"10.0.0.1\",\"change.publish\",1,\"prod\",\"order-service\",\"k\",12,null,"
                + "\"{}\",null,null,345,\"" + "0".repeat(64) + "\"]", ai.csap.apidoc.team.audit.AuditChain.canonical(v));
        assertEquals("cf8a33044d1e0bcca346041875856cc31cd1b14ef9503e98e9e0d45b05c58653",
                ai.csap.apidoc.team.audit.AuditChain.hash(ai.csap.apidoc.team.audit.AuditChain.ZERO, v));
        // 转义:引号、反斜杠、换行、制表符、控制字符用 u00xx 小写十六进制,非 ASCII 原样输出
        Object[] w = ai.csap.apidoc.team.audit.AuditChain.values(1L, 2L, "SYSTEM", null, "s\"q\\\n\t\u0001é", "X", null, "a", null, null, null, null,
                null, "行1\n行2", null, null, null, null, "a".repeat(64));
        assertEquals("[1,2,\"SYSTEM\",null,\"s\\\"q\\\\\\n\\t\\u0001é\",\"X\",null,\"a\",null,null,null,null,null,\"行1\\n行2\",null,null,null,null,\"" + "a".repeat(64) + "\"]",
                ai.csap.apidoc.team.audit.AuditChain.canonical(w));
        assertEquals("7b1747a652441fa5b20aed1b1b6a2a3c06e64fb62b5981ce378f15d2f9f40224",
                ai.csap.apidoc.team.audit.AuditChain.hash("a".repeat(64), w));
    }

    @Test
    void onlyTheChainCodeTouchesAuditRowsAfterTheyAreWritten() throws Exception {
        // 契约 6.2:除写入时补 hash、升级回填和保留期清理外,不得对 csap_team_audit 做 UPDATE / DELETE
        Pattern p = Pattern.compile("(UPDATE\\s+csap_team_audit|DELETE\\s+FROM\\s+csap_team_audit)", Pattern.CASE_INSENSITIVE);
        Map<String, Integer> found = new java.util.TreeMap<>();
        try (var files = Files.walk(Paths.get("src/main/java"))) {
            for (Path f : files.filter(x -> x.toString().endsWith(".java")).toList()) {
                String text = Files.readString(f, StandardCharsets.UTF_8);
                int n = (int) p.matcher(text).results().count();
                if (n > 0) {
                    found.put(f.getFileName().toString(), n);
                }
            }
        }
        assertEquals(Map.of("AuditService.java", 1, "AuditChain.java", 1, "AuditRetention.java", 1), found);
    }

    @Test
    void repositoryHasNoPrivateKeyFiles() throws Exception {
        // 仓库里不得出现私钥文件
        try (var files = Files.walk(Paths.get(".."))) {
            files.filter(f -> !f.toString().contains("/target/") && !f.toString().contains("/.git/") && !f.toString().contains("/node_modules/")
                    && !f.toString().contains("/.claude/")).filter(Files::isRegularFile).forEach(f -> {
                        String n = f.getFileName().toString();
                        assertFalse(n.equals("private.pem") || n.endsWith(".key"), "不得有私钥文件: " + f);
                    });
        }
    }
}
