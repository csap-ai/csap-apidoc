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

import ai.csap.apidoc.database.DatabaseRuleStore;
import ai.csap.apidoc.team.config.TeamProperties;

/**
 * 启动时检查并建表:团队版自己的表(META-INF/csap-team/schema/{方言}.sql)与开源三张表
 * (用开源 {@link DatabaseRuleStore#schema} 生成语句,不复制其代码)。同时写入 schema 版本和令牌签名密钥。
 */
@Component
public class SchemaInitializer implements SmartInitializingSingleton {

    public static final String SCHEMA_VERSION = "1";
    public static final String HISTORY_CURSOR = "oss.history";
    private static final Logger log = LoggerFactory.getLogger(SchemaInitializer.class);

    private final Db db;
    private final Settings settings;
    private final TeamProperties props;

    public SchemaInitializer(Db db, Settings settings, TeamProperties props) {
        this.db = db;
        this.settings = settings;
        this.props = props;
    }

    /** 所有单例创建完、定时任务启动之前执行。 */
    @Override
    public void afterSingletonsInstantiated() {
        init();
    }

    /** 可重复调用。 */
    public synchronized void init() {
        boolean create = props.getDatabase().isCreateTables();
        if (!db.tableExists("csap_team_setting")) {
            if (!create) {
                throw new IllegalStateException("团队版表不存在。请先执行 META-INF/csap-team/schema/"
                        + db.dialect().name().toLowerCase() + ".sql,或设置 csap.team.database.create-tables=true");
            }
            log.info("创建团队版数据表(方言 {})", db.dialect());
            runScript(readScript());
        }
        ensureOssTables(create);
        if (settings.get("schema.version") == null) {
            settings.set("schema.version", SCHEMA_VERSION);
        }
        if (settings.cursor(HISTORY_CURSOR) == null) {
            // 首次启动:从当前最大 id 开始,之前的历史不回填审计
            Long max = db.one("SELECT MAX(id) FROM " + db.historyTable(), (rs, i) -> {
                long v = rs.getLong(1);
                return rs.wasNull() ? 0L : v;
            });
            settings.setCursor(HISTORY_CURSOR, max == null ? 0 : max);
        }
        if (settings.get("token.signing_key") == null) {
            byte[] key = new byte[48];
            new SecureRandom().nextBytes(key);
            settings.set("token.signing_key", Base64.getEncoder().encodeToString(key));
        }
    }

    private void ensureOssTables(boolean create) {
        String[] names = {db.ruleTable(), db.historyTable(), db.metaTable()};
        boolean missing = false;
        for (String t : names) {
            if (!db.tableExists(t)) {
                missing = true;
            }
        }
        if (!missing) {
            return;
        }
        if (!create) {
            throw new IllegalStateException("开源规则表 " + db.prefix() + "rule/history/meta 不存在。团队版应连接服务共用的库;"
                    + "也可设置 csap.team.database.create-tables=true 在本库创建");
        }
        for (String sql : DatabaseRuleStore.schema(db.dialect(), db.prefix())) {
            String table = sql.contains("CREATE INDEX") ? null : sql.split("\\s+")[2];
            if (table != null && db.tableExists(table)) {
                continue;
            }
            try {
                db.jdbc().execute(sql);
            } catch (RuntimeException e) {
                if (!(sql.startsWith("CREATE INDEX"))) {
                    throw e;
                }
            }
        }
        log.info("已创建开源规则表 {}rule / history / meta", db.prefix());
    }

    private String readScript() {
        String path = "META-INF/csap-team/schema/" + db.dialect().name().toLowerCase() + ".sql";
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
}
