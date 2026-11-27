package ai.csap.apidoc.team.env;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import ai.csap.apidoc.database.DatabaseDialect;
import ai.csap.apidoc.database.DatabaseRuleStore;
import ai.csap.apidoc.team.common.ApiException;
import ai.csap.apidoc.team.config.TeamProperties;
import ai.csap.apidoc.team.db.Db;
import ai.csap.apidoc.team.db.Settings;
import jakarta.annotation.PreDestroy;

/**
 * 环境注册表:按配置(csap.team.environments)建立各环境的开源表访问。未配置时只有一个环境 {@code default}。
 * 配置错误时启动失败并给出中文说明;环境数据源连不上不阻止启动,状态为 DOWN,每 60 秒重试。
 */
@Component
public class Environments {

    public static final String DEFAULT_KEY = "default";
    public static final String HISTORY_CURSOR_PREFIX = "oss.history.";
    private static final Pattern KEY = Pattern.compile("[a-z0-9-]{1,32}");
    private static final Pattern PREFIX = Pattern.compile("[A-Za-z0-9_]*");
    private static final Logger log = LoggerFactory.getLogger(Environments.class);

    private final TeamProperties props;
    private final Db mainDb;
    private final List<Env> envs;
    private final Map<String, Env> byKey = new LinkedHashMap<>();
    private Settings settings;

