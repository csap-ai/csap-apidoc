package ai.csap.apidoc.mcp.autoconfigure;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 内嵌 MCP 端点配置。
 *
 * <pre>
 * csap:
 *   apidoc:
 *     mcp:
 *       enabled: true
 *       auth:
 *         mode: token
 *         tokens:
 *           - name: claude-code
 *             value: ${CSAP_MCP_TOKEN:}
 * </pre>
 *
 * @author ycf
 * @since 1.3.0
 */
@ConfigurationProperties(prefix = McpProperties.PREFIX)
public class McpProperties {

    public static final String PREFIX = "csap.apidoc.mcp";

    /**
     * 是否开启 MCP 端点(默认关闭;关闭时端点不注册,访问返回 404)。
     */
    private boolean enabled = false;

    /**
     * MCP 端点路径。
     */
    private String path = "/csap/mcp";

    /**
     * 回环调用的基础地址,默认 http://127.0.0.1:{本服务端口}{context-path}{servlet-path}。
     * HTTPS、只监听特定网卡或端口不可直连时需要配置。
     */
    private String baseUrl;

    /**
     * 单次工具调用(回环 HTTP)的超时。
     */
    private Duration callTimeout = Duration.ofSeconds(30);

    /**
     * 工具结果文本的最大长度,超出部分截断。
     */
    private int maxResultChars = 50000;

    /**
     * 没有单独设置的 GET 接口是否默认暴露(写操作始终需要逐个打开)。
     */
    private boolean exposeGetByDefault = true;

    /**
     * 过时(DEPRECATED)的接口是否按普通接口的默认规则暴露;默认 false:过时接口默认不暴露,要用在 DevTools 的 MCP 页里逐个打开。
     * 开发中的接口不隐藏(多数接口没标状态)。
     */
    private boolean exposeDeprecated = false;

    /**
     * 允许的浏览器来源(Origin)。请求带 Origin 头且不在此列表中时返回 403(防 DNS rebinding)。
     * 非浏览器客户端不带 Origin,不受影响。
     */
    private List<String> allowedOrigins = new ArrayList<>();

    /**
     * 发给 AI 的服务说明,默认使用文档标题和描述。
     */
    private String instructions;

    /**
     * 规则文件存储:auto(跟随字段规则的存储策略)、yaml、sqlite、memory。
     */
    private Store store = Store.AUTO;

    /**
     * 规则文件的显式路径(yaml 为 .yaml 文件,sqlite 为 .db 文件)。
     * 不配置时与字段规则放在一起;打成 jar 运行时 classpath 只读,需要持久化 DevTools 修改时配置此项。
     */
    private String configFile;

    private Auth auth = new Auth();

    private Audit audit = new Audit();

    public enum Store {
        AUTO, YAML, SQLITE, MEMORY
    }

    public enum AuthMode {
        /**
         * 访问 MCP 端点需要 Authorization: Bearer 令牌
         */
        TOKEN,
        /**
         * MCP 端点本身不校验,把调用方请求头转发给目标接口,由目标接口自己鉴权
         */
        PASSTHROUGH
    }

    /**
     * 鉴权配置。mode / forward-headers 可被规则文件(DevTools)覆盖;令牌值只来自配置。
     */
    public static class Auth {
        /**
         * 鉴权方式默认值:token 或 passthrough。
         */
        private AuthMode mode = AuthMode.TOKEN;

        /**
         * 令牌列表(名称 + 值)。值请用环境变量占位,不要写进代码仓库。
         */
        private List<Token> tokens = new ArrayList<>();

        /**
         * 从 MCP 请求转发给目标接口的请求头(token 模式下不转发 Authorization)。
         */
        private List<String> forwardHeaders = new ArrayList<>(Arrays.asList("Authorization", "Cookie"));

        /**
         * 每次回环调用都附加的请求头,例如服务账号凭据。
         */
        private Map<String, String> upstreamHeaders = new LinkedHashMap<>();

        public AuthMode getMode() {
            return mode;
        }

        public void setMode(AuthMode mode) {
            this.mode = mode;
        }

        public List<Token> getTokens() {
            return tokens;
        }

        public void setTokens(List<Token> tokens) {
            this.tokens = tokens;
        }

        public List<String> getForwardHeaders() {
            return forwardHeaders;
        }

        public void setForwardHeaders(List<String> forwardHeaders) {
            this.forwardHeaders = forwardHeaders;
        }

        public Map<String, String> getUpstreamHeaders() {
            return upstreamHeaders;
        }

        public void setUpstreamHeaders(Map<String, String> upstreamHeaders) {
            this.upstreamHeaders = upstreamHeaders;
        }
    }

    /**
     * 令牌
     */
    public static class Token {
        /**
         * 名称,出现在审计日志里(令牌值不会被记录)。
         */
        private String name;
        /**
         * 令牌值。
         */
        private String value;

