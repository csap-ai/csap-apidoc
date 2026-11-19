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

        public int getRetentionDays() { return retentionDays; }
        public void setRetentionDays(int retentionDays) { this.retentionDays = retentionDays; }
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
}