    public Environments(TeamProperties props, Db mainDb, org.springframework.core.env.Environment spring) {
        this.props = props;
        this.mainDb = mainDb;
        List<TeamProperties.Environment> cfg = props.getEnvironments();
        String mainPrefix = props.getRuleTablePrefix() == null ? "csap_apidoc_" : props.getRuleTablePrefix();
        List<Env> out = new ArrayList<>();
        try {
            if (cfg == null || cfg.isEmpty()) {
                out.add(new Env(DEFAULT_KEY, "默认", 0, false, false, mainDb.withPrefix(mainPrefix), null, null));
            } else {
                String mainUrl = spring.getProperty("spring.datasource.url");
                Set<String> keys = new HashSet<>();
                Set<String> sources = new HashSet<>();
                for (int i = 0; i < cfg.size(); i++) {
                    TeamProperties.Environment c = cfg.get(i);
                    String at = "csap.team.environments[" + i + "]";
                    if (c.getKey() == null || !KEY.matcher(c.getKey()).matches()) {
                        throw new IllegalStateException(at + ".key 只能由小写字母、数字和 - 组成,长度 1–32");
                    }
                    if (!keys.add(c.getKey())) {
                        throw new IllegalStateException("环境 key 重复: " + c.getKey());
                    }
                    if (c.getName() == null || c.getName().isBlank()) {
                        throw new IllegalStateException("环境 " + c.getKey() + " 的 name 不能为空");
                    }
                    String prefix = c.getRuleTablePrefix() == null || c.getRuleTablePrefix().isEmpty() ? mainPrefix : c.getRuleTablePrefix();
                    if (!PREFIX.matcher(prefix).matches()) {
                        throw new IllegalStateException("环境 " + c.getKey() + " 的 rule-table-prefix 只能包含字母、数字和下划线");
                    }
                    TeamProperties.EnvDatasource ds = c.getDatasource();
                    boolean own = ds != null && ds.getUrl() != null && !ds.getUrl().isBlank() && !ds.getUrl().trim().equals(mainUrl);
                    if (ds != null && (ds.getUrl() == null || ds.getUrl().isBlank())
                            && (ds.getUsername() != null || ds.getPassword() != null)) {
                        throw new IllegalStateException("环境 " + c.getKey() + " 配置了 datasource 但没有 url");
                    }
                    String source = (own ? "ds:" + ds.getUrl().trim() + "|" + nz(ds.getUsername()) : "main") + "|" + prefix;
                    if (!sources.add(source)) {
                        throw new IllegalStateException("环境 " + c.getKey()
                                + " 与另一个环境使用了同一个数据源和同一个开源表前缀,请为它指定不同的 rule-table-prefix 或数据源");
                    }
                    if (own) {
                        out.add(buildOwn(c, i, prefix));
                    } else {
                        out.add(new Env(c.getKey(), c.getName().trim(), i, c.isProtected(), c.isReadOnly(), mainDb.withPrefix(prefix), null, null));
                    }
                }
            }
        } catch (RuntimeException e) {
            out.forEach(Env::close);
            throw e;
        }
        this.envs = List.copyOf(out);
        for (Env e : envs) {
            byKey.put(e.key(), e);
        }
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    private Env buildOwn(TeamProperties.Environment c, int order, String prefix) {
        TeamProperties.EnvDatasource ds = c.getDatasource();
        String url = ds.getUrl().trim();
        DatabaseDialect dialect = DatabaseDialect.fromJdbcUrl(url);
        if (dialect == null) {
            dialect = DatabaseDialect.parse(props.getDatabase().getDialect());
        }
        if (dialect == null) {
            throw new IllegalStateException("环境 " + c.getKey() + " 的数据源无法从 JDBC URL 识别方言(mysql / postgresql / dm / kingbase / h2)");
        }
        HikariConfig hc = new HikariConfig();
        hc.setJdbcUrl(url);
        hc.setUsername(ds.getUsername());
        hc.setPassword(ds.getPassword());
        if (ds.getDriverClassName() != null && !ds.getDriverClassName().isBlank()) {
            hc.setDriverClassName(ds.getDriverClassName().trim());
        }
        hc.setMaximumPoolSize(5);
        hc.setMinimumIdle(0);
        hc.setConnectionTimeout(3000);
        hc.setInitializationFailTimeout(-1);
        hc.setPoolName("csap-team-env-" + c.getKey());
        HikariDataSource pool = new HikariDataSource(hc);
        Db db = new Db(pool, new DataSourceTransactionManager(pool), dialect, prefix);
        return new Env(c.getKey(), c.getName().trim(), order, c.isProtected(), c.isReadOnly(), db, pool, ds.getPassword());
    }

    @PreDestroy
    void shutdown() {
        envs.forEach(Env::close);
    }

    // ---- 查询 ----

    public List<Env> all() {
        return envs;
    }

    public Env defaultEnv() {
        return envs.get(0);
    }

    /** 按 key 取,null / 空 = 默认环境;不存在抛 VALIDATION。 */
    public Env require(String key) {
        if (key == null || key.isBlank()) {
            return defaultEnv();
        }
        Env e = byKey.get(key.trim());
        if (e == null) {
            throw ApiException.validation("环境不存在: " + key);
        }
        return e;
    }

    /** 按 key 取,不存在返回 null。 */
    public Env find(String key) {
        return key == null ? null : byKey.get(key);
    }

    public boolean multiple() {
        return envs.size() > 1;
    }

    // ---- 初始化 / 重连 ----

    /** 主库的团队版表就绪后调用:对每个环境建表(create-tables=true)、探测状态、初始化同步游标。 */
    public void init(Settings settings, boolean createTables) {
        this.settings = settings;
        for (Env e : envs) {
            probe(e, createTables);
        }
    }

    @Scheduled(initialDelay = 60, fixedDelay = 60, timeUnit = TimeUnit.SECONDS)
    public void retryDown() {
        if (settings == null) {
            return;
        }
        for (Env e : envs) {
            if (!e.isUp()) {
                probe(e, props.getDatabase().isCreateTables());
            }
        }
    }

    /** 探测一个环境;可重复调用。 */
    public synchronized void probe(Env e, boolean createTables) {
        try {
            ensureOssTables(e.rawDb(), createTables);
            e.setUp();
            ensureCursor(e);
            log.info("环境 {}({})可用", e.key(), e.name());
        } catch (RuntimeException ex) {
            e.setDown(rootMessage(ex));
            log.warn("环境 {}({})不可用: {}", e.key(), e.name(), e.error());
        }
    }

    private static String rootMessage(Throwable t) {
        Throwable r = t;
        while (r.getCause() != null && r.getCause() != r) {
            r = r.getCause();
        }
        String m = r.getMessage();
        if (m == null || m.isBlank()) {
            m = r.getClass().getSimpleName();
        }
        int nl = m.indexOf('\n');
        return nl > 0 ? m.substring(0, nl) : m;
    }

    /** 同步游标不存在时(新环境首次出现)从当前最大 history id 开始。 */
    public void ensureCursor(Env e) {
        String name = HISTORY_CURSOR_PREFIX + e.key();
        if (settings.cursor(name) == null) {
            // 新环境首次出现:从当前最大 id 开始,之前的历史不回填审计
            Long max = e.rawDb().one("SELECT MAX(id) FROM " + e.historyTable(), (rs, i) -> {
                long v = rs.getLong(1);
                return rs.wasNull() ? 0L : v;
            });
            settings.setCursor(name, max == null ? 0 : max);
        }
    }

    private void ensureOssTables(Db db, boolean create) {
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
            throw new IllegalStateException("开源规则表 " + db.prefix() + "rule / history / meta 不存在。环境应连接服务共用的库;"
                    + "也可设置 csap.team.database.create-tables=true 在该库创建");
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
}
