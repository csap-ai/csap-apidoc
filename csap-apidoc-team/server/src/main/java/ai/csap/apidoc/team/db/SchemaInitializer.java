package ai.csap.apidoc.team.db;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.stereotype.Component;

import ai.csap.apidoc.team.audit.AuditChain;
import ai.csap.apidoc.team.audit.AuditService;
import ai.csap.apidoc.team.auth.Actor;
import ai.csap.apidoc.team.config.TeamProperties;
import ai.csap.apidoc.team.env.Environments;

/**
 * 启动时检查并建表 / 升级:团队版自己的表(META-INF/csap-team/schema/{方言}.sql,新库直接是 schema 版本 2),
 * 1.0 的库(版本 1)执行 upgrade/2/{方言}.sql 再做 Java 数据迁移,全部成功后写版本 2;各环境的开源表由 {@link Environments} 处理。
 * 同时写入令牌签名密钥。
 */
@Component
public class SchemaInitializer implements SmartInitializingSingleton {

    public static final String SCHEMA_VERSION = "2";
    /** 1.0 的同步游标名;1.1 起按环境 {@code oss.history.{env}} */
    public static final String LEGACY_HISTORY_CURSOR = "oss.history";
    public static final String LEGACY_MCP_CURSOR = "oss.mcp_audit";
    private static final Logger log = LoggerFactory.getLogger(SchemaInitializer.class);

    private final Db db;
    private final Settings settings;
    private final TeamProperties props;
    private final Environments environments;
    private final AuditChain chain;
    private final AuditService audit;

    public SchemaInitializer(Db db, Settings settings, TeamProperties props, Environments environments, AuditChain chain,
                             AuditService audit) {
        this.db = db;
        this.settings = settings;
        this.props = props;
        this.environments = environments;
        this.chain = chain;
        this.audit = audit;
    }

    /** 所有单例创建完、定时任务启动之前执行。 */
    @Override
    public void afterSingletonsInstantiated() {
        init();
    }

    /** 可重复调用。 */
    public synchronized void init() {
        boolean create = props.getDatabase().isCreateTables();
        boolean fresh = false;
        if (!db.tableExists("csap_team_setting")) {
            if (!create) {
                throw new IllegalStateException("团队版表不存在。请先执行 META-INF/csap-team/schema/"
                        + db.dialect().name().toLowerCase() + ".sql,或设置 csap.team.database.create-tables=true");
            }
            log.info("创建团队版数据表(方言 {})", db.dialect());
            runScript(readScript("META-INF/csap-team/schema/" + db.dialect().name().toLowerCase() + ".sql"));
            fresh = true;
        }
        String version = settings.get("schema.version");
        if (version == null) {
            // 全新建表 -> 2;没有版本号但表已存在(异常中断)按 1 处理
            if (fresh) {
                settings.set("schema.version", SCHEMA_VERSION);
                chain.setHead(0, AuditChain.ZERO);
            } else {
                version = "1";
            }
        }
        if ("1".equals(version)) {
            upgradeTo2(create);
        }
        if (settings.get(AuditChain.HEAD) == null) {
            chain.setHead(0, AuditChain.ZERO);
        }
        if (settings.get("token.signing_key") == null) {
            byte[] key = new byte[48];
            new SecureRandom().nextBytes(key);
            settings.set("token.signing_key", Base64.getEncoder().encodeToString(key));
        }
        environments.init(settings, create);
    }

    // ---- 1 -> 2 ----

    private void upgradeTo2(boolean create) {
        log.info("团队版数据库升级:schema 版本 1 → 2");
        boolean tablesPresent = db.tableExists("csap_team_service_env") && db.tableExists("csap_team_env_instance")
                && db.tableExists("csap_team_comment");
        if (!tablesPresent) {
            if (!create) {
                String d = db.dialect().name().toLowerCase();
                throw new IllegalStateException("团队版数据库需要升级到 schema 版本 2。请手工执行 META-INF/csap-team/schema/upgrade/2/" + d
                        + ".sql,或设置 csap.team.database.create-tables=true 自动升级");
            }
            runUpgradeScript(readScript("META-INF/csap-team/schema/upgrade/2/" + db.dialect().name().toLowerCase() + ".sql"));
        }
        migrateData();
        settings.set("schema.version", SCHEMA_VERSION);
        log.info("团队版数据库已升级到 schema 版本 2");
    }