        public Token() {
        }

        public Token(String name, String value) {
            this.name = name;
            this.value = value;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getValue() {
            return value;
        }

        public void setValue(String value) {
            this.value = value;
        }
    }

    /**
     * 审计存储方式
     */
    public enum AuditStore {
        /**
         * 只记录在内存环形缓冲
         */
        MEMORY,
        /**
         * 同时写本地 SQLite 文件
         */
        SQLITE,
        /**
         * 同时写入数据库模式的库
         */
        DATABASE
    }

    /**
     * 审计配置
     */
    public static class Audit {
        /**
         * 是否记录审计日志。
         */
        private boolean enabled = true;
        /**
         * 内存环形缓冲保留的条数。
         */
        private int capacity = 500;
        /**
         * 审计存储:memory(默认,只在内存环形缓冲)、sqlite(同时写本地 SQLite 文件)、
         * database(同时写入数据库模式的库,表 {前缀}mcp_audit,需要 @EnableApidoc(paramType = DATABASE))。
         * 不设置时由 persist 决定。
         */
        private AuditStore store;
        /**
         * 是否同时写入 SQLite;等同 store=sqlite(兼容 1.3.0 的配置)。
         */
        private boolean persist = false;
        /**
         * 审计 SQLite 文件路径(相对路径相对于工作目录)。
         */
        private String sqliteFile = "csap-mcp-audit.db";
        /**
         * SQLite / 数据库中最多保留的条数(数据库按 service 区分)。
         */
        private int maxRows = 10000;
        /**
         * 参数 / 结果摘要的最大长度。
         */
        private int maxSummaryChars = 512;
        /**
         * 额外需要打码的字段名(在内置的 password、token、secret 等之外)。
         */
        private List<String> maskFields = new ArrayList<>();

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getCapacity() {
            return capacity;
        }

        public void setCapacity(int capacity) {
            this.capacity = capacity;
        }

        public AuditStore getStore() {
            return store;
        }

        public void setStore(AuditStore store) {
            this.store = store;
        }

        /**
         * 实际使用的存储:显式配置的 store 优先,否则 persist=true 为 sqlite,其余为 memory。
         */
        public AuditStore effectiveStore() {
            if (store != null) {
                return store;
            }
            return persist ? AuditStore.SQLITE : AuditStore.MEMORY;
        }

        public boolean isPersist() {
            return persist;
        }

        public void setPersist(boolean persist) {
            this.persist = persist;
        }

        public String getSqliteFile() {
            return sqliteFile;
        }

        public void setSqliteFile(String sqliteFile) {
            this.sqliteFile = sqliteFile;
        }

        public int getMaxRows() {
            return maxRows;
        }

        public void setMaxRows(int maxRows) {
            this.maxRows = maxRows;
        }

        public int getMaxSummaryChars() {
            return maxSummaryChars;
        }

        public void setMaxSummaryChars(int maxSummaryChars) {
            this.maxSummaryChars = maxSummaryChars;
        }

        public List<String> getMaskFields() {
            return maskFields;
        }

        public void setMaskFields(List<String> maskFields) {
            this.maskFields = maskFields;
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public Duration getCallTimeout() {
        return callTimeout;
    }

    public void setCallTimeout(Duration callTimeout) {
        this.callTimeout = callTimeout;
    }

    public int getMaxResultChars() {
        return maxResultChars;
    }

    public void setMaxResultChars(int maxResultChars) {
        this.maxResultChars = maxResultChars;
    }

    public boolean isExposeDeprecated() {
        return exposeDeprecated;
    }

    public void setExposeDeprecated(boolean exposeDeprecated) {
        this.exposeDeprecated = exposeDeprecated;
    }

    public boolean isExposeGetByDefault() {
        return exposeGetByDefault;
    }

    public void setExposeGetByDefault(boolean exposeGetByDefault) {
        this.exposeGetByDefault = exposeGetByDefault;
    }

    public List<String> getAllowedOrigins() {
        return allowedOrigins;
    }

    public void setAllowedOrigins(List<String> allowedOrigins) {
        this.allowedOrigins = allowedOrigins;
    }

    public String getInstructions() {
        return instructions;
    }

    public void setInstructions(String instructions) {
        this.instructions = instructions;
    }

    public Store getStore() {
        return store;
    }

    public void setStore(Store store) {
        this.store = store;
    }

    public String getConfigFile() {
        return configFile;
    }

    public void setConfigFile(String configFile) {
        this.configFile = configFile;
    }

    public Auth getAuth() {
        return auth;
    }

    public void setAuth(Auth auth) {
        this.auth = auth;
    }

    public Audit getAudit() {
        return audit;
    }

    public void setAudit(Audit audit) {
        this.audit = audit;
    }
}
