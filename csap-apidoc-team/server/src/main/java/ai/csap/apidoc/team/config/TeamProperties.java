package ai.csap.apidoc.team.config;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 团队版配置,前缀 {@code csap.team}。
 */
@ConfigurationProperties(prefix = "csap.team")
public class TeamProperties {

    /** 控制台外部访问地址,用于通知里的链接 */
    private String publicUrl = "";
    /** 开源表前缀,必须与服务一致;只允许字母、数字、下划线 */
    private String ruleTablePrefix = "csap_apidoc_";
    /** 信任其 X-Forwarded-For 的代理 IP / CIDR */
    private List<String> trustedProxies = new ArrayList<>();
    /** 注册接口返回的心跳周期(秒) */
    private int heartbeatSeconds = 30;
    /** 会话滑动过期(小时) */
    private int sessionHours = 12;
    private final Database database = new Database();
    private final Docs docs = new Docs();
    private final Sync sync = new Sync();
    private final Audit audit = new Audit();
    private final Webhook webhook = new Webhook();
    private final Presence presence = new Presence();
    /** 环境定义;空 = 只有一个环境 default(1.0 行为) */
    private List<Environment> environments = new ArrayList<>();

    public static class Database {
        /** 启动时缺表就建表 */
        private boolean createTables = true;
        /** 方言;为空时按 JDBC URL 识别 */
        private String dialect = "";

        public boolean isCreateTables() { return createTables; }
        public void setCreateTables(boolean createTables) { this.createTables = createTables; }
        public String getDialect() { return dialect; }
        public void setDialect(String dialect) { this.dialect = dialect; }
    }

    public static class Docs {
        private int refreshSeconds = 60;
        private Map<String, String> headers = new LinkedHashMap<>();
        private int timeoutSeconds = 5;

        public int getRefreshSeconds() { return refreshSeconds; }
        public void setRefreshSeconds(int refreshSeconds) { this.refreshSeconds = refreshSeconds; }
        public Map<String, String> getHeaders() { return headers; }
        public void setHeaders(Map<String, String> headers) { this.headers = headers; }
        public int getTimeoutSeconds() { return timeoutSeconds; }
        public void setTimeoutSeconds(int timeoutSeconds) { this.timeoutSeconds = timeoutSeconds; }
    }

    public static class Sync {
        private int historySeconds = 10;
        /** 只处理写入超过这个时间的历史记录,避免并发事务提交顺序与 id 顺序不一致时漏记 */
        private long settleMillis = 3000;

        public long getSettleMillis() { return settleMillis; }
        public void setSettleMillis(long settleMillis) { this.settleMillis = settleMillis; }
        public int getHistorySeconds() { return historySeconds; }
        public void setHistorySeconds(int historySeconds) { this.historySeconds = historySeconds; }
    }

    public static class Audit {
        /** 保留天数,0 = 永久 */
        private int retentionDays = 0;
        /** audit.checkpoint 事件的 cron(服务器时区),默认每天 00:05 */
        private String checkpointCron = "0 5 0 * * *";

        public String getCheckpointCron() { return checkpointCron; }
        public void setCheckpointCron(String checkpointCron) { this.checkpointCron = checkpointCron; }

        public int getRetentionDays() { return retentionDays; }
        public void setRetentionDays(int retentionDays) { this.retentionDays = retentionDays; }
    }

    public static class Presence {
        private int ttlSeconds = 90;

        public int getTtlSeconds() { return ttlSeconds; }
        public void setTtlSeconds(int ttlSeconds) { this.ttlSeconds = ttlSeconds; }
    }

    /** 一个环境的数据源(省略 = 主数据源)。密码只在内存里,不进日志、接口。 */
    public static class EnvDatasource {
        private String url;
        private String username;
        private String password;
        private String driverClassName;

        public String getUrl() { return url; }
        public void setUrl(String url) { this.url = url; }
        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
        public String getDriverClassName() { return driverClassName; }
        public void setDriverClassName(String driverClassName) { this.driverClassName = driverClassName; }
    }

    public static class Environment {
        private String key;
        private String name;
        private EnvDatasource datasource;
        private String ruleTablePrefix;
        private boolean protect;
        private boolean readOnly;

        public String getKey() { return key; }
        public void setKey(String key) { this.key = key; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public EnvDatasource getDatasource() { return datasource; }
        public void setDatasource(EnvDatasource datasource) { this.datasource = datasource; }
        public String getRuleTablePrefix() { return ruleTablePrefix; }
        public void setRuleTablePrefix(String ruleTablePrefix) { this.ruleTablePrefix = ruleTablePrefix; }
        /** 配置项 {@code protected} */
        public boolean isProtected() { return protect; }
        public void setProtected(boolean value) { this.protect = value; }
        public boolean isReadOnly() { return readOnly; }
        public void setReadOnly(boolean readOnly) { this.readOnly = readOnly; }
    }

    public static class Webhook {
        /** 失败重试间隔(秒),默认 10、60、300 */
        private List<Integer> retryDelaySeconds = new ArrayList<>(List.of(10, 60, 300));

        public List<Integer> getRetryDelaySeconds() { return retryDelaySeconds; }
        public void setRetryDelaySeconds(List<Integer> retryDelaySeconds) { this.retryDelaySeconds = retryDelaySeconds; }
    }

    public String getPublicUrl() { return publicUrl; }
    public void setPublicUrl(String publicUrl) { this.publicUrl = publicUrl; }
    public String getRuleTablePrefix() { return ruleTablePrefix; }
    public void setRuleTablePrefix(String ruleTablePrefix) { this.ruleTablePrefix = ruleTablePrefix; }
    public List<String> getTrustedProxies() { return trustedProxies; }
    public void setTrustedProxies(List<String> trustedProxies) { this.trustedProxies = trustedProxies; }
    public int getHeartbeatSeconds() { return heartbeatSeconds; }
    public void setHeartbeatSeconds(int heartbeatSeconds) { this.heartbeatSeconds = heartbeatSeconds; }
    public int getSessionHours() { return sessionHours; }
    public void setSessionHours(int sessionHours) { this.sessionHours = sessionHours; }
    public Database getDatabase() { return database; }
    public Docs getDocs() { return docs; }
    public Sync getSync() { return sync; }
    public Audit getAudit() { return audit; }
    public Webhook getWebhook() { return webhook; }
    public Presence getPresence() { return presence; }
    public List<Environment> getEnvironments() { return environments; }
    public void setEnvironments(List<Environment> environments) { this.environments = environments == null ? new ArrayList<>() : environments; }
}