    /** Java 部分的数据迁移(契约 7);每一步都可重复执行。 */
    void migrateData() {
        String k = environments.defaultEnv().key();
        // 2. 服务地址 -> csap_team_service_env
        db.update("INSERT INTO csap_team_service_env (service, env_key, base_url, doc_path, source, last_seen_at, created_at)"
                + " SELECT service, ?, base_url, doc_path, source, last_seen_at, created_at FROM csap_team_project_service p"
                + " WHERE NOT EXISTS (SELECT 1 FROM csap_team_service_env e WHERE e.service = p.service AND e.env_key = ?)", k, k);
        // 3. 修改单的环境(审计的 env_key 不回填)
        db.update("UPDATE csap_team_change SET env_key = ? WHERE env_key IS NULL", k);
        // 4. csap_team_service_instance 的数据丢弃(实例一个心跳周期内会重新注册),表保留
        // 5. 游标改名
        renameCursor(LEGACY_HISTORY_CURSOR, Environments.HISTORY_CURSOR_PREFIX + k);
        renameCursor(LEGACY_MCP_CURSOR, LEGACY_MCP_CURSOR + "." + k);
        // 6. 审计哈希链回填并封存
        if (db.count("SELECT COUNT(*) FROM csap_team_audit WHERE hash IS NULL") > 0 || settings.get(AuditChain.HEAD) == null) {
            long n = chain.backfill();
            audit.logAs(Actor.system(), AuditChain.SEALED_ACTION).reason("sealed " + n + " records at upgrade").save();
            log.info("审计哈希链:封存 {} 条旧记录", n);
        }
    }

    private void renameCursor(String from, String to) {
        Long v = settings.cursor(from);
        if (v == null) {
            return;
        }
        if (settings.cursor(to) == null) {
            settings.setCursor(to, v);
        }
        db.update("DELETE FROM csap_team_cursor WHERE name = ?", from);
    }

    private String readScript(String path) {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalStateException("缺少建表脚本 " + path);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("读取建表脚本失败: " + e.getMessage(), e);
        }
    }

    static List<String> split(String script) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        for (String line : script.split("\r?\n")) {
            if (line.trim().startsWith("--")) {
                continue;
            }
            cur.append(line).append('\n');
            if (line.trim().endsWith(";")) {
                String s = cur.toString().trim();
                out.add(s.substring(0, s.length() - 1));
                cur.setLength(0);
            }
        }
        if (!cur.toString().isBlank()) {
            out.add(cur.toString().trim());
        }
        return out;
    }

    private void runScript(String script) {
        for (String sql : split(script)) {
            db.jdbc().execute(sql);
        }
    }

    /** 升级脚本可重复执行:表 / 列已存在就跳过(上一次升级中途失败后再启动)。 */
    private void runUpgradeScript(String script) {
        for (String sql : split(script)) {
            String[] w = sql.trim().split("\\s+");
            if (w.length > 5 && "CREATE".equals(w[0]) && "TABLE".equals(w[1]) && db.tableExists(w[2])) {
                continue;
            }
            if (w.length > 5 && "ALTER".equals(w[0]) && "TABLE".equals(w[1]) && "ADD".equals(w[3])) {
                String col = "COLUMN".equals(w[4]) ? w[5] : w[4];
                if (db.columnExists(w[2], col)) {
                    continue;
                }
            }
            try {
                db.jdbc().execute(sql);
            } catch (RuntimeException e) {
                if (sql.startsWith("CREATE INDEX")) {
                    log.warn("创建索引失败(可能已存在),已跳过: {}", e.getMessage());
                } else {
                    throw e;
                }
            }
        }
    }
}
